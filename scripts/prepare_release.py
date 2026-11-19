"""Prepare tag releases without publishing or starting Minecraft."""

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import zipfile

ROOT = Path(__file__).resolve().parents[1]


def properties(path):
    return dict(
        line.strip().split('=', 1)
        for line in path.read_text(encoding='utf-8-sig').splitlines()
        if '=' in line and not line.lstrip().startswith('#')
    )


def plan(tag=None):
    config = properties(ROOT / 'gradle.properties')
    version = config['mod_version']
    targets = json.loads((ROOT / 'gradle/minecraft-targets.json').read_text())
    groups = json.loads((ROOT / 'gradle/release-groups.json').read_text())
    if len(targets) != len(set(targets)) or not targets:
        raise ValueError('Minecraft targets must be unique and nonempty')
    grouped = [member for group in groups for member in group]
    if len(grouped) != len(set(grouped)) or not set(grouped) <= set(targets):
        raise ValueError('Release groups overlap or include disabled targets')
    for group in groups:
        if len(group) < 2:
            raise ValueError('A shared release group must have at least two targets')
    for target in targets:
        if not re.fullmatch(r'\d+(?:\.\d+)+', target):
            raise ValueError(f'Invalid Minecraft target: {target}')
        if not (ROOT / 'versions' / target / 'version.properties').is_file():
            raise ValueError(f'Missing configuration for {target}')
    if tag and (tag != f'v{version}' or not re.fullmatch(
        r'v\d+\.\d+\.\d+(?:-[0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*)?', tag
    )):
        raise ValueError(f'Tag must be v{version}, matching mod_version in gradle.properties')
    releases = []
    for target in targets:
        members = next((group for group in groups if target in group), [target])
        if target != members[0]:
            continue
        label = f'{members[0]}-{members[-1]}' if len(members) > 1 else target
        releases.append({
            'file': f"{config['archives_base_name']}-{version}+mc{label}.jar",
            'minecraft_versions': members,
        })
    return {'version': version, 'targets': targets, 'releases': releases,
            'prerelease': '-' in version}


def prepare_assets(release, directory):
    expected = {item['file'] for item in release['releases']}
    actual = {path.name for path in directory.glob('*.jar')}
    if actual != expected:
        raise ValueError(f'Release files differ: missing={expected - actual}, unexpected={actual - expected}')
    for item in release['releases']:
        path = directory / item['file']
        with zipfile.ZipFile(path) as jar:
            if jar.testzip() is not None:
                raise ValueError(f'Corrupt JAR: {path.name}')
            metadata = json.loads(jar.read('fabric.mod.json'))
        members = item['minecraft_versions']
        compatibility = f'>={members[0]} <={members[-1]}' if len(members) > 1 else members[0]
        label = f'{members[0]}-{members[-1]}' if len(members) > 1 else members[0]
        if (metadata['id'] != 'reterra' or
                metadata['version'] != f"{release['version']}+mc{label}" or
                metadata['depends']['minecraft'] != compatibility):
            raise ValueError(f'Wrong release metadata in {path.name}')
        item['sha256'] = hashlib.sha256(path.read_bytes()).hexdigest()
    (directory / 'release-manifest.json').write_text(
        json.dumps(release, indent=2) + '\n', encoding='utf-8')
    (directory / 'SHA256SUMS.txt').write_text(''.join(
        f"{item['sha256']}  {item['file']}\n" for item in release['releases']), encoding='utf-8')
    notes = [f"# ReTerra {release['version']}", '',
             'Install one ReTerra JAR and Fabric API for your Minecraft version.', '',
             '| File | Minecraft versions |', '| --- | --- |']
    notes.extend(f"| `{item['file']}` | {', '.join(item['minecraft_versions'])} |"
                 for item in release['releases'])
    changelog_path = ROOT / 'CHANGELOG.md'
    changelog = changelog_path.read_text(encoding='utf-8-sig') if changelog_path.is_file() else ''
    # Use only the section for this release, never notes from a previous version.
    sections = re.split(r'(?m)^# ', '\n' + changelog)
    for section in sections:
        heading, _, body = section.partition('\n')
        if heading.strip() in (f"ReTerra {release['version']}", release['version']):
            notes.extend(['', body.strip()])
            break
    (directory / 'release-notes.md').write_text('\n'.join(notes) + '\n', encoding='utf-8')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--tag', help='Validate a pushed v-prefixed release tag')
    parser.add_argument('--assets', type=Path, help='Verify downloaded artifacts and prepare release files')
    args = parser.parse_args()
    release = plan(args.tag)
    if args.assets:
        prepare_assets(release, args.assets)
    if output := os.environ.get('GITHUB_OUTPUT'):
        with open(output, 'a', encoding='utf-8') as stream:
            stream.write('matrix=' + json.dumps({'minecraft': release['targets']}) + '\n')
            stream.write(f"prerelease={str(release['prerelease']).lower()}\n")
    print(f"ReTerra {release['version']}: {len(release['targets'])} targets, "
          f"{len(release['releases'])} release JARs")


if __name__ == '__main__':
    main()
