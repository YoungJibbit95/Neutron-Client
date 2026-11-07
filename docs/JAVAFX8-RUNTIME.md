# JavaFX 8 for Microsoft sign-in

The client keeps Temurin 8u482. JavaFX 8 is an additional Windows x64 runtime,
prepared by Maven's `generate-resources` phase using `src/build/javafx8.xml`.
The modern `org.openjfx` Java 11+ modules are not used on this Java 8 project.

## Reproducible dependency

The source is Azul's `zulu8.96.0.205-ca-fx-jre8.0.504-win_x64.zip` (85,231,200 bytes),
SHA-256 `a8710b6033d1d05cfaa637304be3ecf7945994a7a6eb0a8fdb8f8be98aabfcf7`.
The public [vendor metadata](https://api.azul.com/metadata/v1/zulu/packages/57856cfa-7552-4d0d-9c7a-218d39e541a8)
identifies the exact artifact and checksum. The build does not resolve a moving
`latest` version. The archive is cached once under the configured Maven repository's
`me/neutron/javafx8` directory and checked on every build. An already-cached valid
archive also works with Maven offline; the first build needs the vendor download.

Only `jfxrt.jar`, the selected JavaFX/Visual C++ Windows libraries and the original
license/third-party notices are copied into `/neutron/runtime/javafx8-win-x64.zip`.
No second JVM or full client copy is shipped. The vendor folder layout is retained:
JavaFX resolves its DLLs from `../../bin` relative to `lib/ext/jfxrt.jar`.
The archive's `OPENJFX_LICENSE`, `OPENJFX_THIRD_PARTY_README`, `LICENSE`,
`THIRD_PARTY_README`, `ASSEMBLY_EXCEPTION` and `DISCLAIMER` travel with the SDK.
For source and notices, consult the vendor metadata/distribution and
[OpenJFX](https://openjfx.io/); keep the bundled notices when redistributing.

## Runtime and distribution contract

`JavaFxRuntime.load()` extracts the bundled SDK under
`${user.home}/.neutron/runtime/javafx8/`. It validates cache contents against the
packaged entries, repairs changed files and appends the intact `jfxrt.jar` to the
Java 8 application class loader before creating OpenAuth's window. It does not
change `java.home`, `java.library.path`, `org.lwjgl.librarypath` or JVM installation
files. Unsupported platforms receive a fixed login error; this bundle supports
Windows x64 only. An explicitly configured own app retains system-browser PKCE.

The runtime is an ordinary generated resource, so development classes, Assembly,
the `release-bundle` fat JAR and the `launcher-dist` thin client JAR carry the same
SDK. No separate per-launcher JavaFX JVM arguments are needed. IntelliJ must run
Maven `process-classes` (or the normal Maven build) once before its `Start` entry;
the generated resource is not checked into source control. The official Minecraft
Launcher keeps its existing version JSON and Java 8 entrypoint.

The separately maintained, currently untracked Neutron Launcher has an old,
hash-guarded POM adapter. It must accept the reviewed new POM and preserve the
JavaFX build phase before building this source revision. An unapplied adapter
patch is supplied as handoff evidence; the launcher tree itself is not owned by
this change. Testing the thin JAR is not evidence that the installed launcher's
adapter has been upgraded or that a release has been deployed.

## Verification boundaries

`JavaFxRuntimeTest` launches a separate Temurin 8 process in a different working
directory and isolated user home. The probe initializes the real JFXPanel, Glass,
Prism and WebKit, loads local HTML and evaluates JavaScript three times without
showing a JFrame/Stage or accessing account, game or world files. Cache repair,
invalid archives, OAuth return validation, cancellation, late results and retries
have separate regression tests. The same probe can be run against a packaged JAR
using only the test classes and that JAR on its classpath.

These checks do not log into Microsoft, consent, test a real visible login window,
prove OpenAuth's app remains accepted, verify ownership/family restrictions or join
a server. Those are manual release gates. No account secrets are needed for the
automated checks.
