#!/usr/bin/env bash
# Run from an Xvfb-backed Linux display with Temurin 8u482 and Maven 3.9.15.
# Requires x11-xserver-utils: LWJGL 2.9.4 executes the external xrandr program.
# Example: xvfb-run -a -s '-screen 0 1280x960x24 +extension GLX +extension RANDR' bash .github/scripts/run-ui-render-checks.sh
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
  printf 'Tested revision: %s\n' "${GITHUB_SHA:-local working tree}" &&
  printf '%s\n%s\n%s\n' "$java_version" "$javac_version" "$maven_version" &&
  printf 'Native libraries: %s\n' "$native_dir" &&
  sha256sum "$native_dir/liblwjgl64.so" "$native_dir/libjinput-linux64.so" "$native_dir/libopenal64.so" &&
  # LinuxDisplay:951 indexes XRandR.getScreenNames()[0]. This exact dependency
  # fills that list by executing xrandr -q, not just by loading libXrandr.so.
  # Keep normal XRandR discovery on Xvfb and record its actual connected mode;
  # disabling it can leave Xvfb without the XF86VidMode extension LWJGL requires.
  command -v xrandr &&
  xrandr -q &&
  glxinfo -B
} 2>&1 | tee "$logs/environment.log"
python3 "$script_dir/glx-pbuffer-probe.py" 2>&1 | tee "$logs/glx-configs.log"

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
collect_screenshots() (
  local name="$1" preview image destination
  shopt -s nullglob
  for preview in gui-preview dropdown-preview arraylist-preview session-preview theme-preview settings-preview neutron-ui-preview shader-preview hud-preview armor-preview chat-preview trash-preview target-esp-preview diagnostics-preview neutron20-preview; do
    for image in "$fixtures/$name/$preview/"*.png; do
      destination="$screenshots/$name/$preview"
      mkdir -p "$destination" && cp -- "$image" "$destination/" || return "$?"
    done
  done
  # Exact evidence names only: no fixture homes/configs/runtime data are copied.
  if [[ "$name" == shader_modes ]]; then
    destination="$screenshots/$name/shader-preview"
    for image in "$fixtures/$name/shader-preview/flame-motion.gif" "$fixtures/$name/shader-preview/shader-modes.json"; do
      if [[ -f "$image" ]]; then
        mkdir -p "$destination" && cp -- "$image" "$destination/" || return "$?"
      fi
    done
  fi
  if [[ "$name" == target_esp && -f "$fixtures/$name/target-esp-preview/target-esp.json" ]]; then
    destination="$screenshots/$name/target-esp-preview"
    mkdir -p "$destination" && cp -- "$fixtures/$name/target-esp-preview/target-esp.json" "$destination/" || return "$?"
  fi
)

require_marker() {
  local name="$1" marker="$2" output
  output="$(cat "$logs/$name.log")" || return "$?"
  if [[ "$output" != *"$marker"* ]]; then
    printf 'ERROR: %s did not complete its required check: %s\n' "$name" "$marker" >&2
    return 1
  fi
}

