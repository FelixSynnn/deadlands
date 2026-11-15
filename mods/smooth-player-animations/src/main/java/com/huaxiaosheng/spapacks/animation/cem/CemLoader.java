package com.huaxiaosheng.spapacks.animation.cem;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Map.Entry;
import java.util.function.Consumer;
import java.util.function.Function;

public final class CemLoader {
    private static final int MAX_DEPTH = 64;

    private CemLoader() {
    }

    public static CemAnimation load(String jem, Function<String, String> siblings, Consumer<String> warnings) {
        List<String[]> raw = new ArrayList<>();
        collect(JsonParser.parseString(jem).getAsJsonObject(), siblings, warnings, raw, new HashSet<>(), 0);
        Symbols symbols = new Symbols();
        List<String> targets = new ArrayList<>();
        List<MolangExpr> expressions = new ArrayList<>();
        boolean bakesLook = false;

        for (String[] entry : raw) {
            try {
                MolangExpr compiled = MolangParser.parse(entry[1], symbols);
                targets.add(entry[0]);
                expressions.add(compiled);
                bakesLook |= mentions(entry[1], "head_yaw") || mentions(entry[1], "head_pitch");
            } catch (RuntimeException e) {
                warnings.accept(entry[0] + ": " + e.getMessage());
            }
        }

        return new CemAnimation(symbols, targets, expressions, bakesLook);
    }

    private static void collect(
        JsonObject obj, Function<String, String> siblings, Consumer<String> warnings, List<String[]> out, Set<String> visiting, int depth
    ) {
        if (depth > 64) {
            warnings.accept("models nest deeper than 64; the rest is ignored");
        } else {
            if (obj.has("model") && obj.get("model").isJsonPrimitive()) {
                String model = obj.get("model").getAsString();
                if (!visiting.add(model)) {
                    warnings.accept("cyclic reference to " + model + " ignored");
                } else {
                    try {
                        String content = siblings.apply(model);
                        if (content == null) {
                            warnings.accept("missing " + model);
                        } else {
                            collect(JsonParser.parseString(content).getAsJsonObject(), siblings, warnings, out, visiting, depth + 1);
                        }
                    } catch (RuntimeException e) {
                        warnings.accept("cannot read " + model + ": " + e.getMessage());
                    } finally {
                        visiting.remove(model);
                    }
                }
            }

            if (obj.has("animations") && obj.get("animations").isJsonArray()) {
                for (JsonElement block : obj.getAsJsonArray("animations")) {
                    if (block.isJsonObject()) {
                        for (Entry<String, JsonElement> kv : block.getAsJsonObject().entrySet()) {
                            if (!kv.getKey().startsWith("__") && kv.getValue().isJsonPrimitive()) {
                                out.add(new String[]{kv.getKey(), kv.getValue().getAsString()});
                            }
                        }
                    }
                }
            }

            if (obj.has("models") && obj.get("models").isJsonArray()) {
                for (JsonElement child : obj.getAsJsonArray("models")) {
                    if (child.isJsonObject()) {
                        collect(child.getAsJsonObject(), siblings, warnings, out, visiting, depth + 1);
                    }
                }
            }
        }
    }

    private static boolean mentions(String expression, String name) {
        for (int i = expression.indexOf(name); i >= 0; i = expression.indexOf(name, i + name.length())) {
            int end = i + name.length();
            boolean before = i > 0 && isNameChar(expression.charAt(i - 1));
            boolean after = end < expression.length() && isNameChar(expression.charAt(end));
            if (!before && !after) {
                return true;
            }
        }

        return false;
    }

    private static boolean isNameChar(char c) {
        return c == '_' || c == '.' || c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c >= '0' && c <= '9';
    }
}
