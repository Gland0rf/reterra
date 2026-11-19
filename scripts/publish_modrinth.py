"""Upload a chosen GitHub release to Modrinth. Preview by default; --publish uploads.

Uses Python 3.10+ and the standard library. No Gradle build is started.
"""

import argparse
import getpass
import hashlib
import json
import os
from pathlib import Path
import re
import shlex
import sys
import urllib.error
import urllib.parse
import urllib.request
import uuid
import zipfile

from validate_release import validate

ROOT = Path(__file__).resolve().parents[1]
GITHUB = 'https://api.github.com/repos/Gland0rf/reterra'
MODRINTH = 'https://api.modrinth.com/v2'
USER_AGENT = 'Gland0rf/ReTerra-release-tool (github.com/Gland0rf/reterra)'


def local_setting(name):
    value = os.environ.get(name, '').strip()
    if value:
        return value
    try:
        lines = (ROOT / '.env').read_text(encoding='utf-8-sig').splitlines()
    except FileNotFoundError:
        return None
    for line in lines:
        match = re.fullmatch(r'\s*(?:export\s+)?' + re.escape(name) + r'\s*=(.*)', line)
        if not match:
            continue
        try:
            values = shlex.split(match[1], comments=True, posix=True)
        except ValueError:
            raise ValueError(f'Invalid quoting for {name} in the root .env') from None
        if len(values) > 1:
            raise ValueError(f'{name} in the root .env must be one value; quote it if needed')
        return (values[0].strip() or None) if values else None
    return None


def modrinth_token():
    return local_setting('MODRINTH_TOKEN')


def fetch(url, *, token=None, data=None, content_type=None):
    headers = {'User-Agent': USER_AGENT}
    if token:
        if not url.startswith(MODRINTH + '/'):
            raise ValueError('Modrinth credentials cannot be sent to another service')
        headers['Authorization'] = token
    if content_type:
        headers['Content-Type'] = content_type
    request = urllib.request.Request(url, data=data, headers=headers)
    try:
        with urllib.request.urlopen(request, timeout=90) as response:
            return response.read()
    except urllib.error.HTTPError as error:
        # Do not print request headers or credentials.
        endpoint = urllib.parse.urlsplit(url)
        raise ValueError(f'HTTP {error.code} from {endpoint.netloc}{endpoint.path}: '
                         'check the project, token permissions and service availability') from None


def get_json(url, token=None):
    return json.loads(fetch(url, token=token))


def asset_bytes(asset, directory):
    name = asset['name']
    if Path(name).name != name or '/' in name or '\\' in name:
        raise ValueError('Unsafe release asset filename')
    path = directory / name
    data = path.read_bytes() if path.is_file() else None

    def matches(value):
        digest = asset.get('digest')
        return (value is not None and len(value) == asset['size']
                and digest == 'sha256:' + hashlib.sha256(value).hexdigest())

    if not matches(data):
        data = fetch(asset['browser_download_url'])
        if not matches(data):
            raise ValueError(f'GitHub size or SHA-256 mismatch: {name}')
        path.write_bytes(data)
    return data


