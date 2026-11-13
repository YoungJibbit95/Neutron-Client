"""Balanced fresh-JVM evidence for optional CPU strategies in the same build.

This isolates toggles, not whole clients or FPS. Signature failures fail the run;
timing noise is retained and never becomes a pass/fail speed threshold.
"""
import argparse
import hashlib
import json
import math
import os
from pathlib import Path
import platform
import re
import statistics
import subprocess
import time

ROOT = Path(__file__).resolve().parents[2]
BASELINE_REF = "5eccfb7b28110731e9d522dfb6e20ca3270b6ec3"
BENCHMARKS = {
    "particle_cleanup": ("net.minecraft.client.particle.ParticleCleanupBenchmark", 10,
                         ("all_live", "sparse_deaths", "half_dead", "all_dead")),
    "visibility_graph": ("net.minecraft.client.renderer.chunk.VisGraphBenchmark", 20,
                         ("empty", "solid", "floor", "half_solid", "closed_room", "corner_tunnel",
                          "checkerboard", "random_35_percent")),
}
ENVIRONMENT_KEYS = ("java_runtime", "processors", "max_heap_bytes", "allocation_counter_enabled")
SOURCES = (
    "src/main/java/net/minecraft/client/particle/EffectRenderer.java",
    "src/main/java/net/minecraft/client/particle/ParticleRemovalBuffer.java",
    "src/main/java/net/minecraft/client/renderer/chunk/VisGraph.java",
    "src/main/java/net/minecraft/client/renderer/chunk/SetVisibility.java",
    "src/main/java/me/youngjibbit/core/diagnostics/PerformanceOptions.java",
    "src/test/java/net/minecraft/client/particle/ParticleCleanupBenchmark.java",
    "src/test/java/net/minecraft/client/renderer/chunk/VisGraphBenchmark.java",
    "src/test/java/net/minecraft/client/renderer/chunk/VisGraphTestSupport.java",
    "src/test/java/me/youngjibbit/core/diagnostics/CorePerformanceProbe.java",
    ".github/scripts/check_core_performance.py",
)


def integer(value, minimum=0):
    return type(value) is int and minimum <= value <= (1 << 63) - 1


def pair_orders(count):
    if not integer(count, 4) or count % 2:
        raise ValueError("Use an even number of at least four fresh JVM pairs")
    return [("legacy", "fast") if i % 2 == 0 else ("fast", "legacy") for i in range(count)]


def validate_report(report, benchmark, mode, environment=None, reference=None):
    if (report.get("schema") != "neutron.core-performance.v1" or report.get("benchmark") != benchmark
            or report.get("mode") != mode or report.get("baseline_ref") != BASELINE_REF):
        raise ValueError("Wrong core schema, mode or baseline provenance")
    current = {key: report.get(key) for key in ENVIRONMENT_KEYS}
    if (not isinstance(current["java_runtime"], str) or not current["java_runtime"]
            or not integer(current["processors"], 1) or not integer(current["max_heap_bytes"], 1)
            or type(current["allocation_counter_enabled"]) is not bool):
        raise ValueError("Missing or invalid JVM environment")
    if environment is not None and current != environment:
        raise ValueError("Core JVM environments differ")
    if not isinstance(report.get("scope"), str) or not report["scope"]:
        raise ValueError("Missing measurement scope")
    structure = report.get("structure")
    if not isinstance(structure, dict) or not integer(structure.get("fixed_scratch_bytes")):
        raise ValueError("Missing bounded scratch accounting")
    _, iterations, names = BENCHMARKS[benchmark]
    scenes = report.get("scenarios")
    if (not isinstance(scenes, list) or len(scenes) != len(names)
            or {s.get("name") for s in scenes} != set(names)):
        raise ValueError("Missing, duplicate or unexpected core scenarios")
    old_scenes = {} if reference is None else {s["name"]: s for s in reference["scenarios"]}
    for scene in scenes:
        if (scene.get("warmup_iterations") != 1000 or scene.get("iterations_per_sample") != iterations
                or not isinstance(scene.get("parameters"), dict) or not scene["parameters"]):
            raise ValueError("Unexpected warmup, batching or fixture parameters")
        signature = scene.get("expected_signature")
        if (not isinstance(signature, str) or not re.fullmatch(r"[0-9a-fA-F]+", signature)
                or scene.get("observed_signature") != signature):
            raise ValueError("Core correctness signature mismatch")
        elapsed, allocated = scene.get("elapsed_ns"), scene.get("allocated_bytes")
        if not isinstance(elapsed, list) or len(elapsed) != 40 or not all(integer(v, 1) for v in elapsed):
            raise ValueError("Expected 40 positive integer elapsed batch samples")
        valid = (lambda v: integer(v)) if current["allocation_counter_enabled"] else (lambda v: v is None)
        if not isinstance(allocated, list) or len(allocated) != 40 or not all(valid(v) for v in allocated):
            raise ValueError("Incomplete or unsupported allocation samples")
        if reference is not None and any(scene[k] != old_scenes[scene["name"]][k] for k in
                                         ("parameters", "warmup_iterations", "iterations_per_sample", "expected_signature")):
            raise ValueError("Core fixtures or signatures differ between JVMs")
    return current


def nearest_rank(values, quantile):
    return sorted(values)[max(0, math.ceil(len(values) * quantile) - 1)]


def series(values):
    supported = [v for v in values if v is not None]
    return {"values_in_pair_order": values, "median": statistics.median(supported) if supported else None,
            "min": min(supported) if supported else None, "max": max(supported) if supported else None}


