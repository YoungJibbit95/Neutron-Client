"""Offline failure tests for the launch/distribution boundary, no real account data."""
import io
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch
import zipfile

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from client_contract import ENTRY_POINT, digest, launch_command, relative_file, write_json
from provision_minecraft import checked_url, download, extract_natives
from package_client import add_resource_aliases


class ContractTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="Neutron Prüfung ")
        self.root = Path(self.temp.name)
        self.dist = self.root / "distribution"
        self.runtime = self.root / "runtime"
        self.game = self.root / "game"
        for p in (self.dist, self.runtime / "assets/indexes", self.runtime / "natives", self.game):
            p.mkdir(parents=True)
        jar = self.dist / "client.jar"
        jar.write_bytes(b"synthetic-jar")
        index = self.runtime / "assets/indexes/1.8.json"
        write_json(index, {"objects": {}})
        write_json(self.dist / "launch-manifest.json", {"schemaVersion": 1, "entryPoint": ENTRY_POINT,
                   "assetIndex": "1.8", "version": "synthetic", "classpath": [{"path": "client.jar", "sha256": digest(jar)}]})
        write_json(self.runtime / "runtime-manifest.json", {"schemaVersion": 1, "platform": "windows-x64",
                   "javaLibraries": [], "nativeFiles": [], "assetIndexFile": {"path": "assets/indexes/1.8.json", "sha256": digest(index)}})
        self.java = patch("client_contract.java_runtime", return_value=(Path("java"), "windows-x64", {}))
        self.java.start()

    def tearDown(self):
        self.java.stop()
        self.temp.cleanup()

    def command(self, **kwargs):
        return launch_command(self.dist, self.runtime, self.root, self.game, **kwargs)

    def test_explicit_cwd_and_space_unicode_paths(self):
        command, cwd, _ = self.command(validate_only=True)
        self.assertEqual(cwd, self.game.resolve())
        self.assertEqual(command[command.index("--gameDir") + 1], str(cwd))
        self.assertIn("--validateOnly", command)
        self.assertNotIn("Start", command)

    def test_corrupt_jar_prevents_process_start(self):
        (self.dist / "client.jar").write_bytes(b"damaged")
        with self.assertRaisesRegex(ValueError, "integrity"):
            self.command()

    def test_platform_mismatch_and_heap_bounds(self):
        record = json.loads((self.runtime / "runtime-manifest.json").read_text())
        record["platform"] = "macos-x64"
        write_json(self.runtime / "runtime-manifest.json", record)
        with self.assertRaisesRegex(ValueError, "platform"):
            self.command()
        record["platform"] = "windows-x64"
        write_json(self.runtime / "runtime-manifest.json", record)
        for heap in (0, 1023, 4097):
            with self.assertRaisesRegex(ValueError, "heap"):
                self.command(heap_mib=heap)

    def test_traversal_in_manifest_is_rejected(self):
        for value in ("../client.jar", "C:/client.jar", "libraries\\client.jar"):
            with self.assertRaises(ValueError):
                relative_file(self.dist, value)

    def test_archive_traversal_and_symlink_rejected(self):
        for symlink in (False, True):
            archive = self.root / ("link.zip" if symlink else "traversal.zip")
            with zipfile.ZipFile(archive, "w") as z:
                info = zipfile.ZipInfo("lib.so" if symlink else "../outside")
                if symlink:
                    info.external_attr = 0o120777 << 16
                z.writestr(info, b"synthetic")
            with self.assertRaises(ValueError):
                extract_natives(archive, self.runtime / "natives", [])
        self.assertFalse((self.runtime / "outside").exists())

    def test_invalid_download_preserves_existing_target(self):
        target = self.root / "archive.jar"
        target.write_bytes(b"previous")
        response = io.BytesIO(b"corrupt")
        response.url = "https://libraries.minecraft.net/synthetic.jar"
        with patch("urllib.request.urlopen", return_value=response):
            with self.assertRaisesRegex(ValueError, "digest"):
                download(response.url, target, "0" * 40)
        self.assertEqual(target.read_bytes(), b"previous")
        self.assertEqual(list(self.root.glob(".download-*")), [])

    def test_foreign_or_plaintext_download_host_rejected(self):
        for url in ("http://libraries.minecraft.net/a", "https://example.com/a", "https://user@libraries.minecraft.net/a"):
            with self.assertRaises(ValueError):
                checked_url(url)

    def test_packaged_shader_alias_preserves_original_and_is_idempotent(self):
        jar = self.root / "shader.jar"
        with zipfile.ZipFile(jar, "w") as z:
            z.writestr("assets/minecraft/Tenacity/Shaders/vertex.vsh", b"synthetic shader")
        add_resource_aliases(jar)
        add_resource_aliases(jar)
        with zipfile.ZipFile(jar) as z:
            alias = "assets/minecraft/Tenacity/shaders/vertex.vsh"
            self.assertEqual(z.namelist().count(alias), 1)
            self.assertEqual(z.read(alias), z.read("assets/minecraft/Tenacity/Shaders/vertex.vsh"))


if __name__ == "__main__":
    unittest.main()
