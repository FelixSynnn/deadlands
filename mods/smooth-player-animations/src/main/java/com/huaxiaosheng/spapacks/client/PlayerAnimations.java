package com.huaxiaosheng.spapacks.client;

import com.huaxiaosheng.spapacks.anim.graph.AnimGraph;
import com.huaxiaosheng.spapacks.anim.graph.Plan;
import com.huaxiaosheng.spapacks.anim.signal.ExternalVars;
import com.huaxiaosheng.spapacks.animation.PlayerAnimation;
import com.huaxiaosheng.spapacks.animation.VanillaAnimation;
import com.huaxiaosheng.spapacks.animation.cem.CemAnimation;
import com.huaxiaosheng.spapacks.animation.cem.CemInput;
import com.huaxiaosheng.spapacks.animation.external.ExternalPose;
import com.huaxiaosheng.spapacks.platform.Game;
import com.huaxiaosheng.spapacks.platform.Platform;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class PlayerAnimations {
    private static final Map<AbstractClientPlayer, PlayerAnimations.Tracked> PLAYERS = new IdentityHashMap<>();

    private PlayerAnimations() {
    }

    public static void install() {
        ModConfig.load();
        SpaResources.install();
        CemContent.install();
        FaceOverlayLayer.install();
        Decisions.install();
        EmfShield.install();
        LanternModel.install();
        Platform.onClientTick(client -> {
            if (client.level == null) {
                PLAYERS.clear();
            } else {
                PLAYERS.keySet().removeIf(px -> px.isRemoved() || px.level() != client.level);

                for (AbstractClientPlayer p : client.level.players()) {
                    PlayerAnimations.Tracked t = tracked(p);
                    t.decisions.tick(p);
                    t.reach.tick(client, p);
                }
            }
        });
    }

    public static boolean cemSide() {
        return ModConfig.on() && ModConfig.get().cemEnabled;
    }

    public static boolean supported(AbstractClientPlayer p) {
        return ModConfig.on() && !cemSide() && SpaResources.ready() && !p.isInLava() && !p.isSleeping();
    }

    private static PlayerAnimations.Tracked tracked(AbstractClientPlayer player) {
        return PLAYERS.computeIfAbsent(player, p -> new PlayerAnimations.Tracked());
    }

    public static void sample(
        AbstractClientPlayer player, double time, float[][] vanilla, ExternalPose external, float[][] palette, Quaternionf[] items, Vector3f[] offsets
    ) {
        PlayerAnimations.Tracked t = tracked(player);
        boolean grounded = t.ground.at(player);
        t.rig().feet(grounded, Mth.rotLerp(Game.partialTick(), player.yBodyRotO, player.yBodyRot), ModConfig.get().footIk ? t.ground : null);
        boolean arms = ModConfig.get().handReach
            && !player.onClimbable()
            && !ExternalCapture.carrying(player)
            && !ExternalCapture.parCoolAnimating(player)
            && !ExternalCapture.betterCombatAttacking(player);
        float pt = Game.partialTick();
        t.reach
            .frame(
                t.rig().reach(), arms, Mth.lerp(pt, player.xo, player.getX()), Mth.lerp(pt, player.yo, player.getY()), Mth.lerp(pt, player.zo, player.getZ())
            );
        t.rig().sample(time, t.decisions.plan(), SpaResources.graph(), vanilla, external, palette, items, offsets);
    }

    public static void sampleVanilla(
        AbstractClientPlayer player,
        double time,
        float tickDelta,
        float headYaw,
        float headPitch,
        float[][] vanilla,
        float[][] defaults,
        ExternalPose external,
        float[][] parts,
        float[] root,
        float[][] items,
        float[] packRoot,
        boolean baseOnly
    ) {
        PlayerAnimations.Tracked t = tracked(player);
        CemAnimation animation = baseOnly ? null : CemContent.animation(Game.slim(player));
        if (animation != null) {
            CemBinding.capture(player, tickDelta, headYaw, headPitch, t.yaw, t.input);
        }

        t.vanilla()
            .sample(
                time, baseOnly ? null : t.decisions.plan(), SpaResources.graph(), animation, t.input, vanilla, defaults, external, parts, root, items, packRoot
            );
    }

    public static float hipDrop(AbstractClientPlayer player) {
        PlayerAnimations.Tracked t = PLAYERS.get(player);
        return t != null && t.rig != null && supported(player) ? t.rig.hipDrop() : 0.0F;
    }

    public static void suspend(AbstractClientPlayer p) {
    }

    static Plan plan(AbstractClientPlayer player) {
        PlayerAnimations.Tracked t = PLAYERS.get(player);
        return t == null ? null : t.decisions.plan();
    }

    static RigPlayerModel.Pool frames(AbstractClientPlayer player) {
        return tracked(player).frames;
    }

    public static ExternalVars external(AbstractClientPlayer player) {
        return tracked(player).decisions.external;
    }

    public static boolean bentRigEnabled() {
        return ModConfig.on() && !cemSide();
    }

    public static float itemReveal(AbstractClientPlayer player) {
        PlayerAnimations.Tracked t = ModConfig.on() && !PunchyMoves.drives() ? PLAYERS.get(player) : null;
        return t == null ? 1.0F : t.decisions.itemReveal();
    }

    static void decide(AbstractClientPlayer player, double time, float headYaw, float headPitch) {
        PlayerAnimations.Tracked t = tracked(player);
        AnimGraph graph = SpaResources.graph();
        t.decisions.facing(cemSide() ? 0.0 : t.rig().hipTurn());
        Plan plan = t.decisions.update(player, time, headYaw, headPitch, graph == null ? null : (cemSide() ? t.vanilla().clips(graph) : t.rig().clips(graph)));
        if (plan != null && !ModConfig.get().lanternPhysics) {
            plan.lanternPitch = 0.0F;
            plan.lanternRoll = 0.0F;
        }
    }

    private static final class Tracked {
        final Decisions decisions = new Decisions();
        private PlayerAnimation rig;
        private VanillaAnimation vanilla;
        final CemInput input = new CemInput();
        final CemBinding.TickYaw yaw = new CemBinding.TickYaw();
        final RigPlayerModel.Pool frames = new RigPlayerModel.Pool();
        final FootGround ground = new FootGround();
        final BlockReach reach = new BlockReach();

        VanillaAnimation vanilla() {
            if (this.vanilla == null) {
                this.vanilla = new VanillaAnimation();
            }

            return this.vanilla;
        }

        PlayerAnimation rig() {
            if (this.rig == null) {
                this.rig = new PlayerAnimation();
            }

            return this.rig;
        }
    }
}
