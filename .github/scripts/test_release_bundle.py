"""Contract tests for actual ZIP/JSON output, history and rejected inputs."""

import hashlib
import importlib.util
import io
import json
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch
import zipfile

SCRIPT = Path(__file__).with_name("create_release_bundle.py")
spec = importlib.util.spec_from_file_location("release_bundle", SCRIPT)
release = importlib.util.module_from_spec(spec)
spec.loader.exec_module(release)


def metadata():
    return {"id": "1.8.9", "assets": "1.8", "assetIndex": {"id": "1.8"},
            "mainClass": "vanilla.Main", "minecraftArguments": "--accessToken ${auth_access_token}",
            "downloads": {"client": {"url": "https://example.com/vanilla.jar"}},
            "libraries": [{"name": "old:log4j:1"}, {"name": "native:platform:1",
                          "natives": {"windows": "natives-windows"},
                          "downloads": {"classifiers": {"natives-windows": {"sha1": "123"}}}}]}


def fake_jar(path, missing=None, extra=None):
    with zipfile.ZipFile(path, "w") as jar:
        for name in release.REQUIRED_CLASSES:
            if name != missing:
                jar.writestr(name, b"fixture")
        jar.writestr("META-INF/MANIFEST.MF", "Manifest-Version: 1.0\nMain-Class: net.minecraft.client.main.Main\n")
        for original in release.RESOURCE_ALIASES.values():
            jar.writestr(original, b"shader fixture")
        if extra:
            jar.writestr(extra, b"must be rejected")


class ReleaseBundleTest(unittest.TestCase):
    def test_changed_upstream_metadata_is_rejected_before_packaging(self):
        with patch.object(release, "urlopen", return_value=io.BytesIO(b"{}")):
            with self.assertRaisesRegex(ValueError, "checksum mismatch"):
                release.load_metadata()

    def test_version_cannot_escape_output_folder(self):
        for tag in ("../evil", "v1/2/3", "v1.0", "v1.0.0\n", "v1.0.0; echo token"):
            with self.subTest(tag=tag), self.assertRaises(ValueError):
                release.version_id(tag)
        self.assertEqual("Neutron-1.0.0-alpha.1", release.version_id("v1.0.0-alpha.1"))

    def test_json_uses_neutron_without_vanilla_java_dependency_shadowing(self):
        vanilla = metadata()
        result = release.launcher_manifest(vanilla, "Neutron-1.0.0", "2026-11-02T00:00:00Z")
        self.assertEqual("net.minecraft.client.main.Main", result["mainClass"])
        self.assertNotIn("downloads", result)
        self.assertNotIn("inheritsFrom", result)
        self.assertEqual(1, len(result["libraries"]))
        self.assertIn("natives", result["libraries"][0])
        self.assertEqual(8, result["javaVersion"]["majorVersion"])
        self.assertEqual("release", result["type"])
        self.assertIn("${auth_access_token}", result["minecraftArguments"])
        self.assertIn("downloads", vanilla)

    def test_incomplete_native_metadata_is_rejected(self):
        data = metadata()
        data["libraries"] = [{"name": "only:java:1"}]
        with self.assertRaises(ValueError):
            release.launcher_manifest(data, "Neutron-1.0.0", "now")

    def test_short_changelog_filters_metric_commits_and_escapes_markup(self):
        notes = release.changelog(["fix(gui): retain selected theme", "fix(gui): retain selected theme",
                                   "docs(metrics): refresh 14-day repo traffic badges", "<script>[bad](url)</script>",
                                   *["feature " + str(i) for i in range(20)]], "owner/repo", "abc")
        self.assertEqual(10, sum(line.startswith("- ") for line in notes.splitlines()))
        self.assertEqual(1, notes.count("retain selected theme"))
        self.assertNotIn("refresh", notes)
        self.assertNotIn("<script>", notes)
        self.assertIn("commits/abc", notes)

    def test_incomplete_jar_and_dependency_signatures_are_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            source, dest = Path(tmp) / "input.jar", Path(tmp) / "output.jar"
            for missing, extra in ((release.REQUIRED_CLASSES[0], None), (None, "META-INF/A.RSA"), (None, "Start.class")):
                fake_jar(source, missing=missing, extra=extra)
                with self.subTest(missing=missing, extra=extra), self.assertRaises(ValueError):
                    release.prepare_jar(source, dest)

    def test_real_bundle_layout_checksums_and_previous_tag_history(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            subprocess.run(["git", "init", "--quiet", str(root)], check=True)
            def git(*args):
                return subprocess.check_output(["git", "-C", str(root), *args], encoding="utf-8").strip()
            git("-c", "user.name=Fixture", "-c", "user.email=fixture@example.com", "commit", "--quiet", "--allow-empty", "-m", "old change")
            git("tag", "v1.0.0")
            git("-c", "user.name=Fixture", "-c", "user.email=fixture@example.com", "commit", "--quiet", "--allow-empty", "-m", "fix: new theme")
            git("tag", "v1.1.0")
            source = root / "input.jar"
            fake_jar(source)
            output = root / "bundle"
            with patch.object(release, "git", side_effect=git), patch.object(release, "load_metadata", return_value=metadata()):
                info = release.bundle(source, output, "v1.1.0", "owner/repo")
                with self.assertRaises(FileExistsError):
                    release.bundle(source, output, "v1.1.0", "owner/repo")
            self.assertEqual("v1.0.0", info["previousTag"])
            manifest = json.loads((output / "Neutron-1.1.0/Neutron-1.1.0.json").read_text())
            self.assertEqual("Neutron-1.1.0", manifest["id"])
            with zipfile.ZipFile(info["archive"]) as archive:
                self.assertEqual({"Neutron-1.1.0/Neutron-1.1.0.jar", "Neutron-1.1.0/Neutron-1.1.0.json", "Neutron-1.1.0/CHANGELOG.md"}, set(archive.namelist()))
            with zipfile.ZipFile(info["jar"]) as jar:
                for alias in release.RESOURCE_ALIASES:
                    self.assertEqual(b"shader fixture", jar.read(alias))
            for line in (output / "SHA256SUMS.txt").read_text().splitlines():
                checksum, name = line.split("  ", 1)
                self.assertEqual(hashlib.sha256((output / name).read_bytes()).hexdigest(), checksum)
            notes = (output / "CHANGELOG.md").read_text()
            self.assertIn("- new theme", notes)
            self.assertNotIn("old change", notes)


if __name__ == "__main__":
    unittest.main()
