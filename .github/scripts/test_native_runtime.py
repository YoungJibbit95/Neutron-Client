"""APT bootstrap regression checks; all process calls are mocked on every OS."""

from contextlib import redirect_stderr
import importlib.util
import io
import os
from pathlib import Path
import stat
import subprocess
import tempfile
import unittest
from unittest.mock import patch


SPEC = importlib.util.spec_from_file_location(
    "install_native_runtime", Path(__file__).with_name("install_native_runtime.py"))
native = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(native)
ROOT = Path(__file__).resolve().parents[2]

# These are the original workflow package lists, including their order.
EXPECTED_PACKAGES = {
    "legacy-ui": (
        "xvfb", "xauth", "x11-xserver-utils", "mesa-utils", "libgl1-mesa-dri",
        "libglx-mesa0", "libglu1-mesa", "libx11-6", "libxext6", "libxrandr2",
        "libxxf86vm1", "libxcursor1", "libxinerama1", "libxi6", "libxtst6",
        "libfontconfig1", "libfreetype6", "fonts-dejavu-core",
    ),
    "render-performance": (
        "xvfb", "xauth", "x11-xserver-utils", "mesa-utils", "libgl1-mesa-dri",
        "libxrandr2", "libxcursor1", "libxinerama1", "libxi6", "libxxf86vm1",
        "fonts-dejavu-core",
    ),
}


def source_overrides(command):
    return dict(value.split("=", 1) for value in command if value.startswith("Dir::"))


class NativeRuntimeTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.parent = Path(self.temporary.name)
        self.calls = []
        self.failures = {}
        self.fallback_directories = []
        self.observe = lambda command: None

    def run_apt(self, command, **kwargs):
        self.calls.append(command)
        self.assertTrue(kwargs["check"])
        self.assertNotIn("shell", kwargs)
        self.assertEqual("noninteractive", kwargs["env"]["DEBIAN_FRONTEND"])
        self.assertEqual(["/usr/bin/timeout", "--signal=TERM", "--kill-after=10s"], command[:3])
        self.assertEqual("/usr/bin/apt-get", command[4])
        self.assertEqual("120s" if "update" in command else "240s", command[3])
        self.assertIn("Acquire::Retries=2", command)
        self.assertIn("Acquire::http::Timeout=20", command)
        self.assertIn("Acquire::https::Timeout=20", command)
        if "update" in command:
            self.assertIn("--error-on=any", command)
        options = source_overrides(command)
        if options:
            self.fallback_directories.append(Path(options["Dir::State::Lists"]).parent)
        self.observe(command)
        if len(self.calls) in self.failures:
            raise self.failures[len(self.calls)]

    def install(self, profile="legacy-ui"):
        with redirect_stderr(io.StringIO()):
            native.install_runtime(profile, runner=self.run_apt, temp_parent=self.parent)

    def fail(self, call_number, code=100):
        error = subprocess.CalledProcessError(code, ["mock-apt"])
        self.failures[call_number] = error
        return error

    def assert_cleaned(self):
        self.assertEqual([], list(self.parent.iterdir()))
        for directory in self.fallback_directories:
            self.assertFalse(directory.exists())

    def test_original_packages_are_preserved_for_both_workflows(self):
        self.assertEqual(EXPECTED_PACKAGES, native.PACKAGES)
        for profile in EXPECTED_PACKAGES:
            with self.subTest(profile=profile):
                self.calls.clear()
                self.install(profile)
                self.assertEqual(2, len(self.calls))
                command = self.calls[1]
                self.assertEqual(EXPECTED_PACKAGES[profile], tuple(command[command.index("--") + 1:]))
                self.assertIn("--no-install-recommends", command)
                self.assertIn("-y", command)
                self.assertTrue(all(not source_overrides(call) for call in self.calls))
                self.assert_cleaned()

    def test_failed_update_skips_primary_install_and_isolates_both_fallback_calls(self):
        self.fail(1)
        observed = []

        def inspect(command):
            options = source_overrides(command)
            if not options:
                return
            self.assertEqual({"Dir::Etc::SourceList", "Dir::Etc::SourceParts", "Dir::State::Lists"}, set(options))
            source_file = Path(options["Dir::Etc::SourceList"])
            parts = Path(options["Dir::Etc::SourceParts"])
            lists = Path(options["Dir::State::Lists"])
            self.assertEqual(source_file.parent, parts.parent)
            self.assertEqual(source_file.parent, lists.parent)
            self.assertEqual(self.parent, source_file.parent.parent)
            self.assertEqual([], list(parts.iterdir()))
            if "update" in command:
                self.assertEqual([], list(lists.iterdir()))
                (lists / "mock-authenticated-index").write_text("fresh", encoding="ascii")
            else:
                self.assertEqual("fresh", (lists / "mock-authenticated-index").read_text(encoding="ascii"))
            sources = source_file.read_text(encoding="ascii")
            self.assertIn("URIs: https://archive.ubuntu.com/ubuntu/\n", sources)
            self.assertIn("URIs: https://security.ubuntu.com/ubuntu/\n", sources)
            self.assertIn("Suites: noble noble-updates noble-backports\n", sources)
            self.assertIn("Suites: noble-security\n", sources)
            self.assertEqual(2, sources.count("Signed-By: /usr/share/keyrings/ubuntu-archive-keyring.gpg\n"))
            self.assertEqual(2, sources.count("Types: deb\n"))
            self.assertNotIn("mirror", sources)
            self.assertNotIn("Trusted:", sources)
            self.assertNotIn("Allow-", sources)
            observed.append(options)

        self.observe = inspect
        self.install()
        self.assertEqual([True, True, False], ["update" in call for call in self.calls])
        self.assertEqual(2, len(observed))
        self.assertEqual(observed[0], observed[1])
        self.assert_cleaned()

    def test_install_failure_retries_the_exact_profile_once(self):
        self.fail(2)
        self.install("render-performance")
        self.assertEqual(4, len(self.calls))
        for command in (self.calls[1], self.calls[3]):
            self.assertEqual(EXPECTED_PACKAGES["render-performance"], tuple(command[command.index("--") + 1:]))
        self.assertEqual(source_overrides(self.calls[2]), source_overrides(self.calls[3]))
        self.assert_cleaned()

    def test_fallback_update_failure_propagates_without_install_or_another_retry(self):
        self.fail(1)
        expected = self.fail(2, 101)
        with self.assertRaises(subprocess.CalledProcessError) as caught:
            self.install()
        self.assertIs(expected, caught.exception)
        self.assertEqual(2, len(self.calls))
        self.assert_cleaned()

    def test_fallback_install_failure_propagates_and_cleans_temporary_lists(self):
        self.fail(1)
        expected = self.fail(3, 102)
        with self.assertRaises(subprocess.CalledProcessError) as caught:
            self.install()
        self.assertIs(expected, caught.exception)
        self.assertEqual(3, len(self.calls))
        self.assert_cleaned()

    def test_bounded_process_timeout_can_use_only_the_single_fallback(self):
        self.fail(1, 124)
        self.fail(3, 124)
        with self.assertRaises(subprocess.CalledProcessError) as caught:
            self.install()
        self.assertEqual(124, caught.exception.returncode)
        self.assertEqual(3, len(self.calls))
        self.assert_cleaned()

    def test_unexpected_fallback_process_error_still_cleans_temporary_files(self):
        self.fail(1)
        self.failures[2] = OSError("mock executable disappeared")
        with self.assertRaisesRegex(OSError, "disappeared"):
            self.install()
        self.assertEqual(2, len(self.calls))
        self.assert_cleaned()

    def test_temp_permissions_allow_apt_traversal_without_other_user_writes(self):
        self.fail(1)
        actual_chmod = Path.chmod
        modes = []

        def record_mode(path, mode, *args, **kwargs):
            modes.append((path.name, mode))
            return actual_chmod(path, mode, *args, **kwargs)

        def inspect(command):
            options = source_overrides(command)
            if not options or os.name != "posix":
                return
            source = Path(options["Dir::Etc::SourceList"])
            self.assertEqual(0o711, stat.S_IMODE(source.parent.stat().st_mode))
            self.assertEqual(0o644, stat.S_IMODE(source.stat().st_mode))
            for key in ("Dir::Etc::SourceParts", "Dir::State::Lists"):
                self.assertEqual(0o755, stat.S_IMODE(Path(options[key]).stat().st_mode))

        self.observe = inspect
        with patch.object(Path, "chmod", record_mode):
            self.install()
        self.assertEqual([0o711, 0o755, 0o755, 0o644], [mode for _, mode in modes])
        self.assert_cleaned()

    def test_invalid_profiles_cannot_add_package_manager_options(self):
        for profile in ("", "--allow-unauthenticated", "-o", "legacy-ui --force-yes", "xvfb", "xvfb-", "../legacy-ui"):
            with self.subTest(profile=profile):
                with self.assertRaises(ValueError):
                    self.install(profile)
        self.assertEqual([], self.calls)
        self.assert_cleaned()

    def test_cli_rejects_extra_arguments_before_any_runtime_or_process_action(self):
        with patch.object(native, "require_noble_root") as require, patch.object(native, "install_runtime") as install:
            for args in ([], ["--allow-unauthenticated"], ["legacy-ui", "-o", "Trusted=yes"], ["xvfb"]):
                with self.subTest(args=args), redirect_stderr(io.StringIO()):
                    with self.assertRaises(SystemExit) as caught:
                        native.main(args)
                    self.assertEqual(2, caught.exception.code)
            require.assert_not_called()
            install.assert_not_called()

    def test_cli_preserves_dependency_failure_status_and_environment_guard(self):
        with patch.object(native, "require_noble_root"), patch.object(native, "install_runtime") as install, redirect_stderr(io.StringIO()):
            install.side_effect = subprocess.CalledProcessError(100, ["mock-apt"])
            self.assertEqual(100, native.main(["legacy-ui"]))
        with patch.object(native, "require_noble_root", side_effect=RuntimeError("wrong runner")), patch.object(native, "install_runtime") as install, redirect_stderr(io.StringIO()):
            self.assertEqual(1, native.main(["legacy-ui"]))
            install.assert_not_called()

    def test_cli_requires_linux_root_and_noble_before_using_ubuntu_sources(self):
        with patch.object(native.sys, "platform", "win32"):
            with self.assertRaisesRegex(RuntimeError, "sudo"):
                native.require_noble_root()
        with patch.object(native.sys, "platform", "linux"), patch.object(native.os, "geteuid", return_value=1000, create=True):
            with self.assertRaisesRegex(RuntimeError, "sudo"):
                native.require_noble_root()
        with patch.object(native.sys, "platform", "linux"), patch.object(native.os, "geteuid", return_value=0, create=True), patch.object(Path, "read_text", return_value="ID=ubuntu\nVERSION_CODENAME=jammy\n"):
            with self.assertRaisesRegex(RuntimeError, "Noble"):
                native.require_noble_root()

    def test_native_workflows_share_bootstrap_and_recheck_when_it_changes(self):
        for filename, profile in (("legacy-ui-render.yml", "legacy-ui"), ("render-performance.yml", "render-performance")):
            with self.subTest(workflow=filename):
                workflow = (ROOT / ".github/workflows" / filename).read_text(encoding="utf-8")
                self.assertIn("sudo python3 -B .github/scripts/install_native_runtime.py " + profile, workflow)
                self.assertIn("- '.github/scripts/install_native_runtime.py'", workflow)
                self.assertIn("- '.github/scripts/test_native_runtime.py'", workflow)
                self.assertIn("runs-on: ubuntu-24.04", workflow)
                self.assertNotIn("sudo apt-get", workflow)


if __name__ == "__main__":
    unittest.main()
