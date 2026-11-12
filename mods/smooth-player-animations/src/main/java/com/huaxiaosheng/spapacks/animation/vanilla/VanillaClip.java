package com.huaxiaosheng.spapacks.animation.vanilla;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.huaxiaosheng.spapacks.anim.play.PlayableClip;
import com.huaxiaosheng.spapacks.anim.pose.Poses;
import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import org.joml.Quaternionf;

public final class VanillaClip implements PlayableClip {
    public static final String[] BONES = new String[]{"body", "torso", "head", "right_arm", "left_arm", "right_leg", "left_leg", "right_item", "left_item"};
    public static final int CHANNEL_POSITION = 0;
    public static final int CHANNEL_ROTATION = 1;
    public static final int CHANNEL_SCALE = 2;
    private final String name;
    private final float length;
    private final VanillaClip.Loop loop;
    private final VanillaClip.Track[][][] tracks = new VanillaClip.Track[BONES.length][3][];
    private static final ThreadLocal<Quaternionf> Q = ThreadLocal.withInitial(Quaternionf::new);

    private VanillaClip(String name, float length, VanillaClip.Loop loop) {
        this.name = name;
        this.length = length;
        this.loop = loop;
    }

    public String name() {
        return this.name;
    }

    @Override
    public double seconds() {
        return this.length / 20.0;
    }

    public VanillaClip.Loop loop() {
        return this.loop;
    }

    @Override
    public boolean loops() {
        return this.loop == VanillaClip.Loop.LOOP;
    }

    @Override
    public boolean holds() {
        return this.loop == VanillaClip.Loop.HOLD;
    }

    @Override
    public int keyed(int bone) {
        VanillaClip.Track[][] t = this.tracks[bone];
        return (t[0] != null ? 1 : 0) | (t[1] != null ? 2 : 0) | (t[2] != null ? 4 : 0);
    }

    public void overlay(double seconds, float[][] pose) {
        float tick = (float)(seconds * 20.0);
        if (this.loop == VanillaClip.Loop.LOOP && this.length > 0.0F) {
            tick -= (float)Math.floor(tick / this.length) * this.length;
        } else {
            tick = Math.max(0.0F, Math.min(tick, this.length));
        }

        for (int b = 0; b < BONES.length; b++) {
            VanillaClip.Track[][] t = this.tracks[b];
            float[] o = pose[b];
            if (t[0] != null) {
                o[0] = value(t[0][0], tick, 0.0F);
                o[1] = value(t[0][1], tick, 0.0F);
                o[2] = value(t[0][2], tick, 0.0F);
            }

            if (t[1] != null) {
                Quaternionf q = Q.get().rotationZYX(value(t[1][2], tick, 0.0F), value(t[1][1], tick, 0.0F), value(t[1][0], tick, 0.0F));
                Poses.setRotation(o, q);
            }

            if (t[2] != null && o.length >= 10) {
                o[7] = value(t[2][0], tick, 1.0F);
                o[8] = value(t[2][1], tick, 1.0F);
                o[9] = value(t[2][2], tick, 1.0F);
            }
        }
    }

    @Override
    public void lay(double time, boolean phased, float[][] out) {
        this.overlay(phased ? time * this.seconds() : time, out);
    }

    private static float value(VanillaClip.Track track, float tick, float neutral) {
        return track == null ? neutral : track.value(tick);
    }

    public static Map<String, VanillaClip> parseFile(Reader reader) {
        JsonElement root = JsonParser.parseReader(reader);
        if (root.isJsonObject() && root.getAsJsonObject().has("animations")) {
            Map<String, VanillaClip> out = new LinkedHashMap<>();

            for (Entry<String, JsonElement> e : root.getAsJsonObject().getAsJsonObject("animations").entrySet()) {
                try {
                    out.put(e.getKey(), parse(e.getKey(), e.getValue().getAsJsonObject()));
                } catch (RuntimeException ex) {
                    throw new IllegalArgumentException(e.getKey() + ": " + ex.getMessage(), ex);
                }
            }

            return out;
        } else {
            throw new IllegalArgumentException("not a Bedrock animation file (no \"animations\")");
        }
    }

