"""Rebuild a source baseline, compare real offscreen RGBA and retain synthetic timings.

Requires Java 8, compiled project/test classes and a native X/GL context (Xvfb is
supported). It never changes the checkout, starts a world or turns timing noise
into a pass/fail speed claim. Pixel differences and native regression failures fail.
"""

import argparse
import hashlib
import json
import math
import os
from pathlib import Path
import re
import subprocess
import statistics
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
SCENARIOS = {
    f"{scene}_{width}x{height}_scale{scale}": (width, height, scale)
    for width, height, scale in ((960, 640, 2), (854, 480, 1))
    for scene in ("ui_text", "clipped_bloom", "gaussian_blur", "glass_surface")
}
CAPTURE_SIZES = {f"{name}_{phase}.rgba": width * height * 4
                 for name, (width, height, _) in SCENARIOS.items()
                 for phase in ("first", "warm")}
ENVIRONMENT_KEYS = ("java", "os", "gl_vendor", "gl_renderer", "gl_version")
METRICS = ("submission_ms", "completed_ms", "allocated_bytes")


def git(*args):
    return subprocess.check_output(["git", *args], cwd=ROOT).decode("utf-8").strip()


def validate_sha(value):
    if not re.fullmatch(r"[0-9a-fA-F]{40}", value) or value == "0" * 40:
        raise ValueError("The baseline must be a complete nonzero commit SHA")
    return value.lower()


def run(command, log, cwd=ROOT, timeout=600, env=None):
    log.parent.mkdir(parents=True, exist_ok=True)
    print("Running " + log.parent.name + "/" + log.name, flush=True)
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


def compare_pixels(before, after, expected_sizes=None):
    old = {p.name: p for p in before.glob("*.rgba")}
    new = {p.name: p for p in after.glob("*.rgba")}
    if not old or set(old) != set(new):
        raise ValueError("Missing or unmatched native RGBA captures")
    if expected_sizes is not None and set(old) != set(expected_sizes):
        raise ValueError("Native RGBA captures do not match all 16 required first/warm scenes")
    comparisons = []
    for name in sorted(old):
        left, right = old[name].read_bytes(), new[name].read_bytes()
        if not left or not right or len(left) % 4 or len(right) % 4:
            raise ValueError("Empty or malformed RGBA capture: " + name)
        if expected_sizes is not None and (len(left) != expected_sizes[name] or len(right) != expected_sizes[name]):
            raise ValueError("Native RGBA byte size does not match the scene dimensions: " + name)
        same = left == right
        comparisons.append({"capture": name, "identical": same, "bytes": len(left),
                            "baseline_sha256": hashlib.sha256(left).hexdigest(),
                            "current_sha256": hashlib.sha256(right).hexdigest(),
                            "changed_channels": 0 if same else sum(a != b for a, b in zip(left, right)) + abs(len(left) - len(right)),
                            "max_channel_difference": 0 if same else 255 if len(left) != len(right) else max(abs(a - b) for a, b in zip(left, right))})
    return comparisons


def pair_orders(pairs):
    if pairs < 2 or pairs > 8 or pairs % 2:
        raise ValueError("Use an even number of JVM pairs from 2 to 8 for balanced AB/BA order")
    return [["baseline", "current"] if index % 2 == 0 else ["current", "baseline"]
            for index in range(pairs)]


def validate_report(report, warmup, samples, expected_environment=None):
    environment = {key: report[key] for key in ENVIRONMENT_KEYS}
    if not all(isinstance(value, str) and value for value in environment.values()):
        raise ValueError("Incomplete native JVM/GL environment metadata")
    if expected_environment is not None and environment != expected_environment:
        raise ValueError("Native JVM/GL environment changed across trials")
    if report["warmup_frames"] != warmup or report["samples"] != samples:
        raise ValueError("Native warmup/sample counts changed across JVMs")
    scenes = report["scenarios"]
    if len(scenes) != len(SCENARIOS) or {scene["name"] for scene in scenes} != set(SCENARIOS):
        raise ValueError("Native report must contain all eight unique scenes")
    for scene in scenes:
        if (scene["width"], scene["height"], scene["scale"]) != SCENARIOS[scene["name"]]:
            raise ValueError("Native scene dimensions or GUI scale changed")
        if scene.get("visible_output_verified") is not True:
            raise ValueError("Native scene did not verify visible output")
        for metric in METRICS:
            distribution = scene[metric]
            values = distribution["samples"]
            if len(values) != samples or any(isinstance(value, bool) or not isinstance(value, (int, float))
                                              or not math.isfinite(value) for value in values):
                raise ValueError("Incomplete or non-finite native samples: " + metric)
            unsupported = metric == "allocated_bytes" and all(value == -1 for value in values)
            if not unsupported and any(value < 0 for value in values):
                raise ValueError("Negative native samples: " + metric)
            for key, percentile in (("p50", .50), ("p95", .95), ("p99", .99)):
                if distribution[key] != nearest_rank(values, percentile):
                    raise ValueError("Native percentile does not match its retained raw samples: " + metric)
    return environment


