"""Offline tests for current-only scene blur evidence; no JVM or GL required."""
import copy
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location("scene_blur_checks", Path(__file__).with_name("check_scene_blur_performance.py"))
checks = importlib.util.module_from_spec(spec)
spec.loader.exec_module(checks)


def distribution(values):
    result = {key: checks.nearest_rank(values, rank) for key, rank in checks.PERCENTILES.items()}
    result["samples"] = values
    return result


def report(mode="full", value=1, samples=40):
    result = {key: "fixed-" + key for key in checks.ENVIRONMENT_KEYS}
    result.update(format_version=1, status="passed", mode=mode, warmup_frames=30, samples=samples, scenarios=[])
    divisor = checks.MODES[mode]
    for name, (width, height, scale) in checks.SCENARIOS.items():
        row = dict(name=name, width=width, height=height, scale=scale, divisor=divisor,
                   visible_output_verified=True, first_workload_completed_ms=8,
                   scene_blur_pixels=2 * ((width + divisor - 1) // divisor) * ((height + divisor - 1) // divisor) * samples,
                   paired_passes=2 * samples if mode == "paired" else 0,
                   fallback_passes=0, framebuffer_creates=0, shader_compiles=0)
        row.update({metric: distribution([value] * samples) for metric in checks.METRICS})
        result["scenarios"].append(row)
    return result


def verify_report():
    result = {key: "fixed-" + key for key in checks.ENVIRONMENT_KEYS}
    result.update(format_version=1, status="passed", mode="verify", checks=checks.expected_verify_records())
    for row in result["checks"]:
        name = row["check"]
        if name.startswith("paired-") or name == "material-quality":
            row.update(max_rgb_error=1, mean_rgb_error=.1, changed_channels=3, alpha_exact=True)
        if name.startswith("fallback-") or name == "resource-override":
            row["exact_rgba"] = True
        if name in ("caller-state-and-alias-rejection", "nested-quality-deferral", "borrowed-capture-invalidation", "warm-resources-resize-release"):
            row["passed"] = True
        if name in ("material-quality", "legacy-mask-quality", "kawase-quality"):
            divisor = row["divisor"]
            row["scene_blur_pixels"] = (4 if name == "kawase-quality" else 2) * ((853 + divisor - 1) // divisor) * ((481 + divisor - 1) // divisor)
            if name == "material-quality":
                row["paired_passes"] = 2 if divisor == 1 else 1
    return result


class SceneBlurEvidenceTest(unittest.TestCase):
    def test_verify_requires_every_native_record_with_its_correct_multiplicity(self):
        original = verify_report()
        self.assertEqual(38, len(original["checks"]))
        checks.validate_verify_report(original)
        for index in range(len(original["checks"])):
            missing = copy.deepcopy(original); missing["checks"].pop(index)
            duplicate = copy.deepcopy(original); duplicate["checks"].append(copy.deepcopy(duplicate["checks"][index]))
            for invalid in (missing, duplicate):
                with self.assertRaises(ValueError):
                    checks.validate_verify_report(invalid)
        changed = copy.deepcopy(original); changed["checks"] = [{"check": "anything", "passed": True}]
        with self.assertRaises(ValueError):
            checks.validate_verify_report(changed)
        for field, value in (("format_version", 2), ("format_version", True), ("status", "failed"), ("mode", "full")):
            changed = copy.deepcopy(original); changed[field] = value
            with self.assertRaises(ValueError):
                checks.validate_verify_report(changed)

    def test_verify_checks_truth_flags_pixel_limits_counters_and_identity(self):
        original = verify_report()
        for index, row in enumerate(original["checks"]):
            for flag in ("passed", "exact_rgba", "alpha_exact"):
                if flag in row:
                    changed = copy.deepcopy(original); changed["checks"][index][flag] = False
                    with self.assertRaises(ValueError):
                        checks.validate_verify_report(changed)
            for field in ("max_rgb_error", "scene_blur_pixels", "paired_passes"):
                if field in row:
                    changed = copy.deepcopy(original); changed["checks"][index][field] += 3
                    with self.assertRaises(ValueError):
                        checks.validate_verify_report(changed)
            for field in ("width", "height", "radius", "divisor", "source"):
                if field in row:
                    changed = copy.deepcopy(original); changed["checks"][index][field] = "missing"
                    with self.assertRaises(ValueError):
                        checks.validate_verify_report(changed)
        changed = copy.deepcopy(original); changed["checks"][0]["mean_rgb_error"] = float("nan")
        with self.assertRaises(ValueError):
            checks.validate_verify_report(changed)

    def test_four_fresh_pairs_balance_each_comparison(self):
        self.assertEqual([["full", "variant"], ["variant", "full"]] * 2, checks.pair_orders(4))
        for count in (0, 1, 3, 9, True):
            with self.assertRaises(ValueError):
                checks.pair_orders(count)

    def test_reports_require_the_requested_active_mode_and_complete_scenes(self):
        for mode in checks.MODES:
            original = report(mode)
            checks.validate_report(original, mode, 30, 40)
            for field, value in (("status", "failed"), ("mode", "missing"), ("format_version", 2),
                                 ("warmup_frames", 29), ("samples", 39)):
                changed = copy.deepcopy(original); changed[field] = value
                with self.assertRaises(ValueError):
                    checks.validate_report(changed, mode, 30, 40)
            changed = copy.deepcopy(original); changed["scenarios"].pop()
            with self.assertRaises(ValueError):
                checks.validate_report(changed, mode, 30, 40)
            changed = copy.deepcopy(original); changed["scenarios"][1] = changed["scenarios"][0]
            with self.assertRaises(ValueError):
                checks.validate_report(changed, mode, 30, 40)

    def test_pass_and_resource_counters_cannot_hide_inactive_options(self):
        original = report("paired")
        for field in ("scene_blur_pixels", "paired_passes", "fallback_passes", "framebuffer_creates", "shader_compiles"):
            for delete in (False, True):
                changed = copy.deepcopy(original)
                if delete:
                    del changed["scenarios"][0][field]
                else:
                    changed["scenarios"][0][field] += 1
                with self.assertRaises(ValueError):
                    checks.validate_report(changed, "paired", 30, 40)
        changed = copy.deepcopy(original); changed["scenarios"][0]["paired_passes"] = 0
        with self.assertRaises(ValueError):
            checks.validate_report(changed, "paired", 30, 40)
        changed = report("quarter"); changed["scenarios"][0]["divisor"] = 1
        with self.assertRaises(ValueError):
            checks.validate_report(changed, "quarter", 30, 40)

    def test_environment_and_frames_are_finite_complete_and_ordered(self):
        original = report(); environment = checks.validate_report(original, "full", 30, 40)
        for key in checks.ENVIRONMENT_KEYS:
            changed = copy.deepcopy(original); changed[key] += "-other"
            with self.assertRaises(ValueError):
                checks.validate_report(changed, "full", 30, 40, environment)
        for metric in checks.METRICS:
            for invalid in (float("nan"), float("inf"), -2, True):
                changed = copy.deepcopy(original); changed["scenarios"][0][metric]["samples"][0] = invalid
                with self.assertRaises(ValueError):
                    checks.validate_report(changed, "full", 30, 40)
            changed = copy.deepcopy(original); changed["scenarios"][0][metric]["samples"].pop()
            with self.assertRaises(ValueError):
                checks.validate_report(changed, "full", 30, 40)
            changed = copy.deepcopy(original); changed["scenarios"][0][metric]["p95"] += .1
            with self.assertRaises(ValueError):
                checks.validate_report(changed, "full", 30, 40)
        changed = copy.deepcopy(original); changed["scenarios"][0]["submission_ms"] = distribution([2] * 40)
        with self.assertRaises(ValueError):
            checks.validate_report(changed, "full", 30, 40)
        changed = copy.deepcopy(original); changed["scenarios"][0]["visible_output_verified"] = False
        with self.assertRaises(ValueError):
            checks.validate_report(changed, "full", 30, 40)

    def test_fixed_paired_pixel_oracle_and_explicit_quality_tradeoff(self):
        with tempfile.TemporaryDirectory() as temporary:
            full, variant = Path(temporary) / "full", Path(temporary) / "variant"
            full.mkdir(); variant.mkdir(); sizes = {"scene": (1, 1, 1)}
            (full / "scene.rgba").write_bytes(bytes((20, 40, 60, 255)))
            for delta, passed in ((2, True), (3, False)):
                (variant / "scene.rgba").write_bytes(bytes((20 + delta, 40, 60, 255)))
                self.assertEqual(passed, checks.compare_pixels(full, variant, "paired", sizes)[0]["passed"])
            self.assertTrue(checks.compare_pixels(full, variant, "half", sizes)[0]["passed"])
            self.assertEqual(3, checks.compare_pixels(full, variant, "half", sizes)[0]["max_rgb_error"])
            (variant / "scene.rgba").write_bytes(bytes((20, 40, 60, 254)))
            for mode in ("paired", "half", "quarter"):
                self.assertFalse(checks.compare_pixels(full, variant, mode, sizes)[0]["passed"])
            (variant / "scene.rgba").write_bytes(b"short")
            with self.assertRaises(ValueError):
                checks.compare_pixels(full, variant, "paired", sizes)
            (variant / "scene.rgba").unlink()
            with self.assertRaises(ValueError):
                checks.compare_pixels(full, variant, "paired", sizes)

    def test_aggregation_retains_slow_pairs_and_uses_paired_ratios(self):
        trials = [dict(comparison="paired", status="passed", full=report(value=a), paired=report("paired", b))
                  for a, b in ((1, 4), (2, 2), (4, 2), (8, 4))]
        measured = checks.aggregate(trials, "paired")[0]["metrics"]["completed_ms"]["p50"]
        self.assertEqual([4, 1, .5, .5], measured["paired_variant_over_full"]["values_in_pair_order"])
        self.assertEqual(.75, measured["paired_variant_over_full"]["median"])
        self.assertEqual([3, 0, -2, -4], measured["paired_variant_minus_full"]["values_in_pair_order"])
        self.assertEqual(1, measured["variant_higher_pairs"])
        self.assertEqual(8, measured["full_jvm"]["max"])
        with self.assertRaises(ValueError):
            checks.aggregate(trials, "half")
        trials[-1]["status"] = "incomplete"
        with self.assertRaises(ValueError):
            checks.aggregate(trials, "paired")

    def test_unavailable_allocation_and_zero_denominators_do_not_invent_speedups(self):
        original = report()
        for row in original["scenarios"]:
            row["allocated_bytes"] = distribution([-1] * 40)
        checks.validate_report(original, "full", 30, 40)
        trials = [dict(comparison="paired", status="passed", full=original, paired=copy.deepcopy(original))]
        allocation = checks.aggregate(trials, "paired")[0]["metrics"]["allocated_bytes"]["p50"]
        self.assertEqual([None], allocation["paired_variant_over_full"]["values_in_pair_order"])
        self.assertIsNone(allocation["paired_variant_minus_full"]["median"])
        for name in ("full_jvm", "variant_jvm"):
            self.assertEqual([None], allocation[name]["values_in_pair_order"])
            self.assertEqual(0, allocation[name]["available_pairs"])
            for key in ("median", "min", "max"):
                self.assertIsNone(allocation[name][key])
        changed = copy.deepcopy(original); changed["scenarios"][0]["allocated_bytes"]["samples"][0] = 0
        with self.assertRaises(ValueError):
            checks.validate_report(changed, "full", 30, 40)
        trials = [dict(comparison="paired", status="passed", full=report(value=0), paired=report("paired", 1))]
        self.assertIsNone(checks.aggregate(trials, "paired")[0]["metrics"]["completed_ms"]["p50"]["paired_variant_over_full"]["median"])

    def test_trial_runner_preserves_all_modes_orders_and_failed_evidence(self):
        with tempfile.TemporaryDirectory() as temporary:
            output = Path(temporary); calls = []
            summary = dict(status="incomplete", trials=[], comparisons={}, completed_pairs=0)
            def execute(mode, directory, _log):
                calls.append(mode); (directory / "results.json").write_text(json.dumps(report(mode)))
            with patch.object(checks, "compare_pixels", return_value=[{"passed": True}]):
                checks.run_trials(output, summary, checks.pair_orders(4), 30, 40, execute)
            self.assertEqual(12, summary["completed_pairs"])
            self.assertEqual(24, len(calls))
            self.assertEqual(set(checks.COMPARISONS), set(summary["comparisons"]))
            self.assertEqual(["full", "paired", "paired", "full"] * 2, calls[:8])
            # Only main can mark success, after the independent native contracts too.
            self.assertEqual("incomplete", summary["status"])
        with tempfile.TemporaryDirectory() as temporary:
            output = Path(temporary); summary = dict(status="incomplete", trials=[], comparisons={}, completed_pairs=0)
            def failing(mode, directory, _log):
                measured = report(mode)
                if mode == "paired":
                    del measured["scenarios"][0]["paired_passes"]
                (directory / "results.json").write_text(json.dumps(measured))
            with self.assertRaises(ValueError):
                checks.run_trials(output, summary, checks.pair_orders(4), 30, 40, failing)
            saved = json.loads((output / "scene-blur-comparison.json").read_text())
            self.assertEqual("failed", saved["status"])
            self.assertEqual("failed", saved["trials"][0]["status"])
            self.assertEqual("passed", saved["trials"][0]["runs"][0]["status"])
            self.assertEqual(0, saved["completed_pairs"])


if __name__ == "__main__":
    unittest.main()