    static VanillaClip parse(String name, JsonObject o) {
        JsonObject bones = o.has("bones") ? o.getAsJsonObject("bones") : new JsonObject();
        List<Object[]> parsed = new ArrayList<>();
        float last = 0.0F;

        for (Entry<String, JsonElement> e : bones.entrySet()) {
            int bone = bone(e.getKey());
            if (bone >= 0 && e.getValue().isJsonObject()) {
                JsonObject channels = e.getValue().getAsJsonObject();
                String[] keys = new String[]{"position", "rotation", "scale"};

                for (int c = 0; c < 3; c++) {
                    if (channels.has(keys[c])) {
                        try {
                            VanillaClip.Track[] axes = tracks(channels.get(keys[c]), c);
                            if (axes != null) {
                                parsed.add(new Object[]{bone, c, axes});

                                for (VanillaClip.Track t : axes) {
                                    float sum = 0.0F;

                                    for (float l : t.length) {
                                        sum += l;
                                    }

                                    last = Math.max(last, sum);
                                }
                            }
                        } catch (RuntimeException ex) {
                            throw new IllegalArgumentException(e.getKey() + "." + keys[c] + ": " + ex.getMessage(), ex);
                        }
                    }
                }
            }
        }

        float length = o.has("animation_length") ? o.get("animation_length").getAsFloat() * 20.0F : (last == 0.0F ? Float.MAX_VALUE : last);
        JsonElement l = o.get("loop");
        VanillaClip.Loop loop = l instanceof JsonPrimitive p && p.isBoolean() && p.getAsBoolean()
            ? VanillaClip.Loop.LOOP
            : (l instanceof JsonPrimitive p2 && p2.isString() && p2.getAsString().equals("hold_on_last_frame") ? VanillaClip.Loop.HOLD : VanillaClip.Loop.ONCE);
        VanillaClip clip = new VanillaClip(name, length, loop);

        for (Object[] p : parsed) {
            clip.tracks[(Integer)p[0]][(Integer)p[1]] = (VanillaClip.Track[])p[2];
        }

        return clip;
    }

    static int bone(String name) {
        String n = name.replaceAll("([A-Z])", "_$1").toLowerCase(Locale.ROOT);

        for (int i = 0; i < BONES.length; i++) {
            if (BONES[i].equals(n)) {
                return i;
            }
        }

        return -1;
    }

    private static VanillaClip.Track[] tracks(JsonElement element, int channel) {
        List<Object[]> frames = new ArrayList<>();
        if (element instanceof JsonPrimitive p) {
            JsonArray a = new JsonArray();
            a.add(p);
            a.add(p);
            a.add(p);
            frames.add(new Object[]{0.0F, a, null});
        } else if (element instanceof JsonArray a) {
            frames.add(new Object[]{0.0F, a, null});
        } else {
            if (!(element instanceof JsonObject obj)) {
                throw new IllegalArgumentException("unexpected " + element);
            }

            if (obj.has("vector")) {
                frames.add(new Object[]{0.0F, obj.getAsJsonArray("vector"), obj});
            } else {
                for (Entry<String, JsonElement> e : obj.entrySet()) {
                    float time;
                    try {
                        time = Float.parseFloat(e.getKey());
                    } catch (NumberFormatException ex) {
                        time = 0.0F;
                    }

                    JsonElement v = e.getValue();
                    if (v instanceof JsonObject k && !k.has("vector")) {
                        if (k.has("pre")) {
                            JsonObject pre = k.has("easing") ? new JsonObject() : null;
                            if (pre != null) {
                                pre.add("easing", k.get("easing"));
                                if (k.has("easingArgs")) {
                                    pre.add("easingArgs", k.get("easingArgs"));
                                }
                            }

                            frames.add(new Object[]{time == 0.0F ? time : time - 0.001F, vector(k.get("pre")), pre});
                        }

                        if (k.has("post")) {
                            JsonObject post = null;
                            if (k.has("lerp_mode")) {
                                post = new JsonObject();
                                post.add("easing", k.get("lerp_mode"));
                            }

                            frames.add(new Object[]{time, vector(k.get("post")), post});
                        } else if (!k.has("pre")) {
                            throw new IllegalArgumentException("keyframe " + e.getKey() + ": expected a vector, pre or post");
                        }
                    } else {
                        frames.add(
                            new Object[]{time, v instanceof JsonObject k2 ? k2.getAsJsonArray("vector") : vector(v), v instanceof JsonObject k3 ? k3 : null}
                        );
                    }
                }
            }
        }

        if (frames.isEmpty()) {
            return null;
        }

        VanillaClip.Track[] axes = new VanillaClip.Track[3];
        String[] axisNames = new String[]{"X", "Y", "Z"};

        for (int a = 0; a < 3; a++) {
            VanillaClip.Track t = new VanillaClip.Track(frames.size());
            float prevTime = 0.0F;
            float prevValue = 0.0F;

            for (int i = 0; i < frames.size(); i++) {
                Object[] f = frames.get(i);
                float time = (Float)f[0];
                JsonArray vec = (JsonArray)f[1];
                JsonObject entry = (JsonObject)f[2];
                if (vec.size() <= a) {
                    throw new IllegalArgumentException("a keyframe needs three values");
                }

                JsonElement raw = vec.get(a);
                if (raw instanceof JsonPrimitive rp && rp.isString() && rp.getAsString().trim().equals("pal.disabled")) {
                    return null;
                }

                float value = number(raw, channel == 2 ? 1.0F : 0.0F);
                if (channel == 1) {
                    value = (float)Math.toRadians(value);
                }

                t.length[i] = (time - (i == 0 ? 0.0F : prevTime)) * 20.0F;
                t.start[i] = i == 0 ? value : prevValue;
                t.end[i] = value;
                VanillaClip.Easing easing = entry != null && entry.has("easing") ? VanillaClip.Easing.of(entry.get("easing")) : VanillaClip.Easing.LINEAR;
                if (entry != null && entry.has("easing" + axisNames[a])) {
                    easing = VanillaClip.Easing.of(entry.get("easing" + axisNames[a]));
                }

                t.easing[i] = easing;
                t.arg[i] = Float.NaN;
                if ((entry == null ? null : (entry.has("easingArgs" + axisNames[a]) ? entry.get("easingArgs" + axisNames[a]) : entry.get("easingArgs"))) instanceof JsonArray arr
                    && !arr.isEmpty()) {
                    t.arg[i] = arr.get(0).getAsFloat();
                }

                prevTime = time;
                prevValue = value;
            }

            if (frames.size() == 1) {
                t.easing[0] = VanillaClip.Easing.LINEAR;
            }

            for (int i = 0; i < frames.size(); i++) {
                t.before[i] = i == 0 ? t.start[i] : t.end[i - 1];
                t.after[i] = i + 1 >= frames.size() ? t.end[i] : t.end[i + 1];
            }

            axes[a] = t;
        }

        return axes;
    }