def nearest_rank(values, percentile):
    return sorted(values)[math.ceil(len(values) * percentile) - 1]


def series_summary(values):
    # Unavailable counters or zero-denominator ratios stay explicit. Never drop
    # such a trial and summarize only the convenient remaining measurements.
    available = bool(values) and all(value is not None for value in values)
    return {"values_in_trial_order": values, "available_for_all_trials": available,
            "median": statistics.median(values) if available else None,
            "min": min(values) if available else None,
            "max": max(values) if available else None}


def aggregate_metrics(trials, name, phase=None):
    result = {}
    for metric in METRICS:
        medians = {}
        for revision in ("baseline", "current"):
            values = []
            for trial in trials:
                distribution = next(scene for scene in trial[revision]["scenarios"] if scene["name"] == name)[metric]
                # The mixed scene alternates inputs. Its overall p50 sits on the
                # edge of the faster half; retain it, but also compare like phases.
                value = distribution["p50"] if phase is None else nearest_rank(distribution["samples"][phase::2], .50)
                values.append(None if metric == "allocated_bytes" and value < 0 else value)
            medians[revision] = values
        pairs = list(zip(medians["baseline"], medians["current"]))
        ratios = [current / baseline if baseline is not None and current is not None and baseline > 0 else None
                  for baseline, current in pairs]
        result[metric] = {
            "baseline_jvm_p50": series_summary(medians["baseline"]),
            "current_jvm_p50": series_summary(medians["current"]),
            "paired_current_over_baseline_p50": series_summary(ratios),
            "paired_p50_change_percent": series_summary([None if ratio is None else (ratio - 1) * 100 for ratio in ratios]),
            "comparable_pairs": sum(before is not None and after is not None for before, after in pairs),
            "current_lower_pairs": sum(after < before for before, after in pairs if before is not None and after is not None),
            "current_higher_pairs": sum(after > before for before, after in pairs if before is not None and after is not None),
        }
    return result


def aggregate_trials(trials):
    aggregate = []
    for name, (width, height, scale) in SCENARIOS.items():
        result = {"name": name, "width": width, "height": height, "scale": scale,
                  "jvm_count_per_revision": len(trials),
                  "overall": aggregate_metrics(trials, name), "phases": []}
        phases = (("radius_7", 0), ("radius_11", 1)) if name.startswith("gaussian_blur_") else (
                  (("iterations_2", 0), ("iterations_3", 1)) if name.startswith("clipped_bloom_") else ())
        for label, index in phases:
            result["phases"].append({"name": label, "sample_index_modulo_2": index,
                                     "samples_per_jvm": trials[0]["baseline"]["samples"] // 2,
                                     "metrics": aggregate_metrics(trials, name, index)})
        aggregate.append(result)
    return aggregate


def write_report(output, summary):
    # If the job is terminated, its last checkpoint remains explicitly incomplete
    # until every pair and both current-only native contract suites have passed.
    temporary = output / "comparison.json.tmp"
    temporary.write_text(json.dumps(summary, indent=2, allow_nan=False) + "\n", encoding="utf-8")
    temporary.replace(output / "comparison.json")


