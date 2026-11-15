package com.huaxiaosheng.spapacks.anim.graph;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.huaxiaosheng.spapacks.anim.signal.ClipSignal;
import com.huaxiaosheng.spapacks.anim.signal.Signal;
import com.huaxiaosheng.spapacks.animation.cem.Symbols;
import com.huaxiaosheng.spapacks.util.Json;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.Map.Entry;

public final class GraphLoader {
    public static final String FORMAT = "spa_graph/1";
    private final Symbols symbols = Expr.newSymbols();
    private final List<Integer> nodeSizes = new ArrayList<>();
    private final Set<String> clipNames = new TreeSet<>();
    private final List<RefMotion> refs = new ArrayList<>();

    private GraphLoader() {
    }

    public static AnimGraph load(Reader reader) {
        return load(JsonParser.parseReader(reader).getAsJsonObject());
    }

    public static AnimGraph load(JsonObject root) {
        return new GraphLoader().compile(root);
    }

    public static JsonObject merge(JsonObject base, JsonObject over) {
        for (Entry<String, JsonElement> e : over.entrySet()) {
            JsonElement old = base.get(e.getKey());
            JsonElement value = e.getValue();
            if (old != null && old.isJsonObject() && value.isJsonObject()) {
                merge(old.getAsJsonObject(), value.getAsJsonObject());
            } else if (old != null && old.isJsonArray() && value.isJsonArray() && byId(old.getAsJsonArray()) && byId(value.getAsJsonArray())) {
                JsonArray into = old.getAsJsonArray();

                for (JsonElement item : value.getAsJsonArray()) {
                    String id = item.getAsJsonObject().get("id").getAsString();
                    JsonObject match = null;

                    for (JsonElement have : into) {
                        if (have.getAsJsonObject().get("id").getAsString().equals(id)) {
                            match = have.getAsJsonObject();
                        }
                    }

                    if (match != null) {
                        merge(match, item.getAsJsonObject());
                    } else {
                        into.add(item.deepCopy());
                    }
                }
            } else {
                base.add(e.getKey(), value.deepCopy());
            }
        }

        return base;
    }

    private static boolean byId(JsonArray array) {
        for (JsonElement e : array) {
            if (!e.isJsonObject() || !e.getAsJsonObject().has("id")) {
                return false;
            }
        }

        return true;
    }

    private AnimGraph compile(JsonObject root) {
        String format = Json.string(root, "format", "spa_graph/1");
        if (!"spa_graph/1".equals(format)) {
            throw new IllegalArgumentException("format must be \"spa_graph/1\", got \"" + format + "\"");
        }

        List<Float> variables = new ArrayList<>();
        JsonObject vars = Json.object(root, "variables");
        if (vars != null) {
            for (Entry<String, JsonElement> v : vars.entrySet()) {
                if (!v.getKey().startsWith("_")) {
                    if (!v.getKey().startsWith("v.")) {
                        throw new IllegalArgumentException("variables: \"" + v.getKey() + "\" must start with v.");
                    }

                    this.symbols.slot(v.getKey());
                    variables.add(v.getValue().getAsFloat());
                }
            }
        }

        JsonArray envs = Json.array(root, "environments");
        if (envs != null && !envs.isEmpty()) {
            List<Environment> environments = new ArrayList<>();

            for (JsonElement e : envs) {
                environments.add(this.environment(e.getAsJsonObject()));
            }

            this.resolveRefs(environments);
            String defaultId = Json.string(root, "default_environment", environments.get(0).id);
            int defaultIndex = -1;

            for (int i = 0; i < environments.size(); i++) {
                if (environments.get(i).id.equals(defaultId)) {
                    defaultIndex = i;
                }
            }

            if (defaultIndex < 0) {
                throw new IllegalArgumentException("default_environment \"" + defaultId + "\" is not an environment");
            }

            String[] slots = Json.strings(root, "slots", new String[]{"full", "upper", "off_hand", "main_hand"});
            List<List<Action>> bySlot = new ArrayList<>();

            for (int i = 0; i < slots.length; i++) {
                bySlot.add(new ArrayList<>());
            }

            JsonArray actions = Json.array(root, "actions");
            if (actions != null) {
                for (JsonElement a : actions) {
                    Action action = this.action(a.getAsJsonObject());
                    int slot = Arrays.asList(slots).indexOf(action.slot);
                    if (slot < 0) {
                        throw new IllegalArgumentException("actions[" + action.id + "]: slot \"" + action.slot + "\" is not in slots " + Arrays.toString(slots));
                    }

                    bySlot.get(slot).add(action);
                }
            }

            Action[][] actionArray = new Action[slots.length][];

            for (int i = 0; i < slots.length; i++) {
                List<Action> list = bySlot.get(i);
                list.sort(Comparator.comparingInt(a -> -a.priority));
                actionArray[i] = list.toArray(new Action[0]);
            }

            Map<String, List<String[]>> fallbacks = this.rules(root, "fallbacks");
            Map<String, List<String[]>> aliases = this.rules(root, "aliases");
            Procedural procedural = this.procedural(root);
            float[] varArray = new float[variables.size()];

            for (int i = 0; i < varArray.length; i++) {
                varArray[i] = variables.get(i);
            }

            int[] sizes = new int[this.nodeSizes.size()];

            for (int i = 0; i < sizes.length; i++) {
                sizes[i] = this.nodeSizes.get(i);
            }

            this.symbols.freeze();
            List<String> externalNames = new ArrayList<>();
            List<Integer> externalSlots = new ArrayList<>();
            int declared = 0;

            for (int slot = Signal.COUNT; slot < this.symbols.size(); slot++) {
                String name = this.symbols.nameOf(slot);
                if (name.startsWith("x.")) {
                    externalNames.add(name.substring(2));
                    externalSlots.add(slot);
                } else if (slot != Signal.COUNT + declared++ || declared > varArray.length) {
                    throw new IllegalArgumentException("variable \"" + name + "\" is not declared in \"variables\"");
                }
            }

            return new AnimGraph(
                this.symbols.size(),
                varArray,
                environments.toArray(new Environment[0]),
                defaultIndex,
                slots,
                actionArray,
                sizes.length,
                sizes,
                procedural,
                fallbacks,
                aliases,
                List.copyOf(this.clipNames),
                externalNames.toArray(new String[0]),
                externalSlots.stream().mapToInt(Integer::intValue).toArray()
            );
        } else {
            throw new IllegalArgumentException("no environments");
        }
    }

