# IntelliJ: client startup and test source sets

The development `Start` entrypoint belongs to `src/main/java`. Its launch arguments
and native-directory behavior are unchanged. The shared **Neutron Client (Java 8)**
Application configuration uses production dependencies only. Normal startup must
not require `target/test-classes`, JUnit, `NetworkTestSupport` or native screenshot
fixtures.

`PbufferTestContext` remains public in
`src/test/java/me/youngjibbit/rendercheck/PbufferTestContext.java`. It is deliberately
NOT a production dependency. Native render checks still require their existing
explicit entrypoints and native environment. The new `DevelopmentSourceSetsTest`
checks production/test output separation and fixture resolution without creating
an OpenGL context. No native test or other regression is disabled by this change.

## After pulling this change

1. Reimport/reload the root `pom.xml` in IntelliJ's Maven tool window. Use Java 8
   as the project SDK and Maven runner JDK. `src/main/java` must be **Sources**;
   `src/test/java` must be **Test Sources**, with no nested production root below
   the test directory. Do not mark all of `src` as a production source root.
2. Run `mvn clean test` once with Java 8. This also removes the old generated
   `target/test-classes/Start.class` after the source move. Maven clean deletes
   generated `target` contents; back up anything personal manually placed there.
   Do not delete `.idea`, saved worlds, accounts or launcher profiles to repair
   the build. IntelliJ's ordinary Rebuild is not a substitute for a Maven clean.
3. Use the committed **Neutron Client (Java 8)** configuration, which sets
   `INCLUDE_TEST_CLASSPATH=false`. Recreate an old copied local configuration from
   this shared one so it uses `Start` from production sources and module `Neutron`.
   Keep the normal build/error check before launch. Existing assets and
   platform-matched LWJGL2 natives are still required.

## If a test helper is still reported as missing

A source file existing in Git does not prove IntelliJ compiles it in the same
source set as its caller. Inspect the *first* compiler error and its caller path.
Check **Project Structure > Modules > Sources** for wrong/nested roots, and
**Settings > Build, Execution, Deployment > Compiler > Excludes** for excluded test
folders or helpers. Remove only unintended local exclusions, then reload Maven.
Do not add test sources to production or change JUnit to compile scope.

`mvn clean test` is the independent comparison: if Maven succeeds but IntelliJ's
builder does not, compare the IDE source roots, exclusions and output directories
rather than adding imports to unrelated production files. For this Maven project,
**Delegate IDE build/run actions to Maven** is another way to use the same build
model as CI. Individual file compilation is not a full project/test compilation.

This change separates client startup from the test build. It does not claim to
have reproduced a user's uncommitted local IntelliJ configuration or performed
a native Minecraft playtest.
