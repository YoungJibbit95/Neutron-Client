# Security policy

## Report privately

Use [Report a vulnerability](https://github.com/YoungJibbit95/Neutron-Client/security/advisories/new).
Do not put working credentials, account data or sensitive exploit details in a public
issue. Include the affected version/commit, prerequisites, a minimal reproduction,
the expected boundary and observed impact. Use dummy accounts and redact logs.

Reports are reviewed as maintainer availability permits. Coordinate disclosure in
the private advisory; no response-time guarantee is implied.

## Scope and maintained versions

Security fixes target `main` and the latest maintained release. Older versions may
receive a backport when feasible. Previews must be identified as previews.

Relevant boundaries include account/token handling, configuration and file access,
download/hash verification, archive extraction, dependencies, native loading and
GitHub Actions credentials or release integrity.

An anti-cheat detection result or missing gameplay mode is normally a compatibility
issue. Use the compatibility form unless it also exposes a security boundary failure.

## Handling fixes

Confirm the affected boundary, minimize reproduction data and add a regression
where practical. Review dependency/runtime compatibility and keep secrets out of
tests. Use a new release version for published artifact fixes. Affected credentials
must be revoked or rotated by their owner; deleting a committed value is insufficient.

Secret scanning, push protection and dependency alerts supplement review. CodeQL
source analysis and workflow linting can miss defects; a green run is not a security
certification. Report suspected gaps through the same private channel.
