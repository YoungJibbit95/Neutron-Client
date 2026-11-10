"""Rebuild a source baseline, compare real offscreen RGBA and retain synthetic timings.

Requires Java 8, compiled project/test classes and a native X/GL context (Xvfb is
supported). It never changes the checkout, starts a world or turns timing noise
into a pass/fail speed claim. Pixel differences and native regression failures fail.
"""

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import time
from urllib.request import urlopen
import zipfile
import io

ROOT = Path(__file__).resolve().parents[2]
BENCHMARK = "me.youngjibbit.module.impl.render.RenderPerformanceBenchmark"
NATIVE_URL = ("https://libraries.minecraft.net/org/lwjgl/lwjgl/lwjgl-platform/"
              "2.9.4-nightly-20150209/lwjgl-platform-2.9.4-nightly-20150209-natives-linux.jar")
NATIVE_SHA256 = "d312147c3999b868fd649887f45203a4195bc10fc563cee4c4f8d22761421f94"


def git(*args):
    return subprocess.check_output(["git", *args], cwd=ROOT).decode("utf-8").strip()


def validate_sha(value):
    if not re.fullmatch(r"[0-9a-fA-F]{40}", value) or value == "0" * 40:
        raise ValueError("The baseline must be a complete nonzero commit SHA")
    return value.lower()


def run(command, log, cwd=ROOT, timeout=600, env=None):
    log.parent.mkdir(parents=True, exist_ok=True)
    print("Running " + log.name, flush=True)
    with log.open("w", encoding="utf-8") as output:
        output.write("Command: " + repr([str(part) for part in command]) + "\n")
        output.flush()
        result = subprocess.run([str(part) for part in command], cwd=cwd, stdout=output,
                                stderr=subprocess.STDOUT, timeout=timeout, env=env)
    if result.returncode:
        print(log.read_text(encoding="utf-8", errors="replace")[-14000:], file=sys.stderr)
        raise RuntimeError(f"{log.name} failed with exit code {result.returncode}")


def native_libraries(destination):
    destination.mkdir(parents=True, exist_ok=True)
    for attempt in range(3):
        try:
            with urlopen(NATIVE_URL, timeout=45) as response:
                data = response.read(10 * 1024 * 1024)
            break
        except OSError:
            if attempt == 2:
                raise
            time.sleep(3 * (attempt + 1))
    if hashlib.sha256(data).hexdigest() != NATIVE_SHA256:
        raise ValueError("Pinned LWJGL Linux natives checksum mismatch")
    with zipfile.ZipFile(io.BytesIO(data)) as archive:
        for name in ("liblwjgl64.so", "libopenal64.so"):
            (destination / name).write_bytes(archive.read(name))


def baseline_classes(base, dependency_classpath, output, javac):
    # Overloaded ShaderUtil methods change how unchanged callers are compiled.
    # Compile every original caller, referencing unchanged sources in place; only
    # changed/deleted original files are materialized. No checkout/source clone.
    source_root = ROOT / "target/render-baseline-sources"
    classes = ROOT / "target/render-baseline-classes"
    if classes.exists() and any(classes.iterdir()):
        raise RuntimeError("Use a fresh target/render-baseline-classes directory; stale bytecode is not a valid baseline")
    classes.mkdir(parents=True, exist_ok=True)
    paths = [p for p in git("ls-tree", "-r", "--name-only", base, "--", "src/main/java").splitlines() if p.endswith(".java")]
    changed = set(git("diff", "--name-only", base, "HEAD", "--", "src/main/java").splitlines())
    unsupported = git("diff", "--name-only", base, "HEAD", "--", "pom.xml", "src/build", "src/main/resources")
    if unsupported:
        raise RuntimeError("Source-only comparison requires unchanged dependencies/resources: " + unsupported)
    sources, materialized = [], []
    for path in paths:
        source = ROOT / path
        if path in changed or not source.is_file():
            source = source_root / path
            source.parent.mkdir(parents=True, exist_ok=True)
            source.write_bytes(subprocess.check_output(["git", "show", base + ":" + path], cwd=ROOT))
            materialized.append(path)
        sources.append(source)
    arguments = ["-encoding", "UTF-8", "-source", "8", "-target", "8", "-cp", dependency_classpath, "-d", str(classes)] + [str(p) for p in sources]
    argument_file = ROOT / "target/render-baseline-javac.args"
    argument_file.write_text("\n".join('"' + arg.replace("\\", "\\\\").replace('"', '\\"') + '"' for arg in arguments), encoding="utf-8")
    run([javac, "-J-Xmx2g", "@" + str(argument_file)], output / "compile-baseline.log")
    return classes, materialized, len(sources)