def download_release(tag):
    if not re.fullmatch(r'v\d+\.\d+\.\d+(?:-[0-9A-Za-z.-]+)?', tag):
        raise ValueError('Use a version tag such as v0.1.1')
    release = get_json(GITHUB + '/releases/tags/' + tag)
    if release['draft'] or release['tag_name'] != tag:
        raise ValueError('Choose a published GitHub release')
    directory = ROOT / '.tools' / 'published-release-audit' / tag
    directory.mkdir(parents=True, exist_ok=True)
    assets = {asset['name']: asset for asset in release['assets']}
    manifest = json.loads(asset_bytes(assets['release-manifest.json'], directory))
    sums = asset_bytes(assets['SHA256SUMS.txt'], directory).decode('utf-8')
    checksums = dict(line.split('  ', 1)[::-1] for line in sums.splitlines() if line)
    items = manifest['releases']
    names = [item['file'] for item in items]
    if not items or len(names) != len(set(names)):
        raise ValueError('Release manifest has no files or duplicate files')
    if set(names) != {name for name in assets if name.endswith('.jar')} or set(names) != set(checksums):
        raise ValueError('GitHub JARs, manifest and checksums disagree')
    coverage = [version for item in items for version in item['minecraft_versions']]
    if len(coverage) != len(set(coverage)) or sorted(coverage) != sorted(manifest['targets']):
        raise ValueError('Minecraft version coverage is incomplete or overlapping')
    if tag != 'v' + manifest['version']:
        raise ValueError('Release tag and manifest version disagree')
    for item in items:
        name = item['file']
        if not re.fullmatch(r'reterra-[A-Za-z0-9.+_-]+\.jar', name):
            raise ValueError(f'Unexpected release filename: {name}')
        data = asset_bytes(assets[name], directory)
        if hashlib.sha256(data).hexdigest() != item['sha256'] or item['sha256'] != checksums[name]:
            raise ValueError(f'Release manifest or checksum mismatch: {name}')
        path = directory / name
        validate(path, None)
        with zipfile.ZipFile(path) as jar:
            metadata = json.loads(jar.read('fabric.mod.json'))
        versions = item['minecraft_versions']
        if not versions:
            raise ValueError(f'No Minecraft versions for {name}')
        label = versions[0] if len(versions) == 1 else f'{versions[0]}-{versions[-1]}'
        expected_version = f"{manifest['version']}+mc{label}"
        compatibility = versions[0] if len(versions) == 1 else f'>={versions[0]} <={versions[-1]}'
        if metadata['version'] != expected_version or metadata['depends']['minecraft'] != compatibility:
            raise ValueError(f'Minecraft compatibility or mod version mismatch: {name}')
        item.update(path=path, sha512=hashlib.sha512(data).hexdigest(), version_number=expected_version)
    return release, manifest


def existing_match(item, versions, dependency_id):
    for version in versions:
        same_number = version['version_number'] == item['version_number']
        same_file = any(file.get('hashes', {}).get('sha512') == item['sha512']
                        for file in version.get('files', []))
        if not same_number and not same_file:
            continue
        correct = (same_file and set(version['game_versions']) == set(item['minecraft_versions'])
                   and version['loaders'] == ['fabric']
                   and version.get('status') == 'listed'
                   and any(dep.get('project_id') == dependency_id and dep['dependency_type'] == 'required'
                           for dep in version.get('dependencies', [])))
        if not correct:
            raise ValueError(f"Existing Modrinth version conflicts with {item['file']}; "
                             'review it on Modrinth before uploading')
        return version
    return None


def modrinth_filename(item):
    return item['version_number'].replace('+mc', '+', 1) + '.jar'


