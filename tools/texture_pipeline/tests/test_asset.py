import json
import tempfile
import unittest
from pathlib import Path

from tools.texture_pipeline.asset import AssetError, asset_build_dir, load_asset_package


class AssetPackageTest(unittest.TestCase):
    def setUp(self):
        self.temp_dir = tempfile.TemporaryDirectory()
        self.package_dir = Path(self.temp_dir.name)

    def tearDown(self):
        self.temp_dir.cleanup()

    def write_package(self, *, asset_changes=None, source_changes=None):
        asset = {
            "schemaVersion": 1,
            "assetId": "quirky:item/example",
            "assetClass": "item_texture",
            "output": {
                "path": "src/main/resources/assets/quirky/textures/item/example.png",
                "width": 16,
                "height": 16,
                "colorMode": "rgba",
            },
            "styleProfile": "quirky_vanilla_item",
            "brief": {
                "subject": "Example item",
                "gameplayMeaning": "Communicates the example mechanic",
                "visualPriority": ["readable silhouette"],
            },
            "mustHave": ["clear native-scale silhouette"],
            "mustNotHave": ["smooth antialiasing"],
            "qualityGates": {
                "maximumPaletteSize": 24,
                "allowPartialAlpha": False,
                "requireVisualAudit": True,
                "requireHumanApproval": True,
            },
        }
        source = {
            "canvas": {"width": 16, "height": 16, "background": "transparent"},
            "palette": {"base": "#A8754DFF", "outline": "#38271FFF"},
            "layers": [
                {
                    "id": "body",
                    "operation": "rect",
                    "color": "base",
                    "x": 4,
                    "y": 4,
                    "width": 8,
                    "height": 8,
                }
            ],
        }
        if asset_changes:
            self.apply_changes(asset, asset_changes)
        if source_changes:
            self.apply_changes(source, source_changes)
        (self.package_dir / "asset.json").write_text(json.dumps(asset), encoding="utf-8")
        (self.package_dir / "source.json").write_text(json.dumps(source), encoding="utf-8")

    @staticmethod
    def apply_changes(target, changes):
        for path, value in changes.items():
            parts = path.split(".")
            current = target
            for part in parts[:-1]:
                current = current[int(part)] if isinstance(current, list) else current[part]
            final = parts[-1]
            if value is _DELETE:
                if isinstance(current, list):
                    del current[int(final)]
                else:
                    del current[final]
            elif isinstance(current, list):
                current[int(final)] = value
            else:
                current[final] = value

    def assert_invalid(self, *, asset_changes=None, source_changes=None, message):
        self.write_package(asset_changes=asset_changes, source_changes=source_changes)
        with self.assertRaisesRegex(AssetError, message):
            load_asset_package(self.package_dir)

    def test_loads_valid_package_and_derives_build_directory(self):
        self.write_package()

        package = load_asset_package(self.package_dir)

        self.assertEqual(package.spec.asset_id, "quirky:item/example")
        self.assertEqual(package.spec.output.width, 16)
        self.assertEqual(package.spec.quality_gates.maximum_palette_size, 24)
        self.assertEqual(
            asset_build_dir(Path("build/texture-pipeline"), package.spec.asset_id),
            Path("build/texture-pipeline/quirky/item/example"),
        )

    def test_rejects_unsupported_schema_version(self):
        self.assert_invalid(asset_changes={"schemaVersion": 2}, message="schemaVersion")

    def test_rejects_invalid_resource_identifier(self):
        self.assert_invalid(asset_changes={"assetId": "quirky:item/Seed Pouch"}, message="assetId")

    def test_rejects_output_outside_quirky_texture_tree(self):
        self.assert_invalid(
            asset_changes={"output.path": "src/main/resources/assets/minecraft/textures/item/example.png"},
            message="output.path",
        )

    def test_rejects_mismatched_canvas_dimensions(self):
        self.assert_invalid(source_changes={"canvas.width": 15}, message="canvas")

    def test_rejects_duplicate_layer_ids(self):
        duplicate = {
            "id": "body",
            "operation": "point",
            "color": "outline",
            "x": 1,
            "y": 1,
        }
        self.write_package()
        source_path = self.package_dir / "source.json"
        source = json.loads(source_path.read_text(encoding="utf-8"))
        source["layers"].append(duplicate)
        source_path.write_text(json.dumps(source), encoding="utf-8")

        with self.assertRaisesRegex(AssetError, "duplicate layer id"):
            load_asset_package(self.package_dir)

    def test_rejects_unknown_palette_reference(self):
        self.assert_invalid(source_changes={"layers.0.color": "missing"}, message="palette")

    def test_rejects_out_of_bounds_coordinate(self):
        self.assert_invalid(source_changes={"layers.0.x": 12}, message="bounds")

    def test_rejects_empty_semantic_brief(self):
        self.assert_invalid(asset_changes={"brief.subject": ""}, message="brief.subject")

    def test_rejects_unknown_operation(self):
        self.assert_invalid(source_changes={"layers.0.operation": "blur"}, message="operation")

    def test_rejects_invalid_polygon_points(self):
        polygon = {
            "id": "body",
            "operation": "polygon",
            "color": "base",
            "points": [[1, 1], [2, 2]],
        }
        self.assert_invalid(source_changes={"layers.0": polygon}, message="polygon")

    def test_rejects_copy_destination_outside_canvas(self):
        copy = {
            "id": "body",
            "operation": "copy",
            "x": 0,
            "y": 0,
            "width": 4,
            "height": 4,
            "destX": 14,
            "destY": 14,
        }
        self.assert_invalid(source_changes={"layers.0": copy}, message="copy.*bounds")

    def test_rejects_unknown_mirror_axis(self):
        mirror = {"id": "body", "operation": "mirror", "axis": "diagonal"}
        self.assert_invalid(source_changes={"layers.0": mirror}, message="mirror.axis")


_DELETE = object()


if __name__ == "__main__":
    unittest.main()