def compare_pixels(before, after):
    old = {p.name: p for p in before.glob("*.rgba")}
    new = {p.name: p for p in after.glob("*.rgba")}
    if not old or set(old) != set(new):
        raise ValueError("Missing or unmatched native RGBA captures")
    comparisons = []
    for name in sorted(old):
        left, right = old[name].read_bytes(), new[name].read_bytes()
        if not left or not right or len(left) % 4 or len(right) % 4:
            raise ValueError("Empty or malformed RGBA capture: " + name)
        same = left == right
        comparisons.append({"capture": name, "identical": same, "bytes": len(left),
                            "baseline_sha256": hashlib.sha256(left).hexdigest(),
                            "current_sha256": hashlib.sha256(right).hexdigest(),
                            "changed_channels": 0 if same else sum(a != b for a, b in zip(left, right)) + abs(len(left) - len(right)),
                            "max_channel_difference": 0 if same else 255 if len(left) != len(right) else max(abs(a - b) for a, b in zip(left, right))})
    return comparisons


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base", required=True, type=validate_sha)
    parser.add_argument("--classpath", type=Path, default=Path("target/render-classpath.txt"))
    parser.add_argument("--output", type=Path, default=Path("target/render-performance"))
    parser.add_argument("--warmup", type=int, default=20)
    parser.add_argument("--samples", type=int, default=60)
    args = parser.parse_args()
    if not 1 <= args.warmup <= 200 or not 20 <= args.samples <= 500:
        parser.error("Warmup must be 1..200 and samples 20..500")
    output = (ROOT / args.output).resolve()
    output.mkdir(parents=True, exist_ok=True)
    java_home = Path(os.environ["JAVA_HOME"])
    java, javac = java_home / "bin/java", java_home / "bin/javac"
    version = subprocess.check_output([str(java), "-version"], stderr=subprocess.STDOUT).decode()
    if 'version "1.8.0_' not in version:
        raise RuntimeError("Render comparison requires the same Java 8 toolchain for both revisions")
    git("cat-file", "-e", args.base + "^{commit}")
    head = git("rev-parse", "HEAD")
    dependency_classpath = (ROOT / args.classpath).read_text(encoding="utf-8").strip()
    if not dependency_classpath:
        raise RuntimeError("Empty dependency classpath; run Maven test-compile and dependency:build-classpath first")
    baseline, materialized, source_count = baseline_classes(args.base, dependency_classpath, output, javac)
    natives = ROOT / "target/render-native-libraries"
    native_libraries(natives)
    classpath = os.pathsep.join([str(ROOT / "target/test-classes"), str(ROOT / "target/classes"), dependency_classpath])
    common = [java, "-Xms256m", "-Xmx1024m", "-Djava.awt.headless=true",
              "-Dorg.lwjgl.librarypath=" + str(natives), "-Djava.library.path=" + str(natives)]
    environment = dict(os.environ, LIBGL_ALWAYS_SOFTWARE="1", GALLIUM_DRIVER="llvmpipe", LP_NUM_THREADS="2")
    for name, prefix in (("baseline", str(baseline) + os.pathsep), ("current", "")):
        directory = output / name
        directory.mkdir(exist_ok=True)
        run(common + ["-cp", prefix + classpath, BENCHMARK, directory, str(args.warmup), str(args.samples)],
            output / (name + ".log"), cwd=directory, env=environment)
    comparisons = compare_pixels(output / "baseline", output / "current")
    summary = {"baseline_commit": args.base, "current_commit": head, "java": version.strip(),
               "baseline_sources_compiled": source_count, "materialized_original_sources": materialized,
               "pixels": comparisons, "pixel_parity": all(item["identical"] for item in comparisons),
               "timing_gate": "None: timings are evidence, not a noisy pass/fail speed threshold",
               "coverage": "Pbuffer/FBO, actual shaders/fonts, two viewport/GUI scales; excludes native Display, worlds, shaderpacks and game FPS",
               "current_native_checks_passed": False}
    for name in ("baseline", "current"):
        summary[name] = json.loads((output / name / "results.json").read_text())
    for key in ("java", "gl_renderer", "gl_version"):
        if summary["baseline"][key] != summary["current"][key]:
            raise RuntimeError("Baseline/current environment changed: " + key)
    report = output / "comparison.json"
    report.write_text(json.dumps(summary, indent=2) + "\n", encoding="utf-8")
    # New contract tests run only against current classes. Each suite gets a fresh JVM.
    regression = output / "current-regressions"
    regression.mkdir(exist_ok=True)
    flags = ["fonts", "cleanup", "ui.state", "effects", "composition", "resources"]
    run(common + ["-Dpulsar." + flag + ".check=true" for flag in flags] + ["-cp", classpath,
            "me.youngjibbit.module.impl.render.SessionInfoRenderCheck"],
        output / "current-regressions.log", cwd=regression, env=environment)
    legacy = output / "current-legacy-facades"
    legacy.mkdir(exist_ok=True)
    run(common + ["-cp", classpath, BENCHMARK, legacy, "--verify-current"],
        output / "current-legacy-facades.log", cwd=legacy, env=environment)
    summary["current_native_checks_passed"] = True
    report.write_text(json.dumps(summary, indent=2) + "\n", encoding="utf-8")
    for name in ("baseline", "current"):
        prefix = str(baseline) + os.pathsep if name == "baseline" else ""
        run(common + ["-cp", prefix + classpath, "me.youngjibbit.utils.font.FontMetricsBenchmark"],
            output / (name + "-font-metrics.log"), cwd=output, env=environment)
    if not summary["pixel_parity"]:
        failed = [item["capture"] for item in comparisons if not item["identical"]]
        raise AssertionError("Native RGBA differs: " + ", ".join(failed))
    print(f"PASS: {len(comparisons)} exact RGBA comparisons and current native contract checks")


if __name__ == "__main__":
    main()
