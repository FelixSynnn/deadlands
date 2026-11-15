package com.huaxiaosheng.spapacks.client;

import com.huaxiaosheng.spapacks.SpaPacks;
import com.huaxiaosheng.spapacks.animation.external.ExternalPose;
import com.huaxiaosheng.spapacks.platform.Game;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3f;

final class ExternalCapture {
    private static final float OWNED = 0.001F;
    private static final float CHANGED = 1.0E-4F;
    private static final ExternalCapture.Kosmx KOSMX = new ExternalCapture.Kosmx();
    private static final ExternalCapture.Pal PAL = new ExternalCapture.Pal();
    private static final ExternalCapture.CarryOn CARRY_ON = new ExternalCapture.CarryOn();
    private static final ExternalCapture.Watut WATUT = new ExternalCapture.Watut();
    private static final ExternalCapture.BetterCombat BETTER_COMBAT = new ExternalCapture.BetterCombat();
    private static final ExternalCapture.EmfCompat EMF_COMPAT = new ExternalCapture.EmfCompat();
    private static final ExternalCapture.Musket MUSKET = new ExternalCapture.Musket();
    private static final ExternalCapture.ParCool PARCOOL = new ExternalCapture.ParCool();
    private static final float[] SNEAK_Y = new float[]{3.2F, 4.2F, 3.2F, 3.2F, 0.2F, 0.2F};
    private static final float[] SNEAK_Z = new float[]{0.0F, 0.0F, 0.0F, 0.0F, 4.0F, 4.0F};
    private static final float[] at0 = new float[6];
    private static final float[] at1 = new float[6];
    private static final float[] boneTerms = new float[6];

    private ExternalCapture() {
    }

    static boolean betterCombatAttacking(AbstractClientPlayer player) {
        return BETTER_COMBAT.attacking(player);
    }

    static boolean betterCombatPlaying(AbstractClientPlayer player) {
        return BETTER_COMBAT.playing(player);
    }

    static boolean parCoolAnimating(AbstractClientPlayer player) {
        return PARCOOL.animating(player);
    }

    static boolean carrying(AbstractClientPlayer player) {
        return CARRY_ON.carrying(player);
    }

    static void read(AbstractClientPlayer player, ModelPart[] parts, float[][] vanilla, ExternalPose out) {
        out.clear();

        for (int p = 0; p < 6; p++) {
            if (vanilla != null) {
                System.arraycopy(vanilla[p], 0, out.vanilla[p], 0, 6);
            } else {
                store(parts[p], out.vanilla[p]);
            }
        }

        for (int p = 0; p < 6; p++) {
            store(parts[p], out.model[p]);
        }

        standing(vanilla != null && player.isCrouching(), out);
        KOSMX.read(player, out);
        PAL.read(player, parts, out);
        EMF_COMPAT.read(player, out);
        if (!PARCOOL.animating(player)) {
            if (vanilla != null) {
                out.exclusive = BETTER_COMBAT.attacking(player);
                if (MUSKET.aiming(player)) {
                    take(parts, 2, out);
                    take(parts, 3, out);
                    take(parts, 1, out);
                }

                if (CARRY_ON.carrying(player)) {
                    changed(parts, 2, out);
                    changed(parts, 3, out);
                }

                if (WATUT.animating(player)) {
                    changed(parts, 2, out);
                    changed(parts, 3, out);
                    changed(parts, 1, out);
                }
            }
        } else {
            out.exclusive = true;

            for (int p = 0; p < 6; p++) {
                take(parts, p, out);
            }
        }
    }

    private static void standing(boolean sneaking, ExternalPose out) {
        for (int p = 0; p < 6; p++) {
            float[] s = out.standing[p];
            System.arraycopy(out.vanilla[p], 0, s, 0, 3);
            s[3] = 0.0F;
            s[4] = 0.0F;
            s[5] = 0.0F;
            if (sneaking) {
                s[1] -= SNEAK_Y[p];
                s[2] -= SNEAK_Z[p];
            }
        }
    }

