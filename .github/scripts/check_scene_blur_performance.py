"""Compare current Full/paired/Half/Quarter scene blur in balanced fresh JVMs.

The immutable baseline harness stays separate. Native contracts and fixed image
oracles gate correctness; synthetic software-GL timings are retained as evidence,
including slower trials, and never gate on a noisy speed threshold.
"""
import argparse
from collections import Counter
import hashlib
import importlib.util
import json
import math
import os
from pathlib import Path
import statistics
import subprocess
import time

ROOT = Path(__file__).resolve().parents[2]
MAIN = "me.youngjibbit.module.impl.render.SceneBlurOptimizationNativeCheck"
MODES = {"full": 1, "paired": 1, "half": 2, "quarter": 4}
COMPARISONS = ("paired", "half", "quarter")
SCENARIOS = {f"glass_scene_{w}x{h}": (w, h, 2) for w, h in ((960, 640), (853, 481))}
ENVIRONMENT_KEYS = ("java", "os", "gl_vendor", "gl_renderer", "gl_version")
METRICS = ("submission_ms", "completed_ms", "allocated_bytes")
PERCENTILES = {"p50": .50, "p95": .95, "p99": .99}
_spec = importlib.util.spec_from_file_location("scene_blur_runtime", Path(__file__).with_name("check_render_performance.py"))
runtime = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(runtime)


def nearest_rank(values, percentile):
    return sorted(values)[math.ceil(len(values) * percentile) - 1]


def pair_orders(pairs):
    if type(pairs) is not int or pairs < 2 or pairs > 8 or pairs % 2:
        raise ValueError("Use an even number of fresh JVM pairs from 2 through 8")
    return [["full", "variant"] if i % 2 == 0 else ["variant", "full"] for i in range(pairs)]


def environment(report, expected=None):
    actual = {key: report.get(key) for key in ENVIRONMENT_KEYS}
    if any(not isinstance(value, str) or not value for value in actual.values()):
        raise ValueError("Missing native JVM/GL environment")
    if expected is not None and actual != expected:
        raise ValueError("Native JVM/GL environment differs between runs")
    return actual


def finite_number(value):
    return type(value) in (int, float) and math.isfinite(value)


def expected_verify_records():
    records = []
    for width, height in ((129, 97), (257, 193)):
        records.extend({"check": f"paired-{width}x{height}-r{radius}", "width": width, "height": height, "radius": radius}
                       for radius in (1, 2, 7, 11, 31, 127))
        records.extend({"check": name} for name in ("fallback-nearest", "fallback-legacy-clamp", "caller-state-and-alias-rejection"))
    records.extend({"check": "material-quality", "divisor": divisor} for divisor in (1, 2, 4, 2, 1))
    records.extend({"check": name} for name in ("nested-quality-deferral", "borrowed-capture-invalidation"))
    records.extend({"check": name, "divisor": divisor} for name in ("legacy-mask-quality", "kawase-quality") for divisor in (1, 2, 4))
    records.extend({"check": "resource-override", "source": source} for source in ("gaussian.frag", "gaussianPaired.frag", "kawaseScene.frag", "vertex.vsh"))
    records.extend({"check": "warm-resources-resize-release", "width": width, "height": height} for width, height in ((960, 640), (853, 481), (320, 241)))
    return records


def verify_identity(row):
    return tuple((key, row[key]) for key in ("check", "width", "height", "radius", "divisor", "source") if key in row)


