"""The main menu's background pool: the owner's shader screenshots (~/Downloads/photo).

Every screenshot becomes textures/gui/title/backgrounds/bg_NN.png at 1920 x 1080 (cover-cropped, no stretching),
saved without alpha and with PNG optimisation to keep the jar small. The one non-screenshot in the folder (the rat
photo, any file not named like a Minecraft screenshot) becomes backgrounds/rat.png; the menu shows it second
every time and often after that (client/ui/MenuBackgrounds). Skipped when the folder is missing.
"""
from __future__ import annotations

import re
from pathlib import Path

from PIL import Image

import dl_noise as N

SRC = Path.home() / "Downloads" / "photo"
OUT = N.ASSETS / "textures" / "gui" / "title" / "backgrounds"
SCREENSHOT = re.compile(r"^\d{4}-\d{2}-\d{2}_\d{2}\.\d{2}\.\d{2}$")
W, H = 1920, 1080


def cover(image: Image.Image) -> Image.Image:
    scale = max(W / image.width, H / image.height)
    resized = image.resize((round(image.width * scale), round(image.height * scale)), Image.LANCZOS)
    left = (resized.width - W) // 2
    top = (resized.height - H) // 2
    return resized.crop((left, top, left + W, top + H))


def build() -> None:
    if not SRC.exists():
        print("  menu backgrounds: no ~/Downloads/photo, keeping what is there")
        return
    OUT.mkdir(parents=True, exist_ok=True)
    shots = sorted(p for p in SRC.glob("*.png") if SCREENSHOT.match(p.stem))
    others = sorted(p for p in SRC.glob("*.png") if not SCREENSHOT.match(p.stem))
    for i, path in enumerate(shots, start=1):
        cover(Image.open(path).convert("RGB")).save(OUT / f"bg_{i:02d}.png", "PNG", optimize=True)
    if others:
        cover(Image.open(others[0]).convert("RGB")).save(OUT / "rat.png", "PNG", optimize=True)
    print(f"  menu backgrounds: {len(shots)} screenshots{', and the rat' if others else ''}")


if __name__ == "__main__":
    build()
