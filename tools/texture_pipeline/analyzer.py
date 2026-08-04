"""Compute exact pixel facts and enforce Quirky texture quality gates."""

from __future__ import annotations

import hashlib
from collections import Counter
from collections.abc import Mapping
from pathlib import Path
from typing import Any

from .asset import AssetSpec
from .png_io import Pixels, RGBA


def _rgba_hex(pixel: RGBA) -> str:
    return "#" + "".join(f"{channel:02X}" for channel in pixel)


def analyze_pixels(pixels: Pixels, path: Path | None = None) -> dict[str, Any]:
    """Return deterministic facts for a non-empty rectangular RGBA matrix."""
    if not pixels or not pixels[0] or any(len(row) != len(pixels[0]) for row in pixels):
        raise ValueError("pixels must be a non-empty rectangular matrix")
    width, height = len(pixels[0]), len(pixels)
    flattened = [pixel for row in pixels for pixel in row]
    counts = Counter(flattened)
    colors = [
        {"rgba": _rgba_hex(pixel), "count": count}
        for pixel, count in sorted(counts.items(), key=lambda entry: (-entry[1], _rgba_hex(entry[0])))
    ]
    occupied = [(x, y) for y, row in enumerate(pixels) for x, pixel in enumerate(row) if pixel[3] > 0]
    if occupied:
        left = min(x for x, _ in occupied)
        right = max(x for x, _ in occupied)
        top = min(y for _, y in occupied)
        bottom = max(y for _, y in occupied)
        bounds: dict[str, int] | None = {
            "x": left,
            "y": top,
            "width": right - left + 1,
            "height": bottom - top + 1,
        }
        margins: dict[str, int] | None = {
            "top": top,
            "right": width - right - 1,
            "bottom": height - bottom - 1,
            "left": left,
        }
    else:
        bounds = None
        margins = None
    digest = hashlib.sha256(Path(path).read_bytes()).hexdigest() if path is not None else None
    return {
        "sha256": digest,
        "width": width,
        "height": height,
        "colorMode": "RGBA",
        "paletteSize": len(counts),
        "colors": colors,
        "transparentPixels": sum(count for pixel, count in counts.items() if pixel[3] == 0),
        "opaquePixels": sum(count for pixel, count in counts.items() if pixel[3] == 255),
        "partialAlphaPixels": sum(count for pixel, count in counts.items() if 0 < pixel[3] < 255),
        "occupiedBounds": bounds,
        "blankMargins": margins,
    }


def validate_candidate(spec: AssetSpec, facts: Mapping[str, Any]) -> dict[str, Any]:
    """Apply manifest gates to machine facts without making visual judgments."""
    failures: list[str] = []
    warnings: list[str] = []
    actual_dimensions = (facts.get("width"), facts.get("height"))
    expected_dimensions = (spec.output.width, spec.output.height)
    if actual_dimensions != expected_dimensions:
        failures.append(f"dimensions {actual_dimensions} do not match expected {expected_dimensions}")
    palette_size = facts.get("paletteSize")
    if type(palette_size) is not int or palette_size > spec.quality_gates.maximum_palette_size:
        failures.append(
            f"palette size {palette_size} exceeds maximum {spec.quality_gates.maximum_palette_size}"
        )
    partial_alpha = facts.get("partialAlphaPixels")
    if not spec.quality_gates.allow_partial_alpha and partial_alpha != 0:
        failures.append(f"partial alpha pixels are forbidden, found {partial_alpha}")
    bounds = facts.get("occupiedBounds")
    if bounds is None:
        failures.append("image is empty")
    else:
        margins = facts.get("blankMargins")
        if isinstance(margins, Mapping) and any(value == 0 for value in margins.values()):
            warnings.append("visible content touches at least one canvas edge")
    return {
        "status": "fail" if failures else "pass",
        "failures": failures,
        "warnings": warnings,
    }