def aggregate_trials(trials, benchmark):
    result = []
    for name in BENCHMARKS[benchmark][2]:
        scene = {"name": name, "metrics": {}}
        for metric, unit in (("elapsed_ns", "nanoseconds_per_operation"), ("allocated_bytes", "bytes_per_operation")):
            distributions = {mode: [] for mode in ("legacy", "fast")}
            for trial in trials:
                for mode in distributions:
                    sample = next(s for s in trial[mode]["scenarios"] if s["name"] == name)
                    values = sample[metric]
                    distributions[mode].append(None if values[0] is None else
                                               [v / sample["iterations_per_sample"] for v in values])
            percentiles = {}
            for label, quantile in (("p50", .50), ("p95", .95), ("p99", .99)):
                old = [None if v is None else nearest_rank(v, quantile) for v in distributions["legacy"]]
                new = [None if v is None else nearest_rank(v, quantile) for v in distributions["fast"]]
                ratios = [b / a if a and b is not None else None for a, b in zip(old, new)]
                deltas = [b - a if a is not None and b is not None else None for a, b in zip(old, new)]
                percentiles[label] = {"legacy_jvm": series(old), "fast_jvm": series(new),
                                      "paired_fast_over_legacy": series(ratios), "paired_fast_minus_legacy": series(deltas),
                                      "fast_higher_pairs": sum(d is not None and d > 0 for d in deltas)}
            scene["metrics"][metric] = {"unit": unit, "within_jvm_percentiles": "nearest_rank_of_batch_means",
                                       "scope": "Per-operation batch averages, not individual tick or frame latency", **percentiles}
        result.append(scene)
    return result


def write_json(path, data):
    path.write_text(json.dumps(data, indent=2, allow_nan=False) + "\n", encoding="utf-8")


def run_java(command, log):
    with log.open("w", encoding="utf-8") as stream:
        stream.write("Command: " + repr([str(p) for p in command]) + "\n")
        stream.flush()
        result = subprocess.run([str(p) for p in command], cwd=ROOT, stdout=stream,
                                stderr=subprocess.STDOUT, timeout=240)
    if result.returncode:
        raise RuntimeError(f"Core benchmark exited {result.returncode}; see {log}")


def collect_trials(benchmark, count, java, classpath, output, summary):
    trials, environment, reference = [], None, None
    section = summary["benchmarks"][benchmark] = {"status": "incomplete", "completed_pairs": 0, "trials": []}
    report_path = output / "core-comparison.json"
    for index, order in enumerate(pair_orders(count), 1):
        trial, record = {}, {"pair": index, "order": list(order), "status": "incomplete", "runs": {}}
        section["trials"].append(record)
        for mode in order:
            directory = output / benchmark / f"pair-{index:02d}" / mode
            directory.mkdir(parents=True)
            destination = directory / "results.json"
            record["runs"][mode] = {"status": "incomplete", "report": str(destination.relative_to(output))}
            write_json(report_path, summary)
            print(f"Running {benchmark} pair {index}/{count} {mode}", flush=True)
            run_java([java, "-Xms256m", "-Xmx512m", "-cp", classpath,
                      BENCHMARKS[benchmark][0], mode, destination], directory / "jvm.log")
            measured = json.loads(destination.read_text(encoding="utf-8"))
            environment = validate_report(measured, benchmark, mode, environment, reference)
            if reference is None:
                reference = measured
            trial[mode] = measured
            record["runs"][mode].update(status="validated", sha256=hashlib.sha256(destination.read_bytes()).hexdigest())
            write_json(report_path, summary)
        trials.append(trial)
        record["status"] = "validated"
        section.update(completed_pairs=len(trials), environment=environment, scenarios=aggregate_trials(trials, benchmark))
        write_json(report_path, summary)
    section["status"] = "passed"
    write_json(report_path, summary)


def git(*arguments):
    return subprocess.check_output(["git", *arguments], cwd=ROOT).decode("utf-8").strip()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--classpath", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--pairs", type=int, default=4)
    args = parser.parse_args()
    pair_orders(args.pairs)
    output = args.output.resolve()
    if output.exists() and any(output.iterdir()):
        raise ValueError("Use a fresh core output directory; stale samples cannot be mixed")
    output.mkdir(parents=True, exist_ok=True)
    java = Path(os.environ["JAVA_HOME"]) / "bin/java" if os.environ.get("JAVA_HOME") else "java"
    dependencies = args.classpath.read_text(encoding="utf-8").strip()
    classpath = os.pathsep.join((str(ROOT / "target/test-classes"), str(ROOT / "target/classes"), dependencies))
    summary = {"schema": "neutron.core-comparison.v1", "status": "incomplete", "pairs_requested": args.pairs,
               "comparison": "legacy and opt-in strategies in the same current build; not whole-client FPS",
               "baseline_ref": BASELINE_REF, "build_commit": git("rev-parse", "HEAD"),
               "working_tree_dirty": bool(git("status", "--porcelain")), "host": platform.platform(),
               "source_sha256": {p: hashlib.sha256((ROOT / p).read_bytes()).hexdigest() for p in SOURCES},
               "benchmarks": {}, "started_utc": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())}
    try:
        for benchmark in BENCHMARKS:
            collect_trials(benchmark, args.pairs, java, classpath, output, summary)
        summary["status"] = "passed"
    except Exception as error:
        summary.update(status="failed", failure=f"{type(error).__name__}: {error}")
        raise
    finally:
        summary["finished_utc"] = time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())
        write_json(output / "core-comparison.json", summary)
    print("PASS: core signatures, complete balanced evidence and bounded scratch accounting")
    print("Timing is synthetic evidence, not an FPS claim or a speed pass threshold.")


if __name__ == "__main__":
    main()
