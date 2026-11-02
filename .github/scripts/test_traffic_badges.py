import importlib.util
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import xml.etree.ElementTree as ET

spec = importlib.util.spec_from_file_location("traffic", Path(__file__).with_name("update_traffic_badges.py"))
traffic = importlib.util.module_from_spec(spec)
spec.loader.exec_module(traffic)


class TrafficBadgeTest(unittest.TestCase):
    def test_svg_has_visible_date_and_separate_rows_for_large_counts(self):
        svg = ET.fromstring(traffic.make_svg("views", 1234567, 123456, "2026-11-02"))
        text = svg.findall(".//{http://www.w3.org/2000/svg}text")
        self.assertEqual(4, len({t.attrib["y"] for t in text}))
        self.assertIn("1,234,567", [t.text for t in text])
        self.assertIn("Updated 2026-11-02 UTC", [t.text for t in text])
        self.assertEqual("280", svg.attrib["width"])

    def test_second_api_failure_preserves_both_existing_badges(self):
        with tempfile.TemporaryDirectory() as tmp:
            output = Path(tmp)
            for kind in ("views", "clones"):
                (output / (kind + "-14d.svg")).write_text("previous " + kind)
            with patch.object(traffic, "OUTPUT", output), patch.dict(os.environ, {"NEUTRON_TRAFFIC_TOKEN": "fixture"}), patch.object(traffic, "get_traffic", side_effect=[(5, 2), ValueError("denied")]):
                with self.assertRaises(ValueError):
                    traffic.main()
            for kind in ("views", "clones"):
                self.assertEqual("previous " + kind, (output / (kind + "-14d.svg")).read_text())

    def test_missing_secret_keeps_files_and_warns(self):
        with patch.dict(os.environ, {"NEUTRON_TRAFFIC_TOKEN": "", "GITHUB_STEP_SUMMARY": ""}), patch.object(traffic, "get_traffic") as api, patch("builtins.print") as log:
            traffic.main()
            api.assert_not_called()
            self.assertIn("::warning::", log.call_args.args[0])


if __name__ == "__main__":
    unittest.main()
