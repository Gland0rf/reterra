"""Offline checks for upload safety and resuming interrupted publishes."""

from contextlib import redirect_stdout
from email import policy
from email.parser import BytesParser
import hashlib
import io
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import publish_modrinth as publisher


class TokenLoadingTests(unittest.TestCase):
    def test_project_id_can_be_loaded_from_dotenv(self):
        with patch.dict('os.environ', {}, clear=True), \
                patch.object(Path, 'read_text', return_value='MODRINTH_PROJECT_ID=fixture-project\nMODRINTH_TOKEN=ignored'):
            self.assertEqual(publisher.local_setting('MODRINTH_PROJECT_ID'), 'fixture-project')

    def test_terminal_token_takes_priority_without_reading_dotenv(self):
        with patch.dict('os.environ', {'MODRINTH_TOKEN': ' terminal-token '}, clear=True), \
                patch.object(Path, 'read_text') as read:
            self.assertEqual(publisher.modrinth_token(), 'terminal-token')
            read.assert_not_called()

    def test_dotenv_quotes_comments_and_export(self):
        # Mock file reads: these checks never open the real protected .env.
        for assignment in ['MODRINTH_TOKEN=fixture-token',
                           ' MODRINTH_TOKEN = "fixture-token" # upload token',
                           "export MODRINTH_TOKEN='fixture-token'"]:
            with self.subTest(assignment=assignment), \
                    patch.dict('os.environ', {}, clear=True), \
                    patch.object(Path, 'read_text', return_value='# comment\nOTHER=ignored\n' + assignment):
                self.assertEqual(publisher.modrinth_token(), 'fixture-token')

    def test_absent_or_empty_dotenv_token_allows_prompt_fallback(self):
        for text in ['OTHER=ignored', 'MODRINTH_TOKEN=', 'MODRINTH_TOKEN=""']:
            with patch.dict('os.environ', {}, clear=True), \
                    patch.object(Path, 'read_text', return_value=text):
                self.assertIsNone(publisher.modrinth_token())
        with patch.dict('os.environ', {}, clear=True), \
                patch.object(Path, 'read_text', side_effect=FileNotFoundError):
            self.assertIsNone(publisher.modrinth_token())

    def test_invalid_dotenv_error_does_not_expose_value(self):
        with patch.dict('os.environ', {}, clear=True), \
                patch.object(Path, 'read_text', return_value='MODRINTH_TOKEN="fixture-secret'):
            with self.assertRaises(ValueError) as error:
                publisher.modrinth_token()
        self.assertNotIn('fixture-secret', str(error.exception))