    private Map<String, List<String[]>> rules(JsonObject root, String key) {
        Map<String, List<String[]>> out = new HashMap<>();
        JsonObject o = Json.object(root, key);
        if (o != null) {
            for (Entry<String, JsonElement> backend : o.entrySet()) {
                if (backend.getValue().isJsonArray()) {
                    List<String[]> rules = new ArrayList<>();

                    for (JsonElement rule : backend.getValue().getAsJsonArray()) {
                        JsonArray pair = rule.getAsJsonArray();
                        rules.add(new String[]{pair.get(0).getAsString(), pair.get(1).isJsonNull() ? null : pair.get(1).getAsString()});
                    }

                    out.put(backend.getKey(), rules);
                }
            }
        }

        return out;
    }

    private void resolveRefs(List<Environment> environments) {
        Map<String, Motion> byPath = new HashMap<>();

        for (Environment env : environments) {
            for (State s : env.states) {
                byPath.put(env.id + "/" + s.id, s.motion);
            }
        }

        for (RefMotion r : this.refs) {
            Motion m = byPath.get(r.ref);
            if (m == null) {
                throw new IllegalArgumentException("same_as: no state \"" + r.ref + "\" (write environment/state)");
            }

            r.target = m;
        }

        for (RefMotion r : this.refs) {
            Motion m = r;

            for (int depth = 0; m instanceof RefMotion; depth++) {
                RefMotion ref = (RefMotion)m;
                if (depth > this.refs.size()) {
                    throw new IllegalArgumentException("same_as: \"" + r.ref + "\" refers back to itself");
                }

                m = ref.target;
            }
        }
    }

    private Procedural procedural(JsonObject root) {
        JsonObject p = Json.object(root, "procedural");
        p = p == null ? new JsonObject() : p;

        try {
            JsonObject look = Json.object(p, "look");
            look = look == null ? new JsonObject() : look;
            JsonObject lean = Json.object(p, "lean");
            lean = lean == null ? new JsonObject() : lean;
            JsonObject lantern = Json.object(p, "lantern");
            lantern = lantern == null ? new JsonObject() : lantern;
            return new Procedural(
                (float)Json.number(look, "chest_share", 0.25),
                spineSplit(look),
                (float)Json.number(look, "max_yaw", 80.0),
                (float)Json.number(look, "max_pitch", 80.0),
                this.expr(lean, "when", "0"),
                Json.number(lean, "forward", 0.0),
                Json.number(lean, "max_forward", 0.0),
                Json.number(lean, "bank", 0.0),
                Json.number(lean, "max_bank", 0.0),
                Json.number(lean, "halflife", 0.15),
                this.expr(lantern, "when", "0"),
                this.flight(Json.object(p, "flight"))
            );
        } catch (RuntimeException e) {
            throw wrap("procedural", e);
        }
    }

