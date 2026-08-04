import tempfile
import unittest
from pathlib import Path

from tools.texture_pipeline.png_io import read_rgba_png, write_rgba_png
from tools.texture_pipeline.preview import composite_background, generate_preview_set, scale_nearest


class PreviewTest(unittest.TestCase):
    def test_nearest_scaling_repeats_exact_pixel_blocks(self):
        red = (255, 0, 0, 255)
        blue = (0, 0, 255, 255)

        scaled = scale_nearest([[red, blue]], 2)

        self.assertEqual(scaled, [[red, red, blue, blue], [red, red, blue, blue]])

    def test_background_composition_uses_straight_alpha(self):
        pixels = [[(255, 0, 0, 128), (10, 20, 30, 0), (1, 2, 3, 255)]]

        composited = composite_background(pixels, (0, 0, 255, 255))

        self.assertEqual(composited, [[(128, 0, 127, 255), (0, 0, 255, 255), (1, 2, 3, 255)]])

    def test_generates_complete_preview_set(self):
        pixels = [[(255, 0, 0, 255), (0, 0, 0, 0)], [(0, 0, 0, 0), (0, 255, 0, 255)]]
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            candidate = root / "candidate.png"
            output = root / "previews"
            write_rgba_png(candidate, pixels)

            paths = generate_preview_set(candidate, output)

            self.assertEqual(
                set(paths),
                {"native", "nearest-16x", "checker-16x", "light-16x", "dark-16x", "inventory-context"},
            )
            self.assertTrue(all(path.is_file() for path in paths.values()))
            self.assertEqual(read_rgba_png(paths["native"]), pixels)
            nearest = read_rgba_png(paths["nearest-16x"])
            self.assertEqual((len(nearest[0]), len(nearest)), (32, 32))
            inventory = read_rgba_png(paths["inventory-context"])
            self.assertEqual((len(inventory[0]), len(inventory)), (32, 32))
            self.assertEqual(inventory[15][15], (255, 0, 0, 255))
            self.assertEqual(inventory[16][16], (0, 255, 0, 255))


if __name__ == "__main__":
    unittest.main()
