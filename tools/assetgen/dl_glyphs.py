"""DEADLANDS stencil letterforms.

Original military-stencil glyphs drawn as polygons on a 100-unit-tall grid (y grows
downward). Stencil "bridges" (gaps) keep every counter connected to the outside, the
way a real cut-out stencil has to be. Only the letters the logo and icons need exist.
"""
from __future__ import annotations

# Each glyph: advance width, fill polygons, cut polygons (cuts are subtracted after fills).
Glyph = tuple[float, list[list[tuple[float, float]]], list[list[tuple[float, float]]]]


def _rect(x0: float, y0: float, x1: float, y1: float) -> list[tuple[float, float]]:
    return [(x0, y0), (x1, y0), (x1, y1), (x0, y1)]


GLYPHS: dict[str, Glyph] = {
    "D": (75, [
        _rect(0, 0, 22, 100),
        [(28, 0), (52, 0), (75, 23), (75, 77), (52, 100), (28, 100)],
    ], [
        [(28, 22), (44, 22), (53, 31), (53, 69), (44, 78), (28, 78)],
    ]),
    "E": (70, [
        _rect(0, 0, 22, 100),
        _rect(28, 0, 70, 22),
        _rect(28, 39, 62, 61),
        _rect(28, 78, 70, 100),
    ], []),
    "A": (75, [
        [(0, 100), (0, 25), (25, 0), (50, 0), (75, 25), (75, 100), (53, 100), (53, 68), (22, 68), (22, 100)],
    ], [
        [(22, 31), (31, 22), (44, 22), (53, 31), (53, 47), (22, 47)],
        _rect(34, -2, 41, 23),
    ]),
    "L": (66, [
        _rect(0, 0, 22, 100),
        _rect(28, 78, 66, 100),
    ], []),
    "N": (88, [
        _rect(0, 0, 22, 100),
        [(22, 0), (42, 0), (60, 82), (60, 100), (40, 100), (22, 18)],
        _rect(66, 0, 88, 100),
    ], []),
    "S": (75, [
        [(12, 0), (75, 0), (75, 22), (24, 22), (24, 39), (63, 39), (75, 51), (75, 88),
         (63, 100), (0, 100), (0, 78), (51, 78), (51, 61), (12, 61), (0, 49), (0, 12)],
    ], [
        _rect(31, 38, 36, 62),
    ]),
}

SPACING = 16.0


def text_width(text: str, spacing: float = SPACING) -> float:
    widths = [GLYPHS[c][0] for c in text]
    return sum(widths) + spacing * (len(widths) - 1)


def draw_text(draw, text: str, x: float, y: float, unit: float, spacing: float = SPACING) -> None:
    """Draw stencil text onto a PIL ImageDraw ('L' mode): fills 255, cuts 0. unit = px per grid unit."""
    cx = x
    for ch in text:
        adv, fills, cuts = GLYPHS[ch]
        for poly in fills:
            draw.polygon([(cx + px * unit, y + py * unit) for px, py in poly], fill=255)
        for poly in cuts:
            draw.polygon([(cx + px * unit, y + py * unit) for px, py in poly], fill=0)
        cx += (adv + spacing) * unit