    private static void take(ModelPart[] parts, int p, ExternalPose out) {
        store(parts[p], out.value[p]);
        out.weight[p] = 1.0F;
    }

    private static void changed(ModelPart[] parts, int p, ExternalPose out) {
        if (!(out.weight[p] > 0.0F)) {
            store(parts[p], out.value[p]);
            if (differs(out.value[p], out.vanilla[p])) {
                out.weight[p] = 1.0F;
            }
        }
    }

    private static void store(ModelPart part, float[] out) {
        out[0] = part.x;
        out[1] = part.y;
        out[2] = part.z;
        out[3] = part.xRot;
        out[4] = part.yRot;
        out[5] = part.zRot;
    }

    private static boolean differs(float[] a, float[] b) {
        for (int i = 0; i < 6; i++) {
            if (Math.abs(a[i] - b[i]) > (i < 3 ? 9.999999E-4F : 1.0E-4F)) {
                return true;
            }
        }

        return false;
    }

    private static float solve(float[] at0, float[] at1, float[] vanilla, float[] value) {
        float owned = 0.0F;

        for (int i = 0; i < 6; i++) {
            float a = at0[i];
            float share = Math.max(0.0F, Math.min(1.0F, 1.0F - (at1[i] - a)));
            if (share > 0.001F) {
                value[i] = a / share;
                owned = Math.max(owned, share);
            } else {
                value[i] = vanilla[i];
            }
        }

        return owned;
    }

    private static Class<?> find(String name) {
        try {
            return Class.forName(name, false, ExternalCapture.class.getClassLoader());
        } catch (ClassNotFoundException | LinkageError absent) {
            return null;
        }
    }

    private static Field field(Class<?> type, String name) {
        try {
            return type.getField(name);
        } catch (NoSuchFieldException absent) {
            return null;
        }
    }

    private static Object enumConstant(Class<?> type, String name) {
        return Enum.valueOf((Class)type, name);
    }

    private static final class BetterCombat {
        private static final int RELEASE_LEAD = 8;
        private boolean resolved;
        private Field stack;
        private Field base;
        private Method isActive;
        private Method animation;
        private Class<?> keyframes;
        private Method currentTick;
        private Method stopTick;
        private Method ticks;
        private Method instance;
        private Method length;
        private static final int IDLE = 0;
        private static final int TAIL = 1;
        private static final int ATTACKING = 2;

        boolean attacking(AbstractClientPlayer player) {
            return this.state(player) == 2;
        }

        boolean playing(AbstractClientPlayer player) {
            return this.state(player) != 0;
        }

