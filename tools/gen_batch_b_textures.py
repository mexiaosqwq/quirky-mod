#!/usr/bin/env python3
"""Generate 16x16 RGBA placeholder textures for Quirky batch B items (no PIL needed)."""
import struct
import zlib


def png_chunk(tag, data):
    return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)


def write_png(path, pixels):
    """pixels: list of 16 rows, each a list of 16 (r, g, b, a) tuples."""
    height = len(pixels)
    width = len(pixels[0])
    raw = b""
    for row in pixels:
        raw += b"\x00"  # filter type 0 (None)
        for (r, g, b, a) in row:
            raw += bytes((r, g, b, a))
    ihdr = struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0)
    data = b"\x89PNG\r\n\x1a\n"
    data += png_chunk(b"IHDR", ihdr)
    data += png_chunk(b"IDAT", zlib.compress(raw, 9))
    data += png_chunk(b"IEND", b"")
    with open(path, "wb") as f:
        f.write(data)


def in_ellipse(cx, cy, rx, ry, x, y):
    return ((x - cx) / rx) ** 2 + ((y - cy) / ry) ** 2 <= 1.0


def seed_pouch():
    """棕色小布袋 + 露出的种子：袋身棕色，顶部开口处浅色收口带，几点种子色点缀。"""
    bg = (0, 0, 0, 0)
    pouch = (121, 85, 58, 255)      # 棕色袋身
    pouch_dark = (94, 64, 43, 255)  # 袋身阴影
    band = (150, 111, 82, 255)      # 收口浅棕
    seed = (196, 168, 112, 255)     # 露出的麦粒
    seed_dark = (140, 108, 60, 255)
    rows = []
    for y in range(16):
        row = []
        for x in range(16):
            c = bg
            if 2 <= x <= 13 and 4 <= y <= 14:
                if in_ellipse(7.5, 13.5, 6.0, 3.2, x, y):
                    c = pouch_dark
                elif in_ellipse(7.5, 10.5, 6.4, 4.6, x, y):
                    c = pouch
                if 2 <= y <= 4 and 2 <= x <= 13:
                    if in_ellipse(7.5, 3.0, 5.9, 1.4, x, y):
                        c = band
            # 袋口露出的种子粒
            if (x == 5 and y == 3) or (x == 8 and y == 2) or (x == 10 and y == 4):
                c = seed
            if (x == 6 and y == 4) or (x == 9 and y == 3):
                c = seed_dark
            # 袋子底部一圈暗色，制造立体感
            if 13 <= y <= 14 and 3 <= x <= 12:
                c = pouch_dark
            row.append(c)
        rows.append(row)
    return rows


def fish_bait():
    """一小团棕黄色碎饵：中心棕黄，外圈深棕颗粒，带几粒淡色碎屑。"""
    bg = (0, 0, 0, 0)
    core = (168, 130, 72, 255)
    chunk = (128, 96, 54, 255)
    crumb = (196, 160, 96, 255)
    rows = []
    for y in range(16):
        row = []
        for x in range(16):
            c = bg
            if in_ellipse(7.5, 8.0, 5.6, 5.4, x, y):
                c = chunk
                if in_ellipse(7.5, 8.0, 3.6, 3.4, x, y):
                    c = core
            # 碎屑颗粒
            if (x == 3 and y == 5) or (x == 12 and y == 7) or (x == 10 and y == 12) or (x == 4 and y == 11):
                c = chunk
            if (x == 6 and y == 4) or (x == 11 and y == 6) or (x == 9 and y == 11) or (x == 5 and y == 12):
                c = crumb
            row.append(c)
        rows.append(row)
    return rows


if __name__ == "__main__":
    base = "src/main/resources/assets/quirky/textures/item"
    write_png(f"{base}/seed_pouch.png", seed_pouch())
    write_png(f"{base}/fish_bait.png", fish_bait())
    print("written")