    private static JsonArray vector(JsonElement e) {
        if (e instanceof JsonArray a) {
            return a;
        } else if (e instanceof JsonObject o && o.has("vector")) {
            return o.getAsJsonArray("vector");
        } else if (e instanceof JsonPrimitive p) {
            JsonArray a = new JsonArray();
            a.add(p);
            a.add(0);
            a.add(0);
            return a;
        } else {
            throw new IllegalArgumentException("expected a vector, got " + e);
        }
    }

    private static float number(JsonElement e, float fallback) {
        if (e instanceof JsonPrimitive p) {
            if (p.isNumber()) {
                return p.getAsFloat();
            }

            try {
                return Float.parseFloat(p.getAsString().trim());
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("MoLang expressions are not supported in vanilla clips: \"" + p.getAsString() + "\"");
            }
        } else {
            return fallback;
        }
    }

    enum Easing {
        LINEAR,
        CONSTANT,
        STEP,
        CATMULLROM,
        EASEINSINE,
        EASEOUTSINE,
        EASEINOUTSINE,
        EASEINQUAD,
        EASEOUTQUAD,
        EASEINOUTQUAD,
        EASEINCUBIC,
        EASEOUTCUBIC,
        EASEINOUTCUBIC,
        EASEINQUART,
        EASEOUTQUART,
        EASEINOUTQUART,
        EASEINQUINT,
        EASEOUTQUINT,
        EASEINOUTQUINT,
        EASEINEXPO,
        EASEOUTEXPO,
        EASEINOUTEXPO,
        EASEINCIRC,
        EASEOUTCIRC,
        EASEINOUTCIRC,
        EASEINBACK,
        EASEOUTBACK,
        EASEINOUTBACK,
        EASEINELASTIC,
        EASEOUTELASTIC,
        EASEINOUTELASTIC,
        EASEINBOUNCE,
        EASEOUTBOUNCE,
        EASEINOUTBOUNCE;

