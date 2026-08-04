"""Generate deterministic multi-context previews for PNG candidates."""

from __future__ import annotations

from pathlib import Path

from .png_io import Pixels, RGBA, read_rgba_png, write_rgba_png


_LIGHT = (238, 238, 238, 255)
_DARK = (35, 37, 42, 255)
_CHECKER_LIGHT = (176, 176, 176, 255)
_CHECKER_DARK = (112, 112, 112, 255)
_SLOT_OUTLINE = (43, 43, 43, 255)
_SLOT_INNER = (139, 139, 139, 255)


def scale_nearest(pixels: Pixels, factor: int) -> Pixels:
    if type(factor) is not int or factor <= 0:
        raise ValueError("scale factor must be a positive integer")
    output: Pixels = []
    for row in pixels:
        expanded = [pixel for pixel in row for _ in range(factor)]
        output.extend([expanded[:] for _ in range(factor)])
    return output


def _over(source: RGBA, background: RGBA) -> RGBA:
    if background[3] != 255:
        raise ValueError("preview background must be opaque")
    alpha = source[3]
    if alpha == 255:
        return source
    if alpha == 0:
        return background
    return tuple((source[channel] * alpha + background[channel] * (255 - alpha) + 127) // 255 for channel in range(3)) + (255,)


def composite_background(pixels: Pixels, background: RGBA) -> Pixels:
    return [[_over(pixel, background) for pixel in row] for row in pixels]


def _checker_composite(pixels: Pixels, square: int = 16) -> Pixels:
    return [
        [
            _over(pixel, _CHECKER_LIGHT if (x // square + y // square) % 2 == 0 else _CHECKER_DARK)
            for x, pixel in enumerate(row)
        ]
        for y, row in enumerate(pixels)
    ]


def _inventory_context(pixels: Pixels) -> Pixels:
    size = 32
    canvas: Pixels = [
        [_SLOT_OUTLINE if x in {0, size - 1} or y in {0, size - 1} else _SLOT_INNER for x in range(size)]
        for y in range(size)
    ]
    height, width = len(pixels), len(pixels[0])
    offset_x = (size - width) // 2
    offset_y = (size - height) // 2
    for y, row in enumerate(pixels):
        for x, pixel in enumerate(row):
            canvas[offset_y + y][offset_x + x] = _over(pixel, canvas[offset_y + y][offset_x + x])
    return canvas


def generate_preview_set(candidate: Path, output_dir: Path) -> dict[str, Path]:
    """Generate the six required preview files and return their semantic names."""
    pixels = read_rgba_png(candidate)
    enlarged = scale_nearest(pixels, 16)
    output_dir = Path(output_dir)
    previews = {
        "native": ("native.png", pixels),
        "nearest-16x": ("nearest-16x.png", enlarged),
        "checker-16x": ("checker-16x.png", _checker_composite(enlarged)),
        "light-16x": ("light-16x.png", composite_background(enlarged, _LIGHT)),
        "dark-16x": ("dark-16x.png", composite_background(enlarged, _DARK)),
        "inventory-context": ("inventory-context.png", _inventory_context(pixels)),
    }
    paths: dict[str, Path] = {}
    for name, (filename, preview_pixels) in previews.items():
        path = output_dir / filename
        write_rgba_png(path, preview_pixels)
        paths[name] = path
    return paths
