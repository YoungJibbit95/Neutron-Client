# JavaFX 8 sign-in migration

Microsoft sign-in now uses the operating system's default browser. The former
OpenAuth window and the additional JavaFX 8 runtime have been removed. Temurin
8u482 remains the client runtime; no JavaFX installation is required for login.

The browser can use its existing Microsoft sessions and password manager. Neutron
receives an authorization code through a temporary loopback callback and exchanges
it using PKCE. It does not read browser cookies or browser password stores.

See [Microsoft browser sign-in](../design/microsoft-browser-login.md) for the
application registration, shared configuration, account behavior and acceptance
requirements. A valid, owned application ID is required; an empty configuration
opens Microsoft setup rather than an embedded login window. This repository does
not supply an approved Neutron application ID.

## Build and package migration

Maven no longer downloads or bundles a JavaFX SDK. The `javafx8-runtime` Antrun
execution, `src/build/javafx8.xml`, OpenAuth dependency and JavaFX runtime loader
have been removed. All packaging profiles use the same browser authentication
implementation. No JavaFX-specific JVM arguments or native library paths are
needed.

Run a clean build when moving from an older checkout:

```text
mvn -B -ntp -Prelease-bundle clean package
```

A clean build removes the old generated SDK ZIP and removed classes from
`target/classes`; an incremental package can otherwise carry stale files. The
existing `PackagedRuntimeCheck` release gate now resolves the browser
implementation and verifies that no OpenAuth classes, removed login classes or
JavaFX runtime payload remain in the packaged JAR. It does not open a browser or
read personal account data. Other release checks remain in place.

Previously created caches under `${user.home}/.neutron/runtime/javafx8/` and the
Maven repository's `me/neutron/javafx8/` are no longer used. This migration does
not automatically delete existing user files or caches. The new build does not
create them.

A separately maintained launcher that validates a POM hash must accept this
reviewed dependency/build change through its normal update procedure. This source
change does not establish that a separately installed launcher was updated or that
a release was deployed.

## Acceptance boundary

Packaging and local OAuth fixture tests cannot prove Microsoft or Minecraft API
acceptance of an application ID. Before release, the owner must configure the
registered application and complete a real browser sign-in, verify the resulting
Minecraft Java profile and test the intended online session. A missing application
registration or unperformed live check remains an open acceptance requirement.
