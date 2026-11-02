"""Plan or apply this repository's GitHub policy through an authenticated gh CLI.

Dry run by default. Only the two named Neutron rulesets are updated; other rules
and unrelated settings are retained. Missing default-branch workflows defer their
required checks so a policy rollout cannot require a check that does not exist.
"""

import argparse
import base64
from copy import deepcopy
import json
from pathlib import Path
import re
import subprocess

REPOSITORY = "YoungJibbit95/Neutron-Client"
ROOT = Path(__file__).resolve().parents[2]
USES = re.compile(r"^\s*(?:-\s*)?uses:\s*['\"]?([^\s'\"#]+)")


def api(path, method="GET", payload=None, missing_ok=False):
    command = ["gh", "api", "repos/" + REPOSITORY + path, "--method", method]
    if payload is not None:
        command += ["--input", "-"]
    result = subprocess.run(command, input=json.dumps(payload) if payload is not None else None,
                            text=True, encoding="utf-8", capture_output=True)
    if result.returncode:
        if missing_ok and "HTTP 404" in result.stderr:
            return None
        raise RuntimeError("GitHub {} {} failed: {}".format(method, path, result.stderr.strip()))
    return json.loads(result.stdout) if result.stdout.strip() else {}


def unpinned_references(text):
    issues = []
    for number, line in enumerate(text.splitlines(), 1):
        if line.lstrip().startswith("#"):
            continue
        match = USES.match(line)
        if not match:
            # Defer pin enforcement on unfamiliar YAML syntax rather than guessing.
            if re.search(r"\buses\s*:", line):
                issues.append(number)
            continue
        value = match.group(1)
        if value.startswith("./") and ".." not in value.split("/"):
            continue
        if not re.fullmatch(r"[A-Za-z0-9_.\-/]+@[0-9a-fA-F]{40}", value):
            issues.append(number)
    return issues


def ready_main_ruleset(desired, workflow_paths, present):
    result = deepcopy(desired)
    pending = []
    for rule in result["rules"]:
        if rule["type"] != "required_status_checks":
            continue
        checks = rule["parameters"]["required_status_checks"]
        for check in checks:
            path = workflow_paths[check["context"]]
            if path not in present:
                pending.append(check["context"])
        rule["parameters"]["required_status_checks"] = [c for c in checks if c["context"] not in pending]
        if not rule["parameters"]["required_status_checks"]:
            raise RuntimeError("No required CI workflow exists on the default branch; refusing an empty gate")
    return result, pending


def put_ruleset(desired, existing):
    matches = [r for r in existing if r["name"] == desired["name"]]
    if len(matches) > 1:
        raise RuntimeError("Duplicate managed ruleset name: " + desired["name"])
    if matches:
        return api("/rulesets/" + str(matches[0]["id"]), "PUT", desired)
    return api("/rulesets", "POST", desired)


def collect():
    repo = api("")
    default = repo["default_branch"]
    workflows = api("/contents/.github/workflows?ref=" + default)
    contents = {}
    for file in workflows:
        if file["name"].endswith((".yml", ".yaml")):
            blob = api("/contents/" + file["path"] + "?ref=" + default)
            contents[file["path"]] = base64.b64decode(blob["content"]).decode("utf-8")
    state = {
        "repository": repo,
        "rulesets": [api("/rulesets/" + str(r["id"])) for r in api("/rulesets")],
        "actions": api("/actions/permissions"),
        "workflow_permissions": api("/actions/permissions/workflow"),
        "fork_approval": api("/actions/permissions/fork-pr-contributor-approval"),
        "private_reporting": api("/private-vulnerability-reporting"),
        "immutable_releases": api("/immutable-releases", missing_ok=True),
        "dependabot_alerts_enabled": api("/vulnerability-alerts", missing_ok=True) is not None,
        "dependabot_updates": api("/automated-security-fixes", missing_ok=True),
        "topics": api("/topics").get("names", []),
        "labels": api("/labels?per_page=100"),
    }
    return state, contents


def run(apply=False, snapshot=None):
    settings = json.loads((ROOT / ".github/repository-settings.json").read_text(encoding="utf-8"))
    main = json.loads((ROOT / ".github/rulesets/main.json").read_text(encoding="utf-8"))
    releases = json.loads((ROOT / ".github/rulesets/releases.json").read_text(encoding="utf-8"))
    before, workflows = collect()
    if apply and not before["repository"].get("permissions", {}).get("admin"):
        raise RuntimeError("Repository admin access is required")
    if before["repository"]["default_branch"] != "main":
        raise RuntimeError("Default branch changed; review the policy before applying")
    if snapshot:
        # Exclusive creation preserves an earlier administrative backup.
        with Path(snapshot).open("x", encoding="utf-8") as stream:
            json.dump(before, stream, indent=2)
            stream.write("\n")
    main, pending = ready_main_ruleset(main, settings["required_check_workflows"], workflows)
    unpinned = {path: lines for path, text in workflows.items()
                if (lines := unpinned_references(text))}
    actions = deepcopy(settings["actions"])
    if unpinned:
        actions["sha_pinning_required"] = before["actions"].get("sha_pinning_required", False)
    plan = {"apply": apply, "main_ruleset": main, "release_ruleset": releases,
            "actions": actions, "pending_required_checks": pending,
            "pin_enforcement_waits_for_default_branch": unpinned,
            "repository_settings": settings["repository"],
            "workflow_permissions": settings["workflow_permissions"],
            "fork_approval": settings["fork_approval"],
            "enable_features": ["dependabot_alerts", "dependabot_security_updates", "private_reporting", "immutable_releases"],
            "topics": sorted(set(before.get("topics", [])) | set(settings["topics"])),
            "new_labels": [label for label in settings["labels"] if label["name"] not in {l["name"] for l in before.get("labels", [])}]}
    if apply:
        api("", "PATCH", settings["repository"])
        api("/vulnerability-alerts", "PUT")
        api("/automated-security-fixes", "PUT")
        api("/private-vulnerability-reporting", "PUT")
        if not (before["immutable_releases"] or {}).get("enabled", False):
            api("/immutable-releases", "PUT")
        api("/topics", "PUT", {"names": sorted(set(before["topics"]) | set(settings["topics"]))})
        known_labels = {label["name"] for label in before["labels"]}
        for label in settings["labels"]:
            if label["name"] not in known_labels:
                api("/labels", "POST", label)
        api("/actions/permissions", "PUT", actions)
        api("/actions/permissions/selected-actions", "PUT", settings["selected_actions"])
        api("/actions/permissions/workflow", "PUT", settings["workflow_permissions"])
        api("/actions/permissions/fork-pr-contributor-approval", "PUT", settings["fork_approval"])
        plan["applied_rulesets"] = [put_ruleset(r, before["rulesets"]) for r in (main, releases)]
    print(json.dumps(plan, indent=2))
    return plan


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apply", action="store_true", help="Apply settings; without this flag only print the plan")
    parser.add_argument("--snapshot", type=Path, help="New local JSON file for the previous settings; keep outside Git")
    args = parser.parse_args()
    run(args.apply, args.snapshot)
