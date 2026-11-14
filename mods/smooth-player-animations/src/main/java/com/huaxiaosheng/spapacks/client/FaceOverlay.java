package com.huaxiaosheng.spapacks.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.huaxiaosheng.spapacks.SpaPacks;
import com.huaxiaosheng.spapacks.animation.cem.CemAnimation;
import com.huaxiaosheng.spapacks.animation.cem.CemFace;
import com.huaxiaosheng.spapacks.animation.cem.CemInput;
import com.huaxiaosheng.spapacks.platform.Vertices;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public final class FaceOverlay {
    private static final Set<String> VANILLA_IDS = Set.of(
        "head",
        "headwear",
        "body",
        "jacket",
        "right_arm",
        "left_arm",
        "right_sleeve",
        "left_sleeve",
        "right_leg",
        "left_leg",
        "right_pants",
        "left_pants",
        "root"
    );
    static final int STRIDE = 8;
    private static final float FORWARD_BIAS = 0.06F;
    private static final String[] CHANNELS = new String[]{"visible", "tx", "ty", "tz", "rx", "ry", "rz", "sx", "sy", "sz"};
    private static CemAnimation animation;
    private static List<FaceOverlay.Part> roots;
    private static String source;
    private static boolean warned;
    private static final Map<AbstractClientPlayer, FaceOverlay.State> STATES = new WeakHashMap<>();
    private static final FloatArrayList BAKE = new FloatArrayList(2048);
    private static final Vector3f V = new Vector3f();

    private FaceOverlay() {
    }

    static void reload(CemContent.Adopted pack) {
        animation = null;
        roots = null;
        source = null;
        warned = false;
        STATES.clear();
        if (pack != null) {
            try {
                JsonObject jem = JsonParser.parseString(pack.jem()).getAsJsonObject();
                if (!jem.has("models") || !jem.get("models").isJsonArray()) {
                    return;
                }

                float[] texSize = texSize(jem, 64.0F, 64.0F);
                List<FaceOverlay.Part> parsed = new ArrayList<>();

                for (JsonElement element : jem.getAsJsonArray("models")) {
                    if (element.isJsonObject()) {
                        JsonObject entry = element.getAsJsonObject();
                        if ("head".equals(str(entry, "part", null)) && !VANILLA_IDS.contains(str(entry, "id", ""))) {
                            if (entry.has("model")) {
                                String jpm = pack.siblings().apply(entry.get("model").getAsString());
                                if (jpm != null) {
                                    JsonObject model = JsonParser.parseString(jpm).getAsJsonObject();
                                    parsed.add(parse(model, texSize(model, texSize[0], texSize[1]), "", 0));
                                    continue;
                                }
                            }

                            parsed.add(parse(entry, texSize, "", 0));
                        }
                    }
                }

                if (parsed.isEmpty()) {
                    return;
                }

                for (FaceOverlay.Part root : parsed) {
                    bind(root, pack.animation());
                }

                animation = pack.animation();
                roots = parsed;
                source = pack.pack();
                SpaPacks.LOGGER.info("[cem] face overlay: {} head-mounted part tree(s) from pack {}", parsed.size(), source);
            } catch (RuntimeException e) {
                SpaPacks.LOGGER.error("[cem] cannot read the face geometry of pack {}", pack.pack(), e);
            }
        }
    }

    static boolean active() {
        return roots != null && ModConfig.on() && ModConfig.get().faceOverlay;
    }

    static float[] bake(AbstractClientPlayer player, float tickDelta, float headYaw, float headPitch) {
        if (!active()) {
            return null;
        }

        CemAnimation playing = animation;
        FaceOverlay.State state = STATES.get(player);
        if (state == null || !state.face.plays(playing)) {
            state = new FaceOverlay.State(playing);
            STATES.put(player, state);
        }

        CemBinding.capture(player, tickDelta, headYaw, headPitch, state.yaw, state.input);
        if (!state.face.sample(state.input, player.tickCount + tickDelta)) {
            return state.baked;
        }

        try {
            BAKE.clear();
            Matrix4f m = new Matrix4f().translate(0.0F, 0.0F, -0.00375F);

            for (FaceOverlay.Part root : roots) {
                walk(root, m, state.face);
            }

            state.baked = BAKE.isEmpty() ? null : BAKE.toFloatArray();
            return state.baked;
        } catch (RuntimeException e) {
            if (!warned) {
                warned = true;
                SpaPacks.LOGGER.error("[cem] the face overlay failed; it is off until the next resource reload", e);
            }

            roots = null;
            return null;
        }
    }

    static void emit(VertexConsumer vertices, Pose entry, float[] verts, int light) {
        for (int i = 0; i < verts.length; i += 8) {
            Vertices.put(
                vertices,
                entry,
                verts[i],
                verts[i + 1],
                verts[i + 2],
                -1,
                verts[i + 3],
                verts[i + 4],
                OverlayTexture.NO_OVERLAY,
                light,
                verts[i + 5],
                verts[i + 6],
                verts[i + 7]
            );
        }
    }

    private static void bind(FaceOverlay.Part part, CemAnimation animation) {
        for (int c = 0; c < CHANNELS.length; c++) {
            part.slots[c] = part.id.isEmpty() ? -1 : animation.assignedSlot(part.id + "." + CHANNELS[c]);
        }

        for (FaceOverlay.Part child : part.children) {
            bind(child, animation);
        }
    }

    private static void walk(FaceOverlay.Part p, Matrix4f parent, CemFace face) {
        int[] s = p.slots;
        if (s[0] < 0 || face.value(s[0], 1.0F) != 0.0F) {
            float tx = face.value(s[1], p.x);
            float ty = face.value(s[2], p.y);
            float tz = face.value(s[3], p.z);
            float rx = face.value(s[4], p.rx);
            float ry = face.value(s[5], p.ry);
            float rz = face.value(s[6], p.rz);
            float sx;
            float sy;
            float sz;
            if (s[7] < 0 && s[8] < 0 && s[9] >= 0) {
                sx = sy = sz = face.value(s[9], p.scale);
            } else {
                sx = face.value(s[7], p.scale);
                sy = face.value(s[8], p.scale);
                sz = face.value(s[9], p.scale);
            }

            Matrix4f m = new Matrix4f(parent).translate(tx / 16.0F, ty / 16.0F, tz / 16.0F);
            if (rx != 0.0F || ry != 0.0F || rz != 0.0F) {
                m.rotateZYX(rz, ry, rx);
            }

            if (sx != 1.0F || sy != 1.0F || sz != 1.0F) {
                m.scale(sx, sy, sz);
            }

            for (FaceOverlay.Quad q : p.quads) {
                for (int i = 0; i < 4; i++) {
                    m.transformPosition(V.set(q.xs[i] / 16.0F, q.ys[i] / 16.0F, q.zs[i] / 16.0F));
                    BAKE.add(V.x);
                    BAKE.add(V.y);
                    BAKE.add(V.z);
                    BAKE.add(q.us[i]);
                    BAKE.add(q.vs[i]);
                    m.transformDirection(V.set(q.nx, q.ny, q.nz)).normalize();
                    BAKE.add(V.x);
                    BAKE.add(V.y);
                    BAKE.add(V.z);
                }
            }

            for (FaceOverlay.Part child : p.children) {
                walk(child, m, face);
            }
        }
    }

    private static FaceOverlay.Part parse(JsonObject o, float[] texSize, String parentInvert, int depth) {
        if (depth > 64) {
            throw new IllegalArgumentException("face model nests deeper than 64 parts");
        }

        String invert = str(o, "invertAxis", parentInvert);
        boolean ix = invert.contains("x");
        boolean iy = invert.contains("y");
        boolean iz = invert.contains("z");
        float[] tr = floats(o, "translate", 3);
        float[] rot = floats(o, "rotate", 3);
        FaceOverlay.Part p = new FaceOverlay.Part(
            str(o, "id", ""),
            ix ? -tr[0] : tr[0],
            iy ? -tr[1] : tr[1],
            iz ? -tr[2] : tr[2],
            (float)Math.toRadians(ix ? -rot[0] : rot[0]),
            (float)Math.toRadians(iy ? -rot[1] : rot[1]),
            (float)Math.toRadians(iz ? -rot[2] : rot[2]),
            o.has("scale") ? o.get("scale").getAsFloat() : 1.0F
        );
        float[] ts = texSize(o, texSize[0], texSize[1]);
        String mirror = str(o, "mirrorTexture", "");
        boolean mirrorU = mirror.contains("u");
        boolean mirrorV = mirror.contains("v");
        if (o.has("boxes") && o.get("boxes").isJsonArray()) {
            for (JsonElement b : o.getAsJsonArray("boxes")) {
                if (b.isJsonObject()) {
                    box(p, b.getAsJsonObject(), ix, iy, iz, mirrorU, mirrorV, ts[0], ts[1]);
                }
            }
        }

        for (String key : new String[]{"submodels", "models"}) {
            if (o.has(key) && o.get(key).isJsonArray()) {
                for (JsonElement child : o.getAsJsonArray(key)) {
                    if (child.isJsonObject()) {
                        p.children.add(parse(child.getAsJsonObject(), ts, invert, depth + 1));
                    }
                }
            }
        }

        return p;
    }

    private static void box(FaceOverlay.Part p, JsonObject b, boolean ix, boolean iy, boolean iz, boolean mirrorU, boolean mirrorV, float texW, float texH) {
        float[] c = floats(b, "coordinates", 6);
        float bx = ix ? -c[0] - c[3] : c[0];
        float by = iy ? -c[1] - c[4] : c[1];
        float bz = iz ? -c[2] - c[5] : c[2];
        float add = b.has("sizeAdd") ? b.get("sizeAdd").getAsFloat() : 0.0F;
        float x1 = bx - add;
        float y1 = by - add;
        float z1 = bz - add;
        float x2 = bx + c[3] + add;
        float y2 = by + c[4] + add;
        float z2 = bz + c[5] + add;
        float[] v1 = new float[]{x1, y1, z1};
        float[] v2 = new float[]{x2, y1, z1};
        float[] v3 = new float[]{x2, y2, z1};
        float[] v4 = new float[]{x1, y2, z1};
        float[] v5 = new float[]{x1, y1, z2};
        float[] v6 = new float[]{x2, y1, z2};
        float[] v7 = new float[]{x2, y2, z2};
        float[] v8 = new float[]{x1, y2, z2};
        float[] uv;
        if ((uv = floats4(b, "uvNorth")) != null) {
            p.quads.add(new FaceOverlay.Quad(new float[][]{v2, v1, v4, v3}, uv, mirrorU, mirrorV, texW, texH, 0.0F, 0.0F, -1.0F));
        }

        if ((uv = floats4(b, "uvSouth")) != null) {
            p.quads.add(new FaceOverlay.Quad(new float[][]{v5, v6, v7, v8}, uv, mirrorU, mirrorV, texW, texH, 0.0F, 0.0F, 1.0F));
        }

        if ((uv = floats4(b, "uvWest")) != null) {
            p.quads
                .add(
                    new FaceOverlay.Quad(
                        mirrorU ? new float[][]{v1, v5, v8, v4} : new float[][]{v6, v2, v3, v7},
                        uv,
                        mirrorU,
                        mirrorV,
                        texW,
                        texH,
                        mirrorU ? -1.0F : 1.0F,
                        0.0F,
                        0.0F
                    )
                );
        }

        if ((uv = floats4(b, "uvEast")) != null) {
            p.quads
                .add(
                    new FaceOverlay.Quad(
                        mirrorU ? new float[][]{v6, v2, v3, v7} : new float[][]{v1, v5, v8, v4},
                        uv,
                        mirrorU,
                        mirrorV,
                        texW,
                        texH,
                        mirrorU ? 1.0F : -1.0F,
                        0.0F,
                        0.0F
                    )
                );
        }

        if ((uv = floats4(b, "uvUp")) != null) {
            p.quads
                .add(
                    new FaceOverlay.Quad(
                        mirrorV ? new float[][]{v8, v7, v3, v4} : new float[][]{v1, v2, v6, v5},
                        uv,
                        mirrorU,
                        mirrorV,
                        texW,
                        texH,
                        0.0F,
                        mirrorV ? 1.0F : -1.0F,
                        0.0F
                    )
                );
        }

        if ((uv = floats4(b, "uvDown")) != null) {
            p.quads
                .add(
                    new FaceOverlay.Quad(
                        mirrorV ? new float[][]{v1, v2, v6, v5} : new float[][]{v8, v7, v3, v4},
                        uv,
                        mirrorU,
                        mirrorV,
                        texW,
                        texH,
                        0.0F,
                        mirrorV ? -1.0F : 1.0F,
                        0.0F
                    )
                );
        }
    }

    private static String str(JsonObject o, String key, String fallback) {
        return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString() : fallback;
    }

    private static float[] texSize(JsonObject o, float w, float h) {
        float[] f = floats(o, "textureSize", 2);
        return f[0] > 0.0F && f[1] > 0.0F ? f : new float[]{w, h};
    }

    private static float[] floats(JsonObject o, String key, int n) {
        if (o.has(key) && o.get(key).isJsonArray()) {
            JsonArray a = o.getAsJsonArray(key);
            float[] f = new float[Math.max(n, a.size())];

            for (int i = 0; i < a.size(); i++) {
                f[i] = a.get(i).getAsFloat();
            }

            return f;
        } else {
            return new float[n];
        }
    }

    private static float[] floats4(JsonObject o, String key) {
        return o.has(key) ? floats(o, key, 4) : null;
    }

    private static final class Part {
        final String id;
        final float x;
        final float y;
        final float z;
        final float rx;
        final float ry;
        final float rz;
        final float scale;
        final List<FaceOverlay.Quad> quads = new ArrayList<>(2);
        final List<FaceOverlay.Part> children = new ArrayList<>(4);
        final int[] slots = new int[FaceOverlay.CHANNELS.length];

        Part(String id, float x, float y, float z, float rx, float ry, float rz, float scale) {
            this.id = id;
            this.x = x;
            this.y = y;
            this.z = z;
            this.rx = rx;
            this.ry = ry;
            this.rz = rz;
            this.scale = scale;
        }
    }

    private static final class Quad {
        final float[] xs = new float[4];
        final float[] ys = new float[4];
        final float[] zs = new float[4];
        final float[] us = new float[4];
        final float[] vs = new float[4];
        final float nx;
        final float ny;
        final float nz;

        Quad(float[][] verts, float[] uv, boolean mirrorU, boolean mirrorV, float texW, float texH, float nx, float ny, float nz) {
            float u1 = mirrorU ? uv[2] : uv[0];
            float v1 = mirrorV ? uv[3] : uv[1];
            float u2 = mirrorU ? uv[0] : uv[2];
            float v2 = mirrorV ? uv[1] : uv[3];
            float[] cu = new float[]{u2, u1, u1, u2};
            float[] cv = new float[]{v1, v1, v2, v2};

            for (int i = 0; i < 4; i++) {
                this.xs[i] = verts[i][0];
                this.ys[i] = verts[i][1];
                this.zs[i] = verts[i][2];
                this.us[i] = cu[i] / texW;
                this.vs[i] = cv[i] / texH;
            }

            this.nx = nx;
            this.ny = ny;
            this.nz = nz;
        }
    }

    private static final class State {
        final CemFace face;
        final CemInput input = new CemInput();
        final CemBinding.TickYaw yaw = new CemBinding.TickYaw();
        float[] baked;

        State(CemAnimation animation) {
            this.face = new CemFace(animation);
        }
    }
}
