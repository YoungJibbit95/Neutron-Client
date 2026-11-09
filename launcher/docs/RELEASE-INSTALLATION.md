# Windows x64 release installation

Launcher 0.3.0 installs client JAR/JSON assets from YoungJibbit95/Neutron-Client.
Schema-2 resources/release.json selects the launcher version, channel and trusted
repository. Client source/archive pins, Maven and the build adapter are removed.

## Catalog and integrity

The catalog refreshes at startup, every five minutes while idle, manually and before
installation. Only published matching Neutron-<version>.jar/.json pairs with valid
sizes and SHA-256 digests are offered. Drafts, incomplete pairs and launcher releases
are excluded. The newest stable client is the default; prereleases are labelled.
The renderer selects catalog tags and cannot supply arbitrary download URLs.

--prepare-release prepares the published files, verified Temurin 8u482-b08,
Minecraft 1.8.9 assets and x64 natives. The version JSON contract is checked and
both files are hashed. Java dependencies come from the complete release JAR.
Production Bootstrap validation and receipt writing precede atomic state selection.
The receipt identifies release ID/tag, tag commit, JAR/JSON/manifest hashes,
toolchain and selected version. Failure/cancellation retains the prior selection.

## Storage and upgrades

Run setup as an ordinary Windows user. Elevated/all-users installation is refused
before program replacement. First setup needs internet and 3 GiB free on the data
drive. Select data separately from program files; existing profiles retain their
folder. The persistent selector is %APPDATA%\Neutron Launcher\data-directory.txt.
Missing drives/incomplete profiles fail explicitly; no empty C: fallback is created.

To change data drive, close Neutron, copy the complete data folder while retaining
the original and select the copy in setup. Its retained files are compared before
activation. No personal files are moved/deleted. A separately configured game
folder retains its existing path. /DATA="F:\Neutron Data" selects data; final
argument /D=... selects program files. NEUTRON_DATA_ROOT is process-only.

Legacy source-built records/files remain readable and retained, but cannot satisfy
a new release installation and their old source-build updater is unavailable.
Online setup adds a published release. Old versions/caches/settings are not deleted.
NSIS replaces the launcher EXE before client preparation: failures retain the old
selected client, not the old EXE. Rerun setup to repair; uninstall retains data.

## Offline behavior

Play verifies installed files and starts Java locally. Metadata refresh never
installs a client or interrupts play. Installed versions remain available offline.
--verify-release verifies the selected downloaded release with networking disabled.
--prepare-release --offline reuses an intact selected release. Missing/corrupt files
requiring download fail without replacing the selection. A legacy source build is
not a downloaded release. Game account/session behavior remains a client concern.

## Acceptance and publication

Disposable Windows CI tests actual ordinary-user NSIS install, local/offline
verification, renderer startup, failure preservation, reinstall and uninstall.
A real historical 0.2.0 installer is followed by the new candidate. The diagnostic
reports whether that old installation completed or merely registered incompletely;
release notes distinguish upgrade from repair.

Independent artifact review checks exact bytes, source revision and receipts.
Main pushes build and review artifacts for playtesting. After the user's playtest
approval, publication requires an explicit manual dispatch with publish enabled
on main, a normal merge and successful required PR checks. It uploads unchanged
accepted files under a new launcher-v tag, verifies
draft asset digests and checks the public EXE after publication. Existing published
releases and client v tags remain unchanged. Checksums, metadata and receipts are
attached. Unsigned installer acceptance does not establish full GUI/DPI/GPU-loss
or real gameplay coverage.