class PublishingTests(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.items = []
        for target in ['1.21.4', '1.21.5']:
            path = Path(self.directory.name) / f'reterra-0.1.1+mc{target}.jar'
            path.write_bytes(b'fixture-' + target.encode())
            self.items.append({'file': path.name, 'path': path, 'minecraft_versions': [target],
                               'version_number': f'0.1.1+mc{target}',
                               'sha512': hashlib.sha512(path.read_bytes()).hexdigest()})
        self.existing = []
        self.version_list_error = None
        self.writes = []
        self.project_type = 'mod'
        self.project_versions = []
        self.manifest = {'version': '0.1.1', 'targets': ['1.21.4', '1.21.5'], 'releases': self.items}
        self.release = {'html_url': 'https://github.com/Gland0rf/reterra/releases/tag/v0.1.1',
                        'body': 'Release notes'}

    def get_json(self, url, token=None):
        if url.endswith('/project/fabric-api'):
            return {'id': 'fabric-id'}
        if url.endswith('/tag/game_version'):
            return [{'version': value} for value in self.manifest['targets']]
        if url.endswith('/project/project-id/version'):
            if self.version_list_error:
                raise ValueError(self.version_list_error)
            return self.existing
        if url.endswith('/project/reterra'):
            return {'id': 'project-id', 'slug': 'reterra', 'title': 'ReTerra',
                    'project_type': self.project_type, 'versions': self.project_versions}
        self.fail('Unexpected request: ' + url)

    @staticmethod
    def read_multipart(data, content_type):
        message = BytesParser(policy=policy.default).parsebytes(
            ('Content-Type: ' + content_type + '\r\nMIME-Version: 1.0\r\n\r\n').encode() + data)
        parts = {part.get_param('name', header='content-disposition'): part
                 for part in message.iter_parts()}
        return json.loads(parts['data'].get_payload(decode=True)), parts['jar'].get_payload(decode=True)

    def upload(self, url, *, token=None, data=None, content_type=None):
        self.assertEqual(url, publisher.MODRINTH + '/version')
        self.assertEqual(token, 'fixture-token')
        payload, jar = self.read_multipart(data, content_type)
        self.writes.append((payload, jar))
        return json.dumps({'id': 'created'}).encode()

    def run_command(self, *args):
        with patch.object(publisher, 'download_release', return_value=(self.release, self.manifest)), \
                patch.object(publisher, 'get_json', side_effect=self.get_json), \
                patch.object(publisher, 'fetch', side_effect=self.upload), \
                patch.dict('os.environ', {'MODRINTH_TOKEN': 'fixture-token'}), \
                redirect_stdout(io.StringIO()):
            publisher.main(['--tag', 'v0.1.1', '--project', 'reterra', *args])

    def test_preview_never_uploads(self):
        self.run_command()
        self.assertEqual(self.writes, [])

    def test_private_version_list_failure_explains_required_scopes_without_uploading(self):
        self.version_list_error = 'HTTP 404'
        with self.assertRaisesRegex(ValueError, 'Read versions \\(VERSION_READ\\)'):
            self.run_command('--publish')
        self.assertEqual(self.writes, [])

    def test_first_upload_accepts_modrinths_empty_generic_project(self):
        self.project_type = 'project'
        self.run_command('--publish')
        self.assertEqual(len(self.writes), 2)
        self.assertTrue(all(payload['loaders'] == ['fabric'] for payload, _ in self.writes))

    def test_known_non_mod_projects_are_rejected_before_upload(self):
        for project_type in ['modpack', 'shader', 'resourcepack', None]:
            with self.subTest(project_type=project_type):
                self.project_type = project_type
                with self.assertRaisesRegex(ValueError, 'has type'):
                    self.run_command('--publish')
                self.assertEqual(self.writes, [])

    def test_generic_project_with_existing_versions_is_not_assumed_to_be_a_mod(self):
        self.project_type = 'project'
        self.project_versions = ['existing-version']
        with self.assertRaisesRegex(ValueError, 'has type'):
            self.run_command('--publish')
        self.assertEqual(self.writes, [])

    def test_project_default_is_resolved_from_local_configuration(self):
        with patch.object(publisher, 'local_setting', return_value='reterra') as setting:
            with patch.object(publisher, 'download_release', side_effect=ValueError('fixture stop')):
                with self.assertRaisesRegex(ValueError, 'fixture stop'):
                    publisher.main(['--tag', 'v0.1.1'])
        setting.assert_called_once_with('MODRINTH_PROJECT_ID')

    def test_upload_preserves_files_and_sets_compatibility_dependency_and_channel(self):
        self.run_command('--publish')
        self.assertEqual(len(self.writes), 2)
        for item, (payload, jar) in zip(self.items, self.writes):
            self.assertEqual(jar, item['path'].read_bytes())
            self.assertEqual(payload['version_number'], item['version_number'])
            self.assertEqual(payload['game_versions'], item['minecraft_versions'])
            self.assertEqual(payload['loaders'], ['fabric'])
            self.assertEqual(payload['version_type'], 'release')
            self.assertEqual(payload['dependencies'], [{'project_id': 'fabric-id', 'dependency_type': 'required'}])

    def existing_version(self, item):
        return {'version_number': item['version_number'],
                'files': [{'hashes': {'sha512': item['sha512']}}],
                'game_versions': item['minecraft_versions'], 'loaders': ['fabric'], 'status': 'listed',
                'dependencies': [{'project_id': 'fabric-id', 'dependency_type': 'required'}]}

    def test_rerun_skips_an_upload_that_already_succeeded(self):
        self.existing.append(self.existing_version(self.items[0]))
        self.run_command('--publish')
        self.assertEqual(len(self.writes), 1)
        self.assertEqual(self.writes[0][0]['version_number'], self.items[1]['version_number'])

    def test_only_modrinth_attachment_filename_changes(self):
        for suffix in ['1.21.4', '26.1-26.1.2']:
            item = dict(self.items[0], version_number=f'0.1.1+mc{suffix}')
            data, content_type = publisher.multipart({'version_number': item['version_number']}, item)
            message = BytesParser(policy=policy.default).parsebytes(
                ('Content-Type: ' + content_type + '\r\n\r\n').encode() + data)
            attachment = list(message.iter_parts())[1]
            self.assertEqual(attachment.get_filename(), f'0.1.1+{suffix}.jar')
            payload, jar = self.read_multipart(data, content_type)
            self.assertEqual(payload['version_number'], f'0.1.1+mc{suffix}')
            self.assertEqual(jar, item['path'].read_bytes())
            self.assertEqual(item['path'].name, self.items[0]['file'])

    def test_conflict_in_last_file_prevents_all_uploads(self):
        conflict = self.existing_version(self.items[1])
        conflict['files'][0]['hashes']['sha512'] = 'different-file'
        self.existing.append(conflict)
        with self.assertRaisesRegex(ValueError, 'conflicts'):
            self.run_command('--publish')
        self.assertEqual(self.writes, [])

    def test_incorrect_existing_compatibility_is_not_silently_skipped(self):
        conflict = self.existing_version(self.items[0])
        conflict['game_versions'] = ['1.21.1']
        with self.assertRaisesRegex(ValueError, 'conflicts'):
            publisher.existing_match(self.items[0], [conflict], 'fabric-id')

    def test_corrupt_download_is_rejected(self):
        asset = {'name': 'release.jar', 'size': 3,
                 'digest': 'sha256:' + hashlib.sha256(b'jar').hexdigest(),
                 'browser_download_url': 'https://example.invalid/release.jar'}
        with patch.object(publisher, 'fetch', return_value=b'bad'):
            with self.assertRaisesRegex(ValueError, 'mismatch'):
                publisher.asset_bytes(asset, Path(self.directory.name))
        self.assertFalse((Path(self.directory.name) / 'release.jar').exists())


if __name__ == '__main__':
    unittest.main()
