# Repository governance

## Protected history

`Neutron main` is an active ruleset for `main`, with no bypass actors. Changes need
a PR, up-to-date required checks and resolved review conversations. Force-push and
branch deletion are blocked, including for admins. Merge commits remain supported.

`Neutron release tags` blocks updates and deletion of `v*` tags. Release immutability
protects future published assets; assemble and review drafts before publishing.
Corrections require a new tag and release, not replacing published files.

Required CI results are bound to the GitHub Actions app (ID `15368`). The existing
`Java 8 / Windows regression` check is enforced immediately. `Repository policy` and
`Dependency review` are added only after their workflow exists on GitHub `main`.

## Review with one maintainer

CODEOWNERS assigns the current maintainer. Mandatory approval count is initially
zero because the only maintainer cannot approve their own PR. PR/CI/discussion
requirements still apply, and there is no admin bypass.

When another active maintainer joins, update CODEOWNERS, set
`required_approving_review_count` to at least `1` and evaluate code-owner and
last-push approval requirements. Do not introduce a reviewer gate with nobody
available to satisfy it.

## Actions and dependency policy

- Workflow tokens default to read-only. Write scopes are declared for specific
  reporting, traffic or tagged-release jobs. Fork contributions require approval
  from a maintainer before running workflows. Do not use `pull_request_target` to
  execute contributor code.
- Only GitHub-owned actions are allowed initially. Full commit SHA pinning is
  enforced once the current default-branch workflows satisfy it. New action/tool
  sources require an explicit policy change. Linter downloads have SHA256 locks.
- GitHub combines the setting for Actions-created PRs and review approval. It is
  enabled to allow the traffic PR; repository workflows do not submit approvals or
  merge PRs. Auto-merge remains disabled.
- Token-created PR workflows may wait for approval. The traffic workflow also
  dispatches read-only Java 8 and repository/dependency checks on its badge-only branch. Its token
  has `actions: write` for this dispatch; it does not run a workflow supplied by an
  unreviewed contributor branch.
- Dependabot checks Maven and Actions weekly, with bounded PR counts. Minor/patch
  Maven updates are grouped; major updates remain separate. Java 8, reflection,
  JNI and launcher compatibility must pass before merging dependency changes.
- Dependency review rejects newly introduced high/critical advisories. CodeQL
  analyzes Java sources without a game build; missing dependency information can
  reduce precision. Findings need triage. Existing alerts are not silently waived.

## Security and releases

Private vulnerability reporting, Dependabot alerts/security updates, secret scanning
and push protection are enabled through the administrator policy. These settings
do not prove there are no existing vulnerabilities or exposed credentials.

Tagged release bundles get a provenance attestation through GitHub OIDC, with no
stored signing key. Before installation, verify a downloaded ZIP with:

```shell
gh attestation verify Neutron-<version>-bundle.zip --repo YoungJibbit95/Neutron-Client
```

Attestation establishes provenance; launcher/game acceptance remains separate.

## Reapply and verify

The source of truth is `.github/repository-settings.json` and `.github/rulesets/`.
The administrator script uses the existing `gh` authentication and never saves a
token. Plan first:

```shell
python -B .github/scripts/apply_repository_policy.py
```

After merging the governance PR, use an administrator session to apply the full
required-check policy:

```shell
python -B .github/scripts/apply_repository_policy.py --apply --snapshot <new-local-backup.json>
```

Keep the backup outside Git. It contains the previous settings for recovery through
GitHub's Rules and Settings UI. The script updates only the two named Neutron
rulesets, preserves other rulesets, adds missing labels and keeps existing topics.
It reports deferred required checks/pin enforcement when default-branch files are
missing or unpinned. Review those results; never claim a deferred gate is active.

[GitHub rules](https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/managing-rulesets)
· [Actions security](https://docs.github.com/en/actions/reference/security/secure-use)
· [Artifact attestations](https://docs.github.com/en/actions/how-tos/secure-your-work/use-artifact-attestations/use-artifact-attestations)
