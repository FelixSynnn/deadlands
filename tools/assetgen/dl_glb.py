"""Minimal glTF 2.0 (.glb) reader for the asset tools: world-space triangles per primitive, with UVs, normals and material."""
import json, struct, io
import numpy as np
from PIL import Image

COMP = {5120: np.int8, 5121: np.uint8, 5122: np.int16, 5123: np.uint16, 5125: np.uint32, 5126: np.float32}
NCOMP = {"SCALAR": 1, "VEC2": 2, "VEC3": 3, "VEC4": 4, "MAT4": 16}


def load(path):
    d = open(path, "rb").read()
    n = struct.unpack("<I", d[12:16])[0]
    j = json.loads(d[20:20 + n])
    off = 20 + n
    blen = struct.unpack("<I", d[off:off + 4])[0]
    binary = d[off + 8:off + 8 + blen]
    return j, binary


def accessor(j, binary, i):
    a = j["accessors"][i]
    bv = j["bufferViews"][a["bufferView"]]
    dt = COMP[a["componentType"]]
    nc = NCOMP[a["type"]]
    start = bv.get("byteOffset", 0) + a.get("byteOffset", 0)
    stride = bv.get("byteStride", 0)
    item = np.dtype(dt).itemsize * nc
    if stride and stride != item:
        raw = np.frombuffer(binary, np.uint8, count=stride * a["count"], offset=start).reshape(a["count"], stride)[:, :item]
        arr = np.frombuffer(raw.tobytes(), dt).reshape(a["count"], nc)
    else:
        arr = np.frombuffer(binary, dt, count=a["count"] * nc, offset=start).reshape(a["count"], nc)
    if a.get("normalized"):
        arr = arr.astype(np.float32) / np.iinfo(dt).max
    return arr.astype(np.float64)


def quat(q):
    x, y, z, w = q
    return np.array([[1 - 2 * (y * y + z * z), 2 * (x * y - z * w), 2 * (x * z + y * w)],
                     [2 * (x * y + z * w), 1 - 2 * (x * x + z * z), 2 * (y * z - x * w)],
                     [2 * (x * z - y * w), 2 * (y * z + x * w), 1 - 2 * (x * x + y * y)]])


def local(node):
    if "matrix" in node:
        return np.array(node["matrix"]).reshape(4, 4).T
    m = np.eye(4)
    s = np.diag(node.get("scale", [1, 1, 1]))
    r = quat(node.get("rotation", [0, 0, 0, 1]))
    m[:3, :3] = r @ s
    m[:3, 3] = node.get("translation", [0, 0, 0])
    return m


def world_matrices(j):
    parent = {}
    for i, n in enumerate(j["nodes"]):
        for c in n.get("children", []):
            parent[c] = i
    out = {}

    def w(i):
        if i not in out:
            out[i] = (w(parent[i]) if i in parent else np.eye(4)) @ local(j["nodes"][i])
        return out[i]

    for i in range(len(j["nodes"])):
        w(i)
    return out


def image(j, binary, tex_index):
    t = j["textures"][tex_index]
    src = t.get("source")
    im = j["images"][src]
    bv = j["bufferViews"][im["bufferView"]]
    data = binary[bv.get("byteOffset", 0):bv.get("byteOffset", 0) + bv["byteLength"]]
    return Image.open(io.BytesIO(data)).convert("RGBA")


def primitives(path):
    """[(node name, material name, positions (n,3) world, normals (n,3), uvs (n,2), triangles (m,3))]"""
    j, binary = load(path)
    worlds = world_matrices(j)
    out = []
    for i, node in enumerate(j["nodes"]):
        if "mesh" not in node:
            continue
        m = worlds[i]
        normal_m = np.linalg.inv(m[:3, :3]).T
        for pr in j["meshes"][node["mesh"]]["primitives"]:
            pos = accessor(j, binary, pr["attributes"]["POSITION"])
            nor = accessor(j, binary, pr["attributes"]["NORMAL"]) if "NORMAL" in pr["attributes"] else np.zeros_like(pos)
            uv = accessor(j, binary, pr["attributes"]["TEXCOORD_0"]) if "TEXCOORD_0" in pr["attributes"] else np.zeros((len(pos), 2))
            idx = accessor(j, binary, pr["indices"]).astype(np.int64).reshape(-1, 3) if "indices" in pr else np.arange(len(pos)).reshape(-1, 3)
            wp = (m[:3, :3] @ pos.T).T + m[:3, 3]
            wn = (normal_m @ nor.T).T
            ln = np.linalg.norm(wn, axis=1, keepdims=True)
            wn = wn / np.where(ln == 0, 1, ln)
            mat = j["materials"][pr.get("material", 0)]
            out.append((node.get("name"), mat, wp, wn, uv, idx))
    return j, binary, out
