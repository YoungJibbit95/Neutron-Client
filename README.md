# Pulsar Client Revived

Minecraft 1.8.9 utility client built on MavenMCP. Parts of the interface still use the Neutron name. This repository contains the current development source and regression checks.

## Development setup

Use **Java 8** (validated with Temurin 8u482) and **Maven 3.9.15**. Import `pom.xml` into the IDE and include test sources in the development launch classpath.

```text
mvn clean test
```

Once dependencies are cached, `mvn -o clean test` runs without repository access. A fresh-machine dependency/bootstrap installation has not yet been validated; some inherited repositories are legacy services.

To launch from the IDE:

1. Create a disposable `run-local` directory inside the project and provision the Minecraft 1.8 assets under its `assets` directory. Assets and personal game data are not supplied as a new runtime distribution.
2. Run the test-source class `Start` with Java 8 and `run-local` as the working directory. Its native-library path resolves to the project's `test_natives` directory.
3. Put heap/GC options in **VM options**, never program arguments. A typical pair is `-Xms2G -Xmx4G`.
4. Use an offline test profile and local world for the first functional checks. Open ClickGUI with right Shift.

The existing JAR packaging tasks remain available through `mvn package`. Their CPU/logging dependency paths are tested, but the inherited launcher entrypoint and standalone asset/native delivery are still an open release task. These JARs are not yet a validated standalone installer.

## Accounts

Offline accounts are supported. Microsoft browser authentication uses a registered public desktop app, an authorization-code/PKCE flow and a loopback callback. The project does not ship another application's client ID or a client secret. See [Microsoft browser login setup](design/microsoft-browser-login.md) for registration, configuration and verification requirements.

Personal credentials, tokens, worlds, logs and local profiles must not be committed. New browser accounts persist profile metadata only; browser tokens remain in memory.

## Validation

Ordinary unit checks use synthetic state. Native rendering checks use isolated Pbuffer/FBO contexts and Windows LWJGL natives. For the current native test groups:

```text
mvn -o test -Dpulsar.test.glContext=true -Dpulsar.world.overlay.native=true -Dpulsar.chat.native=true -Dorg.lwjgl.librarypath=<absolute-path-to-test_natives/windows>
mvn -o test -Dtest=NametagsLabelNativeCheck -Dpulsar.nativeLabels=true
```

Offscreen checks do not establish native-window, shaderpack, live-server or real-account behavior. See the reports under [design](design/) for precise coverage and remaining audit findings.

## License

See [LICENSE](LICENSE) and the notices accompanying dependencies and retained third-party code.