require_images() (
  local name="$1" preview="$2"
  local -a images
  shopt -s nullglob
  images=("$screenshots/$name/$preview/"*.png)
  if (( ${#images[@]} == 0 )); then
    printf 'ERROR: %s produced no %s screenshots.\n' "$name" "$preview" >&2
    return 1
  fi
)

run_check() {
  local name="$1" main_class="$2" marker="$3" result=0 collection_result=0
  shift 3
  mkdir -p "$fixtures/$name" || return "$?"
  printf 'Running %s in its own JVM\n' "$main_class" || return "$?"
  # Capturing the exit code lets us retain screenshots even when a fixture fails;
  # it is returned unchanged after collection. No test failure is ignored.
  (
    cd "$fixtures/$name" &&
    timeout --signal=TERM --kill-after=15s 6m "$java_bin" \
      -Xms256m -Xmx2g -ea -Dfile.encoding=UTF-8 -Djava.awt.headless=true \
      "-Djava.library.path=$native_dir" "-Dorg.lwjgl.librarypath=$native_dir" \
      "-Dnet.java.games.input.librarypath=$native_dir" \
      -Dpulsar.gui.baseline=false -Ddropdown.performance.baseline=false \
      "$@" -cp "$test_classpath" "$main_class"
  ) 2>&1 | tee "$logs/$name.log" || result=$?
  collect_screenshots "$name" || collection_result=$?
  if (( result != 0 )); then
    printf 'ERROR: %s exited with status %s.\n' "$main_class" "$result" >&2
    return "$result"
  fi
  if (( collection_result != 0 )); then
    printf 'ERROR: %s screenshot collection exited with status %s.\n' "$main_class" "$collection_result" >&2
    return "$collection_result"
  fi
  require_marker "$name" "$marker"
}

# The driver handles failures so every independent JVM still runs. Bash disables
# implicit errexit in that context; explicit && chains preserve every suite gate.
suite_panel() {
  run_check panel me.youngjibbit.clickgui.ClickGuiRenderCheck \
    'PASS [Pbuffer/FBO only]: clipping, input' &&
  require_images panel gui-preview
}
suite_dropdown() {
  run_check dropdown me.youngjibbit.clickgui.DropdownGuiRenderCheck \
    'PASS [Pbuffer/FBO]: Dropdown mode' &&
  require_marker dropdown 'PASS [Pbuffer/FBO]: actual config save/load/overwrite/delete/cancel' &&
  require_images dropdown dropdown-preview
}
suite_arraylist() {
  run_check arraylist me.youngjibbit.clickgui.ArrayListRenderCheck \
    'PASS [Pbuffer/FBO only]: unified contour' &&
  require_images arraylist arraylist-preview
}
suite_session() {
  run_check session me.youngjibbit.module.impl.render.SessionInfoRenderCheck \
    'PASS [Pbuffer/FBO only]: six designs' \
    -Dpulsar.settings.check=true -Dpulsar.theme.check=true -Dpulsar.neutron.ui.check=true \
    -Dpulsar.hud.check=true -Dpulsar.armor.check=true -Dpulsar.island.compact.check=true &&
  require_marker session 'PASS: editor UI RGBA/name/direction/motion/sliders' &&
  require_marker session 'PASS: Library hub layout/navigation' &&
  require_marker session 'PASS: real client settings exact/invalid/cancel/reset' &&
  require_images session session-preview &&
  require_images session theme-preview &&
  require_images session settings-preview &&
  require_images session neutron-ui-preview &&
  require_images session hud-preview &&
  require_images session armor-preview &&
  require_marker session 'PASS: compact Island notifications, balanced dock composition'
}
suite_trash() {
  run_check trash me.youngjibbit.module.impl.render.SessionInfoRenderCheck \
    'PASS: Trash registry/icons/variants' -Dpulsar.trash.check=true &&
  require_images trash trash-preview
}
suite_diagnostics() {
  run_check diagnostics me.youngjibbit.module.impl.render.SessionInfoRenderCheck \
    'PASS: diagnostics production panels' -Dpulsar.diagnostics.check=true &&
  require_images diagnostics diagnostics-preview
}
suite_neutron20() {
  run_check neutron20 me.youngjibbit.module.impl.render.SessionInfoRenderCheck \
    'PASS: Neutron 2.0 independent Nametags/Keystrokes pixels' -Dpulsar.neutron20.hud.check=true &&
  require_images neutron20 neutron20-preview
}
suite_target_esp() {
  run_check target_esp me.youngjibbit.module.impl.render.TargetEspRenderCheck \
    'PASS: TargetESP production halo/marker, exact placement, depth, clip, state, fallback and bounded resource lifetime' &&
  require_images target_esp target-esp-preview &&
  python3 - "$screenshots/target_esp/target-esp-preview/target-esp.json" <<'PY'
import json, sys
with open(sys.argv[1], encoding='utf-8') as source:
    report = json.load(source)
assert report['status'] == 'passed', report
PY
}
suite_shader_modes() {
  run_check shader_modes me.youngjibbit.module.impl.render.ShaderModesRenderCheck \
    'PASS: shader modes actual exterior geometry' &&
  require_marker shader_modes 'PASS: fixed mask pass count for 1/12 entities and 2/16 lines' &&
  require_images shader_modes shader-preview &&
  [[ -s "$screenshots/shader_modes/shader-preview/flame-motion.gif" ]] &&
  python3 - "$screenshots/shader_modes/shader-preview/shader-modes.json" <<'PY'
import json, sys
with open(sys.argv[1], encoding='utf-8') as source:
    report = json.load(source)
assert report['status'] == 'passed', report
assert len(report['measurements']) == 6, 'Missing shader-mode timing evidence'
PY
}

suite_chat() {
  run_check chat net.minecraft.client.gui.GuiChatInputNativeCheck \
    'PASS: real GuiChat typing/edit/history/completion' &&
  require_marker chat 'PASS: compact chat presentation' &&
  require_images chat chat-preview
}

failed_suites=()
suite_results=()
for suite in panel dropdown arraylist session shader_modes target_esp trash chat diagnostics neutron20; do
  suite_result=0
  "suite_$suite" 2>&1 | tee "$logs/$suite-suite.log" || suite_result=$?
  suite_results+=("$suite: exit $suite_result")
  if (( suite_result != 0 )); then
    failed_suites+=("$suite")
  fi
done
printf '%s\n' "${suite_results[@]}" | tee "$logs/suites.log"

# Small JPEG copies let remote reviewers inspect the actual fixture images from
# job logs. Full-resolution PNGs above remain the visual/pixel evidence.
printf '%s\n' '::group::Native UI visual review copies'
"$java_bin" -Djava.awt.headless=true -cp "$test_classpath" \
  me.youngjibbit.rendercheck.UiReviewImages "$screenshots"
printf '%s\n' '::endgroup::'

if (( ${#failed_suites[@]} != 0 )); then
  printf 'FAIL: Native UI suites failed: %s. All ten suites were attempted; see each suite log.\n' \
    "${failed_suites[*]}" | tee "$logs/summary.log"
  exit 1
fi

printf '%s\n' \
  'PASS: Ten separate native JVMs completed with production shaders, FBO/readback and fixture diagnostics enabled.' \
  'Coverage: panel/subcategories, dropdown/library/config actions, Arraylist, compact TargetHUD/SessionInfo/ArmorHUD, compact Island/docking, animated chat/input/links/resize, Theme editor, Client settings, Library transitions, searchable Trash menu, TargetESP production halo/hitpoint, Flame/Neon/Lines ESP/UI contours, diagnostics graphs and independent Nametags/Keystrokes data/motion/lifecycle.' \
  'Not covered: game-world rendering, native Display/fullscreen/VSync, multiplayer or full client bootstrap.' \
  | tee "$logs/summary.log"