def multipart(payload, item):
    boundary = 'reterra-' + uuid.uuid4().hex
    data = (f'--{boundary}\r\nContent-Disposition: form-data; name="data"\r\n'
            'Content-Type: application/json\r\n\r\n').encode() + json.dumps(payload).encode('utf-8')
    data += (f'\r\n--{boundary}\r\nContent-Disposition: form-data; name="jar"; '
             f'filename="{modrinth_filename(item)}"\r\nContent-Type: application/java-archive\r\n\r\n').encode()
    data += item['path'].read_bytes() + f'\r\n--{boundary}--\r\n'.encode()
    return data, 'multipart/form-data; boundary=' + boundary


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--tag', required=True, help='Published GitHub tag, e.g. v0.1.1')
    parser.add_argument('--project',
                        help='Modrinth project ID or slug (or MODRINTH_PROJECT_ID in the environment/.env; default: reterra)')
    parser.add_argument('--publish', action='store_true', help='Upload; without this flag, only preview')
    parser.add_argument('--auth', action='store_true', help='Ask for a token even when previewing a private project')
    parser.add_argument('--channel', choices=['release', 'beta', 'alpha'],
                        help='Default: release for normal tags, beta for tags with a suffix')
    args = parser.parse_args(argv)
    args.project = args.project or local_setting('MODRINTH_PROJECT_ID') or 'reterra'
    release, manifest = download_release(args.tag)
    token = modrinth_token()
    if args.publish or args.auth:
        token = token or getpass.getpass('Modrinth token (hidden): ')
        token = token.strip()
        if not token:
            raise ValueError('A Modrinth token with Create versions permission is required')
    try:
        project = get_json(MODRINTH + '/project/' + urllib.parse.quote(args.project, safe=''), token)
    except ValueError as error:
        raise ValueError(f'{error}. Could not access project {args.project!r}. '
                         'Try --project with the actual project ID from Modrinth. '
                         'For a private project, the token must belong to an account with access '
                         'and include permission to read projects') from None
    # Modrinth v2 reports "project" when a new project has no inferred types yet.
    # Its first uploaded version establishes the mod type from the file/loader.
    project_type = project.get('project_type')
    empty_project = project_type == 'project' and project.get('versions') == []
    if project_type != 'mod' and not empty_project:
        raise ValueError(f"Destination {project.get('slug') or project['id']!r} has type "
                         f"{project_type!r}; expected 'mod' or a new empty 'project'")
    dependency = get_json(MODRINTH + '/project/fabric-api')
    supported = {version['version'] for version in get_json(MODRINTH + '/tag/game_version')}
    unknown = set(manifest['targets']) - supported
    if unknown:
        raise ValueError('Modrinth does not recognize these Minecraft versions: ' + ', '.join(sorted(unknown)))
    try:
        existing = get_json(MODRINTH + '/project/' + project['id'] + '/version', token)
    except ValueError as error:
        raise ValueError(f'{error}. The project lookup succeeded, but its versions could not be read. '
                         'For a private project, enable BOTH Read projects (PROJECT_READ) and '
                         'Read versions (VERSION_READ) on your Modrinth token; keep Create versions '
                         '(VERSION_CREATE) enabled for uploading') from None
    channel = args.channel or ('beta' if '-' in manifest['version'] else 'release')
    pending = []
    print(f"Destination: {project['title']} (https://modrinth.com/mod/{project['slug']})")
    print(f"Source: {release['html_url']}\nChannel: {channel}")
    for item in manifest['releases']:
        match = existing_match(item, existing, dependency['id'])
        print(f"{'SKIP' if match else 'UPLOAD'} {item['file']} -> {modrinth_filename(item)} "
              f"(Minecraft {', '.join(item['minecraft_versions'])})")
        if not match:
            pending.append(item)
    if not args.publish:
        print(f'Preview only: {len(pending)} uploads. Add --publish to upload these files.')
        return
    # All files and conflicts are checked before the first upload. Reruns skip completed uploads.
    for item in pending:
        versions = item['minecraft_versions']
        label = versions[0] if len(versions) == 1 else f'{versions[0]}-{versions[-1]}'
        payload = {
            'project_id': project['id'], 'version_number': item['version_number'],
            'name': f"ReTerra {manifest['version']} (Minecraft {label})",
            'changelog': release.get('body') or f"Published from {release['html_url']}",
            'dependencies': [{'project_id': dependency['id'], 'dependency_type': 'required'}],
            'game_versions': versions, 'version_type': channel, 'loaders': ['fabric'],
            'featured': False, 'status': 'listed', 'environment': 'client_and_server',
            'file_parts': ['jar'], 'primary_file': 'jar',
        }
        data, content_type = multipart(payload, item)
        uploaded = json.loads(fetch(MODRINTH + '/version', token=token, data=data, content_type=content_type))
        print(f"Published {modrinth_filename(item)}: https://modrinth.com/mod/{project['slug']}/version/{uploaded['id']}")
    print(f'Finished: {len(pending)} uploaded, {len(manifest["releases"]) - len(pending)} already present.')


if __name__ == '__main__':
    try:
        main()
    except (ValueError, KeyError, OSError, zipfile.BadZipFile, AssertionError) as error:
        print(f'Upload stopped: {error}. Rerun the same command after resolving the problem.', file=sys.stderr)
        sys.exit(1)
