"""Render validated structured texture sources into exact RGBA pixels."""

from __future__ import annotations

from collections.abc import Mapping
from typing import Any, Iterator

from .png_io import Pixels, RGBA


class RenderError(ValueError):
    """Raised when renderer input cannot be interpreted."""


def parse_color(value: str, palette: Mapping[str, str]) -> RGBA:
    """Resolve transparency, a palette name, or a hexadecimal RGB/RGBA literal."""
    if value == "transparent":
        return (0, 0, 0, 0)
    resolved = palette.get(value, value)
    if not isinstance(resolved, str) or not resolved.startswith("#") or len(resolved) not in {7, 9}:
        raise RenderError(f"invalid color {value!r}")
    try:
        channels = tuple(int(resolved[index : index + 2], 16) for index in range(1, len(resolved), 2))
    except ValueError as exc:
        raise RenderError(f"invalid color {value!r}") from exc
    if len(channels) == 3:
        return channels + (255,)
    return channels


def _line_points(x1: int, y1: int, x2: int, y2: int) -> Iterator[tuple[int, int]]:
    steep = abs(y2 - y1) > abs(x2 - x1)
    if steep:
        x1, y1 = y1, x1
        x2, y2 = y2, x2
    if x1 > x2:
        x1, x2 = x2, x1
        y1, y2 = y2, y1
    delta_x = x2 - x1
    delta_y = abs(y2 - y1)
    error = delta_x // 2
    y = y1
    y_step = 1 if y1 < y2 else -1
    for x in range(x1, x2 + 1):
        yield (y, x) if steep else (x, y)
        error -= delta_y
        if error < 0:
            y += y_step
            error += delta_x


def _inside_polygon(px: float, py: float, points: list[list[int]]) -> bool:
    inside = False
    previous_x, previous_y = points[-1]
    for current_x, current_y in points:
        crosses = (current_y > py) != (previous_y > py)
        if crosses:
            intersection_x = (previous_x - current_x) * (py - current_y) / (previous_y - current_y) + current_x
            if px <= intersection_x:
                inside = not inside
        previous_x, previous_y = current_x, current_y
    return inside


def _blank(width: int, height: int, color: RGBA) -> Pixels:
    return [[color for _ in range(width)] for _ in range(height)]


def render_source(source: Mapping[str, Any]) -> Pixels:
    """Render a source mapping. Asset validation should run before this function in production."""
    try:
        canvas = source["canvas"]
        palette = source["palette"]
        layers = source["layers"]
        width = canvas["width"]
        height = canvas["height"]
        background = parse_color(canvas["background"], palette)
    except (KeyError, TypeError) as exc:
        raise RenderError("source is missing canvas, palette, or layers") from exc
    pixels = _blank(width, height, background)

    for layer in layers:
        operation = layer["operation"]
        color = parse_color(layer["color"], palette) if operation in {"point", "rect", "line", "ellipse", "polygon"} else None
        if operation == "point":
            pixels[layer["y"]][layer["x"]] = color
        elif operation == "rect":
            for y in range(layer["y"], layer["y"] + layer["height"]):
                for x in range(layer["x"], layer["x"] + layer["width"]):
                    pixels[y][x] = color
        elif operation == "line":
            for x, y in _line_points(layer["x1"], layer["y1"], layer["x2"], layer["y2"]):
                pixels[y][x] = color
        elif operation == "ellipse":
            cx, cy, rx, ry = layer["cx"], layer["cy"], layer["rx"], layer["ry"]
            for y in range(height):
                for x in range(width):
                    normalized = ((x + 0.5 - cx) / rx) ** 2 + ((y + 0.5 - cy) / ry) ** 2
                    if normalized <= 1.0:
                        pixels[y][x] = color
        elif operation == "polygon":
            points = layer["points"]
            for y in range(height):
                for x in range(width):
                    if _inside_polygon(x + 0.5, y + 0.5, points):
                        pixels[y][x] = color
        elif operation == "copy":
            snapshot = [row[:] for row in pixels]
            for offset_y in range(layer["height"]):
                for offset_x in range(layer["width"]):
                    pixels[layer["dest_y"] + offset_y][layer["dest_x"] + offset_x] = snapshot[layer["y"] + offset_y][
                        layer["x"] + offset_x
                    ]
        elif operation == "mirror":
            if layer["axis"] == "horizontal":
                pixels = [list(reversed(row)) for row in pixels]
            elif layer["axis"] == "vertical":
                pixels = list(reversed([row[:] for row in pixels]))
            else:
                raise RenderError(f"invalid mirror axis {layer['axis']!r}")
        else:
            raise RenderError(f"unsupported operation {operation!r}")
    return pixels
