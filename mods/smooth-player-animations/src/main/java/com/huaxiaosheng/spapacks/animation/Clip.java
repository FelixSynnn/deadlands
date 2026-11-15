package com.huaxiaosheng.spapacks.animation;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.huaxiaosheng.spapacks.anim.play.PlayableClip;
import com.huaxiaosheng.spapacks.util.Json;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map.Entry;

public final class Clip implements PlayableClip {
    public static final String FORMAT = "spa_clip/1";
    public static final String SPACE = "torso_chain_v1";
    public static final String[] BONE_NAMES = bones();
    public static final int RIGHT_ITEM = 13;
    public static final int LEFT_ITEM = 14;
    public static final int BONES = 15;
    private final String id;
    private final double fps;
    private final int end;
    private final boolean loop;
    private double stride;
    private boolean additive;
    private boolean hold;
    private final int[][] locationFrames = new int[15][];
    private final float[][] locationValues = new float[15][];
    private final int[][] rotationFrames = new int[15][];
    private final float[][] rotationValues = new float[15][];
    private final List<Clip.Marker> markers = new ArrayList<>();
    private static final ThreadLocal<float[]> SCRATCH = ThreadLocal.withInitial(() -> new float[4]);

    private Clip(String id, double fps, int end, boolean loop) {
        this.id = id;
        this.fps = fps;
        this.end = end;
        this.loop = loop;
    }

    public String id() {
        return this.id;
    }

    public double fps() {
        return this.fps;
    }

    public int end() {
        return this.end;
    }

    public boolean loop() {
        return this.loop;
    }

    @Override
    public boolean loops() {
        return this.loop;
    }

    @Override
    public double seconds() {
        return this.end / this.fps;
    }

    @Override
    public double stride() {
        return this.stride;
    }

    @Override
    public boolean additive() {
        return this.additive;
    }

    @Override
    public boolean holds() {
        return this.hold;
    }

    @Override
    public int keyed(int bone) {
        return (this.locationFrames[bone] != null ? 1 : 0) | (this.rotationFrames[bone] != null ? 2 : 0);
    }

    public boolean keys(int bone) {
        return this.locationFrames[bone] != null || this.rotationFrames[bone] != null;
    }

    public List<Clip.Marker> markers() {
        return this.markers;
    }

    public void sample(double frame, float[][] out) {
        this.write(frame, out, 13, false);
    }

    public void overlay(double frame, float[][] out) {
        this.write(frame, out, Math.min(15, out.length), true);
    }

    @Override
    public void lay(double time, boolean phased, float[][] out) {
        this.overlay(phased ? time * this.end : time * this.fps, out);
    }

    private void write(double frame, float[][] out, int bones, boolean keep) {
        double f = this.loop ? frame - Math.floor(frame / this.end) * this.end : Math.max(0.0, Math.min(frame, this.end));

        for (int b = 0; b < bones; b++) {
            float[] o = out[b];
            if (this.locationFrames[b] == null) {
                if (!keep) {
                    o[0] = o[1] = o[2] = 0.0F;
                }
            } else {
                interpolate(this.locationFrames[b], this.locationValues[b], 3, f, o, 0);
            }

            if (this.rotationFrames[b] == null) {
                if (!keep) {
                    o[3] = o[4] = o[5] = 0.0F;
                    o[6] = 1.0F;
                }
            } else {
                interpolate(this.rotationFrames[b], this.rotationValues[b], 4, f, SCRATCH.get(), 0);
                float[] q = SCRATCH.get();
                float n = (float)Math.sqrt(q[0] * q[0] + q[1] * q[1] + q[2] * q[2] + q[3] * q[3]);
                n = n > 1.0E-8F ? 1.0F / n : 0.0F;
                o[3] = q[1] * n;
                o[4] = q[2] * n;
                o[5] = q[3] * n;
                o[6] = n == 0.0F ? 1.0F : q[0] * n;
            }
        }
    }

    private static void interpolate(int[] frames, float[] values, int size, double f, float[] out, int offset) {
        int n = frames.length;
        if (n == 1 || f <= frames[0]) {
            System.arraycopy(values, 0, out, offset, size);
        } else if (f >= frames[n - 1]) {
            System.arraycopy(values, (n - 1) * size, out, offset, size);
        } else {
            int lo = 0;
            int hi = n - 1;

            while (hi - lo > 1) {
                int mid = lo + hi >>> 1;
                if (frames[mid] <= f) {
                    lo = mid;
                } else {
                    hi = mid;
                }
            }

            float t = (float)((f - frames[lo]) / (frames[hi] - frames[lo]));
            int a = lo * size;
            int b = hi * size;

            for (int k = 0; k < size; k++) {
                out[offset + k] = values[a + k] + (values[b + k] - values[a + k]) * t;
            }
        }
    }

