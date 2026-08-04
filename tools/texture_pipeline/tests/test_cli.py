import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path


REPOSITORY_ROOT = Path(__file__).resolve().parents[3]


class CliIntegrationTest(unittest.TestCase):
    def setUp(self):
        self.temp_dir = tempfile.TemporaryDirectory()
        self.root = Path(self.temp_dir.name)
        self.package = self.root / "package"
        self.package.mkdir()
        self.build_root = self.root / "build"
        self.formal_path = REPOSITORY_ROOT / "src/main/resources/assets/quirky/textures/item/__pipeline_test_example.png"
        self.assertFalse(self.formal_path.exists())

    def tearDown(self):
        self.temp_dir.cleanup()

    def write_package(self, *, valid=True):
        asset = {
            "schemaVersion": 1 if valid else 99,
            "assetId": "quirky:item/pipeline_test_example",
            "assetClass": "item_texture",
            "output": {
                "path": "src/main/resources/assets/quirky/textures/item/__pipeline_test_example.png",
                "width": 4,
                "height": 4,
                "colorMode": "rgba",
            },
            "styleProfile": "quirky_vanilla_item",
            "brief": {
                "subject": "Pipeline test item",
                "gameplayMeaning": "Tests candidate staging",
                "visualPriority": ["simple diagonal"],
            },
            "mustHave": ["visible pixels"],
            "mustNotHave": ["partial alpha"],
            "qualityGates": {
                "maximumPaletteSize": 4,
                "allowPartialAlpha": False,
                "requireVisualAudit": True,
                "requireHumanApproval": True,
            },
        }
        source = {
            "canvas": {"width": 4, "height": 4, "background": "transparent"},
            "palette": {"mark": "#FF0000FF"},
            "layers": [
                {"id": "mark", "operation": "line", "color": "mark", "x1": 0, "y1": 0, "x2": 3, "y2": 3}
            ],
        }
        (self.package / "asset.json").write_text(json.dumps(asset), encoding="utf-8")
        (self.package / "source.json").write_text(json.dumps(source), encoding="utf-8")

    def run_cli(self, *arguments):
        return subprocess.run(
            [sys.executable, "-m", "tools.texture_pipeline", *map(str, arguments)],
            cwd=REPOSITORY_ROOT,
            capture_output=True,
            text=True,
        )

    def test_render_stages_candidate_reports_and_previews_without_formal_write(self):
        self.write_package()

        result = self.run_cli("render", self.package, "--candidate", "v01", "--build-root", self.build_root)

        self.assertEqual(result.returncode, 0, result.stderr)
        asset_build = self.build_root / "quirky/item/pipeline_test_example"
        self.assertTrue((asset_build / "candidates/v01.png").is_file())
        self.assertTrue((asset_build / "reports/pixel-facts.json").is_file())
        self.assertTrue((asset_build / "reports/validation.json").is_file())
        for name in (
            "native.png",
            "nearest-16x.png",
            "checker-16x.png",
            "light-16x.png",
            "dark-16x.png",
            "inventory-context.png",
        ):
            self.assertTrue((asset_build / "previews" / name).is_file(), name)
        validation = json.loads((asset_build / "reports/validation.json").read_text(encoding="utf-8"))
        self.assertEqual(validation["status"], "pass")
        self.assertFalse(self.formal_path.exists())

    def test_invalid_package_returns_exit_two_and_concise_error(self):
        self.write_package(valid=False)

        result = self.run_cli("validate", self.package)

        self.assertEqual(result.returncode, 2)
        self.assertTrue(result.stderr.startswith("ERROR:"), result.stderr)
        self.assertNotIn("Traceback", result.stderr)


if __name__ == "__main__":
    unittest.main()
