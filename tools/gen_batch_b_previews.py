#!/usr/bin/env python3
"""Generate 4x design previews of the batch B placeholder textures for build/previews/."""
import sys

sys.path.insert(0, "tools")
from gen_batch_b_textures import fish_bait, seed_pouch, write_png


def scale4(pixels):
    out = []
    for row in pixels:
        big_row = []
        for px in row:
            big_row.extend([px] * 4)
        for _ in range(4):
            out.append(big_row)
    return out


def checkerboard(pixels, size=4):
    """Blow up 4x and checker the outer 2px margin so transparency is visible."""
    big = scale4(pixels)
    h = len(big)
    w = len(big[0])
    for y in range(h):
        for x in range(w):
            if x < 2 or y < 2 or x >= w - 2 or y >= h - 2:
                dark = ((x // 4) + (y // 4)) % 2 == 0
                big[y][x] = (70, 70, 70, 255) if dark else (120, 120, 120, 255)
    return big


if __name__ == "__main__":
    import os

    os.makedirs("build/previews", exist_ok=True)
    write_png("build/previews/seed_pouch_preview.png", checkerboard(seed_pouch()))
    write_png("build/previews/fish_bait_preview.png", checkerboard(fish_bait()))
    print("previews written")
