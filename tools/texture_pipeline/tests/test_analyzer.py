import hashlib
import tempfile
import unittest
from pathlib import Path, PurePosixPath

from tools.texture_pipeline.analyzer import analyze_pixels, validate_candidate
from tools.texture_pipeline.asset import AssetSpec, OutputSpec, QualityGates
from tools.texture_pipeline.png_io import write_rgba_png


T = (0, 0, 0, 0)
R = (255, 0, 0, 255)
G = (0, 255, 0, 255)
P = (1, 2, 3, 127)


def spec(*, width=4, height=4, maximum_palette_size=8, allow_partial_alpha=False):
    return AssetSpec(
        asset_id="quirky:item/example",
        asset_class="item_texture",
        output=OutputSpec(
            path=PurePosixPath("src/main/resources/assets/quirky/textures/item/example.png"),
            width=width,
            height=height,
            color_mode="rgba",
        ),
        style_profile="quirky_vanilla_item",
        brief={"subject": "Example"},
        must_have=("readable",),
        must_not_have=(),
        quality_gates=QualityGates(
            maximum_palette_size=maximum_palette_size,
            allow_partial_alpha=allow_partial_alpha,
            require_visual_audit=True,
            require_human_approval=True,
        ),
    )


class AnalyzerTest(unittest.TestCase):
    def setUp(self):
        self.pixels = [
            [T, T, T, T],
            [T, R, R, T],
            [T, G, P, T],
            [T, T, T, T],
        ]

    def test_reports_exact_pixel_facts(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "fixture.png"
            write_rgba_png(path, self.pixels)

            facts = analyze_pixels(self.pixels, path)

            self.assertEqual(facts["sha256"], hashlib.sha256(path.read_bytes()).hexdigest())
            self.assertEqual(facts["path"], str(path))
            self.assertEqual(facts["width"], 4)
            self.assertEqual(facts["height"], 4)
            self.assertEqual(facts["colorMode"], "RGBA")
            self.assertEqual(facts["paletteSize"], 4)
            self.assertEqual(facts["transparentPixels"], 12)
            self.assertEqual(facts["opaquePixels"], 3)
            self.assertEqual(facts["partialAlphaPixels"], 1)
            self.assertEqual(facts["occupiedBounds"], {"x": 1, "y": 1, "width": 2, "height": 2})
            self.assertEqual(facts["blankMargins"], {"top": 1, "right": 1, "bottom": 1, "left": 1})
            self.assertEqual(
                facts["colors"],
                [
                    {"rgba": "#00000000", "count": 12},
                    {"rgba": "#FF0000FF", "count": 2},
                    {"rgba": "#00FF00FF", "count": 1},
                    {"rgba": "#0102037F", "count": 1},
                ],
            )

    def test_gate_rejects_wrong_dimensions(self):
        facts = analyze_pixels(self.pixels)
        result = validate_candidate(spec(width=5), facts)
        self.assertEqual(result["status"], "fail")
        self.assertIn("dimensions", result["failures"][0])

    def test_gate_rejects_excess_palette(self):
        result = validate_candidate(spec(maximum_palette_size=3, allow_partial_alpha=True), analyze_pixels(self.pixels))
        self.assertEqual(result["status"], "fail")
        self.assertTrue(any("palette" in failure for failure in result["failures"]))

    def test_gate_rejects_forbidden_partial_alpha(self):
        result = validate_candidate(spec(), analyze_pixels(self.pixels))
        self.assertEqual(result["status"], "fail")
        self.assertTrue(any("partial alpha" in failure for failure in result["failures"]))

    def test_gate_rejects_empty_image(self):
        result = validate_candidate(spec(), analyze_pixels([[T] * 4 for _ in range(4)]))
        self.assertEqual(result["status"], "fail")
        self.assertTrue(any("empty" in failure for failure in result["failures"]))

    def test_gate_warns_when_visible_content_touches_single_edge(self):
        pixels = [[T] * 4 for _ in range(4)]
        pixels[1][0] = R

        result = validate_candidate(spec(), analyze_pixels(pixels))

        self.assertEqual(result["status"], "pass")
        self.assertEqual(result["failures"], [])
        self.assertTrue(any("edge" in warning for warning in result["warnings"]))

    def test_gate_fails_full_bleed_content_touching_every_edge(self):
        pixels = [[R] * 4 for _ in range(4)]

        result = validate_candidate(spec(), analyze_pixels(pixels))

        self.assertEqual(result["status"], "fail")
        self.assertTrue(any("every edge" in failure for failure in result["failures"]))


if __name__ == "__main__":
    unittest.main()
