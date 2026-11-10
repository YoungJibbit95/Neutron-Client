"""Offline regression tests for native evidence comparison (no GL/network required)."""
import importlib.util
import copy
import io
import json
from pathlib import Path
import subprocess
import tempfile
import unittest
from contextlib import redirect_stdout
from unittest.mock import patch

spec = importlib.util.spec_from_file_location("render_performance", Path(__file__).with_name("check_render_performance.py"))
checks = importlib.util.module_from_spec(spec)
spec.loader.exec_module(checks)


def distribution(values):
    return {"samples": values, "p50": checks.nearest_rank(values, .50),
            "p95": checks.nearest_rank(values, .95), "p99": checks.nearest_rank(values, .99)}


def report(value=1):
    result = {key: "fixed-" + key for key in checks.ENVIRONMENT_KEYS}
    result.update(warmup_frames=60, samples=90, scenarios=[])
    for name, (width, height, scale) in checks.SCENARIOS.items():
        scene = dict(name=name, width=width, height=height, scale=scale, visible_output_verified=True)
        scene.update({metric: distribution([value] * 90) for metric in checks.METRICS})
        result["scenarios"].append(scene)
    return result


class RenderPerformanceEvidenceTest(unittest.TestCase):
    def test_baseline_requires_full_nonzero_sha(self):
        self.assertEqual("a" * 40, checks.validate_sha("A" * 40))
        for value in ("main", "abc123", "0" * 40, "a" * 40 + "; command"):
            with self.assertRaises(ValueError):
                checks.validate_sha(value)

    def test_comparison_checks_every_channel_and_capture(self):
        with tempfile.TemporaryDirectory() as temporary:
            before, after = Path(temporary) / "before", Path(temporary) / "after"
            before.mkdir(); after.mkdir()
            (before / "scene.rgba").write_bytes(bytes((1, 2, 3, 255)))
            (after / "scene.rgba").write_bytes(bytes((1, 2, 3, 255)))
            self.assertTrue(checks.compare_pixels(before, after)[0]["identical"])
            (after / "scene.rgba").write_bytes(bytes((1, 2, 3, 254)))
            result = checks.compare_pixels(before, after)[0]
            self.assertFalse(result["identical"])
            self.assertEqual(1, result["changed_channels"])
            self.assertEqual(1, result["max_channel_difference"])
            (after / "scene.rgba").unlink()
            with self.assertRaises(ValueError):
                checks.compare_pixels(before, after)

    def test_empty_evidence_is_not_a_pass(self):
        with tempfile.TemporaryDirectory() as temporary:
            with self.assertRaises(ValueError):
                checks.compare_pixels(Path(temporary), Path(temporary))
            (Path(temporary) / "empty.rgba").touch()
            with self.assertRaises(ValueError):
                checks.compare_pixels(Path(temporary), Path(temporary))

    def test_required_capture_set_rejects_shared_omissions_and_wrong_dimensions(self):
        with tempfile.TemporaryDirectory() as temporary:
            before, after = Path(temporary) / "before", Path(temporary) / "after"
            before.mkdir(); after.mkdir()
            for directory in (before, after):
                (directory / "scene.rgba").write_bytes(bytes((1, 2, 3, 255)))
            with self.assertRaises(ValueError):
                checks.compare_pixels(before, after, {"scene.rgba": 4, "omitted.rgba": 4})
            with self.assertRaises(ValueError):
                checks.compare_pixels(before, after, {"scene.rgba": 8})

    def test_four_pairs_balance_order_without_dropping_any_jvm(self):
        self.assertEqual([["baseline", "current"], ["current", "baseline"]] * 2, checks.pair_orders(4))
        for count in (0, 1, 3, 9):
            with self.assertRaises(ValueError):
                checks.pair_orders(count)

    def test_all_jvms_require_same_environment_complete_finite_samples_and_scenes(self):
        original = report()
        environment = checks.validate_report(original, 60, 90)
        for key in checks.ENVIRONMENT_KEYS:
            changed = copy.deepcopy(original); changed[key] += "-changed"
            with self.assertRaises(ValueError):
                checks.validate_report(changed, 60, 90, environment)
        for invalid in (float("nan"), float("inf"), -2):
            changed = copy.deepcopy(original)
            changed["scenarios"][0]["completed_ms"]["samples"][0] = invalid
            with self.assertRaises(ValueError):
                checks.validate_report(changed, 60, 90, environment)
        for key, value in (("samples", 60), ("warmup_frames", 20)):
            changed = copy.deepcopy(original); changed[key] = value
            with self.assertRaises(ValueError):
                checks.validate_report(changed, 60, 90, environment)
        changed = copy.deepcopy(original); changed["scenarios"].pop()
        with self.assertRaises(ValueError):
            checks.validate_report(changed, 60, 90, environment)

    def test_within_jvm_percentiles_preserve_java_nearest_rank(self):
        values = list(range(90))
        self.assertEqual(44, checks.nearest_rank(values, .5))
        measured = report()
        measured["scenarios"][0]["completed_ms"] = distribution(values)
        checks.validate_report(measured, 60, 90)
        measured["scenarios"][0]["completed_ms"]["p50"] = 44.5
        with self.assertRaises(ValueError):
            checks.validate_report(measured, 60, 90)

    def test_jvm_medians_and_paired_ratios_retain_slow_trials(self):
        trials = [{"baseline": report(value), "current": report(value * 2)} for value in (1, 3, 5, 101)]
        metric = checks.aggregate_trials(trials)[0]["overall"]["completed_ms"]
        self.assertEqual([1, 3, 5, 101], metric["baseline_jvm_p50"]["values_in_trial_order"])
        self.assertEqual(4, metric["baseline_jvm_p50"]["median"])
        self.assertEqual(1, metric["baseline_jvm_p50"]["min"])
        self.assertEqual(101, metric["baseline_jvm_p50"]["max"])
        self.assertEqual([2, 2, 2, 2], metric["paired_current_over_baseline_p50"]["values_in_trial_order"])
        self.assertEqual(4, metric["current_higher_pairs"])
        # A quotient of the two aggregate medians is not a paired statistic.
        trials = [{"baseline": report(before), "current": report(after)}
                  for before, after in ((1, 4), (2, 2), (4, 2), (8, 4))]
        ratio = checks.aggregate_trials(trials)[0]["overall"]["completed_ms"]["paired_current_over_baseline_p50"]
        self.assertEqual([4, 1, .5, .5], ratio["values_in_trial_order"])
        self.assertEqual(.75, ratio["median"])

    def test_alternating_phase_medians_do_not_confuse_the_mixture_boundary(self):
        trials = []
        for _ in range(4):
            before, after = report(), report()
            for old, new in zip(before["scenarios"], after["scenarios"]):
                if old["name"].startswith(("gaussian_blur_", "clipped_bloom_")):
                    values = [25.5, 35.5] * 45
                    values[88] = 31  # One high fast-phase sample moves the mixed p50.
                    old["completed_ms"] = distribution([25, 35] * 45)
                    new["completed_ms"] = distribution(values)
            trials.append({"baseline": before, "current": after})
        scenes = {scene["name"]: scene for scene in checks.aggregate_trials(trials)}
        gaussian = scenes["gaussian_blur_960x640_scale2"]
        self.assertEqual(31, gaussian["overall"]["completed_ms"]["current_jvm_p50"]["median"])
        self.assertEqual(["radius_7", "radius_11"], [phase["name"] for phase in gaussian["phases"]])
        self.assertEqual(25.5, gaussian["phases"][0]["metrics"]["completed_ms"]["current_jvm_p50"]["median"])
        self.assertEqual(35.5, gaussian["phases"][1]["metrics"]["completed_ms"]["current_jvm_p50"]["median"])
        self.assertEqual(["iterations_2", "iterations_3"], [phase["name"] for phase in scenes["clipped_bloom_960x640_scale2"]["phases"]])

    def test_zero_and_unsupported_counters_do_not_invent_improvements(self):
        trials = [{"baseline": report(0), "current": report(0)} for _ in range(4)]
        for trial in trials:
            for revision in ("baseline", "current"):
                for scene in trial[revision]["scenarios"]:
                    scene["allocated_bytes"] = distribution([-1] * 90)
                checks.validate_report(trial[revision], 60, 90)
        metrics = checks.aggregate_trials(trials)[0]["overall"]
        self.assertIsNone(metrics["completed_ms"]["paired_current_over_baseline_p50"]["median"])
        self.assertEqual([None] * 4, metrics["allocated_bytes"]["baseline_jvm_p50"]["values_in_trial_order"])
        self.assertEqual(0, metrics["allocated_bytes"]["comparable_pairs"])

    def exercise_trials(self, failure=None):
        with tempfile.TemporaryDirectory() as temporary:
            output = Path(temporary)
            summary = dict(status="incomplete", trials=[], completed_pairs=0, exact_rgba_pairs=0,
                           pixel_parity=False, current_native_checks_passed=False)
            calls = []

            def execute(revision, directory, log):
                calls.append(revision)
                log.write_text("Retained JVM output\n")
                if failure == "timeout" and len(calls) == 7:
                    raise subprocess.TimeoutExpired("java", 600)
                measured = report(2 if revision == "current" else 1)
                if failure == "environment" and len(calls) == 7:
                    measured["gl_renderer"] = "different-in-trial-four"
                (directory / "results.json").write_text(json.dumps(measured))

            comparisons = [{"capture": name, "identical": True} for name in checks.CAPTURE_SIZES]
            with patch.object(checks, "compare_pixels", return_value=comparisons), redirect_stdout(io.StringIO()):
                if failure:
                    with self.assertRaises((ValueError, subprocess.TimeoutExpired)):
                        checks.run_trials(output, summary, checks.pair_orders(4), 60, 90, execute)
                else:
                    checks.run_trials(output, summary, checks.pair_orders(4), 60, 90, execute)
            saved = json.loads((output / "comparison.json").read_text())
            self.assertEqual(summary, saved)
            self.assertTrue((output / "trial-01/baseline/results.json").is_file())
            self.assertTrue((output / "trial-04/current.log").is_file())
            return saved, calls

    def test_slow_correct_trials_complete_without_a_speed_gate(self):
        saved, calls = self.exercise_trials()
        self.assertEqual(["baseline", "current", "current", "baseline"] * 2, calls)
        self.assertEqual(4, saved["completed_pairs"])
        self.assertEqual(64, saved["exact_rgba_pairs"])
        self.assertTrue(saved["pixel_parity"])
        self.assertFalse(saved["current_native_checks_passed"])
        self.assertEqual("incomplete", saved["status"])  # Contracts remain required.
        self.assertEqual(4, saved["aggregate_jvm_p50"][0]["overall"]["completed_ms"]["current_higher_pairs"])

    def test_late_failure_preserves_partial_evidence_and_never_marks_success(self):
        for failure in ("timeout", "environment"):
            saved, calls = self.exercise_trials(failure)
            self.assertEqual(7, len(calls))
            self.assertEqual("failed", saved["status"])
            self.assertEqual(3, saved["completed_pairs"])
            self.assertEqual(48, saved["exact_rgba_pairs"])
            self.assertFalse(saved["pixel_parity"])
            self.assertFalse(saved["current_native_checks_passed"])
            self.assertEqual(["passed"] * 3 + ["failed"], [trial["status"] for trial in saved["trials"]])


if __name__ == "__main__":
    unittest.main()