    private Procedural.Flight flight(JsonObject o) {
        o = o == null ? new JsonObject() : o;
        Procedural.Flight f = new Procedural.Flight();
        f.when = this.expr(o, "when", "0");
        f.reference = Json.number(o, "reference", 11.0);
        f.maxTurn = Json.number(o, "max_turn", 300.0);
        f.halflife = Json.number(o, "halflife", 0.2);
        f.lean = Json.number(o, "lean", 0.0);
        f.brake = Json.number(o, "brake", 0.0);
        f.bank = Json.number(o, "bank", 0.0);
        f.turnBank = Json.number(o, "turn_bank", 0.0);
        f.armsTrail = Json.number(o, "arms_trail", 0.0);
        f.armsBrake = Json.number(o, "arms_brake", 0.0);
        f.armsSide = Json.number(o, "arms_side", 0.0);
        f.armsTurnSwing = Json.number(o, "arms_turn_swing", 0.0);
        f.armsTurnOut = Json.number(o, "arms_turn_out", 0.0);
        f.legsTrail = Json.number(o, "legs_trail", 0.0);
        f.legsSide = Json.number(o, "legs_side", 0.0);
        f.legsTurn = Json.number(o, "legs_turn", 0.0);
        return f;
    }

    private static float[] spineSplit(JsonObject look) {
        float[] s = new float[]{0.0F, 0.4F, 0.6F};
        JsonArray a = Json.array(look, "spine_split");
        if (a != null) {
            if (a.size() != 3) {
                throw new IllegalArgumentException("look.spine_split: three shares (pelvis, waist, chest)");
            }

            for (int i = 0; i < 3; i++) {
                s[i] = Math.max(0.0F, a.get(i).getAsFloat());
            }
        }

        float sum = s[0] + s[1] + s[2];

        for (int i = 0; i < 3; i++) {
            s[i] = sum > 0.0F ? s[i] / sum : (i == 2 ? 1.0F : 0.0F);
        }

        return s;
    }

    private Environment environment(JsonObject o) {
        String id = required(o, "id", "environments[]");
        String where = "environments[" + id + "]";

        try {
            List<State> states = new ArrayList<>();
            JsonArray list = Json.array(o, "states");
            if (list != null && !list.isEmpty()) {
                double transition = Json.number(o, "transition", 0.2);

                for (JsonElement s : list) {
                    states.add(this.state(s.getAsJsonObject()));
                }

                states.sort(Comparator.comparingInt(sx -> -sx.priority));
                return new Environment(
                    id,
                    (int)Json.number(o, "priority", 0.0),
                    this.expr(o, "when", "1"),
                    states.toArray(new State[0]),
                    transition,
                    this.flag(o, "cem"),
                    Json.strings(o, "backends", new String[0])
                );
            } else {
                throw new IllegalArgumentException("no states");
            }
        } catch (RuntimeException e) {
            throw wrap(where, e);
        }
    }

    private State state(JsonObject o) {
        String id = required(o, "id", "states[]");

        try {
            JsonElement motion = o.get("motion");
            if (motion == null) {
                throw new IllegalArgumentException("no motion");
            } else {
                return new State(
                    id,
                    (int)Json.number(o, "priority", 0.0),
                    this.expr(o, "when", "1"),
                    this.motion(motion),
                    Json.number(o, "transition", Double.NaN),
                    Json.bool(o, "until_end", false),
                    Json.number(o, "end_lead", 0.0),
                    Json.string(o, "group", null),
                    Json.number(o, "debounce", 0.1),
                    Json.strings(o, "backends", new String[0]),
                    o.has("cem") ? this.flag(o, "cem") : null
                );
            }
        } catch (RuntimeException e) {
            throw wrap("states[" + id + "]", e);
        }
    }

