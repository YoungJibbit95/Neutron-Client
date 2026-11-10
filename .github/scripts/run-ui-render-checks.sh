#!/usr/bin/env bash
# Run from an Xvfb-backed Linux display with Temurin 8u482 and Maven 3.9.15.
# Example: xvfb-run -a -s '-screen 0 1280x960x24 +extension GLX' bash .github/scripts/run-ui-render-checks.sh
# The fixtures use real LWJGL2 Pbuffers/FBOs. Missing production shaders,
# application FBO fallbacks, and native errors must fail the checks.
set -euo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd -- "$script_dir/../.." && pwd)"
java_bin="${JAVA_HOME:?Temurin 8u482 JAVA_HOME is required}/bin/java"
javac_bin="$JAVA_HOME/bin/javac"
maven_bin="${MAVEN_HOME:?Maven 3.9.15 MAVEN_HOME is required}/bin/mvn"
: "${DISPLAY:?Run the checks under xvfb-run with Mesa GLX support}"

evidence_root="$repo_root/target/legacy-ui-render"
mkdir -p "$evidence_root"
run_root="$(mktemp -d "$evidence_root/run.XXXXXXXX")"
logs="$run_root/logs"
screenshots="$run_root/screenshots"
fixtures="$run_root/fixtures"
mkdir -p "$logs" "$screenshots" "$fixtures"
native_dir="$repo_root/test_natives/linux"

java_version="$("$java_bin" -version 2>&1)"
javac_version="$("$javac_bin" -version 2>&1)"
maven_version="$("$maven_bin" --version 2>&1)"
{
  printf 'Tested revision: %s\n' "${GITHUB_SHA:-local working tree}"
  printf '%s\n%s\n%s\n' "$java_version" "$javac_version" "$maven_version"
  printf 'Native libraries: %s\n' "$native_dir"
  sha256sum "$native_dir/liblwjgl64.so" "$native_dir/libjinput-linux64.so" "$native_dir/libopenal64.so"
  glxinfo -B
} 2>&1 | tee "$logs/environment.log"

if [[ "$java_version" != *'1.8.0_482'* || "$java_version" != *'Temurin'* \
      || "$java_version" != *'build 1.8.0_482-b08'* || "$javac_version" != 'javac 1.8.0_482' ]]; then
  printf '%s\n' 'ERROR: These native checks require exact Temurin 8u482-b08 and its compiler.' >&2
  exit 1
fi
if [[ "$maven_version" != *'Apache Maven 3.9.15'* ]]; then
  printf '%s\n' 'ERROR: These native checks require Maven 3.9.15.' >&2
  exit 1
fi

# Keep fixture homes/config files and dependency jars outside uploaded evidence.
# test-compile executes the normal production/test compilation without a skip flag.
classpath_file="$run_root/test-classpath.txt"
maven_repository="${RUNNER_TEMP:-$repo_root/target}/legacy-ui-m2"
cd "$repo_root"
"$maven_bin" -B -ntp -Dstyle.color=never "-Dmaven.repo.local=$maven_repository" \
  test-compile org.apache.maven.plugins:maven-dependency-plugin:3.8.1:build-classpath \
  -DincludeScope=test "-Dmdep.outputFile=$classpath_file" 2>&1 | tee "$logs/maven.log"
[[ -s "$classpath_file" ]] || { printf '%s\n' 'ERROR: Maven produced no test dependency classpath.' >&2; exit 1; }
dependency_classpath="$(tr -d '\r\n' < "$classpath_file")"
test_classpath="$repo_root/target/test-classes:$repo_root/target/classes:$dependency_classpath"

# Only top-level images produced by these known fixtures are published. In
# particular, their home/, fixture-*/ and library-fixture-*/ trees are excluded.
collect_screenshots() {
  local name="$1" preview image destination
  shopt -s nullglob
  for preview in gui-preview dropdown-preview arraylist-preview session-preview theme-preview settings-preview neutron-ui-preview; do
    for image in "$fixtures/$name/$preview/"*.png; do
      destination="$screenshots/$name/$preview"
      mkdir -p "$destination"
      cp -- "$image" "$destination/"
    done
  done
  shopt -u nullglob
}

require_marker() {
  local name="$1" marker="$2" output
  output="$(cat "$logs/$name.log")"
  if [[ "$output" != *"$marker"* ]]; then
    printf 'ERROR: %s did not complete its required check: %s\n' "$name" "$marker" >&2
    return 1
  fi
}

require_images() {
  local name="$1" preview="$2"
  local -a images
  shopt -s nullglob
  images=("$screenshots/$name/$preview/"*.png)
  shopt -u nullglob
  if (( ${#images[@]} == 0 )); then
    printf 'ERROR: %s produced no %s screenshots.\n' "$name" "$preview" >&2
    return 1
  fi
}

run_check() {
  local name="$1" main_class="$2" marker="$3" result=0
  shift 3
  mkdir -p "$fixtures/$name"
  printf 'Running %s in its own JVM\n' "$main_class"
  # Capturing the exit code lets us retain screenshots even when a fixture fails;
  # it is returned unchanged after collection. No test failure is ignored.
  (
    cd "$fixtures/$name"
    timeout --signal=TERM --kill-after=15s 6m "$java_bin" \
      -Xms256m -Xmx2g -ea -Dfile.encoding=UTF-8 -Djava.awt.headless=true \
      "-Djava.library.path=$native_dir" "-Dorg.lwjgl.librarypath=$native_dir" \
      "-Dnet.java.games.input.librarypath=$native_dir" \
      -Dpulsar.gui.baseline=false -Ddropdown.performance.baseline=false \
      "$@" -cp "$test_classpath" "$main_class"
  ) 2>&1 | tee "$logs/$name.log" || result=$?
  collect_screenshots "$name"
  if (( result != 0 )); then
    printf 'ERROR: %s exited with status %s.\n' "$main_class" "$result" >&2
    return "$result"
  fi
  require_marker "$name" "$marker"
}

run_check panel me.youngjibbit.clickgui.ClickGuiRenderCheck \
  'PASS [Pbuffer/FBO only]: clipping, input'
require_images panel gui-preview

run_check dropdown me.youngjibbit.clickgui.DropdownGuiRenderCheck \
  'PASS [Pbuffer/FBO]: Dropdown mode'
require_marker dropdown 'PASS [Pbuffer/FBO]: actual config save/load/overwrite/delete/cancel'
require_images dropdown dropdown-preview

run_check arraylist me.youngjibbit.clickgui.ArrayListRenderCheck \
  'PASS [Pbuffer/FBO only]: unified contour'
require_images arraylist arraylist-preview

run_check session me.youngjibbit.module.impl.render.SessionInfoRenderCheck \
  'PASS [Pbuffer/FBO only]: four designs' \
  -Dpulsar.settings.check=true -Dpulsar.theme.check=true -Dpulsar.neutron.ui.check=true
require_marker session 'PASS: editor UI RGBA/name/direction/motion/sliders'
require_marker session 'PASS: Library hub layout/navigation'
require_marker session 'PASS: real client settings exact/invalid/cancel/reset'
for preview in session-preview theme-preview settings-preview neutron-ui-preview; do
  require_images session "$preview"
done

printf '%s\n' \
  'PASS: Four separate native JVMs completed with production shaders, FBO/readback and fixture diagnostics enabled.' \
  'Coverage: panel/subcategories, dropdown/library/config actions, Arraylist, SessionInfo, Theme editor, Client settings and Library transitions.' \
  'Not covered: game-world rendering, native Display/fullscreen/VSync, multiplayer or full client bootstrap.' \
  | tee "$logs/summary.log"
