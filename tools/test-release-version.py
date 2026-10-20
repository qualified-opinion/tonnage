"""Run the real release-version shell step against a temporary Git history; never publish."""
import os
from pathlib import Path
import subprocess
import tempfile
import textwrap
import unittest

WORKFLOW = Path(__file__).resolve().parents[1] / '.github/workflows/android-release.yml'
STEP = textwrap.dedent(WORKFLOW.read_text().split('      - name: Work out version\n', 1)[1]
                      .split('        run: |\n', 1)[1].split('\n      - name:', 1)[0])


class ReleaseVersionTest(unittest.TestCase):
    def test_tags_and_manual_runs(self):
        with tempfile.TemporaryDirectory() as directory:
            env = dict(os.environ, GIT_AUTHOR_NAME='Test', GIT_COMMITTER_NAME='Test',
                       GIT_AUTHOR_EMAIL='test@example.invalid', GIT_COMMITTER_EMAIL='test@example.invalid')

            def git(*args):
                return subprocess.check_output(['git', *args], cwd=directory, env=env, text=True).strip()

            git('init', '--quiet')
            git('commit', '--allow-empty', '-qm', 'Ancestor')
            ancestor = git('rev-parse', 'HEAD')
            git('commit', '--allow-empty', '-qm', 'Main')
            main = git('rev-parse', 'HEAD')
            git('update-ref', 'refs/remotes/origin/main', main)
            git('checkout', '-qb', 'feature', ancestor)
            git('commit', '--allow-empty', '-qm', 'Unmerged')
            unmerged = git('rev-parse', 'HEAD')
            output = Path(directory) / 'github-env'
            for sha, ref, version, allowed in [
                (main, 'refs/tags/v1.2.3', '', True),
                (ancestor, 'refs/tags/v1.2.3', '', True),
                (unmerged, 'refs/tags/v1.2.3', '', False),
                (main, 'refs/heads/main', '1.2.3', True),
                (unmerged, 'refs/heads/feature', '1.2.3', False),
                (unmerged, 'refs/heads/feature', '', True),
            ]:
                with self.subTest(ref=ref, sha=sha, version=version):
                    output.write_text('')
                    result = subprocess.run(['bash', '-e', '-c', STEP], cwd=directory, env=dict(
                        env, GITHUB_SHA=sha, GITHUB_REF=ref, GITHUB_REF_NAME=ref.rsplit('/', 1)[-1],
                        INPUT_VERSION=version, GITHUB_ENV=str(output), GITHUB_RUN_NUMBER='42',
                    ), text=True, capture_output=True)
                    self.assertEqual(allowed, result.returncode == 0, result.stdout + result.stderr)
                    if allowed:
                        expected = '1.2.3' if version or ref.startswith('refs/tags/') else '1.0.42'
                        self.assertIn(f'TONNAGE_VERSION_NAME={expected}', output.read_text())
                        self.assertIn('TONNAGE_VERSION_CODE=42', output.read_text())
                    else:
                        self.assertIn('::error::', result.stdout)
                        self.assertEqual('', output.read_text())


if __name__ == '__main__':
    unittest.main()
