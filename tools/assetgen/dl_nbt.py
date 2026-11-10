"""Minimal NBT writer and structure-template builder (Minecraft structure .nbt, gzip, big-endian)."""
from __future__ import annotations

import gzip
import struct
from pathlib import Path

DATA_VERSION = 4671  # Minecraft 1.21.11 (version.json world_version)

END, BYTE, SHORT, INT, LONG, FLOAT, DOUBLE, BYTE_ARRAY, STRING, LIST, COMPOUND = range(11)


class Long(int):
    """Marks an integer to be written as TAG_Long."""


def _string(s: str) -> bytes:
    data = s.encode("utf-8")
    return struct.pack(">H", len(data)) + data


def _type(v) -> int:
    if isinstance(v, bool):
        return BYTE
    if isinstance(v, Long):
        return LONG
    if isinstance(v, int):
        return INT
    if isinstance(v, float):
        return DOUBLE
    if isinstance(v, str):
        return STRING
    if isinstance(v, list):
        return LIST
    if isinstance(v, dict):
        return COMPOUND
    raise TypeError(f"unsupported NBT value {v!r}")


def _payload(v) -> bytes:
    t = _type(v)
    if t == BYTE:
        return struct.pack(">b", 1 if v else 0)
    if t == LONG:
        return struct.pack(">q", v)
    if t == INT:
        return struct.pack(">i", v)
    if t == DOUBLE:
        return struct.pack(">d", v)
    if t == STRING:
        return _string(v)
    if t == LIST:
        if not v:
            return struct.pack(">bi", END, 0)
        et = _type(v[0])
        return struct.pack(">bi", et, len(v)) + b"".join(_payload(e) for e in v)
    out = b""
    for key, value in v.items():
        out += struct.pack(">b", _type(value)) + _string(key) + _payload(value)
    return out + struct.pack(">b", END)


def write_nbt(path: Path, root: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    raw = struct.pack(">b", COMPOUND) + _string("") + _payload(root)
    path.write_bytes(gzip.compress(raw, mtime=0))


class Structure:
    """Places blocks in a box and writes a structure template. Unset positions are left untouched."""

    def __init__(self, sx: int, sy: int, sz: int) -> None:
        self.size = (sx, sy, sz)
        self.blocks: dict[tuple[int, int, int], tuple[str, tuple, dict | None]] = {}

    def set(self, x, y, z, name, props: dict | None = None, nbt: dict | None = None) -> None:
        assert 0 <= x < self.size[0] and 0 <= y < self.size[1] and 0 <= z < self.size[2], (x, y, z)
        if ":" not in name:
            name = "deadlands:" + name
        self.blocks[(x, y, z)] = (name, tuple(sorted((props or {}).items())), nbt)

    def fill(self, x1, y1, z1, x2, y2, z2, name, props: dict | None = None) -> None:
        for x in range(min(x1, x2), max(x1, x2) + 1):
            for y in range(min(y1, y2), max(y1, y2) + 1):
                for z in range(min(z1, z2), max(z1, z2) + 1):
                    self.set(x, y, z, name, props)

    def write(self, path: Path) -> None:
        palette: list[tuple[str, tuple]] = []
        index: dict[tuple[str, tuple], int] = {}
        blocks = []
        for (x, y, z), (name, props, nbt) in sorted(self.blocks.items(), key=lambda kv: (kv[0][1], kv[0][2], kv[0][0])):
            key = (name, props)
            if key not in index:
                index[key] = len(palette)
                palette.append(key)
            entry = {"pos": [x, y, z], "state": index[key]}
            if nbt is not None:
                entry["nbt"] = nbt
            blocks.append(entry)
        pal = []
        for name, props in palette:
            p = {"Name": name}
            if props:
                p["Properties"] = {k: str(v).lower() if isinstance(v, bool) else str(v) for k, v in props}
            pal.append(p)
        write_nbt(path, {
            "DataVersion": DATA_VERSION,
            "size": list(self.size),
            "palette": pal,
            "blocks": blocks,
            "entities": [],
        })
