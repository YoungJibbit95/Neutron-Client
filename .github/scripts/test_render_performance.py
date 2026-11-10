"""Offline regression tests for native evidence comparison (no GL/network required)."""
import importlib.util
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location("render_performance", Path(__file__).with_name("check_render_performance.py"))
checks = importlib.util.module_from_spec(spec)
spec.loader.exec_module(checks)


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


if __name__ == "__main__":
    unittest.main()
