# Contributing

Start with a reproducible bug, a small feature proposal or an existing issue.
[Project direction](docs/PROJECT-DIRECTION.md) describes the gameplay and visual goals.
Report vulnerabilities through [private security reporting](SECURITY.md).

## Build and test

Use Temurin **8u482** and Maven **3.9.15**. Run `mvn test` before opening a PR.
Windows Java 8 is the current regression environment. Optional native checks and
real launcher/game tests are separate gates; list which ones you actually ran.
See [development setup](docs/DEVELOPMENT-WORKFLOW.md) and [release bundles](docs/RELEASES.md).

For workflow changes, use Python 3.9+ and run:

```shell
python -B -m unittest discover -s .github/scripts -p "test_*.py" -v
python -B .github/scripts/install_workflow_tools.py --destination <temporary-tools-folder>
```

Run the installed `actionlint` and `zizmor --offline --min-severity medium .github/workflows`.
The tool versions and download hashes are pinned in `.github/workflow-tools.json`.

## Keep changes reviewable

- Use a short-lived branch and a focused PR. Explain the trigger, changed behavior
  and validation; include remaining limits. Keep unfinished work as a draft.
- For gameplay changes, add a regression at the affected movement, rotation,
  packet or lifecycle boundary. Preserve unrelated module behavior.
- For Intave/Polar changes, identify the anti-cheat build/configuration, client
  revision and test environment. Record expected and observed behavior, a known
  baseline and the limits of the result. Test on servers you operate or have
  permission to use for testing.
- For visuals, reuse shared themes, fonts, materials, motion and render helpers.
  Show screenshots at more than one GUI scale. Check input, clipping, long labels,
  keyboard focus, reduced motion and resource cleanup.
- For dependencies, keep Java 8 compatibility and verify reflection, JNI and
  packaged resources. A newer dependency version still needs review.

Keep accounts, tokens, worlds, personal profiles, IDE files, build output and local
logs out of commits. Redact reports and recordings before attaching them.

Record the upstream URL, revision, license and required notices for imported code
or assets. Contributions must be yours to submit under the repository's license;
do not infer redistribution rights from a project's description.

## Review and release

`main` requires a PR, current Java 8 CI and resolved review conversations. CODEOWNERS
routes reviews to the maintainer. There is currently one maintainer, so a mandatory
second approval is deferred until another active reviewer joins. Workflows do not
approve or merge PRs automatically. [Repository governance](docs/REPOSITORY-GOVERNANCE.md)
documents the live policy and rollout steps.

The Java 8 workflow can also run manually on a branch. Bot-created PRs may need a
maintainer to approve their workflow runs; the traffic updater explicitly dispatches
the Java check on its validated badge-only branch.

Tagged bundles remain draft releases until the launcher, account, native/game and
applicable server checks pass. Published releases are immutable: fix an artifact
by publishing a new version. Include accurate notices for distributed components.
