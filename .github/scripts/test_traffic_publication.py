import importlib.util
from pathlib import Path
import subprocess
import tempfile
import unittest


spec = importlib.util.spec_from_file_location("publisher", Path(__file__).with_name("publish_traffic_badges.py"))
publisher = importlib.util.module_from_spec(spec)
spec.loader.exec_module(publisher)


class TrafficPublicationTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.remote = self.root / "remote.git"
        self.remote.mkdir()
        publisher.git(self.remote, "init", "--bare", "--initial-branch=main")
        self.source = self.root / "source"
        self.write_badges("first")

    def write_badges(self, version):
        for name in publisher.FILES:
            badge = self.source / name
            badge.parent.mkdir(parents=True, exist_ok=True)
            badge.write_text('<svg xmlns="http://www.w3.org/2000/svg"><title>' + version + " " + badge.name + "</title></svg>")

    def head(self):
        return publisher.git(self.remote, "rev-parse", publisher.BRANCH).stdout.strip()

    def read_badge(self, name):
        return publisher.git(self.remote, "show", publisher.BRANCH + ":" + name).stdout

    def test_first_publish_creates_only_data_branch_and_two_badges(self):
        self.assertTrue(publisher.publish(self.source, self.remote))
        branches = publisher.git(self.remote, "for-each-ref", "--format=%(refname)", "refs/heads").stdout.splitlines()
        self.assertEqual(["refs/heads/readme-metrics"], branches)
        files = publisher.git(self.remote, "ls-tree", "-r", "--name-only", publisher.BRANCH).stdout.splitlines()
        self.assertEqual(sorted(publisher.FILES), files)
        for name in publisher.FILES:
            self.assertEqual((self.source / name).read_text(), self.read_badge(name))

    def test_unchanged_rerun_does_not_create_commit(self):
        publisher.publish(self.source, self.remote)
        previous = self.head()
        self.assertFalse(publisher.publish(self.source, self.remote))
        self.assertEqual(previous, self.head())

    def test_refresh_fast_forwards_both_badges(self):
        publisher.publish(self.source, self.remote)
        previous = self.head()
        self.write_badges("second")
        self.assertTrue(publisher.publish(self.source, self.remote))
        self.assertEqual(previous, publisher.git(self.remote, "rev-parse", publisher.BRANCH + "^").stdout.strip())
        for name in publisher.FILES:
            self.assertEqual((self.source / name).read_text(), self.read_badge(name))

    def test_unrelated_branch_content_is_not_overwritten(self):
        publisher.publish(self.source, self.remote)
        editor = self.root / "editor"
        publisher.git(self.root, "clone", "--branch", publisher.BRANCH, str(self.remote), str(editor))
        publisher.git(editor, "config", "user.name", "Fixture")
        publisher.git(editor, "config", "user.email", "fixture@example.invalid")
        (editor / "unrelated.txt").write_text("preserve me")
        publisher.git(editor, "add", "unrelated.txt")
        publisher.git(editor, "commit", "-m", "Unrelated fixture")
        publisher.git(editor, "push", "origin", "HEAD")
        previous = self.head()
        self.write_badges("second")
        with self.assertRaisesRegex(ValueError, "unrelated"):
            publisher.publish(self.source, self.remote)
        self.assertEqual(previous, self.head())

    def test_failed_push_preserves_published_badges(self):
        publisher.publish(self.source, self.remote)
        previous = self.head()
        contents = {name: self.read_badge(name) for name in publisher.FILES}
        hook = self.remote / "hooks/pre-receive"
        hook.write_text("#!/bin/sh\nexit 1\n")
        hook.chmod(0o755)
        self.write_badges("second")
        with self.assertRaises(subprocess.CalledProcessError):
            publisher.publish(self.source, self.remote)
        self.assertEqual(previous, self.head())
        for name in publisher.FILES:
            self.assertEqual(contents[name], self.read_badge(name))

    def test_missing_badge_preserves_published_pair(self):
        publisher.publish(self.source, self.remote)
        previous = self.head()
        self.write_badges("second")
        (self.source / publisher.FILES[1]).unlink()
        with self.assertRaises(FileNotFoundError):
            publisher.publish(self.source, self.remote)
        self.assertEqual(previous, self.head())


if __name__ == "__main__":
    unittest.main()