        private int state(AbstractClientPlayer player) {
            if (!this.resolved) {
                this.resolved = true;
                Class<?> subStack = ExternalCapture.find("net.bettercombat.client.animation.AttackAnimationSubStack");
                Class<?> controller = subStack == null ? ExternalCapture.find("net.bettercombat.client.animation.AttackAnimationStack") : null;
                if (subStack != null || controller != null) {
                    try {
                        this.stack = AbstractClientPlayer.class.getDeclaredField("attackAnimation");
                        this.stack.setAccessible(true);
                        if (subStack != null) {
                            this.base = subStack.getField("base");
                            this.isActive = this.base.getType().getMethod("isActive");
                        } else {
                            this.isActive = controller.getMethod("isActive");
                        }

                        try {
                            if (subStack != null) {
                                this.animation = this.base.getType().getMethod("getAnimation");
                                this.keyframes = Class.forName("dev.kosmx.playerAnim.api.layered.KeyframeAnimationPlayer");
                                this.currentTick = this.keyframes.getMethod("getCurrentTick");
                                this.stopTick = this.keyframes.getMethod("getStopTick");
                            } else {
                                this.ticks = controller.getMethod("getAnimationTicks");
                                this.instance = controller.getMethod("getCurrentAnimationInstance");
                                this.length = this.instance.getReturnType().getMethod("length");
                            }
                        } catch (ReflectiveOperationException | LinkageError early) {
                            this.animation = null;
                            this.ticks = null;
                        }

                        SpaPacks.LOGGER.info("Better Combat found; its attacks take the whole body");
                    } catch (ReflectiveOperationException | LinkageError | RuntimeException incompatible) {
                        this.stack = null;
                        SpaPacks.LOGGER.warn("Better Combat found but its layout differs; its attacks only take the parts they pose", incompatible);
                    }
                }
            }

            if (this.stack == null) {
                return 0;
            }

            try {
                Object attack = this.stack.get(player);
                Object layer = attack != null && this.base != null ? this.base.get(attack) : attack;
                if (layer == null || !Boolean.TRUE.equals(this.isActive.invoke(layer))) {
                    return 0;
                }

                if (this.ticks != null) {
                    Object swing = this.instance.invoke(layer);
                    if (swing == null) {
                        return 2;
                    }

                    float tick = ((Number)this.ticks.invoke(layer)).floatValue();
                    float stop = ((Number)this.length.invoke(swing)).floatValue();
                    return !(stop <= 8.0F) && !(tick < stop - 8.0F) ? 1 : 2;
                } else {
                    Object swing = this.animation == null ? null : this.animation.invoke(layer);
                    if (swing != null && this.keyframes.isInstance(swing)) {
                        int tick = ((Number)this.currentTick.invoke(swing)).intValue();
                        int stop = ((Number)this.stopTick.invoke(swing)).intValue();
                        return stop > 8 && tick >= stop - 8 ? 1 : 2;
                    } else {
                        return 2;
                    }
                }
            } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
                this.stack = null;
                SpaPacks.LOGGER.warn("Better Combat answered unexpectedly; its attacks only take the parts they pose", e);
                return 0;
            }
        }
    }

    private static final class CarryOn {
        private boolean resolved;
        private Method data;
        private Method carrying;

        boolean carrying(AbstractClientPlayer player) {
            if (!this.resolved) {
                this.resolved = true;
                Class<?> manager = ExternalCapture.find("tschipp.carryon.common.carry.CarryOnDataManager");
                if (manager != null) {
                    try {
                        this.data = manager.getMethod("getCarryData", Player.class);
                        this.carrying = this.data.getReturnType().getMethod("isCarrying");
                        SpaPacks.LOGGER.info("Carry On found; its carrying arms are blended into our animation");
                    } catch (ReflectiveOperationException | LinkageError incompatible) {
                        this.data = null;
                        SpaPacks.LOGGER.warn("Carry On found but its API differs; its carrying arms are not shown", incompatible);
                    }
                }
            }

            if (this.data == null) {
                return false;
            }

            try {
                Object carry = this.data.invoke(null, player);
                return carry != null && Boolean.TRUE.equals(this.carrying.invoke(carry));
            } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
                this.data = null;
                SpaPacks.LOGGER.warn("Carry On answered unexpectedly; its carrying arms are no longer shown", e);
                return false;
            }
        }
    }

    private static final class EmfCompat {
        private static final String[] KEYS = new String[]{"body", "head", "right_arm", "left_arm", "right_leg", "left_leg"};
        private static final float EASE = 0.12F;
        private boolean resolved;
        private Method saved;
        private Method leftArm;
        private Method rightArm;
        private Method partsOf;
        private Field[] values;
        private Field weight;
        private Field rotationOnly;
        private final Map<AbstractClientPlayer, ExternalCapture.EmfCompat.Fade> fades = new WeakHashMap<>();

        void read(AbstractClientPlayer player, ExternalPose out) {
            if (!this.resolved) {
                this.resolved = true;
                Class<?> manager = ExternalCapture.find("strm.emfcompat.core.PoseManager");
                Class<?> poses = ExternalCapture.find("strm.emfcompat.core.SavedPoses");
                Class<?> snapshot = ExternalCapture.find("strm.emfcompat.core.PoseSnapshot");
                if (manager != null && poses != null && snapshot != null) {
                    try {
                        this.saved = manager.getMethod("getSavedPoses", UUID.class);
                        this.leftArm = poses.getMethod("leftArm");
                        this.rightArm = poses.getMethod("rightArm");
                        this.partsOf = poses.getMethod("parts");
                        this.values = new Field[6];
                        String[] names = new String[]{"x", "y", "z", "xRot", "yRot", "zRot"};

                        for (int i = 0; i < 6; i++) {
                            this.values[i] = snapshot.getField(names[i]);
                        }

                        this.weight = snapshot.getField("weight");
                        this.rotationOnly = snapshot.getField("rotationOnly");
                        SpaPacks.LOGGER.info("EMF Compat Core found; Touch'n Motion's poses are blended into our animation");
                    } catch (ReflectiveOperationException | LinkageError incompatible) {
                        this.saved = null;
                        SpaPacks.LOGGER.warn("EMF Compat Core found but its API differs; its poses are not shown", incompatible);
                    }
                }
            }

            if (this.saved != null) {
                ExternalCapture.EmfCompat.Fade fade = this.fades.computeIfAbsent(player, px -> new ExternalCapture.EmfCompat.Fade());
                long now = System.nanoTime();
                float dt = Math.min(0.25F, Math.max(0.0F, (float)(now - fade.at) / 1.0E9F));
                fade.at = now;
                float ease = 1.0F - (float)Math.exp(-dt / 0.12F);

                try {
                    Object poses = this.saved.invoke(null, player.getUUID());
                    Map<?, ?> map = poses == null ? null : (Map)this.partsOf.invoke(poses);

                    for (int p = 0; p < 6; p++) {
                        Object snap = null;
                        if (poses != null) {
                            snap = p == 2 ? this.rightArm.invoke(poses) : (p == 3 ? this.leftArm.invoke(poses) : null);
                            if (snap == null && map != null) {
                                snap = map.get(KEYS[p]);
                            }
                        }

                        float target = 0.0F;
                        if (snap != null) {
                            target = Math.max(0.0F, Math.min(1.0F, this.weight.getFloat(snap)));
                            boolean turnOnly = this.rotationOnly.getBoolean(snap);

                            for (int i = 0; i < 6; i++) {
                                fade.value[p][i] = turnOnly && i < 3 ? out.vanilla[p][i] : this.values[i].getFloat(snap);
                            }
                        }

                        fade.weight[p] = fade.weight[p] + (target - fade.weight[p]) * ease;
                        if (out.weight[p] <= 0.0F && fade.weight[p] > 0.001F) {
                            System.arraycopy(fade.value[p], 0, out.value[p], 0, 6);
                            out.weight[p] = fade.weight[p];
                        }
                    }
                } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
                    this.saved = null;
                    SpaPacks.LOGGER.warn("EMF Compat Core answered unexpectedly; its poses are no longer shown", e);
                }
            }
        }

        private static final class Fade {
            final float[] weight = new float[6];
            final float[][] value = new float[6][6];
            long at = System.nanoTime();
        }
    }

    private static final class Kosmx {
        private static final String[] PARTS = new String[]{"torso", "head", "rightArm", "leftArm", "rightLeg", "leftLeg"};
        private boolean resolved;
        private Class<?> animatedPlayer;
        private Method animation;
        private Method isActive;
        private Method transform;
        private Method bend;
        private Method left;
        private Method right;
        private Method x;
        private Method y;
        private Method z;
        private Object rotation;
        private Object position;
        private Object zero;
        private Object one;
        private boolean broken;

        void read(AbstractClientPlayer player, ExternalPose out) {
            this.resolve();
            if (this.animatedPlayer != null && !this.broken && this.animatedPlayer.isInstance(player)) {
                try {
                    Object applier = this.animation.invoke(player);
                    if (applier == null || !Boolean.TRUE.equals(this.isActive.invoke(applier))) {
                        return;
                    }

                    for (int p = 0; p < 6; p++) {
                        String name = PARTS[p];
                        this.vector(applier, name, this.position, this.zero, ExternalCapture.at0, 0);
                        this.vector(applier, name, this.rotation, this.zero, ExternalCapture.at0, 3);
                        this.vector(applier, name, this.position, this.one, ExternalCapture.at1, 0);
                        this.vector(applier, name, this.rotation, this.one, ExternalCapture.at1, 3);
                        float owned = ExternalCapture.solve(ExternalCapture.at0, ExternalCapture.at1, out.vanilla[p], out.value[p]);
                        out.weight[p] = owned;
                        if (p != 1) {
                            this.bend(applier, name, out.bend[p]);
                            if (p == 0) {
                                float axis = out.bend[p][0];
                                float value = out.bend[p][1];
                                this.bend(applier, "body", out.bend[p]);
                                out.bend[p][0] = out.bend[p][0] + axis;
                                out.bend[p][1] = out.bend[p][1] + value;
                            }

                            if (owned > 0.001F) {
                                out.bend[p][0] = out.bend[p][0] / owned;
                                out.bend[p][1] = out.bend[p][1] / owned;
                            }
                        }
                    }
                } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
                    this.broken = true;
                    SpaPacks.LOGGER.warn("playerAnimator answered unexpectedly; its animations are no longer blended", e);
                }
            }
        }

        private void vector(Object applier, String part, Object type, Object given, float[] out, int offset) throws ReflectiveOperationException {
            Object v = this.transform.invoke(applier, part, type, given);
            out[offset] = ((Number)this.x.invoke(v)).floatValue();
            out[offset + 1] = ((Number)this.y.invoke(v)).floatValue();
            out[offset + 2] = ((Number)this.z.invoke(v)).floatValue();
        }

        private void bend(Object applier, String part, float[] out) throws ReflectiveOperationException {
            Object pair = this.bend.invoke(applier, part);
            out[0] = pair == null ? 0.0F : ((Number)this.left.invoke(pair)).floatValue();
            out[1] = pair == null ? 0.0F : ((Number)this.right.invoke(pair)).floatValue();
        }

        private void resolve() {
            if (!this.resolved) {
                this.resolved = true;
                Class<?> owner = ExternalCapture.find("dev.kosmx.playerAnim.impl.IAnimatedPlayer");
                if (owner != null) {
                    try {
                        this.animation = owner.getMethod("playerAnimator_getAnimation");
                        Class<?> applier = this.animation.getReturnType();
                        Class<?> vec3f = Class.forName("dev.kosmx.playerAnim.core.util.Vec3f", false, ExternalCapture.class.getClassLoader());
                        Class<?> transformType = Class.forName("dev.kosmx.playerAnim.api.TransformType", false, ExternalCapture.class.getClassLoader());
                        this.isActive = applier.getMethod("isActive");
                        this.transform = applier.getMethod("get3DTransform", String.class, transformType, vec3f);
                        this.bend = applier.getMethod("getBend", String.class);
                        Class<?> pair = this.bend.getReturnType();
                        this.left = pair.getMethod("getLeft");
                        this.right = pair.getMethod("getRight");
                        this.x = vec3f.getMethod("getX");
                        this.y = vec3f.getMethod("getY");
                        this.z = vec3f.getMethod("getZ");
                        Constructor<?> vector = vec3f.getConstructor(float.class, float.class, float.class);
                        this.zero = vector.newInstance(0.0F, 0.0F, 0.0F);
                        this.one = vector.newInstance(1.0F, 1.0F, 1.0F);
                        this.rotation = ExternalCapture.enumConstant(transformType, "ROTATION");
                        this.position = ExternalCapture.enumConstant(transformType, "POSITION");
                        this.animatedPlayer = owner;
                        SpaPacks.LOGGER.info("playerAnimator found; its animations are blended into ours part by part");
                    } catch (ReflectiveOperationException | LinkageError | IllegalArgumentException incompatible) {
                        SpaPacks.LOGGER.warn("playerAnimator found but its API differs; its animations are not shown", incompatible);
                    }
                }
            }
        }
    }

    private static final class Musket {
        private boolean resolved;
        private Class<?> gun;
        private Method shouldAim;

        boolean aiming(AbstractClientPlayer player) {
            if (!this.resolved) {
                this.resolved = true;
                String pkg = ExternalCapture.find("ewewukek.musketmod.ClientUtilities") != null ? "ewewukek.musketmod." : "lufurrius.musketmod.";
                Class<?> utilities = ExternalCapture.find(pkg + "ClientUtilities");
                Class<?> gunType = ExternalCapture.find(pkg + "GunItem");
                if (utilities != null && gunType != null) {
                    try {
                        this.shouldAim = utilities.getMethod("shouldAim", LivingEntity.class, ItemStack.class, InteractionHand.class);
                        this.gun = gunType;
                        SpaPacks.LOGGER.info("Musket Mod found; its aiming arms are shown");
                    } catch (ReflectiveOperationException | LinkageError incompatible) {
                        SpaPacks.LOGGER.warn("Musket Mod found but its API differs; its aiming arms are not shown", incompatible);
                    }
                }
            }

            if (this.shouldAim == null) {
                return false;
            }

            try {
                return this.aims(player, player.getMainHandItem(), InteractionHand.MAIN_HAND)
                    || this.aims(player, player.getOffhandItem(), InteractionHand.OFF_HAND);
            } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
                this.shouldAim = null;
                SpaPacks.LOGGER.warn("Musket Mod answered unexpectedly; its aiming arms are no longer shown", e);
                return false;
            }
        }

        private boolean aims(AbstractClientPlayer player, ItemStack stack, InteractionHand hand) throws ReflectiveOperationException {
            return this.gun.isInstance(stack.getItem()) && Boolean.TRUE.equals(this.shouldAim.invoke(null, player, stack, hand));
        }
    }

    private static final class Pal {
        private static final String[] BONES = new String[]{"torso", "head", "right_arm", "left_arm", "right_leg", "left_leg"};
        private boolean resolved;
        private Class<?> animatedPlayer;
        private Method manager;
        private Method isActive;
        private Method transform;
        private Constructor<?> bone;
        private final Field[] fields = new Field[6];
        private Field position;
        private Field rotation;
        private boolean broken;

        void read(AbstractClientPlayer player, ModelPart[] parts, ExternalPose out) {
            this.resolve();
            if (this.animatedPlayer != null && !this.broken && this.animatedPlayer.isInstance(player)) {
                try {
                    Object stack = this.manager.invoke(player);
                    if (stack == null || !Boolean.TRUE.equals(this.isActive.invoke(stack))) {
                        return;
                    }

                    for (int p = 0; p < 6; p++) {
                        if (!(out.weight[p] > 0.0F)) {
                            PartPose initial = parts[p].getInitialPose();
                            this.bone(stack, BONES[p], 0.0F, ExternalCapture.at0);
                            this.bone(stack, BONES[p], 1.0F, ExternalCapture.at1);
                            float[] vanilla = out.vanilla[p];
                            float[] value = out.value[p];
                            float[] given = ExternalCapture.boneTerms;
                            given[0] = vanilla[0] - Game.x(initial);
                            given[1] = -(vanilla[1] - Game.y(initial));
                            given[2] = vanilla[2] - Game.z(initial);
                            given[3] = vanilla[3];
                            given[4] = vanilla[4] - Game.yRot(initial);
                            given[5] = vanilla[5];
                            out.weight[p] = ExternalCapture.solve(ExternalCapture.at0, ExternalCapture.at1, given, value);
                            value[0] += Game.x(initial);
                            value[1] = Game.y(initial) - value[1];
                            value[2] += Game.z(initial);
                            value[4] += Game.yRot(initial);
                        }
                    }
                } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
                    this.broken = true;
                    SpaPacks.LOGGER.warn("Player Animation Library answered unexpectedly; its animations are no longer blended", e);
                }
            }
        }

        private void bone(Object stack, String name, float given, float[] out) throws ReflectiveOperationException {
            Object b = this.bone.newInstance(name);
            if (this.position != null) {
                ((Vector3f)this.position.get(b)).set(given);
                ((Vector3f)this.rotation.get(b)).set(given);
            } else {
                for (Field f : this.fields) {
                    f.setFloat(b, given);
                }
            }

            Object answer = this.transform.invoke(stack, b);
            if (answer == null) {
                answer = b;
            }

            if (this.position != null) {
                Vector3f p = (Vector3f)this.position.get(answer);
                Vector3f r = (Vector3f)this.rotation.get(answer);
                out[0] = p.x;
                out[1] = p.y;
                out[2] = p.z;
                out[3] = r.x;
                out[4] = r.y;
                out[5] = r.z;
            } else {
                for (int i = 0; i < 6; i++) {
                    out[i] = this.fields[i].getFloat(answer);
                }
            }
        }

        private void resolve() {
            if (!this.resolved) {
                this.resolved = true;
                Class<?> owner = ExternalCapture.find("com.zigythebird.playeranim.accessors.IAnimatedPlayer");
                if (owner == null) {
                    owner = ExternalCapture.find("com.zigythebird.playeranim.accessors.IAnimatedAvatar");
                }

                if (owner != null) {
                    try {
                        this.manager = owner.getMethod("playerAnimLib$getAnimManager");
                        Class<?> stack = this.manager.getReturnType();
                        Class<?> boneType = Class.forName("com.zigythebird.playeranimcore.bones.PlayerAnimBone", false, ExternalCapture.class.getClassLoader());
                        this.isActive = stack.getMethod("isActive");
                        this.transform = stack.getMethod("get3DTransform", boneType);
                        this.bone = boneType.getConstructor(String.class);
                        Field position = ExternalCapture.field(boneType, "position");
                        if (position != null && position.getType() == Vector3f.class) {
                            this.position = position;
                            this.rotation = boneType.getField("rotation");
                        } else {
                            String[] names = new String[]{"positionX", "positionY", "positionZ", "rotX", "rotY", "rotZ"};

                            for (int i = 0; i < 6; i++) {
                                this.fields[i] = boneType.getField(names[i]);
                            }
                        }

                        this.animatedPlayer = owner;
                        SpaPacks.LOGGER.info("Player Animation Library found; its animations are blended into ours part by part");
                    } catch (ReflectiveOperationException | LinkageError incompatible) {
                        SpaPacks.LOGGER.warn("Player Animation Library found but its API differs; its animations are not shown", incompatible);
                    }
                }
            }
        }
    }

    private static final class ParCool {
        private boolean resolved;
        private Class<?> holder;
        private Method animator;
        private Method idle;
        private Method config;
        private Field enabled;
        private Method enabledValue;
        private Method attachment;
        private Method hasAnimator;

        boolean animating(AbstractClientPlayer player) {
            if (!this.resolved) {
                this.resolved = true;
                this.resolve();
            }

            try {
                if (this.holder != null) {
                    if (this.holder.isInstance(player) && (this.config == null || this.switchedOn())) {
                        Object a = this.animator.invoke(player);
                        return a != null && !Boolean.TRUE.equals(this.idle.invoke(a));
                    }

                    return false;
                }

                if (this.attachment != null) {
                    Object a = this.attachment.invoke(null, player);
                    return a != null && Boolean.TRUE.equals(this.hasAnimator.invoke(a));
                }
            } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
                this.holder = null;
                this.attachment = null;
                SpaPacks.LOGGER.warn("ParCool answered unexpectedly; its actions no longer take the body", e);
            }

            return false;
        }

        private boolean switchedOn() throws ReflectiveOperationException {
            Object c = this.config.invoke(null);
            Object value = c == null ? null : this.enabled.get(c);
            return value == null || Boolean.TRUE.equals(this.enabledValue.invoke(value));
        }

        private void resolve() {
            Class<?> holderType = ExternalCapture.find("com.alrex.parcool.client.animation.system.IPlayerAnimatorHolder");
            Class<?> attachmentType = holderType == null ? ExternalCapture.find("com.alrex.parcool.common.attachment.client.Animation") : null;
            if (holderType == null && attachmentType == null) {
                attachmentType = ExternalCapture.find("com.alrex.parcool.common.capability.Animation");
            }

            if (holderType != null || attachmentType != null) {
                try {
                    if (holderType != null) {
                        this.animator = holderType.getMethod("getParCoolPlayerAnimator");
                        this.idle = this.animator.getReturnType().getMethod("isIdle");
                        Class<?> configType = ExternalCapture.find("com.alrex.parcool.client.animation.system.config.AnimationSystemConfig");
                        if (configType != null) {
                            try {
                                this.config = configType.getMethod("getInstance");
                                this.enabled = configType.getField("enableAnimation");
                                this.enabledValue = this.enabled.getType().getMethod("get");
                            } catch (ReflectiveOperationException | LinkageError noSwitch) {
                                this.config = null;
                            }
                        }

                        this.holder = holderType;
                    } else {
                        this.attachment = attachmentType.getMethod("get", Player.class);
                        this.hasAnimator = attachmentType.getMethod("hasAnimator");
                    }

                    SpaPacks.LOGGER.info("ParCool found; its actions take the whole body");
                } catch (ReflectiveOperationException | LinkageError incompatible) {
                    this.holder = null;
                    this.attachment = null;
                    SpaPacks.LOGGER.warn("ParCool found but its API differs; its actions only show as our animation", incompatible);
                }
            }
        }
    }

    private static final class Watut {
        private boolean resolved;
        private Method manager;
        private Method status;
        private Method lerping;
        private Method gui;
        private Method idle;

        boolean animating(AbstractClientPlayer player) {
            if (!this.resolved) {
                this.resolved = true;
                Class<?> mod = ExternalCapture.find("com.corosus.watut.WatutMod");
                Class<?> playerStatus = ExternalCapture.find("com.corosus.watut.PlayerStatus");
                if (mod != null && playerStatus != null) {
                    try {
                        this.manager = mod.getMethod("getPlayerStatusManagerClient");
                        this.status = this.manager.getReturnType().getMethod("getStatus", UUID.class);
                        this.lerping = playerStatus.getMethod("isLerping");
                        this.gui = playerStatus.getMethod("getPlayerGuiState");
                        this.idle = playerStatus.getMethod("isIdle");
                        SpaPacks.LOGGER.info("WATUT found; its arm and head poses are blended into our animation");
                    } catch (ReflectiveOperationException | LinkageError incompatible) {
                        this.manager = null;
                        SpaPacks.LOGGER.warn("WATUT found but its API differs; its poses are not shown", incompatible);
                    }
                }
            }

            if (this.manager == null) {
                return false;
            }

            try {
                Object statuses = this.manager.invoke(null);
                Object s = statuses == null ? null : this.status.invoke(statuses, player.getUUID());
                if (s == null) {
                    return false;
                }

                Object state = this.gui.invoke(s);
                return Boolean.TRUE.equals(this.lerping.invoke(s))
                    || state instanceof Enum<?> e && !e.name().equals("NONE")
                    || Boolean.TRUE.equals(this.idle.invoke(s));
            } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
                this.manager = null;
                SpaPacks.LOGGER.warn("WATUT answered unexpectedly; its poses are no longer shown", e);
                return false;
            }
        }
    }
}
