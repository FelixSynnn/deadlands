package com.huaxiaosheng.spapacks.anim.graph;

import com.huaxiaosheng.spapacks.anim.signal.ExternalVars;
import com.huaxiaosheng.spapacks.anim.signal.Signal;
import com.huaxiaosheng.spapacks.animation.cem.MolangExpr;
import com.huaxiaosheng.spapacks.animation.cem.MolangParser;
import com.huaxiaosheng.spapacks.animation.cem.Symbols;

public final class Expr {
    public static final Expr ZERO = new Expr("0", v -> 0.0F);
    public static final Expr ONE = new Expr("1", v -> 1.0F);
    private final String source;
    private final MolangExpr compiled;

    private Expr(String source, MolangExpr compiled) {
        this.source = source;
        this.compiled = compiled;
    }

    public float eval(float[] slots) {
        return this.compiled.eval(slots);
    }

    public boolean test(float[] slots) {
        return this.compiled.eval(slots) != 0.0F;
    }

    @Override
    public String toString() {
        return this.source;
    }

    public static Symbols newSymbols() {
        Symbols symbols = new Symbols();

        for (Signal s : Signal.values()) {
            if (symbols.slot(s.key) != s.ordinal()) {
                throw new IllegalStateException("Signal slots out of order");
            }
        }

        return symbols;
    }

    static Expr compile(String source, Symbols symbols) {
        if (source != null && !source.isBlank()) {
            String s = source.trim();
            if (!s.equals("1") && !s.equals("true")) {
                if (!s.equals("0") && !s.equals("false")) {
                    int before = symbols.size();
                    MolangExpr compiled = MolangParser.parse(s, symbols);

                    for (int slot = before; slot < symbols.size(); slot++) {
                        String name = symbols.nameOf(slot);
                        if (name.startsWith("x.")) {
                            ExternalVars.check(name.substring(2));
                        } else if (!name.startsWith("v.")) {
                            throw new IllegalArgumentException("unknown name \"" + name + "\" in: " + s + " (signals are q.*, variables v.*, other mods' x.*)");
                        }
                    }

                    return new Expr(s, compiled);
                } else {
                    return ZERO;
                }
            } else {
                return ONE;
            }
        } else {
            return ZERO;
        }
    }
}
