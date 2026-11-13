"""Offline tests of CPU evidence completeness, pairing and failure retention."""
import copy
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location("core_performance", Path(__file__).with_name("check_core_performance.py"))
checks = importlib.util.module_from_spec(spec)
spec.loader.exec_module(checks)


def report(mode="legacy", value=100, benchmark="particle_cleanup", allocated=20):
    return {"schema": "neutron.core-performance.v1", "benchmark": benchmark, "mode": mode,
            "baseline_ref": checks.BASELINE_REF, "java_runtime": "Temurin 8u482", "processors": 4,
            "max_heap_bytes": 512 * 1024 * 1024, "allocation_counter_enabled": allocated is not None,
            "scope": "fixture reset excluded; production cleanup included", "structure": {"fixed_scratch_bytes": 16384},
            "scenarios": [{"name": name, "parameters": {"size": 4000}, "warmup_iterations": 1000,
                           "iterations_per_sample": checks.BENCHMARKS[benchmark][1],
                           "expected_signature": "abcd", "observed_signature": "abcd",
                           "elapsed_ns": [value] * 40, "allocated_bytes": [allocated] * 40}
                          for name in checks.BENCHMARKS[benchmark][2]]}


class CorePerformanceTest(unittest.TestCase):
    def test_four_pairs_alternate_order(self):
        self.assertEqual([("legacy", "fast"), ("fast", "legacy")] * 2, checks.pair_orders(4))
        for invalid in (0, 1, 2, 3, 5, True):
            with self.assertRaises(ValueError):
                checks.pair_orders(invalid)

    def test_requires_every_scenario_and_complete_samples(self):
        for benchmark in checks.BENCHMARKS:
            valid = report(benchmark=benchmark)
            checks.validate_report(valid, benchmark, "legacy")
            omitted = copy.deepcopy(valid); omitted["scenarios"].pop()
            duplicate = copy.deepcopy(valid); duplicate["scenarios"][0] = duplicate["scenarios"][1]
            short = copy.deepcopy(valid); short["scenarios"][0]["elapsed_ns"].pop()
            for invalid in (omitted, duplicate, short):
                with self.assertRaises(ValueError):
                    checks.validate_report(invalid, benchmark, "legacy")

    def test_rejects_wrong_provenance_and_false_signature(self):
        for key, value in (("schema", "other"), ("baseline_ref", "main"), ("mode", "fast"),
                           ("benchmark", "other"), ("structure", {})):
            changed = report(); changed[key] = value
            with self.assertRaises(ValueError):
                checks.validate_report(changed, "particle_cleanup", "legacy")
        for key, value in (("observed_signature", "bad"), ("expected_signature", ""),
                           ("iterations_per_sample", 1), ("warmup_iterations", 0)):
            changed = report(); changed["scenarios"][0][key] = value
            with self.assertRaises(ValueError):
                checks.validate_report(changed, "particle_cleanup", "legacy")

    def test_rejects_nonfinite_negative_boolean_and_missing_samples(self):
        for key in ("elapsed_ns", "allocated_bytes"):
            for invalid in (-1, True, None, float("nan"), float("inf"), 1.5):
                changed = report(); changed["scenarios"][0][key][0] = invalid
                with self.assertRaises(ValueError):
                    checks.validate_report(changed, "particle_cleanup", "legacy")
        changed = report(); changed["scenarios"][0]["elapsed_ns"][0] = 0
        with self.assertRaises(ValueError):
            checks.validate_report(changed, "particle_cleanup", "legacy")

    def test_all_jvms_match_environment_and_fixture(self):
        original = report()
        environment = checks.validate_report(original, "particle_cleanup", "legacy")
        for key in checks.ENVIRONMENT_KEYS:
            changed = report("fast")
            changed[key] = not changed[key] if type(changed[key]) is bool else changed[key] + (1 if type(changed[key]) is int else "!")
            with self.assertRaises(ValueError):
                checks.validate_report(changed, "particle_cleanup", "fast", environment, original)
        changed = report("fast"); changed["scenarios"][0]["parameters"]["size"] = 5
        with self.assertRaises(ValueError):
            checks.validate_report(changed, "particle_cleanup", "fast", environment, original)

    def test_batches_normalize_before_paired_percentiles_and_keep_slow_runs(self):
        trials = [{"legacy": report(value=before), "fast": report("fast", after)}
                  for before, after in ((10, 40), (20, 20), (40, 20), (8000, 4000))]
        result = checks.aggregate_trials(trials, "particle_cleanup")[0]["metrics"]["elapsed_ns"]["p50"]
        self.assertEqual([1, 2, 4, 800], result["legacy_jvm"]["values_in_pair_order"])
        self.assertEqual([4, 1, .5, .5], result["paired_fast_over_legacy"]["values_in_pair_order"])
        self.assertEqual(.75, result["paired_fast_over_legacy"]["median"])
        self.assertEqual(1, result["fast_higher_pairs"])
        self.assertEqual(800, result["legacy_jvm"]["max"])

    def test_zero_and_unsupported_allocations_do_not_invent_ratios(self):
        for allocation in (0, None):
            trials = [{"legacy": report(allocated=allocation), "fast": report("fast", allocated=allocation)} for _ in range(4)]
            checks.validate_report(trials[0]["legacy"], "particle_cleanup", "legacy")
            result = checks.aggregate_trials(trials, "particle_cleanup")[0]["metrics"]["allocated_bytes"]["p50"]
            self.assertEqual([None] * 4, result["paired_fast_over_legacy"]["values_in_pair_order"])
            self.assertEqual([None if allocation is None else 0] * 4,
                             result["paired_fast_minus_legacy"]["values_in_pair_order"])

    def test_failure_retains_completed_pair_and_unfinished_run(self):
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory)
            summary = {"status": "incomplete", "benchmarks": {}}
            calls = []
            def execute(command, log):
                mode, destination = command[-2:]
                calls.append(mode)
                if len(calls) == 4:
                    raise RuntimeError("fixture failure")
                destination.write_text(json.dumps(report(mode)), encoding="utf-8")
            with patch.object(checks, "run_java", side_effect=execute):
                with self.assertRaisesRegex(RuntimeError, "fixture failure"):
                    checks.collect_trials("particle_cleanup", 4, "java", "classpath", output, summary)
            saved = json.loads((output / "core-comparison.json").read_text())
            section = saved["benchmarks"]["particle_cleanup"]
            self.assertEqual(["legacy", "fast", "fast", "legacy"], calls)
            self.assertEqual("incomplete", section["status"])
            self.assertEqual(1, section["completed_pairs"])
            self.assertEqual("validated", section["trials"][0]["status"])
            self.assertEqual("validated", section["trials"][1]["runs"]["fast"]["status"])
            self.assertEqual("incomplete", section["trials"][1]["runs"]["legacy"]["status"])
            self.assertEqual("incomplete", saved["status"])


if __name__ == "__main__":
    unittest.main()
