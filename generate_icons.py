#!/usr/bin/env python3
"""Generate Android launcher icons from icon_source.svg"""
import os
import cairosvg
from PIL import Image
import io

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
SVG_PATH = os.path.join(SCRIPT_DIR, "icon_source.svg")
RES_DIR = os.path.join(SCRIPT_DIR, "app/src/main/res")

# density -> (size_px, folder)
DENSITIES = [
    (48,  "mipmap-mdpi"),
    (72,  "mipmap-hdpi"),
    (96,  "mipmap-xhdpi"),
    (144, "mipmap-xxhdpi"),
    (192, "mipmap-xxxhdpi"),
]

with open(SVG_PATH, "rb") as f:
    svg_data = f.read()

for size, folder in DENSITIES:
    out_dir = os.path.join(RES_DIR, folder)
    os.makedirs(out_dir, exist_ok=True)

    # Render SVG to PNG at target size
    png_data = cairosvg.svg2png(bytestring=svg_data, output_width=size, output_height=size)
    img = Image.open(io.BytesIO(png_data)).convert("RGBA")

    # Square icon
    square_path = os.path.join(out_dir, "ic_launcher.png")
    img.save(square_path, "PNG", optimize=True)

    # Round icon: apply circular mask
    mask = Image.new("L", (size, size), 0)
    from PIL import ImageDraw
    draw = ImageDraw.Draw(mask)
    draw.ellipse((0, 0, size - 1, size - 1), fill=255)
    round_img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    round_img.paste(img, mask=mask)
    round_path = os.path.join(out_dir, "ic_launcher_round.png")
    round_img.save(round_path, "PNG", optimize=True)

    print(f"  {folder}: {size}×{size}px → ic_launcher.png + ic_launcher_round.png")

print("\nDone.")
