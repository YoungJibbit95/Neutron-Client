# Release bundles

The **Release bundle** Action builds a version folder for the official Minecraft Launcher:

```text
Neutron-1.0.0-alpha.1-bundle.zip
└── Neutron-1.0.0-alpha.1/
    ├── Neutron-1.0.0-alpha.1.jar
    ├── Neutron-1.0.0-alpha.1.json
    └── CHANGELOG.md
```

The matching JAR and JSON, ZIP, `CHANGELOG.md`, `SHA256SUMS.txt` and `build-info.json`
are also attached separately. Checksums use the bundle's relative paths; verify them
from the downloaded artifact directory.

## Build a candidate

Open **Actions → Release bundle → Run workflow**, select the source branch and enter
a version such as `v1.0.0-alpha.1`. Download `neutron-release-bundle` from the completed
run. This creates an Actions artifact; it does not publish a release or create a tag.
Pull requests changing the release tooling also build a candidate automatically.

Locally, with Temurin 8u482, Maven 3.9.15 and Python 3:

```powershell
mvn -Prelease-bundle clean package
python .github/scripts/create_release_bundle.py --tag v1.0.0-alpha.1
```

Output goes to `target/release-bundle`. The assembler refuses to overwrite an existing
output directory. Move a result you want to retain before building the next one.

## Prepare a release

After the source PR is merged and a release is approved, create a new `vMAJOR.MINOR.PATCH`
tag on the chosen `main` commit and push that tag. Prerelease names such as
`v1.0.0-alpha.1` work too. The workflow checks that the tagged commit belongs to
`main`, runs the Java 8 tests, builds the bundle and checks the packaged runtime
without the development dependency classpath. A failure prevents a release draft.

The Action creates a **draft GitHub Release** with the files attached. Review the
bundle in the official launcher with a separate game directory, including account
sign-in, assets, natives, world entry/exit and GUI input. Complete the project's
remaining game/server gates before publishing. Mark alpha/beta releases as
**prereleases**. Reruns may refresh drafts; published releases cannot be overwritten.

The workflow uses the built-in `GITHUB_TOKEN`: read access for builds, write access
only for attaching files to the draft. No extra release token is needed.

## Changelog

The changelog lists up to **10 unique commit subjects** since the preceding reachable
version tag. It drops conventional prefixes (`fix:`, `feat(gui):`, etc.) and traffic
badge refresh commits. The first bundle uses the available source history. A link
points to the complete history or comparison. Keep commit subjects short and clear;
review the automatically generated notes before publishing.

## Launcher contract

The `release-bundle` Maven profile packages the client and Java dependencies without
minimization, merges service descriptors and removes dependency signatures. It does
not change the normal development package or the separate `launcher-dist` profile.

The JSON uses checksum-verified official 1.8.9 metadata for assets, authentication
arguments and platform native downloads. Java libraries come from the bundled JAR,
so old vanilla libraries cannot override patched dependencies. It contains no vanilla
client download URL that could replace Neutron. A shader path alias handles the
existing case difference between loose Windows resources and JAR resources.

The packaged checks cover entrypoint linkage, Java 8 project bytecode, dependencies,
services, resources and reflective module discovery. They do not establish a real
launcher start or platform compatibility. Windows, Linux and macOS launcher checks
remain required for any platform advertised as supported.

## Integrity and provenance

Release tags cannot be updated or deleted. Future published releases are immutable;
prepare all files in the draft before publishing and use a new version for fixes.
The tagged-release job attests the bundle ZIP through GitHub OIDC. Verify it with:

```shell
gh attestation verify Neutron-<version>-bundle.zip --repo YoungJibbit95/Neutron-Client
```

A provenance attestation identifies the workflow/source that produced the archive.
It does not replace launcher/game acceptance or the component provenance review.