        static VanillaClip.Easing of(JsonElement e) {
            if (e instanceof JsonPrimitive p && p.isString()) {
                try {
                    return valueOf(p.getAsString().toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException var3) {
                }
            }

            return LINEAR;
        }

        float apply(float t, float arg) {
            String n = this.name();
            if (this == LINEAR || this == CATMULLROM) {
                return t;
            } else if (this == CONSTANT) {
                return 0.0F;
            } else if (this == STEP) {
                return step(t, Float.isNaN(arg) ? 2.0F : arg);
            } else {
                String curve = n.startsWith("EASEINOUT") ? n.substring(9) : (n.startsWith("EASEOUT") ? n.substring(7) : n.substring(6));
                if (n.startsWith("EASEINOUT")) {
                    return t < 0.5F ? curve(curve, t * 2.0F, arg) / 2.0F : 1.0F - curve(curve, (1.0F - t) * 2.0F, arg) / 2.0F;
                } else {
                    return n.startsWith("EASEOUT") ? 1.0F - curve(curve, 1.0F - t, arg) : curve(curve, t, arg);
                }
            }
        }

        private static float curve(String name, float n, float arg) {
            switch (name) {
                case "SINE":
                    return 1.0F - (float)Math.cos(n * Math.PI / 2.0);
                case "QUAD":
                    return n * n;
                case "CUBIC":
                    return n * n * n;
                case "QUART":
                    return (float)Math.pow(n, 4.0);
                case "QUINT":
                    return (float)Math.pow(n, 5.0);
                case "EXPO":
                    return (float)Math.pow(2.0, 10.0 * (n - 1.0));
                case "CIRC":
                    return 1.0F - (float)Math.sqrt(1.0F - n * n);
                case "BACK": {
                    float k = Float.isNaN(arg) ? 1.70158F : arg * 1.70158F;
                    return n * n * ((k + 1.0F) * n - k);
                }
                case "ELASTIC": {
                    float k = Float.isNaN(arg) ? 1.0F : arg;
                    return 1.0F - (float)(Math.pow(Math.cos(n * Math.PI / 2.0), 3.0) * Math.cos(n * k * Math.PI));
                }
                case "BOUNCE": {
                    float k = Float.isNaN(arg) ? 0.5F : arg;
                    float one = 7.5625F * n * n;
                    float two = 30.25F * k * (float)Math.pow(n - 0.54545456F, 2.0) + 1.0F - k;
                    float three = 121.0F * k * k * (float)Math.pow(n - 0.8181818F, 2.0) + 1.0F - k * k;
                    float four = 484.0F * k * k * k * (float)Math.pow(n - 0.95454544F, 2.0) + 1.0F - k * k * k;
                    return Math.min(Math.min(one, two), Math.min(three, four));
                }
                default:
                    return n;
            }
        }

        private static float step(float t, float stepsValue) {
            int steps = (int)Math.max(2.0F, stepsValue);
            if (t < 0.0F) {
                return 0.0F;
            }

            float stepLength = 1.0F / steps;
            float last = (steps - 1) * stepLength;
            return t > last ? last : (float)Math.floor(t / stepLength) * stepLength;
        }
    }

    public enum Loop {
        ONCE,
        LOOP,
        HOLD;
    }

    static final class Track {
        final float[] length;
        final float[] start;
        final float[] end;
        final VanillaClip.Easing[] easing;
        final float[] arg;
        final float[] before;
        final float[] after;

        Track(int n) {
            this.length = new float[n];
            this.start = new float[n];
            this.end = new float[n];
            this.easing = new VanillaClip.Easing[n];
            this.arg = new float[n];
            this.before = new float[n];
            this.after = new float[n];
        }

        float value(float tick) {
            float total = 0.0F;

            for (int i = 0; i < this.length.length; i++) {
                total += this.length[i];
                if (total > tick) {
                    float into = tick - (total - this.length[i]);
                    float t = into / this.length[i];
                    if (t >= 1.0F) {
                        return this.end[i];
                    }

                    if (Float.isNaN(t)) {
                        return this.start[i];
                    }

                    if (this.easing[i] == VanillaClip.Easing.CATMULLROM) {
                        return catmullRom(t, this.before[i], this.start[i], this.end[i], this.after[i]);
                    }

                    return this.start[i] + (this.end[i] - this.start[i]) * this.easing[i].apply(t, this.arg[i]);
                }
            }

            return this.end[this.end.length - 1];
        }

        private static float catmullRom(float d, float p0, float p1, float p2, float p3) {
            return 0.5F * (2.0F * p1 + (p2 - p0) * d + (2.0F * p0 - 5.0F * p1 + 4.0F * p2 - p3) * d * d + (3.0F * p1 - p0 - 3.0F * p2 + p3) * d * d * d);
        }
    }
}