    private Motion motion(JsonElement e) {
        int id = this.nodeSizes.size();
        this.nodeSizes.add(0);
        Motion m;
        if (e.isJsonPrimitive()) {
            this.clipNames.add(e.getAsString());
            m = new ClipMotion(id, e.getAsString(), null, ClipMotion.Time.SYNC, 0.0, 0.0, 0.0, Expr.ZERO, Expr.ONE, Expr.ZERO);
        } else if (e.isJsonObject() && e.getAsJsonObject().has("same_as")) {
            RefMotion r = new RefMotion(id, e.getAsJsonObject().get("same_as").getAsString());
            this.refs.add(r);
            m = r;
        } else if (e.isJsonObject() && e.getAsJsonObject().has("clip")) {
            m = this.clip(id, e.getAsJsonObject());
        } else if (e.isJsonObject() && e.getAsJsonObject().has("blend")) {
            m = this.blend(id, e.getAsJsonObject().getAsJsonObject("blend"));
        } else if (e.isJsonObject() && e.getAsJsonObject().has("direction")) {
            m = this.direction(id, e.getAsJsonObject().getAsJsonObject("direction"));
        } else {
            if (!e.isJsonObject() || !e.getAsJsonObject().has("select")) {
                throw new IllegalArgumentException("a motion is a clip name or an object with clip, blend, direction, select or same_as: " + e);
            }

            m = this.select(id, e.getAsJsonObject());
        }

        this.nodeSizes.set(id, m.stateSize());
        return m;
    }

    private ClipMotion clip(int id, JsonObject o) {
        JsonElement c = o.get("clip");
        ClipSignal signal = null;
        String name;
        if (c.isJsonObject() && c.getAsJsonObject().has("signal")) {
            signal = clipSignal(c.getAsJsonObject());
            name = "{" + signal.key + "}";
        } else {
            name = c.getAsString();
        }

        try {
            ClipMotion.Time time = ClipMotion.Time.SYNC;
            double perBlock = 0.0;
            double perSecond = 0.0;
            double stride = 0.0;
            Expr phase = Expr.ZERO;
            JsonElement t = o.get("time");
            if (t != null && t.isJsonPrimitive()) {
                time = switch (t.getAsString()) {
                    case "sync" -> ClipMotion.Time.SYNC;
                    case "free" -> ClipMotion.Time.FREE;
                    case "once" -> ClipMotion.Time.ONCE;
                    default -> throw new IllegalArgumentException("time must be sync, free, once or an object, got \"" + t.getAsString() + "\"");
                };
            } else if (t != null && t.isJsonObject()) {
                JsonObject to = t.getAsJsonObject();
                if (to.has("stride")) {
                    stride = to.get("stride").getAsDouble();
                } else if (to.has("per_block")) {
                    perBlock = to.get("per_block").getAsDouble();
                } else if (to.has("per_second")) {
                    perSecond = to.get("per_second").getAsDouble();
                } else {
                    if (!to.has("phase")) {
                        throw new IllegalArgumentException("time object needs stride, per_block, per_second or phase");
                    }

                    time = ClipMotion.Time.PHASE;
                    phase = this.expr(to, "phase", "0");
                }
            }

            if (signal == null) {
                this.clipNames.add(name);
            }

            return new ClipMotion(
                id, signal == null ? name : null, signal, time, perBlock, perSecond, stride, phase, this.expr(o, "speed", "1"), this.expr(o, "mirror", "0")
            );
        } catch (RuntimeException e) {
            throw wrap("clip " + name, e);
        }
    }

    private BlendMotion blend(int id, JsonObject o) {
        JsonArray points = Json.array(o, "points");
        if (points != null && points.size() >= 2) {
            List<double[]> at = new ArrayList<>();
            List<Motion> motions = new ArrayList<>();

            for (JsonElement p : points) {
                JsonArray pair = p.getAsJsonArray();
                at.add(new double[]{pair.get(0).getAsDouble()});
                motions.add(this.motion(pair.get(1)));
            }

            double[] values = new double[at.size()];

            for (int i = 0; i < values.length; i++) {
                values[i] = at.get(i)[0];
                if (i > 0 && values[i] <= values[i - 1]) {
                    throw new IllegalArgumentException("blend: point values must increase");
                }
            }

            return new BlendMotion(id, this.expr(o, "by", "q.speed"), Json.number(o, "smooth", 0.0), values, motions.toArray(new Motion[0]));
        } else {
            throw new IllegalArgumentException("blend: needs at least two points [value, motion]");
        }
    }

    private DirectionMotion direction(int id, JsonObject o) {
        Motion[] m = new Motion[4];
        String[] keys = new String[]{"forward", "back", "left", "right"};

        for (int i = 0; i < 4; i++) {
            if (o.has(keys[i])) {
                m[i] = this.motion(o.get(keys[i]));
            }
        }

        if (m[0] == null) {
            throw new IllegalArgumentException("direction: needs forward");
        } else {
            return new DirectionMotion(id, this.expr(o, "x", "q.side"), this.expr(o, "y", "q.forward"), Json.number(o, "rate", 14.0), m);
        }
    }

