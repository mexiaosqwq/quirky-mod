"""Deterministic RGBA PNG writing and ImageMagick-backed reading."""

from __future__ import annotations

import struct
import subprocess
import zlib
from pathlib import Path
from typing import TypeAlias


RGBA: TypeAlias = tuple[int, int, int, int]
Pixels: TypeAlias = list[list[RGBA]]


class PngError(ValueError):
    """Raised when pixel data or a decoded PNG violates the RGBA contract."""


def _validate_pixels(pixels: Pixels) -> tuple[int, int]:
    if not isinstance(pixels, list) or not pixels or not isinstance(pixels[0], list) or not pixels[0]:
        raise PngError("pixels must be a non-empty rectangular matrix")
    width = len(pixels[0])
    for row in pixels:
        if not isinstance(row, list) or len(row) != width:
            raise PngError("pixels must be a non-empty rectangular matrix")
        for pixel in row:
            if (
                not isinstance(pixel, tuple)
                or len(pixel) != 4
                or any(type(channel) is not int or not 0 <= channel <= 255 for channel in pixel)
            ):
                raise PngError("every pixel must be an RGBA tuple with channels in 0..255")
    return width, len(pixels)


def _png_chunk(tag: bytes, data: bytes) -> bytes:
    checksum = zlib.crc32(tag + data) & 0xFFFFFFFF
    return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", checksum)


def write_rgba_png(path: Path, pixels: Pixels) -> None:
    """Write an 8-bit color-type-6 PNG with deterministic filter and compression settings."""
    width, height = _validate_pixels(pixels)
    raw = bytearray()
    for row in pixels:
        raw.append(0)
        for pixel in row:
            raw.extend(pixel)
    ihdr = struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0)
    data = b"\x89PNG\r\n\x1a\n"
    data += _png_chunk(b"IHDR", ihdr)
    data += _png_chunk(b"IDAT", zlib.compress(bytes(raw), 9))
    data += _png_chunk(b"IEND", b"")
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(data)


def read_rgba_png(path: Path) -> Pixels:
    """Decode any ImageMagick-supported PNG into exact 8-bit straight RGBA pixels."""
    path = Path(path).resolve()
    try:
        dimensions = subprocess.run(
            ["magick", "identify", "-format", "%w %h", str(path)],
            check=True,
            capture_output=True,
            text=True,
        ).stdout.strip()
        width_text, height_text = dimensions.split()
        width, height = int(width_text), int(height_text)
        raw = subprocess.run(
            ["magick", str(path), "-depth", "8", "rgba:-"],
            check=True,
            capture_output=True,
        ).stdout
    except (FileNotFoundError, subprocess.CalledProcessError, ValueError) as exc:
        raise PngError(f"unable to decode PNG {path}") from exc
    expected = width * height * 4
    if width <= 0 or height <= 0 or len(raw) != expected:
        raise PngError(f"decoded PNG has invalid RGBA byte count: expected {expected}, got {len(raw)}")
    pixels: Pixels = []
    offset = 0
    for _ in range(height):
        row: list[RGBA] = []
        for _ in range(width):
            row.append(tuple(raw[offset : offset + 4]))
            offset += 4
        pixels.append(row)
    return pixels