    public static Clip parse(String id, Reader reader) {
        JsonElement root = JsonParser.parseReader(reader);
        if (!root.isJsonObject()) {
            throw new IllegalArgumentException("not a JSON object");
        }

        JsonObject o = root.getAsJsonObject();
        String format = string(o, "format");
        if (!"spa_clip/1".equals(format)) {
            throw new IllegalArgumentException("format must be \"spa_clip/1\", got " + format);
        }

        String space = o.has("space") ? string(o, "space") : "torso_chain_v1";
        if (!"torso_chain_v1".equals(space)) {
            throw new IllegalArgumentException("space must be \"torso_chain_v1\" (the 身体层级修正 rig), got " + space);
        }

        double fps = o.has("fps") ? o.get("fps").getAsDouble() : 60.0;
        int end = o.get("end").getAsInt();
        if (fps > 0.0 && end >= 1) {
            Clip clip = new Clip(id, fps, end, o.has("loop") && o.get("loop").getAsBoolean());
            clip.stride = o.has("stride") ? o.get("stride").getAsDouble() : 0.0;
            clip.additive = o.has("additive") && o.get("additive").getAsBoolean();
            clip.hold = !clip.loop && o.has("hold") && o.get("hold").getAsBoolean();
            if (clip.stride >= 0.0 && !Double.isInfinite(clip.stride)) {
                JsonObject bones = o.getAsJsonObject("bones");
                if (bones == null) {
                    throw new IllegalArgumentException("no \"bones\"");
                }

                for (Entry<String, JsonElement> e : bones.entrySet()) {
                    int b = control(e.getKey());
                    JsonObject channels = e.getValue().getAsJsonObject();
                    if (channels.has("location")) {
                        float[][] keys = keys(channels.getAsJsonArray("location"), 3, e.getKey() + ".location", end);
                        clip.locationFrames[b] = frames(keys);
                        clip.locationValues[b] = values(keys, 3);
                    }

                    if (channels.has("rotation")) {
                        float[][] keys = keys(channels.getAsJsonArray("rotation"), 4, e.getKey() + ".rotation", end);

                        for (float[] k : keys) {
                            if (k[1] * k[1] + k[2] * k[2] + k[3] * k[3] + k[4] * k[4] < 1.0E-6F) {
                                throw new IllegalArgumentException(e.getKey() + ".rotation: zero quaternion at frame " + (int)k[0]);
                            }
                        }

                        clip.rotationFrames[b] = frames(keys);
                        clip.rotationValues[b] = values(keys, 4);
                    }
                }

                JsonArray markers = o.getAsJsonArray("markers");
                if (markers != null) {
                    for (JsonElement m : markers) {
                        JsonArray a = m.getAsJsonArray();
                        clip.markers.add(new Clip.Marker(a.get(0).getAsInt(), a.get(1).getAsString()));
                    }
                }

                return clip;
            } else {
                throw new IllegalArgumentException("stride must be a speed >= 0");
            }
        } else {
            throw new IllegalArgumentException("fps must be > 0 and end >= 1");
        }
    }

    private static String[] bones() {
        String[] names = Arrays.copyOf(Pose.CONTROL_NAMES, 15);
        names[13] = "right_item";
        names[14] = "left_item";
        return names;
    }

    private static String string(JsonObject o, String key) {
        return Json.string(o, key);
    }

    private static int control(String name) {
        for (int b = 0; b < 15; b++) {
            if (BONE_NAMES[b].equals(name)) {
                return b;
            }
        }

        throw new IllegalArgumentException("unknown bone \"" + name + "\"; the bones are " + String.join(", ", BONE_NAMES));
    }

    private static float[][] keys(JsonArray array, int size, String where, int end) {
        if (array.isEmpty()) {
            throw new IllegalArgumentException(where + ": no keys");
        }

        float[][] keys = new float[array.size()][];
        int last = Integer.MIN_VALUE;

        for (int i = 0; i < keys.length; i++) {
            JsonArray k = array.get(i).getAsJsonArray();
            if (k.size() != size + 1) {
                throw new IllegalArgumentException(where + ": each key is [frame, " + size + " values]");
            }

            float[] v = new float[size + 1];

            for (int j = 0; j <= size; j++) {
                v[j] = k.get(j).getAsFloat();
                if (!Float.isFinite(v[j])) {
                    throw new IllegalArgumentException(where + ": not a number in key " + i);
                }
            }

            int frame = (int)v[0];
            if (frame != v[0] || frame <= last || frame < 0 || frame > end) {
                throw new IllegalArgumentException(where + ": frames must be whole, increasing, within 0.." + end);
            }

            last = frame;
            keys[i] = v;
        }

        return keys;
    }

    private static int[] frames(float[][] keys) {
        int[] f = new int[keys.length];

        for (int i = 0; i < keys.length; i++) {
            f[i] = (int)keys[i][0];
        }

        return f;
    }

    private static float[] values(float[][] keys, int size) {
        float[] v = new float[keys.length * size];

        for (int i = 0; i < keys.length; i++) {
            System.arraycopy(keys[i], 1, v, i * size, size);
        }

        return v;
    }

    public record Marker(int frame, String name) {
    }
}
