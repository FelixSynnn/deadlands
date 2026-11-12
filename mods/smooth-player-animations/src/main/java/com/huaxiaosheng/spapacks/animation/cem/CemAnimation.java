package com.huaxiaosheng.spapacks.animation.cem;

import java.util.List;
import java.util.Locale;

public final class CemAnimation {
    static final String[] PARTS = new String[]{"body", "head", "right_arm", "left_arm", "right_leg", "left_leg"};
    static final String[] CHANNELS = new String[]{"rx", "ry", "rz", "tx", "ty", "tz", "sx", "sy", "sz"};
    private final Symbols symbols;
    private final int[] targets;
    private final MolangExpr[] expressions;
    private final int[][] channels = new int[PARTS.length][CHANNELS.length];
    private final boolean locomotion;
    private final boolean face;
    private final boolean bakesLook;
    private final boolean[] assigned;
    private volatile boolean offsets;

    CemAnimation(Symbols symbols, List<String> targets, List<MolangExpr> expressions, boolean bakesLook) {
        this.symbols = symbols;
        this.targets = new int[targets.size()];
        this.expressions = expressions.toArray(new MolangExpr[0]);
        boolean locomotion = false;
        boolean face = false;

        for (int i = 0; i < this.targets.length; i++) {
            String target = targets.get(i);
            this.targets[i] = symbols.slot(target);
            locomotion |= target.startsWith("right_leg.")
                || target.startsWith("left_leg.")
                || target.startsWith("right_arm.")
                || target.startsWith("left_arm.");
            String t = target.toLowerCase(Locale.ROOT);
            face |= t.startsWith("var.jeface_")
                || t.contains("pupil")
                || t.contains("eyelid")
                || t.contains("blink")
                || t.contains("brow")
                || t.contains("eye_white")
                || t.contains("eyelash");
        }

        this.locomotion = locomotion;
        this.face = face && !locomotion;
        this.bakesLook = bakesLook;

        for (int p = 0; p < PARTS.length; p++) {
            for (int c = 0; c < CHANNELS.length; c++) {
                this.channels[p][c] = symbols.slot(PARTS[p] + "." + CHANNELS[c]);
            }
        }

        this.assigned = new boolean[symbols.size()];

        for (int target : this.targets) {
            this.assigned[target] = true;
        }

        symbols.freeze();
    }

    public int assignedSlot(String name) {
        int slot = this.symbols.find(name);
        return slot >= 0 && this.assigned[slot] ? slot : -1;
    }

    int slots() {
        return this.symbols.size();
    }

    int find(String name) {
        return this.symbols.find(name);
    }

    int[] channels(int part) {
        return this.channels[part];
    }

    public int assignments() {
        return this.targets.length;
    }

    public boolean drivesLocomotion() {
        return this.locomotion;
    }

    public boolean drivesFace() {
        return this.face;
    }

    public boolean bakesLook() {
        return this.bakesLook;
    }

    public boolean offsets() {
        return this.offsets;
    }

    public CemAnimation asOffsets() {
        this.offsets = true;
        return this;
    }

    boolean assigns(int slot) {
        return slot >= 0 && slot < this.assigned.length && this.assigned[slot];
    }

    public void eval(float[] v) {
        for (int i = 0; i < this.targets.length; i++) {
            float value = this.expressions[i].eval(v);
            v[this.targets[i]] = Float.isFinite(value) ? value : 0.0F;
        }
    }
}