    private SelectMotion select(int id, JsonObject o) {
        JsonArray options = o.getAsJsonArray("select");
        Expr[] when = new Expr[options.size()];
        Motion[] motions = new Motion[options.size()];

        for (int i = 0; i < options.size(); i++) {
            JsonObject opt = options.get(i).getAsJsonObject();
            when[i] = opt.has("when") ? this.expr(opt, "when", "1") : null;
            motions[i] = this.motion(opt.get("motion"));
        }

        return new SelectMotion(id, when, motions, Json.bool(o, "latch", false), Json.number(o, "transition", 0.15));
    }

    private Action action(JsonObject o) {
        String id = required(o, "id", "actions[]");

        try {
            if (!o.has("when") && !o.has("trigger")) {
                throw new IllegalArgumentException("needs when or trigger");
            }

            String clip = null;
            ClipSignal signal = null;
            Expr pick = null;
            String[] choices = null;
            JsonElement c = o.get("clip");
            if (c == null) {
                throw new IllegalArgumentException("no clip");
            }

            if (c.isJsonPrimitive()) {
                clip = c.getAsString();
                this.clipNames.add(clip);
            } else if (c.isJsonObject() && c.getAsJsonObject().has("pick")) {
                pick = this.expr(c.getAsJsonObject(), "pick", "0");
                choices = Json.strings(c.getAsJsonObject(), "of", null);
                if (choices == null || choices.length == 0) {
                    throw new IllegalArgumentException("clip: a pick needs \"of\": [clip names]");
                }

                this.clipNames.addAll(Arrays.asList(choices));
            } else {
                if (!c.isJsonObject() || !c.getAsJsonObject().has("signal")) {
                    throw new IllegalArgumentException("clip: a name, {\"signal\": ...} or {\"pick\": ..., \"of\": [...]}");
                }

                signal = clipSignal(c.getAsJsonObject());
            }

            return new Action(
                id,
                required(o, "slot", "actions[" + id + "]"),
                (int)Json.number(o, "priority", 0.0),
                o.has("when") ? this.expr(o, "when", "0") : null,
                o.has("trigger") ? this.expr(o, "trigger", "0") : null,
                this.expr(o, "yield", "0"),
                clip,
                signal,
                pick,
                choices,
                Json.strings(o, "backends", new String[0]),
                Json.string(o, "mask", "full"),
                Json.string(o, "override", "arms"),
                Json.bool(o, "additive", false),
                this.expr(o, "mirror", "0"),
                Json.bool(o, "loop", false),
                this.expr(o, "speed", "1"),
                Json.number(o, "fade_in", 0.1),
                Json.number(o, "fade_out", 0.15),
                Json.number(o, "blend_out", 0.0),
                Json.number(o, "duration", 0.0),
                this.flag(o, "aim"),
                this.expr(o, "lower", "0"),
                Json.bool(o, "chain", false),
                this.expr(o, "strength", "1"),
                o.has("rearm") ? this.expr(o, "rearm", "0") : null,
                Json.bool(o, "exclusive", false)
            );
        } catch (RuntimeException e) {
            throw wrap("actions[" + id + "]", e);
        }
    }

    private static ClipSignal clipSignal(JsonObject o) {
        String key = o.get("signal").getAsString();
        ClipSignal signal = ClipSignal.byKey(key);
        if (signal == null) {
            throw new IllegalArgumentException("clip signal \"" + key + "\" unknown; one of " + Arrays.toString(ClipSignal.values()).toLowerCase());
        } else {
            return signal;
        }
    }

    private Expr flag(JsonObject o, String key) {
        if (o.get(key) instanceof JsonPrimitive p && p.isBoolean()) {
            return p.getAsBoolean() ? Expr.ONE : Expr.ZERO;
        } else {
            return this.expr(o, key, "0");
        }
    }

    private Expr expr(JsonObject o, String key, String fallback) {
        JsonElement e = o.get(key);
        String source = e == null ? fallback : (e.isJsonPrimitive() ? e.getAsString() : null);
        if (source == null) {
            throw new IllegalArgumentException(key + ": expected an expression, got " + e);
        }

        try {
            return Expr.compile(source, this.symbols);
        } catch (RuntimeException ex) {
            throw wrap(key, ex);
        }
    }

    private static IllegalArgumentException wrap(String where, RuntimeException e) {
        return new IllegalArgumentException(where + ": " + e.getMessage(), e);
    }

    private static String required(JsonObject o, String key, String where) {
        String s = Json.string(o, key, null);
        if (s == null) {
            throw new IllegalArgumentException(where + ": no \"" + key + "\"");
        } else {
            return s;
        }
    }
}