def validate_verify_report(report, expected_environment=None):
    if type(report.get("format_version")) is not int or report["format_version"] != 1 or report.get("status") != "passed" or report.get("mode") != "verify":
        raise ValueError("Incomplete native scene blur contract report")
    actual_environment = environment(report, expected_environment)
    rows = report.get("checks")
    if (not isinstance(rows, list) or any(not isinstance(row, dict) or not isinstance(row.get("check"), str)
            or any(key in row and type(row[key]) is not int for key in ("width", "height", "radius", "divisor"))
            or "source" in row and not isinstance(row["source"], str) for row in rows)
            or Counter(map(verify_identity, rows)) != Counter(map(verify_identity, expected_verify_records()))):
        raise ValueError("Missing, duplicated or unexpected native scene blur contract records")
    for row in rows:
        name = row["check"]
        if name.startswith("paired-") or name == "material-quality":
            maximum, mean, changed = row.get("max_rgb_error"), row.get("mean_rgb_error"), row.get("changed_channels")
            channels = 3 * (row["width"] * row["height"] if "width" in row else 853 * 481)
            if (row.get("alpha_exact") is not True or type(maximum) is not int or not 0 <= maximum <= 2
                    or not finite_number(mean) or not 0 <= mean <= maximum
                    or type(changed) is not int or not 0 <= changed <= channels
                    or (changed == 0) != (maximum == 0) or (mean == 0) != (maximum == 0)):
                raise ValueError("Fixed paired RGB/alpha contract is missing or failed")
        if name.startswith("fallback-") or name == "resource-override":
            if row.get("exact_rgba") is not True:
                raise ValueError("Exact fallback pixels were not verified")
        if name in ("caller-state-and-alias-rejection", "nested-quality-deferral", "borrowed-capture-invalidation", "warm-resources-resize-release"):
            if row.get("passed") is not True:
                raise ValueError("Native state/lifetime/resource contract did not pass")
        if name in ("material-quality", "legacy-mask-quality", "kawase-quality"):
            divisor = row["divisor"]
            pixels = (4 if name == "kawase-quality" else 2) * ((853 + divisor - 1) // divisor) * ((481 + divisor - 1) // divisor)
            if type(row.get("scene_blur_pixels")) is not int or row["scene_blur_pixels"] != pixels:
                raise ValueError("Native quality pass work is incorrect")
            if name == "material-quality" and (type(row.get("paired_passes")) is not int or row["paired_passes"] != (2 if divisor == 1 else 1)):
                raise ValueError("Native material paired shader was inactive")
    return actual_environment


def validate_report(report, mode, warmup, samples, expected_environment=None):
    if mode not in MODES or report.get("format_version") != 1 or report.get("status") != "passed" or report.get("mode") != mode:
        raise ValueError("Missing, incomplete or wrong scene blur mode")
    actual_environment = environment(report, expected_environment)
    if report.get("warmup_frames") != warmup or report.get("samples") != samples:
        raise ValueError("Unexpected warmup/frame count")
    rows = report.get("scenarios", [])
    if not isinstance(rows, list) or len(rows) != len(SCENARIOS) or {row.get("name") for row in rows} != set(SCENARIOS):
        raise ValueError("Missing, duplicated or unexpected scene blur scenarios")
    divisor = MODES[mode]
    for row in rows:
        width, height, scale = SCENARIOS[row["name"]]
        if (row.get("width"), row.get("height"), row.get("scale"), row.get("divisor")) != (width, height, scale, divisor):
            raise ValueError("Incorrect viewport or scene resolution")
        expected = {
            "scene_blur_pixels": 2 * ((width + divisor - 1) // divisor) * ((height + divisor - 1) // divisor) * samples,
            "paired_passes": 2 * samples if mode == "paired" else 0,
            "fallback_passes": 0, "framebuffer_creates": 0, "shader_compiles": 0,
        }
        if any(type(row.get(key)) is not int or row[key] != value for key, value in expected.items()):
            raise ValueError("Missing or incorrect pass/resource counters: " + row["name"])
        if row.get("visible_output_verified") is not True:
            raise ValueError("Visible workload was not verified")
        cold = row.get("first_workload_completed_ms")
        if not finite_number(cold) or cold < 0:
            raise ValueError("Missing cold workload observation")
        for metric in METRICS:
            measured = row.get(metric, {})
            values = measured.get("samples", [])
            if not isinstance(values, list) or len(values) != samples or any(not finite_number(value) for value in values):
                raise ValueError("Missing or non-finite frame samples: " + metric)
            unsupported = metric == "allocated_bytes" and all(value == -1 for value in values)
            if not unsupported and any(value < 0 for value in values):
                raise ValueError("Negative or partially unavailable frame samples: " + metric)
            for percentile, fraction in PERCENTILES.items():
                value = measured.get(percentile)
                if not finite_number(value) or value != nearest_rank(values, fraction):
                    raise ValueError("Incorrect nearest-rank percentile: " + metric + "/" + percentile)
        if any(done < sent for sent, done in zip(row["submission_ms"]["samples"], row["completed_ms"]["samples"])):
            raise ValueError("Completed time cannot precede submission")
    return actual_environment


def compare_pixels(full, variant, mode, expected_scenarios=None):
    sizes = SCENARIOS if expected_scenarios is None else expected_scenarios
    expected = {name + ".rgba" for name in sizes}
    if not expected or {p.name for p in full.glob("*.rgba")} != expected or {p.name for p in variant.glob("*.rgba")} != expected:
        raise ValueError("Missing or unmatched scene blur RGBA captures")
    result = []
    for name, (width, height, _) in sizes.items():
        before, after = (full / (name + ".rgba")).read_bytes(), (variant / (name + ".rgba")).read_bytes()
        if len(before) != width * height * 4 or len(after) != len(before):
            raise ValueError("Incorrect scene blur capture dimensions: " + name)
        rgb_max, rgb_sum, changed, alpha_max = 0, 0, 0, 0
        for index, (a, b) in enumerate(zip(before, after)):
            delta = abs(a - b)
            if index % 4 == 3:
                alpha_max = max(alpha_max, delta)
            else:
                rgb_max = max(rgb_max, delta)
                rgb_sum += delta
                changed += delta != 0
        result.append({"name": name, "max_rgb_error": rgb_max, "mean_rgb_error": rgb_sum / (width * height * 3),
                       "changed_rgb_channels": changed, "max_alpha_error": alpha_max,
                       "full_sha256": hashlib.sha256(before).hexdigest(), "variant_sha256": hashlib.sha256(after).hexdigest(),
                       "contract": "alpha_exact_rgb_max_2" if mode == "paired" else "quality_tradeoff_alpha_exact",
                       "passed": alpha_max == 0 and (mode != "paired" or rgb_max <= 2)})
    return result


def series(values):
    available = [value for value in values if value is not None]
    return {"values_in_pair_order": values, "available_pairs": len(available),
            "median": statistics.median(available) if available else None,
            "min": min(available) if available else None, "max": max(available) if available else None}


def aggregate(trials, mode):
    selected = [trial for trial in trials if trial["comparison"] == mode]
    if not selected or any(trial.get("status") != "passed" for trial in selected):
        raise ValueError("Cannot aggregate absent or incomplete pairs")
    result = []
    for name in SCENARIOS:
        rows = [(next(row for row in trial["full"]["scenarios"] if row["name"] == name),
                 next(row for row in trial[mode]["scenarios"] if row["name"] == name)) for trial in selected]
        metrics = {}
        for metric in METRICS:
            percentiles = {}
            for percentile in PERCENTILES:
                before = [a[metric][percentile] if a[metric][percentile] >= 0 else None for a, _ in rows]
                after = [b[metric][percentile] if b[metric][percentile] >= 0 else None for _, b in rows]
                available = [a is not None and b is not None for a, b in zip(before, after)]
                ratios = [b / a if ok and a > 0 else None for a, b, ok in zip(before, after, available)]
                deltas = [b - a if ok else None for a, b, ok in zip(before, after, available)]
                percentiles[percentile] = {"full_jvm": series(before), "variant_jvm": series(after),
                                          "paired_variant_over_full": series(ratios), "paired_variant_minus_full": series(deltas),
                                          "variant_higher_pairs": sum(ok and b > a for a, b, ok in zip(before, after, available))}
            metrics[metric] = percentiles
        result.append({"name": name, "metrics": metrics})
    return result


def write_report(output, summary):
    (output / "scene-blur-comparison.json").write_text(json.dumps(summary, indent=2, allow_nan=False) + "\n", encoding="utf-8")


def run_trials(output, summary, orders, warmup, samples, execute):
    expected_environment = summary.get("environment")
    try:
        for mode in COMPARISONS:
            for number, template in enumerate(orders, 1):
                order = [mode if value == "variant" else value for value in template]
                directory = output / f"{mode}-pair-{number:02d}"
                directory.mkdir()
                trial = {"comparison": mode, "pair": number, "order": order, "status": "incomplete", "runs": []}
                summary["trials"].append(trial)
                summary["phase"] = directory.name
                started_pair = time.monotonic()
                write_report(output, summary)
                for current in order:
                    target = directory / current
                    target.mkdir()
                    record = {"mode": current, "output": str(target.relative_to(output)), "status": "incomplete"}
                    trial["runs"].append(record)
                    write_report(output, summary)
                    started = time.monotonic()
                    execute(current, target, directory / (current + ".log"))
                    report = json.loads((target / "results.json").read_text(encoding="utf-8"))
                    expected_environment = validate_report(report, current, warmup, samples, expected_environment)
                    summary["environment"] = expected_environment
                    trial[current] = report
                    record.update(status="passed", elapsed_seconds=time.monotonic() - started)
                    write_report(output, summary)
                trial["pixels"] = compare_pixels(directory / "full", directory / mode, mode)
                trial["elapsed_seconds"] = time.monotonic() - started_pair
                if not all(row["passed"] for row in trial["pixels"]):
                    raise AssertionError("Fixed scene blur pixel contract failed: " + directory.name)
                trial["status"] = "passed"
                summary["completed_pairs"] += 1
                write_report(output, summary)
            summary["comparisons"][mode] = aggregate(summary["trials"], mode)
            write_report(output, summary)
        if set(summary["comparisons"]) != set(COMPARISONS) or summary["completed_pairs"] != len(orders) * len(COMPARISONS):
            raise ValueError("Missing scene blur mode or pair")
    except Exception as error:
        summary["status"] = "failed"
        summary["error"] = {"type": type(error).__name__, "message": str(error)}
        if summary["trials"]:
            summary["trials"][-1]["status"] = "failed"
        write_report(output, summary)
        raise


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--classpath", type=Path, default=Path("target/render-classpath.txt"))
    parser.add_argument("--output", type=Path, default=Path("target/render-performance/scene-blur"))
    parser.add_argument("--pairs", type=int, default=4)
    parser.add_argument("--warmup", type=int, default=30)
    parser.add_argument("--samples", type=int, default=40)
    args = parser.parse_args()
    if not 1 <= args.warmup <= 200 or not 20 <= args.samples <= 500:
        parser.error("Warmup must be 1..200 and samples 20..500")
    try:
        orders = pair_orders(args.pairs)
    except ValueError as error:
        parser.error(str(error))
    output = (ROOT / args.output).resolve()
    output.mkdir(parents=True, exist_ok=True)
    if any(output.iterdir()):
        raise RuntimeError("Use a fresh scene blur evidence directory; previous evidence must not be overwritten")
    java = Path(os.environ["JAVA_HOME"]) / "bin/java"
    version = subprocess.check_output([str(java), "-version"], stderr=subprocess.STDOUT).decode()
    if 'version "1.8.0_' not in version:
        raise RuntimeError("Scene blur evidence requires Java 8")
    if runtime.git("status", "--porcelain", "--untracked-files=no"):
        raise RuntimeError("Native revision evidence requires a clean tracked working tree")
    dependencies = (ROOT / args.classpath).read_text(encoding="utf-8").strip()
    if not dependencies:
        raise RuntimeError("Missing dependency classpath; compile test classes and build-classpath first")
    natives = ROOT / "target/render-native-libraries"
    runtime.native_libraries(natives)
    classpath = os.pathsep.join((str(ROOT / "target/test-classes"), str(ROOT / "target/classes"), dependencies))
    common = [java, "-Xms256m", "-Xmx1024m", "-Djava.awt.headless=true", "-Dorg.lwjgl.librarypath=" + str(natives),
              "-Djava.library.path=" + str(natives), "-cp", classpath, MAIN]
    env = dict(os.environ, LIBGL_ALWAYS_SOFTWARE="1", GALLIUM_DRIVER="llvmpipe", LP_NUM_THREADS="2")
    summary = {"format_version": 1, "current_commit": runtime.git("rev-parse", "HEAD"), "java": version.strip(),
               "status": "incomplete", "phase": "native_contracts", "trials": [], "comparisons": {},
               "requested_pairs_per_comparison": args.pairs, "completed_pairs": 0,
               "warmup_frames": args.warmup, "samples_per_jvm": args.samples, "native_contracts_passed": False,
               "aggregation_method": "Within-JVM nearest-rank p50/p95/p99, then median/min/max across fresh JVMs. Ratios and differences are paired within each comparison, never pooled frames. All negative outcomes remain.",
               "timing_gate": "None; correctness/pass-count/resource oracles gate success, timings are evidence.",
               "coverage": "Current-only software OpenGL Pbuffer UI/Glass scenes at two dimensions. Half/Quarter are explicit quality tradeoffs. No Display, live world, shaderpack, target-GPU or game-FPS claim."}
    write_report(output, summary)
    try:
        verify = output / "verify"
        verify.mkdir()
        runtime.run(common + [verify, "--verify"], output / "verify.log", cwd=verify, env=env)
        verified = json.loads((verify / "scene-blur-report.json").read_text(encoding="utf-8"))
        summary["environment"] = validate_verify_report(verified)
        summary["native_contracts_passed"] = True
        write_report(output, summary)

        def execute(mode, directory, log):
            runtime.run(common + [directory, mode, str(args.warmup), str(args.samples)], log, cwd=directory, env=env)

        run_trials(output, summary, orders, args.warmup, args.samples, execute)
        summary.update(status="passed", phase="complete")
        write_report(output, summary)
    except Exception as error:
        summary["status"] = "failed"
        summary["error"] = {"type": type(error).__name__, "message": str(error)}
        write_report(output, summary)
        raise
    print(f"PASS: scene blur contracts and {args.pairs} balanced fresh JVM pairs for each of paired/half/quarter")


if __name__ == "__main__":
    main()
