import tempfile
import unittest
from pathlib import Path

from tools.texture_pipeline.png_io import read_rgba_png, write_rgba_png
from tools.texture_pipeline.renderer import parse_color, render_source


T = (0, 0, 0, 0)
R = (255, 0, 0, 255)
G = (0, 255, 0, 255)
B = (0, 0, 255, 255)


def source(width, height, layers, *, background="transparent", palette=None):
    return {
        "canvas": {"width": width, "height": height, "background": background},
        "palette": palette or {"red": "#FF0000FF", "green": "#00FF00FF", "blue": "#0000FFFF"},
        "layers": layers,
    }


def occupied(pixels):
    return {
        (x, y): pixel
        for y, row in enumerate(pixels)
        for x, pixel in enumerate(row)
        if pixel[3] != 0
    }


class ColorParsingTest(unittest.TestCase):
    def test_parses_transparent_palette_rgb_and_rgba(self):
        palette = {"cloth": "#A8754DFF"}

        self.assertEqual(parse_color("transparent", palette), T)
        self.assertEqual(parse_color("cloth", palette), (168, 117, 77, 255))
        self.assertEqual(parse_color("#123456", palette), (18, 52, 86, 255))
        self.assertEqual(parse_color("#12345678", palette), (18, 52, 86, 120))


class RendererTest(unittest.TestCase):
    def test_point_and_rect_replace_pixels_in_layer_order(self):
        pixels = render_source(
            source(
                4,
                4,
                [
                    {"id": "base", "operation": "rect", "color": "red", "x": 1, "y": 1, "width": 2, "height": 2},
                    {"id": "highlight", "operation": "point", "color": "green", "x": 2, "y": 2},
                ],
            )
        )

        self.assertEqual(
            occupied(pixels),
            {(1, 1): R, (2, 1): R, (1, 2): R, (2, 2): G},
        )

    def test_line_uses_stable_bresenham_pixels_in_all_directions(self):
        cases = [
            ((0, 0, 4, 2), {(0, 0), (1, 0), (2, 1), (3, 1), (4, 2)}),
            ((4, 2, 0, 0), {(0, 0), (1, 0), (2, 1), (3, 1), (4, 2)}),
            ((0, 0, 2, 4), {(0, 0), (0, 1), (1, 2), (1, 3), (2, 4)}),
            ((0, 4, 4, 2), {(0, 4), (1, 4), (2, 3), (3, 3), (4, 2)}),
            ((4, 4, 2, 0), {(2, 0), (2, 1), (3, 2), (3, 3), (4, 4)}),
            ((4, 0, 0, 2), {(0, 2), (1, 2), (2, 1), (3, 1), (4, 0)}),
        ]
        for (x1, y1, x2, y2), expected in cases:
            with self.subTest(endpoints=(x1, y1, x2, y2)):
                pixels = render_source(
                    source(
                        5,
                        5,
                        [{"id": "line", "operation": "line", "color": "red", "x1": x1, "y1": y1, "x2": x2, "y2": y2}],
                    )
                )
                self.assertEqual(set(occupied(pixels)), expected)

    def test_ellipse_uses_pixel_center_containment(self):
        pixels = render_source(
            source(
                5,
                5,
                [{"id": "ellipse", "operation": "ellipse", "color": "red", "cx": 2, "cy": 2, "rx": 2, "ry": 2}],
            )
        )

        self.assertEqual(
            set(occupied(pixels)),
            {
                (1, 0), (2, 0),
                (0, 1), (1, 1), (2, 1), (3, 1),
                (0, 2), (1, 2), (2, 2), (3, 2),
                (1, 3), (2, 3),
            },
        )

    def test_concave_polygon_uses_even_odd_fill(self):
        pixels = render_source(
            source(
                5,
                5,
                [
                    {
                        "id": "polygon",
                        "operation": "polygon",
                        "color": "red",
                        "points": [[0, 0], [4, 0], [4, 1], [1, 1], [1, 4], [0, 4]],
                    }
                ],
            )
        )

        self.assertEqual(
            set(occupied(pixels)),
            {(0, 0), (1, 0), (2, 0), (3, 0), (0, 1), (0, 2), (0, 3)},
        )

    def test_polygon_symmetric_edges_render_symmetric(self):
        # 左右交点（2.5 / 12.5）相对中心 7.5 对称；边界像素归属必须一致，
        # 否则同一多边形左缘多 1px（armor_chestplate v01 实测缺陷）。
        pixels = render_source(
            source(
                16,
                16,
                [
                    {
                        "id": "body",
                        "operation": "polygon",
                        "color": "red",
                        "points": [[3, 2], [12, 2], [13, 5], [13, 9], [11, 11], [11, 13], [9, 14], [6, 14], [4, 13], [4, 11], [2, 9], [2, 5]],
                    }
                ],
            )
        )

        row = pixels[3]
        filled = [x for x, pixel in enumerate(row) if pixel[3] == 255]
        self.assertEqual(filled, list(range(3, 13)))

    def test_copy_reads_from_snapshot_when_regions_overlap(self):
        pixels = render_source(
            source(
                4,
                1,
                [
                    {"id": "red", "operation": "point", "color": "red", "x": 0, "y": 0},
                    {"id": "green", "operation": "point", "color": "green", "x": 1, "y": 0},
                    {"id": "blue", "operation": "point", "color": "blue", "x": 2, "y": 0},
                    {"id": "copy", "operation": "copy", "x": 0, "y": 0, "width": 3, "height": 1, "dest_x": 1, "dest_y": 0},
                ],
            )
        )

        self.assertEqual(pixels[0], [R, R, G, B])

    def test_copy_rejects_legacy_camel_case_fields(self):
        with self.assertRaises(KeyError):
            render_source(
                source(
                    4,
                    1,
                    [
                        {"id": "red", "operation": "point", "color": "red", "x": 0, "y": 0},
                        {"id": "copy", "operation": "copy", "x": 0, "y": 0, "width": 3, "height": 1, "destX": 1, "destY": 0},
                    ],
                )
            )

    def test_mirror_replaces_complete_canvas(self):
        horizontal = render_source(
            source(
                3,
                2,
                [
                    {"id": "mark", "operation": "point", "color": "red", "x": 0, "y": 0},
                    {"id": "mirror", "operation": "mirror", "axis": "horizontal"},
                ],
            )
        )
        vertical = render_source(
            source(
                3,
                2,
                [
                    {"id": "mark", "operation": "point", "color": "red", "x": 0, "y": 0},
                    {"id": "mirror", "operation": "mirror", "axis": "vertical"},
                ],
            )
        )

        self.assertEqual(occupied(horizontal), {(2, 0): R})
        self.assertEqual(occupied(vertical), {(0, 1): R})


class PngCodecTest(unittest.TestCase):
    def test_round_trips_rgba_and_is_byte_stable(self):
        pixels = [[T, R], [G, (1, 2, 3, 127)]]
        with tempfile.TemporaryDirectory() as directory:
            first = Path(directory) / "first.png"
            second = Path(directory) / "second.png"

            write_rgba_png(first, pixels)
            write_rgba_png(second, pixels)

            self.assertEqual(first.read_bytes(), second.read_bytes())
            self.assertEqual(read_rgba_png(first), pixels)


if __name__ == "__main__":
    unittest.main()
