package com.huaxiaosheng.spapacks.client;

import com.huaxiaosheng.spapacks.SpaPacks;
import com.huaxiaosheng.spapacks.anim.graph.AnimGraph;
import com.huaxiaosheng.spapacks.anim.graph.ClipInfo;
import com.huaxiaosheng.spapacks.anim.graph.GraphInstance;
import com.huaxiaosheng.spapacks.anim.graph.Plan;
import com.huaxiaosheng.spapacks.anim.graph.Sample;
import com.huaxiaosheng.spapacks.anim.signal.ClipSignal;
import com.huaxiaosheng.spapacks.anim.signal.ExternalVars;
import com.huaxiaosheng.spapacks.anim.signal.Signal;
import com.huaxiaosheng.spapacks.anim.signal.SignalTracker;
import com.huaxiaosheng.spapacks.anim.signal.Signals;
import com.huaxiaosheng.spapacks.anim.signal.TickInput;
import com.huaxiaosheng.spapacks.platform.Game;
import com.huaxiaosheng.spapacks.platform.Platform;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

final class Decisions {
    private final TickInput tick = new TickInput();
    private final SignalTracker tracker = new SignalTracker();
    private final Signals signals = new Signals();
    final ExternalVars external = new ExternalVars();
    private final SignalCapture.Probe probe = new SignalCapture.Probe();
    private GraphInstance graph;
    private Plan plan;
    private double lastTime = Double.NaN;
    private double drawnAt = Double.NaN;
    private String drawClip;
    private static Decisions shown;
    private static final boolean DEBUG = Boolean.getBoolean("spa.debug");
    private String logged;
    private static final ClipInfo ANY_CLIP = new ClipInfo() {
        @Override
        public String resolve(String name) {
            return name;
        }

        @Override
        public double seconds(String resolved) {
            return 1.0;
        }

        @Override
        public boolean loops(String resolved) {
            return true;
        }
    };
    private double facing;

    void tick(LivingEntity player) {
        SignalCapture.tick(player, this.tick);
        this.tracker.tick(this.tick, this.probe.at(player));
    }

    float itemReveal() {
        if (this.drawClip != null && AnimationToggles.drawOff(this.drawClip)) {
            return 1.0F;
        } else {
            return !Double.isNaN(this.drawnAt) && this.drawClip != null ? ItemDissolve.reveal(this.drawClip, this.lastTime - this.drawnAt) : 1.0F;
        }
    }

    Plan plan() {
        return this.plan;
    }

    void facing(double radians) {
        this.facing = radians;
    }

    Plan update(LivingEntity player, double time, float headYaw, float headPitch, ClipInfo clips) {
        AnimGraph current = SpaResources.graph();
        if (current == null) {
            return null;
        }

        if (this.graph == null || this.graph.graph() != current) {
            this.graph = new GraphInstance(current);
        }

        if (time == this.lastTime && this.plan != null) {
            this.plan.lookYaw = Mth.wrapDegrees(headYaw) * this.plan.lookKeep;
            this.plan.lookPitch = headPitch * this.plan.lookKeep;
            return this.plan;
        }

        double gap = time - this.lastTime;
        this.lastTime = time;
        this.tracker.frame(time, Mth.wrapDegrees(headYaw), headPitch, this.signals);
        SignalCapture.clips(player, this.signals);
        if (this.signals.is(Signal.ITEM_DRAWN)) {
            this.drawnAt = time;
            this.drawClip = this.signals.clip(ClipSignal.DRAW);
        }

        this.graph.setFacing(this.facing);
        this.graph.setSkip(AnimationToggles::skipped);
        this.external.beginFrame();
        this.signals.external(this.external);
        this.plan = this.graph.update(this.signals, clips == null ? ANY_CLIP : clips);
        AnimationToggles.apply(this.plan);
        if (player == Minecraft.getInstance().player) {
            shown = this;
            if (DEBUG) {
                if (gap > 0.12) {
                    SpaPacks.LOGGER.info(String.format(Locale.ROOT, "[spa-debug] %.2f gap of %.2f s since the last frame", time, gap));
                }

                if (this.signals.is(Signal.JUMP_START) || this.signals.is(Signal.LANDED)) {
                    SpaPacks.LOGGER
                        .info(
                            String.format(
                                Locale.ROOT,
                                "[spa-debug] %.2f event %s ground=%s vy=%.2f air=%.2f",
                                time,
                                this.signals.is(Signal.JUMP_START) ? "jump" : "land",
                                this.signals.is(Signal.ON_GROUND),
                                this.signals.get(Signal.VERTICAL),
                                this.signals.get(Signal.AIR_TIME)
                            )
                        );
                }

                String now = this.plan.environment + "/" + this.plan.state;
                if (!now.equals(this.logged)) {
                    SpaPacks.LOGGER
                        .info(
                            String.format(
                                Locale.ROOT,
                                "[spa-debug] %.2f %s base=%s actions=%s speed=%.2f vy=%.2f",
                                time,
                                now,
                                this.plan.base,
                                this.plan.layers,
                                this.signals.get(Signal.SPEED),
                                this.signals.get(Signal.VERTICAL)
                            )
                        );
                    this.logged = now;
                }
            }
        }

        return this.plan;
    }

    static void install() {
        Platform.onHud(
            (context, delta) -> {
                Minecraft client = Minecraft.getInstance();
                if (DEBUG && client.player != null && Game.debugScreen(client)) {
                    float yaw = Mth.wrapDegrees(client.player.getViewYRot(delta) - Mth.rotLerp(delta, client.player.yBodyRotO, client.player.yBodyRot));
                    PlayerAnimations.decide(client.player, (client.player.tickCount + delta) / 20.0, yaw, client.player.getViewXRot(delta));
                    Decisions d = shown;
                    if (d != null && d.plan != null) {
                        List<String> lines = new ArrayList<>();
                        Plan p = d.plan;
                        lines.add("SPA " + p.environment + " / " + p.state + (p.cem ? " (cem)" : ""));
                        StringBuilder base = new StringBuilder("base:");

                        for (Sample s : p.base) {
                            base.append(String.format(Locale.ROOT, " %s %.0f%%", s.clip, s.weight * 100.0F));
                        }

                        lines.add(base.toString());
                        Signals s = d.signals;
                        lines.add(
                            String.format(
                                Locale.ROOT,
                                "speed %.2f (peak %.2f) fwd %.2f side %.2f vy %.2f ground %s air %.2f sprint %s run %s",
                                s.get(Signal.SPEED),
                                s.get(Signal.RECENT_SPEED),
                                s.get(Signal.FORWARD),
                                s.get(Signal.SIDE),
                                s.get(Signal.VERTICAL),
                                s.is(Signal.ON_GROUND),
                                s.get(Signal.AIR_TIME),
                                s.is(Signal.SPRINTING),
                                s.is(Signal.RUNNING)
                            )
                        );

                        for (Plan.Layer l : p.layers) {
                            lines.add(
                                String.format(Locale.ROOT, "%s: %s %.0f%%%s", l.action, l.sample.clip, l.weight * 100.0F, l.sample.mirror ? " (mirror)" : "")
                            );
                        }

                        int y = context.height() - 64 - 12 * lines.size();

                        for (String line : lines) {
                            int w = client.font.width(line);
                            context.fill((context.width() - w) / 2 - 2, y - 1, (context.width() + w) / 2 + 2, y + 10, -1879048192);
                            context.text(client.font, line, (context.width() - w) / 2, y, -8064);
                            y += 12;
                        }
                    }
                }
            }
        );
    }
}
