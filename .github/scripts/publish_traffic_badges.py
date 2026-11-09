#!/usr/bin/env python3
"""Publish the two generated traffic SVGs without changing the source branch."""

import os
from pathlib import Path
import re
import subprocess
import tempfile


BRANCH = "readme-metrics"
FILES = (
    "assets/readme/metrics/views-14d.svg",
    "assets/readme/metrics/clones-14d.svg",
)


def git(directory, *arguments, check=True):
    return subprocess.run(
        ["git", "-C", str(directory), *arguments], check=check,
        stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True,
    )


def publish(source, remote):
    # Read both before touching the publication repository. A missing output must
    # never publish a partial update or remove the last valid badge.
    badges = {name: (Path(source) / name).read_bytes() for name in FILES}
    with tempfile.TemporaryDirectory(
        prefix="neutron-traffic-", dir=os.environ.get("RUNNER_TEMP")
    ) as temporary:
        checkout = Path(temporary)
        git(checkout, "init", "--initial-branch=" + BRANCH)
        git(checkout, "remote", "add", "origin", str(remote))
        exists = git(
            checkout, "ls-remote", "--exit-code", "--heads", "origin",
            "refs/heads/" + BRANCH, check=False,
        )
        if exists.returncode == 0:
            git(checkout, "fetch", "--depth=1", "origin", "refs/heads/" + BRANCH)
            # Refuse a reused source branch, extra files or symlinks; publication
            # only owns two regular SVG files on this generated-data branch.
            for entry in git(checkout, "ls-tree", "-r", "-z", "FETCH_HEAD").stdout.split("\0"):
                if not entry:
                    continue
                metadata, name = entry.split("\t", 1)
                if name not in FILES or not metadata.startswith("100644 blob "):
                    raise ValueError("The metrics branch contains unrelated files or non-regular badges.")
            git(checkout, "checkout", "-B", BRANCH, "FETCH_HEAD")
        elif exists.returncode != 2:
            raise subprocess.CalledProcessError(
                exists.returncode, exists.args, exists.stdout, exists.stderr
            )

        for name, content in badges.items():
            destination = checkout / name
            destination.parent.mkdir(parents=True, exist_ok=True)
            destination.write_bytes(content)
        git(checkout, "add", "--", *FILES)
        changed = git(checkout, "diff", "--cached", "--quiet", check=False)
        if changed.returncode == 0:
            print("Traffic badge files unchanged.")
            return False
        if changed.returncode != 1:
            raise subprocess.CalledProcessError(
                changed.returncode, changed.args, changed.stdout, changed.stderr
            )
        git(checkout, "config", "user.name", "github-actions[bot]")
        git(checkout, "config", "user.email", "41898282+github-actions[bot]@users.noreply.github.com")
        git(checkout, "commit", "-m", "docs(metrics): refresh 14-day repo traffic badges")
        # A concurrent external change rejects the push instead of being lost.
        git(checkout, "push", "origin", "HEAD:refs/heads/" + BRANCH)
        print("Published both traffic badges to " + BRANCH + ".")
        return True


def main():
    repository = os.environ.get("GITHUB_REPOSITORY", "")
    if not re.fullmatch(r"[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+", repository):
        raise ValueError("GITHUB_REPOSITORY must identify the repository to update.")
    publish(Path.cwd(), "https://github.com/" + repository + ".git")


if __name__ == "__main__":
    try:
        main()
    except (OSError, ValueError, subprocess.CalledProcessError) as exc:
        raise SystemExit("Traffic publication failed; the remote badges were not replaced: " + str(exc))
