"""Regression controls for policy rollout and checksum-locked tooling."""

import hashlib
import importlib.util
import io
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
from urllib.error import HTTPError
import zipfile


def module(name):
    spec = importlib.util.spec_from_file_location(name, Path(__file__).with_name(name + ".py"))
    result = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(result)
    return result


policy = module("apply_repository_policy")
tools = module("install_workflow_tools")


class RepositoryPolicyTest(unittest.TestCase):
    def setUp(self):
        self.settings = json.loads((policy.ROOT / ".github/repository-settings.json").read_text())
        self.main = json.loads((policy.ROOT / ".github/rulesets/main.json").read_text())

    def test_rollout_requires_only_existing_default_branch_workflows(self):
        rule, pending = policy.ready_main_ruleset(self.main, self.settings["required_check_workflows"],
                                                 {".github/workflows/java8-pr.yml"})
        checks = next(r for r in rule["rules"] if r["type"] == "required_status_checks")["parameters"]
        self.assertEqual(["Java 8 / Windows regression"], [c["context"] for c in checks["required_status_checks"]])
        self.assertEqual({"Repository policy", "Dependency review"}, set(pending))
        self.assertTrue(checks["strict_required_status_checks_policy"])

    def test_no_existing_ci_refuses_to_create_an_empty_gate(self):
        with self.assertRaisesRegex(RuntimeError, "No required CI workflow"):
            policy.ready_main_ruleset(self.main, self.settings["required_check_workflows"], set())

    def test_workflow_pins_reject_tags_and_ambiguous_syntax(self):
        self.assertEqual([], policy.unpinned_references("- uses: actions/checkout@" + "a" * 40))
        for text in ("- uses: actions/checkout@v7", "- uses: 'actions/checkout@main'", "- {uses: actions/checkout@v7}", "- uses: ./../outside/action"):
            with self.subTest(text=text):
                self.assertTrue(policy.unpinned_references(text))

    def test_ruleset_update_preserves_other_named_rulesets(self):
        with patch.object(policy, "api", return_value={}) as api:
            policy.put_ruleset(self.main, [{"name": "Other policy", "id": 4}, {"name": self.main["name"], "id": 9}])
            api.assert_called_once_with("/rulesets/9", "PUT", self.main)

    def test_duplicate_managed_rule_names_fail_without_writing(self):
        with patch.object(policy, "api") as api:
            with self.assertRaises(RuntimeError):
                policy.put_ruleset(self.main, [{"name": self.main["name"], "id": 4}, {"name": self.main["name"], "id": 9}])
            api.assert_not_called()

    def test_nonempty_disabled_immutable_state_is_enabled(self):
        before = {"repository": {"default_branch": "main", "permissions": {"admin": True}},
                  "actions": {}, "rulesets": [], "immutable_releases": {"enabled": False},
                  "topics": [], "labels": []}
        workflows = {".github/workflows/java8-pr.yml": "- uses: actions/checkout@" + "a" * 40}
        with patch.object(policy, "collect", return_value=(before, workflows)), patch.object(policy, "api", return_value={}) as api, patch("builtins.print"):
            policy.run(apply=True)
            self.assertIn(unittest.mock.call("/immutable-releases", "PUT"), api.call_args_list)

    def test_dry_run_never_calls_mutating_endpoints(self):
        before = {"repository": {"default_branch": "main"}, "actions": {"sha_pinning_required": False}}
        with patch.object(policy, "collect", return_value=(before, {".github/workflows/java8-pr.yml": "- uses: actions/checkout@v7"})), patch.object(policy, "api") as api, patch("builtins.print"):
            result = policy.run()
            api.assert_not_called()
            self.assertFalse(result["actions"]["sha_pinning_required"])


class WorkflowToolTest(unittest.TestCase):
    def archive(self):
        stream = io.BytesIO()
        with zipfile.ZipFile(stream, "w") as archive:
            archive.writestr("tool.exe", b"verified binary fixture")
            archive.writestr("../outside.txt", b"must never be extracted")
        return stream.getvalue()

    def test_changed_download_never_writes_a_binary(self):
        data = self.archive()
        entry = {"url": "https://github.com/owner/tool/releases/download/v1/tool.zip", "sha256": "0" * 64, "member": "tool.exe"}
        with tempfile.TemporaryDirectory() as tmp:
            with self.assertRaisesRegex(ValueError, "checksum mismatch"):
                tools.install(entry, Path(tmp), "tool.exe", fetch=lambda *a, **k: io.BytesIO(data))
            self.assertEqual([], list(Path(tmp).iterdir()))

    def test_transient_gateway_errors_retry_the_exact_pinned_archive(self):
        data = self.archive()
        entry = {"url": "https://github.com/owner/tool/releases/download/v1/tool.zip",
                 "sha256": hashlib.sha256(data).hexdigest(), "member": "tool.exe"}
        attempts = []
        def fetch(url, timeout):
            attempts.append((url, timeout))
            if len(attempts) < 3:
                raise HTTPError(url, 504, "Gateway Timeout", None, None)
            return io.BytesIO(data)
        with tempfile.TemporaryDirectory() as tmp, patch.object(tools.time, "sleep") as sleep:
            target = tools.install(entry, Path(tmp), "tool.exe", fetch=fetch)
            self.assertEqual(b"verified binary fixture", target.read_bytes())
            self.assertEqual([(entry["url"], 60)] * 3, attempts)
            self.assertEqual([unittest.mock.call(3), unittest.mock.call(6)], sleep.call_args_list)

    def test_nonretryable_http_failure_keeps_destination_empty(self):
        entry = {"url": "https://github.com/owner/tool/releases/download/v1/tool.zip",
                 "sha256": "0" * 64, "member": "tool.exe"}
        attempts = []
        def fetch(url, timeout):
            attempts.append(url)
            raise HTTPError(url, 404, "Not Found", None, None)
        with tempfile.TemporaryDirectory() as tmp, patch.object(tools.time, "sleep") as sleep:
            with self.assertRaises(HTTPError):
                tools.install(entry, Path(tmp), "tool.exe", fetch=fetch)
            self.assertEqual([entry["url"]], attempts)
            sleep.assert_not_called()
            self.assertEqual([], list(Path(tmp).iterdir()))

    def test_verified_archive_copies_only_the_expected_binary(self):
        data = self.archive()
        entry = {"url": "https://github.com/owner/tool/releases/download/v1/tool.zip", "sha256": hashlib.sha256(data).hexdigest(), "member": "tool.exe"}
        with tempfile.TemporaryDirectory() as tmp:
            output = tools.install(entry, Path(tmp) / "bin", "tool.exe", fetch=lambda *a, **k: io.BytesIO(data))
            self.assertEqual(b"verified binary fixture", output.read_bytes())
            self.assertFalse((Path(tmp) / "outside.txt").exists())


if __name__ == "__main__":
    unittest.main()
