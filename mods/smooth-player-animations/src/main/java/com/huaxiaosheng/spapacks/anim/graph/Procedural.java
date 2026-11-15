package com.huaxiaosheng.spapacks.anim.graph;

public final class Procedural {
    public final float chestShare;
    public final float[] spineSplit;
    public final float maxYaw;
    public final float maxPitch;
    final Expr leanWhen;
    final double leanForward;
    final double leanMaxForward;
    final double leanBank;
    final double leanMaxBank;
    final double leanHalflife;
    final Expr lanternWhen;
    final Procedural.Flight flight;

    Procedural(
        float chestShare,
        float[] spineSplit,
        float maxYaw,
        float maxPitch,
        Expr leanWhen,
        double leanForward,
        double leanMaxForward,
        double leanBank,
        double leanMaxBank,
        double leanHalflife,
        Expr lanternWhen,
        Procedural.Flight flight
    ) {
        this.flight = flight;
        this.spineSplit = spineSplit;
        this.lanternWhen = lanternWhen;
        this.chestShare = chestShare;
        this.maxYaw = maxYaw;
        this.maxPitch = maxPitch;
        this.leanWhen = leanWhen;
        this.leanForward = leanForward;
        this.leanMaxForward = leanMaxForward;
        this.leanBank = leanBank;
        this.leanMaxBank = leanMaxBank;
        this.leanHalflife = leanHalflife;
    }

    static final class Flight {
        Expr when;
        double reference = 11.0;
        double maxTurn = 300.0;
        double halflife = 0.2;
        double lean;
        double brake;
        double bank;
        double turnBank;
        double armsTrail;
        double armsBrake;
        double armsSide;
        double armsTurnSwing;
        double armsTurnOut;
        double legsTrail;
        double legsSide;
        double legsTurn;
    }
}