def run_trials(output, summary, orders, warmup, samples, execute_revision):
    expected_environment = None
    try:
        for index, order in enumerate(orders, 1):
            directory = output / f"trial-{index:02d}"
            directory.mkdir(exist_ok=True)
            trial = {"trial": index, "order": order, "status": "incomplete", "runs": []}
            summary["trials"].append(trial)
            pair_started = time.monotonic()
            write_report(output, summary)
            for revision in order:
                target = directory / revision
                target.mkdir(exist_ok=True)
                record = {"revision": revision, "output": str(target.relative_to(output)), "status": "incomplete"}
                trial["runs"].append(record)
                write_report(output, summary)
                started = time.monotonic()
                execute_revision(revision, target, directory / (revision + ".log"))
                record["elapsed_seconds"] = time.monotonic() - started
                report = json.loads((target / "results.json").read_text(encoding="utf-8"))
                expected_environment = validate_report(report, warmup, samples, expected_environment)
                trial[revision] = report
                record["status"] = "passed"
                write_report(output, summary)
            trial["pixels"] = compare_pixels(directory / "baseline", directory / "current", CAPTURE_SIZES)
            trial["pixel_parity"] = all(item["identical"] for item in trial["pixels"])
            trial["elapsed_seconds"] = time.monotonic() - pair_started
            evidence = [path for path in directory.rglob("*") if path.is_file() and path.suffix in (".json", ".log", ".png", ".rgba")]
            trial["artifact_files"] = len(evidence)
            trial["uncompressed_artifact_bytes"] = sum(path.stat().st_size for path in evidence)
            trial["status"] = "passed" if trial["pixel_parity"] else "failed"
            summary["completed_pairs"] = index
            summary["exact_rgba_pairs"] = sum(item["identical"] for pair in summary["trials"] for item in pair.get("pixels", []))
            if index == 1:
                summary["first_pair_budget_estimate"] = {
                    "observed_pair_seconds": trial["elapsed_seconds"],
                    "observed_uncompressed_artifact_bytes": trial["uncompressed_artifact_bytes"],
                    "projected_all_pairs_seconds": trial["elapsed_seconds"] * len(orders),
                    "projected_all_pairs_uncompressed_artifact_bytes": trial["uncompressed_artifact_bytes"] * len(orders),
                    "scope": "Linear planning estimate; excludes CI setup, baseline compilation, current contracts, CPU font probes and artifact upload. Workflow limit remains 25 minutes.",
                }
                print("FIRST_PAIR_BUDGET " + json.dumps(summary["first_pair_budget_estimate"]), flush=True)
            write_report(output, summary)
            if not trial["pixel_parity"]:
                raise AssertionError("Native RGBA differs in trial " + str(index))
        summary["pixel_parity"] = True
        summary["aggregate_jvm_p50"] = aggregate_trials(summary["trials"])
        write_report(output, summary)
    except Exception as error:
        summary["status"] = "failed"
        summary["error"] = {"type": type(error).__name__, "message": str(error)}
        if summary["trials"]:
            summary["trials"][-1]["status"] = "failed"
        write_report(output, summary)
        raise


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base", required=True, type=validate_sha)
    parser.add_argument("--classpath", type=Path, default=Path("target/render-classpath.txt"))
    parser.add_argument("--output", type=Path, default=Path("target/render-performance"))
    parser.add_argument("--pairs", type=int, default=4)
    parser.add_argument("--warmup", type=int, default=60)
    parser.add_argument("--samples", type=int, default=90)
    args = parser.parse_args()
    if not 1 <= args.warmup <= 200 or not 20 <= args.samples <= 500 or args.samples % 2:
        parser.error("Warmup must be 1..200 and samples an even number from 20..500")
    try:
        orders = pair_orders(args.pairs)
    except ValueError as error:
        parser.error(str(error))
    output = (ROOT / args.output).resolve()
    output.mkdir(parents=True, exist_ok=True)
    if (output / "comparison.json").exists() or any(output.glob("trial-*")):
        raise RuntimeError("Use a fresh evidence output directory; previous trials must not be overwritten")
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
    environment = dict(os.environ, LIBGL_ALWAYS_SOFTWARE="1", GALLIUM_DRIVER="llvmpipe", LP_NUM_THREADS="2")
    # The pinned LWJGL 2 XRandR implementation executes this CLI. Validate the
    # real X/GL environment before compiling the baseline, retaining diagnostics
    # instead of disabling RandR or substituting desktop state in the harness.
    run(["xrandr", "--query"], output / "xrandr.log", timeout=30, env=environment)
    run(["glxinfo", "-B"], output / "glxinfo.log", timeout=30, env=environment)
    run(["glxinfo", "-t"], output / "glxinfo-configs.log", timeout=30, env=environment)
    baseline, materialized, source_count = baseline_classes(args.base, dependency_classpath, output, javac)
    natives = ROOT / "target/render-native-libraries"
    native_libraries(natives)
    classpath = os.pathsep.join([str(ROOT / "target/test-classes"), str(ROOT / "target/classes"), dependency_classpath])
    common = [java, "-Xms256m", "-Xmx1024m", "-Djava.awt.headless=true",
              "-Dorg.lwjgl.librarypath=" + str(natives), "-Djava.library.path=" + str(natives)]
    summary = {"format_version": 2, "baseline_commit": args.base, "current_commit": head, "java": version.strip(),
               "baseline_sources_compiled": source_count, "materialized_original_sources": materialized,
               "status": "incomplete", "phase": "render_trials", "trials": [], "pair_orders": orders,
               "requested_pairs": args.pairs, "completed_pairs": 0, "exact_rgba_pairs": 0,
               "warmup_frames": args.warmup, "samples_per_jvm": args.samples, "pixel_parity": False,
               "aggregation_method": "Median/min/max of JVM p50 values, retaining every trial. Within-JVM p50/p95/p99 use nearest-rank and remain with raw frame samples; frames are never pooled across JVMs. Paired ratios use current/baseline from the same trial, including ratios above one.",
               "phase_method": "Measured sample index modulo 2: Gaussian radii 7/11 and bloom iterations 2/3. Phase p50 is nearest-rank within each JVM, then median/min/max across JVMs. Mixed overall distributions are also retained.",
               "timing_gate": "None: timings are evidence, not a noisy pass/fail speed threshold",
               "coverage": "Pbuffer/FBO, actual shaders/fonts, two viewport/GUI scales; excludes native Display, worlds, shaderpacks and game FPS",
               "current_native_checks_passed": False,
               "native_contracts": {"current-regressions": "not_run", "current-legacy-facades": "not_run"},
               "font_metrics": {"baseline": "not_run", "current": "not_run"}}
    write_report(output, summary)

    def execute_revision(name, directory, log):
        prefix = str(baseline) + os.pathsep if name == "baseline" else ""
        run(common + ["-cp", prefix + classpath, BENCHMARK, directory, str(args.warmup), str(args.samples)],
            log, cwd=directory, env=environment)

    try:
        run_trials(output, summary, orders, args.warmup, args.samples, execute_revision)
        # Every current-only contract remains required; each suite runs once in a fresh JVM.
        flags = ["fonts", "cleanup", "ui.state", "effects", "composition", "resources"]
        contracts = {
            "current-regressions": ["-Dpulsar." + flag + ".check=true" for flag in flags] + ["-cp", classpath, "me.youngjibbit.module.impl.render.SessionInfoRenderCheck"],
            "current-legacy-facades": ["-cp", classpath, BENCHMARK, output / "current-legacy-facades", "--verify-current"],
        }
        for name, command in contracts.items():
            directory = output / name
            directory.mkdir(exist_ok=True)
            summary["phase"] = "native_contracts/" + name
            summary["native_contracts"][name] = "incomplete"
            write_report(output, summary)
            run(common + command, output / (name + ".log"), cwd=directory, env=environment)
            summary["native_contracts"][name] = "passed"
            write_report(output, summary)
        summary["current_native_checks_passed"] = True
        for name in ("baseline", "current"):
            prefix = str(baseline) + os.pathsep if name == "baseline" else ""
            summary["phase"] = "font_metrics/" + name
            summary["font_metrics"][name] = "incomplete"
            write_report(output, summary)
            run(common + ["-cp", prefix + classpath, "me.youngjibbit.utils.font.FontMetricsBenchmark"],
                output / (name + "-font-metrics.log"), cwd=output, env=environment)
            summary["font_metrics"][name] = "passed"
        summary["phase"] = "complete"
        summary["status"] = "passed"
        write_report(output, summary)
    except Exception as error:
        summary["status"] = "failed"
        summary["error"] = {"type": type(error).__name__, "message": str(error)}
        write_report(output, summary)
        raise
    print(f"PASS: {args.pairs} balanced fresh JVM pairs, {summary['exact_rgba_pairs']} exact RGBA comparisons and current native contract checks")


if __name__ == "__main__":
    main()
