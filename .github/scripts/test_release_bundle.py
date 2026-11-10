"""Contract tests for actual ZIP/JSON output, history and rejected inputs."""

import hashlib
import importlib.util
import io
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import textwrap
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
            self.assertNotIn(b"\r", (output / "SHA256SUMS.txt").read_bytes())
            for line in (output / "SHA256SUMS.txt").read_text().splitlines():
                checksum, name = line.split("  ", 1)
                self.assertEqual(hashlib.sha256((output / name).read_bytes()).hexdigest(), checksum)
            notes = (output / "CHANGELOG.md").read_text()
            self.assertIn("- new theme", notes)
            self.assertNotIn("old change", notes)


class DraftPreparationTest(unittest.TestCase):
    """Execute the actual workflow shell against fixtures; never contact GitHub."""

    def workflow_script(self, name):
        workflow = (SCRIPT.parents[1] / "workflows/release-bundle.yml").read_text()
        step = workflow.split("      - name: " + name + "\n", 1)[1]
        lines = step.split("        run: |\n", 1)[1].splitlines()
        body = []
        for line in lines:
            if line.strip() and not line.startswith("          "):
                break
            body.append(line)
        return textwrap.dedent("\n".join(body))

    def prepare(self, *, event="workflow_dispatch", ref="refs/heads/main", opted=True,
                tag_exists=False, tag_sha=None, state="missing", target=None,
                info_change=None, corrupt=False):
        sha, tag, version = "a" * 40, "v0.3.0-alpha.1", "Neutron-0.3.0-alpha.1"
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            folder = root / "release" / version
            folder.mkdir(parents=True)
            payloads = {folder / (version + ".jar"): b"fixture jar",
                        folder / (version + ".json"): json.dumps({"id": version}).encode(),
                        folder / "CHANGELOG.md": b"fixture notes",
                        root / "release" / (version + "-bundle.zip"): b"fixture zip"}
            for path, content in payloads.items():
                path.write_bytes(content)
            (root / "release/SHA256SUMS.txt").write_bytes("".join(
                hashlib.sha256(data).hexdigest() + "  " + path.relative_to(root / "release").as_posix() + "\n"
                for path, data in payloads.items()).encode("utf-8"))
            info = {"version": version, "commit": sha}
            info.update(info_change or {})
            (root / "release/build-info.json").write_text(json.dumps(info))
            (root / "release/CHANGELOG.md").write_text("fixture notes")
            if corrupt:
                (folder / (version + ".jar")).write_bytes(b"changed after checksum")
            env = {**os.environ, "GITHUB_EVENT_NAME": event, "GITHUB_REF": ref,
                   "GITHUB_REF_TYPE": "tag" if ref.startswith("refs/tags/") else "branch",
                   "GITHUB_SHA": sha, "GH_REPO": "fixture/repository", "RELEASE_TAG": tag,
                   "PREPARE_RELEASE": str(opted).lower(), "GITHUB_OUTPUT": "workflow-outputs",
                   "PYTHON_EXE": Path(sys.executable).as_posix(), "FIXTURE_STATE": state,
                   "FIXTURE_TAG_EXISTS": str(tag_exists).lower(), "FIXTURE_TAG_SHA": tag_sha or sha,
                   "FIXTURE_RELEASE": json.dumps({"isDraft": state == "draft", "tagName": tag,
                                                  "targetCommitish": target or sha}),
                   "FIXTURE_CREATED": json.dumps({"isDraft": True, "tagName": tag, "targetCommitish": sha})}
            stub = r'''
            # The production shell runs on Ubuntu; normalize native Windows
            # Python text output here while retaining errors through pipefail.
            python3() { "$PYTHON_EXE" "$@" | tr -d '\r'; }
            gh() {
              printf '%s\n' "$*" >> gh-calls.txt
              case "$1 $2" in
                "api "*"/git/matching-refs/"*)
                  if [[ "$FIXTURE_TAG_EXISTS" == true ]]; then printf 'refs/tags/%s\n' "$RELEASE_TAG"; fi ;;
                "api "*"/commits/"*) printf '%s\n' "$FIXTURE_TAG_SHA" ;;
                "release view")
                  if [[ -f created-draft ]]; then printf '%s\n' "$FIXTURE_CREATED"; return 0; fi
                  if [[ "$FIXTURE_STATE" == missing ]]; then echo 'release not found' >&2; return 1; fi
                  if [[ "$FIXTURE_STATE" == error ]]; then echo 'HTTP 503 unavailable' >&2; return 1; fi
                  if [[ "$*" == *'--jq'* ]]; then
                    [[ "$FIXTURE_STATE" == draft ]] && echo true || echo false
                  else printf '%s\n' "$FIXTURE_RELEASE"; fi ;;
                "release create") touch created-draft ;;
                "release edit"|"release upload") return 0 ;;
                *) echo 'Unexpected fixture command' >&2; return 90 ;;
              esac
            }
            '''
            git_bash = Path(os.environ.get("ProgramFiles", "")) / "Git/bin/bash.exe"
            bash = str(git_bash) if git_bash.is_file() else shutil.which("bash")
            self.assertIsNotNone(bash, "Workflow tests require Bash, present on both CI runner images")
            def execute(name):
                return subprocess.run([bash, "--noprofile", "--norc", "-c", textwrap.dedent(stub) + self.workflow_script(name)],
                                      cwd=root, env=env, text=True, capture_output=True)
            result = execute("Validate bundle and draft source")
            output = root / "workflow-outputs"
            outputs = dict(line.split("=", 1) for line in output.read_text().splitlines()) if output.exists() else {}
            if result.returncode == 0 and outputs.get("skip") != "true":
                result = execute("Create or refresh draft (never overwrite a published release)")
            calls = root / "gh-calls.txt"
            return result, calls.read_text().splitlines() if calls.exists() else [], outputs

    def test_explicit_main_dispatch_prepares_pinned_alpha_without_creating_a_tag(self):
        result, calls, _ = self.prepare()
        self.assertEqual(0, result.returncode, result.stderr)
        create = next(call for call in calls if call.startswith("release create "))
        self.assertIn("--draft --target " + "a" * 40, create)
        self.assertIn("--prerelease", create)
        self.assertNotIn("--verify-tag", create)
        self.assertTrue(any(call.startswith("release upload ") for call in calls))

    def test_non_main_unrequested_and_ordinary_push_cannot_prepare(self):
        for args in ({"ref": "refs/heads/topic"}, {"opted": False}, {"event": "push"}):
            with self.subTest(args=args):
                result, calls, _ = self.prepare(**args)
                self.assertNotEqual(0, result.returncode)
                self.assertEqual([], calls)

    def test_wrong_bundle_source_version_and_checksums_stop_before_github(self):
        for args in ({"info_change": {"commit": "b" * 40}}, {"info_change": {"version": "Neutron-9.0.0"}}, {"corrupt": True}):
            with self.subTest(args=args):
                result, calls, _ = self.prepare(**args)
                self.assertNotEqual(0, result.returncode)
                self.assertEqual([], calls)

    def test_published_release_is_preserved_on_tag_push_but_manual_prepare_fails(self):
        for event, ref, code in (("push", "refs/tags/v0.3.0-alpha.1", 0), ("workflow_dispatch", "refs/heads/main", 1)):
            with self.subTest(event=event):
                result, calls, outputs = self.prepare(event=event, ref=ref, tag_exists=True, state="published")
                self.assertEqual(code, result.returncode, result.stderr)
                self.assertFalse(any(call.startswith(("release create ", "release edit ", "release upload ")) for call in calls))
                if event == "push":
                    self.assertEqual("true", outputs["skip"])

    def test_draft_must_keep_its_source_and_api_errors_are_not_absent_releases(self):
        for args in ({"state": "draft", "target": "b" * 40}, {"state": "error"}, {"tag_exists": True, "tag_sha": "b" * 40}):
            with self.subTest(args=args):
                result, calls, _ = self.prepare(**args)
                self.assertNotEqual(0, result.returncode)
                self.assertFalse(any(call.startswith(("release create ", "release edit ", "release upload ")) for call in calls))
        result, calls, _ = self.prepare(state="draft")
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertTrue(any(call.startswith("release edit ") for call in calls))

    def test_tag_build_requires_exact_existing_tag_and_keeps_verify_tag(self):
        args = {"event": "push", "ref": "refs/tags/v0.3.0-alpha.1"}
        result, calls, _ = self.prepare(**args)
        self.assertNotEqual(0, result.returncode)
        self.assertFalse(any(call.startswith("release create ") for call in calls))
        result, calls, _ = self.prepare(**args, tag_exists=True)
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn("--verify-tag", next(call for call in calls if call.startswith("release create ")))


if __name__ == "__main__":
    unittest.main()
