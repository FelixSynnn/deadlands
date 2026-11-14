package com.huaxiaosheng.spapacks.client;

import com.huaxiaosheng.spapacks.anim.graph.Plan;
import com.huaxiaosheng.spapacks.animation.external.ExternalPose;
import com.huaxiaosheng.spapacks.platform.Game;
import com.huaxiaosheng.spapacks.rig.Anchors;
import com.huaxiaosheng.spapacks.rig.Palette;
import com.huaxiaosheng.spapacks.rig.RigSurface;
import com.huaxiaosheng.spapacks.rig.Skeleton;
import com.huaxiaosheng.spapacks.rig.SkinnedMesh;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.HumanoidArm;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class RigPlayerModel extends PlayerModel implements RigRoots.Drawn {
    private static final Map<Object, RigPlayerModel.Frame> FRAMES = new WeakHashMap<>();
    private final SkinnedMesh mesh = SkinnedMesh.ALEX;
    private final RigSurface skin;
    private final RigSurface outer;
    private final ModelPart[] parts;
    private final ModelPart[] layers;
    private final boolean[] layerShown = new boolean[6];
    private final int[] bones;
    private final float[][] pivots;
    private final ModelPart[] external;
    private final float[][] vanilla = new float[6][6];
    private boolean vanillaNoted;
    private final ExternalPose externalPose = new ExternalPose();
    private final float[][] defaults = new float[6][3];
    private final Matrix4f toModel = new Matrix4f().translation(0.0F, 1.501F, 0.0F).scale(1.0666667F, -1.0666667F, -1.0666667F);
    private final Matrix4f fromModel = new Matrix4f(this.toModel).invert();
    private final Matrix4f matrix = new Matrix4f();
    private final Matrix4f rotationMatrix = new Matrix4f();
    private int backBone = -1;
    private final Vector3f vector = new Vector3f();
    private final Vector3f angles = new Vector3f();
    private final Quaternionf rotation = new Quaternionf();
    private final boolean slimArms;
    private final int[] bendBones;
    private final RigPlayerModel.Frame own = new RigPlayerModel.Frame();
    private RigPlayerModel.Frame shown = this.own;
    private RigPlayerModel.Frame sampledFrame;
    private int sampledVersion;

    public RigPlayerModel(ModelPart root, boolean slim) {
        super(root, slim);
        this.slimArms = slim;
        this.skin = new RigSurface(this.mesh, slim, false, 0.0F);
        this.outer = new RigSurface(this.mesh, slim, true, 0.0F);
        this.parts = parts(this);
        this.layers = new ModelPart[]{this.hat, this.jacket, this.rightSleeve, this.leftSleeve, this.rightPants, this.leftPants};
        this.external = new ModelPart[]{this.body, this.head, this.rightArm, this.leftArm, this.rightLeg, this.leftLeg};

        for (int i = 0; i < 6; i++) {
            PartPose d = this.external[i].getInitialPose();
            this.defaults[i][0] = Game.x(d);
            this.defaults[i][1] = Game.y(d);
            this.defaults[i][2] = Game.z(d);
        }

        this.bones = new int[]{
            this.mesh.boneIndex("head"),
            this.mesh.boneIndex("torso_upper"),
            this.mesh.boneIndex("right_arm_lower_def"),
            this.mesh.boneIndex("left_arm_lower_def"),
            this.mesh.boneIndex("right_leg"),
            this.mesh.boneIndex("left_leg")
        };
        this.pivots = new float[6][3];
        this.bendBones = new int[RigBend.BONES.length];

        for (int i = 0; i < this.bendBones.length; i++) {
            this.bendBones[i] = this.mesh.boneIndex(RigBend.BONES[i]);
        }

        for (int i = 0; i < 6; i++) {
            ModelPart p = this.parts[i];
            this.pivots[i] = new float[]{p.x / 16.0F, p.y / 16.0F, p.z / 16.0F};
        }

        if (slim) {
            this.pivots[2][0] = this.pivots[2][0] + 0.03125F;
            this.pivots[3][0] = this.pivots[3][0] - 0.03125F;
            this.pivots[2][1] = this.pivots[2][1] - 0.03125F;
            this.pivots[3][1] = this.pivots[3][1] - 0.03125F;
        }

        RigRoots.register(root, this);
    }

    static ModelPart[] parts(HumanoidModel<?> model) {
        return new ModelPart[]{model.head, model.body, model.rightArm, model.leftArm, model.rightLeg, model.leftLeg};
    }

    static void place(ModelPart part, float[] posed) {
        part.setPos(posed[0], posed[1], posed[2]);
        part.xRot = posed[3];
        part.yRot = posed[4];
        part.zRot = posed[5];
    }

    public static void extracted(Object state, AbstractClientPlayer player) {
        RigPlayerModel.Frame frame = PlayerAnimations.frames(player).take();
        FRAMES.put(state, frame);
        frame.player = player;
        frame.stale = true;
        frame.capeLaid = false;
    }

    static RigPlayerModel.Frame frameFor(Object state) {
        return FRAMES.get(state);
    }

    public static AbstractClientPlayer playerOf(Object state) {
        RigPlayerModel.Frame frame = FRAMES.get(state);
        return frame == null ? null : frame.player;
    }

    static RigPlayerModel.Frame frameOf(Object state) {
        RigPlayerModel.Frame frame = FRAMES.get(state);
        return frame != null && !frame.stale ? frame : null;
    }

    @Override
    public void setupAnim(AvatarRenderState state) {
        this.begin();
        super.setupAnim(state);
        RigPlayerModel.Frame frame = FRAMES.get(state);
        if (frame != null && frame.player != null) {
            try {
                if (frame.stale) {
                    this.pose(frame, frame.player, state.ageInTicks, state.yRot, state.xRot);
                }

                this.show(frame);
            } catch (Throwable e) {
                this.failed(e);
            }
        } else {
            this.shown = this.own;
            this.own.custom = false;
            this.own.vanillaPosed = false;
            RigBend.end();
        }
    }

    private void failed(Throwable e) {
        SafeMode.fail("posing the player", e);
        this.shown = this.own;
        this.own.custom = false;
        this.own.vanillaPosed = false;
        RigBend.end();
    }

    private void begin() {
        for (ModelPart p : this.parts) {
            p.resetPose();
        }

        this.vanillaNoted = false;
    }

    private void pose(RigPlayerModel.Frame frame, AbstractClientPlayer player, float age, float headYaw, float headPitch) {
        frame.stale = false;
        frame.version++;
        frame.vanillaPosed = false;
        if (age != 0.0F && ModConfig.on()) {
            float frameDelta = Game.partialTick();
            PlayerAnimations.decide(player, (player.tickCount + frameDelta) / 20.0, headYaw, headPitch);
        }

        frame.custom = age != 0.0F && PlayerAnimations.supported(player);
        frame.posing = frame.custom ? player : null;
        if (!frame.custom) {
            if (age != 0.0F) {
                PlayerAnimations.suspend(player);
                if (PlayerAnimations.cemSide()) {
                    this.poseCem(frame, player, headYaw, headPitch);
                }
            }
        } else {
            PunchyMoves.restRoot(this);
            float tickDelta = Game.partialTick();
            ExternalPose external = this.capture(player);
            PlayerAnimations.sample(
                player, (player.tickCount + tickDelta) / 20.0, this.vanillaNoted ? this.vanilla : null, external, frame.palette, frame.items, frame.itemOffsets
            );
            if (this.backBone < 0) {
                this.backBone = this.mesh.boneIndex("torso_upper");
            }

            float[] c = frame.palette[this.backBone];
            Palette.toMatrix(c, frame.back).mul(this.fromModel).mulLocal(this.toModel);
            Plan plan = PlayerAnimations.plan(player);

            for (int i = 0; i < 6; i++) {
                float[] m = frame.palette[this.bones[i]];
                Palette.toMatrix(m, this.matrix);
                this.matrix.mul(this.fromModel);
                this.matrix.mulLocal(this.toModel);
                if (i == 2 || i == 3) {
                    Matrix4f hand = frame.handTransforms[i - 2].set(this.matrix).translate(this.pivots[i][0], this.pivots[i][1], this.pivots[i][2]);
                    Anchors.holdItem(hand, i == 3, frame.items[i - 2], frame.itemOffsets[i - 2]);
                    if (plan != null && (i == 2 ? plan.lanternRight : plan.lanternLeft)) {
                        LanternHang.hang(hand, i == 2, (float)Math.toRadians(plan.lanternPitch), (float)Math.toRadians(plan.lanternRoll));
                    }
                }

                this.matrix.transformPosition(this.pivots[i][0], this.pivots[i][1], this.pivots[i][2], this.vector);
                this.rotationMatrix.rotation(this.matrix.getUnnormalizedRotation(this.rotation).normalize()).getEulerAnglesZYX(this.angles);
                float[] posed = frame.posed[i];
                posed[0] = this.vector.x * 16.0F;
                posed[1] = this.vector.y * 16.0F;
                posed[2] = this.vector.z * 16.0F;
                posed[3] = this.angles.x;
                posed[4] = this.angles.y;
                posed[5] = this.angles.z;
            }

            for (int i = 0; i < this.bendBones.length; i++) {
                float[] m = frame.palette[this.bendBones[i]];
                Palette.toMatrix(m, frame.bendMatrices[i]).mul(this.fromModel).mulLocal(this.toModel);
            }
        }
    }

    private void poseCem(RigPlayerModel.Frame frame, AbstractClientPlayer player, float headYaw, float headPitch) {
        float tickDelta = Game.partialTick();
        boolean punchy = PunchyMoves.drives();
        if (punchy && this.vanillaNoted) {
            this.captureVanilla();
        }

        ExternalPose external = this.capture(player);

        for (int i = 0; i < 6; i++) {
            ModelPart p = this.external[i];
            float[] v = frame.cemParts[i];
            v[0] = p.xRot;
            v[1] = p.yRot;
            v[2] = p.zRot;
            v[3] = p.x;
            v[4] = p.y;
            v[5] = p.z;
            v[6] = p.xScale;
            v[7] = p.yScale;
            v[8] = p.zScale;
        }

        double time = (player.tickCount + tickDelta) / 20.0;
        PlayerAnimations.sampleVanilla(
            player,
            time,
            tickDelta,
            headYaw,
            headPitch,
            this.vanillaNoted ? this.vanilla : null,
            this.defaults,
            external,
            frame.cemParts,
            frame.root,
            frame.heldItems,
            frame.packRoot,
            punchy
        );
        frame.vanillaPosed = true;
        if (!punchy && TouchNMotion.present()) {
            this.applyCem(frame);
            if (TouchNMotion.pose(player, this::part)) {
                for (int i = 0; i < 6; i++) {
                    ModelPart p = this.external[i];
                    float[] v = frame.cemParts[i];
                    v[0] = p.xRot;
                    v[1] = p.yRot;
                    v[2] = p.zRot;
                    v[3] = p.x;
                    v[4] = p.y;
                    v[5] = p.z;
                    v[6] = p.xScale;
                    v[7] = p.yScale;
                    v[8] = p.zScale;
                }
            }
        }
    }

    private ModelPart part(String name) {
        return switch (name) {
            case "head" -> this.head;
            case "body" -> this.body;
            case "right_arm" -> this.rightArm;
            case "left_arm" -> this.leftArm;
            case "right_leg" -> this.rightLeg;
            case "left_leg" -> this.leftLeg;
            case "hat" -> this.hat;
            case "jacket" -> this.jacket;
            case "right_sleeve" -> this.rightSleeve;
            case "left_sleeve" -> this.leftSleeve;
            case "right_pants" -> this.rightPants;
            case "left_pants" -> this.leftPants;
            default -> null;
        };
    }

    private ExternalPose capture(AbstractClientPlayer player) {
        ExternalCapture.read(player, this.external, this.vanillaNoted ? this.vanilla : null, this.externalPose);
        if (!ModConfig.get().externalAnimations) {
            this.externalPose.vanillaOnly();
        }

        return this.externalPose;
    }

    private void show(RigPlayerModel.Frame frame) {
        this.shown = frame;
        if (frame.custom) {
            this.applyPosed();
            RigBend.begin(frame.posed, frame.bendMatrices, frame.palette);
        } else {
            RigBend.end();
            if (frame.vanillaPosed) {
                this.applyCem(frame);
            }
        }
    }

    private void applyCem(RigPlayerModel.Frame frame) {
        for (int i = 0; i < 6; i++) {
            ModelPart p = this.external[i];
            float[] v = frame.cemParts[i];
            p.xRot = v[0];
            p.yRot = v[1];
            p.zRot = v[2];
            p.setPos(v[3], v[4], v[5]);
            p.xScale = v[6];
            p.yScale = v[7];
            p.zScale = v[8];
        }
    }

    public float[] cemRoot() {
        return this.shown.vanillaPosed && !this.shown.custom ? this.shown.root : null;
    }

    public float[] cemPackRoot() {
        return this.shown.vanillaPosed && !this.shown.custom ? this.shown.packRoot : null;
    }

    public float[] item(HumanoidArm arm) {
        return this.shown.vanillaPosed && !this.shown.custom ? this.shown.heldItems[arm == HumanoidArm.RIGHT ? 0 : 1] : null;
    }

    public boolean back(Matrix4f out) {
        if (!this.shown.custom) {
            return false;
        }

        out.set(this.shown.back);
        return true;
    }

    boolean drawsFace() {
        return this.shown.custom || PlayerAnimations.cemSide();
    }

    boolean drawsRig() {
        return this.shown.custom;
    }

    public void captureVanilla() {
        for (int i = 0; i < 6; i++) {
            ModelPart p = this.external[i];
            float[] v = this.vanilla[i];
            v[0] = p.x;
            v[1] = p.y;
            v[2] = p.z;
            v[3] = p.xRot;
            v[4] = p.yRot;
            v[5] = p.zRot;
        }

        this.vanillaNoted = true;
    }

    private void applyPosed() {
        for (int i = 0; i < 6; i++) {
            ModelPart p = this.parts[i];
            place(p, this.shown.posed[i]);
        }
    }

    @Override
    public boolean drawRig(PoseStack matrices, VertexConsumer vertices, int light, int overlay, int color) {
        RigPlayerModel.Frame frame = this.shown;
        if (!frame.custom) {
            return false;
        }

        this.applyPosed();
        if (frame.posing != null) {
            AnchorTracker.drawn(frame.posing, matrices.last().pose(), frame.palette, frame.items, frame.itemOffsets, this.slimArms);
        }

        if (this.sampledFrame != frame || this.sampledVersion != frame.version) {
            this.skin.sample(frame.palette);
            this.outer.sample(frame.palette);
            this.sampledFrame = frame;
            this.sampledVersion = frame.version;
        }

        AlexRenderer.draw(this.mesh, this.skin, this.parts, matrices, vertices, light, overlay, color);

        for (int i = 0; i < 6; i++) {
            this.layerShown[i] = this.layers[i].visible;
            this.layers[i].visible = this.layers[i].visible & this.parts[i].visible;
        }

        AlexRenderer.draw(this.mesh, this.outer, this.layers, matrices, vertices, light, overlay, color);

        for (int i = 0; i < 6; i++) {
            this.layers[i].visible = this.layerShown[i];
        }

        return true;
    }

    @Override
    public void translateToHand(AvatarRenderState state, HumanoidArm arm, PoseStack matrices) {
        RigPlayerModel.Frame frame = frameOf(state);
        this.translateToHand(frame != null ? frame : this.shown, arm, matrices);
    }

    private void translateToHand(RigPlayerModel.Frame frame, HumanoidArm arm, PoseStack matrices) {
        if (frame.custom) {
            Game.multiply(matrices, frame.handTransforms[arm == HumanoidArm.RIGHT ? 0 : 1]);
        } else {
            (arm == HumanoidArm.RIGHT ? this.rightArm : this.leftArm).translateAndRotate(matrices);
        }
    }

    private void lend(HumanoidModel<?> model) {
        if (model instanceof RigArmorModel armor) {
            armor.follow(this.shown.custom ? this.shown.palette : null);
        }
    }

    public static void follow(HumanoidModel<?> model, Object state) {
        if (model instanceof RigArmorModel armor && state != null) {
            armor.showParts(state);
        }

        RigPlayerModel.Frame frame = frameOf(state);
        if (frame != null && frame.custom) {
            ModelPart[] parts = parts(model);

            for (int i = 0; i < 6; i++) {
                place(parts[i], frame.posed[i]);
            }

            if (model instanceof RigArmorModel armor) {
                armor.follow(frame.palette);
            }

            RigBend.begin(frame.posed, frame.bendMatrices, frame.palette);
        } else {
            RigBend.end();
            if (model instanceof RigArmorModel armor) {
                armor.follow(null);
            }

            if (frame != null && frame.vanillaPosed) {
                ModelPart[] parts = new ModelPart[]{model.body, model.head, model.rightArm, model.leftArm, model.rightLeg, model.leftLeg};

                for (int i = 0; i < 6; i++) {
                    float[] v = frame.cemParts[i];
                    ModelPart p = parts[i];
                    p.xRot = v[0];
                    p.yRot = v[1];
                    p.zRot = v[2];
                    p.setPos(v[3], v[4], v[5]);
                    p.xScale = v[6];
                    p.yScale = v[7];
                    p.zScale = v[8];
                }
            }
        }
    }

    static final class Frame {
        AbstractClientPlayer player;
        boolean stale = true;
        int version;
        boolean custom;
        AbstractClientPlayer posing;
        final float[][] palette = new float[Skeleton.ALEX.boneCount()][12];
        final Quaternionf[] items = new Quaternionf[]{new Quaternionf(), new Quaternionf()};
        final Vector3f[] itemOffsets = new Vector3f[]{new Vector3f(), new Vector3f()};
        final float[][] posed = new float[6][6];
        final Matrix4f back = new Matrix4f();
        final Matrix4f[] handTransforms = new Matrix4f[]{new Matrix4f(), new Matrix4f()};
        final Matrix4f[] bendMatrices = new Matrix4f[RigBend.BONES.length];
        boolean vanillaPosed;
        final float[][] cemParts = new float[6][9];
        final float[] root = new float[10];
        final float[] packRoot = new float[]{0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F};
        final float[][] heldItems = new float[2][10];
        boolean capeLaid;
        boolean capeCrouching;
        boolean capeShifted;
        final Matrix4f capeWorld = new Matrix4f();

        Frame() {
            for (int i = 0; i < this.bendMatrices.length; i++) {
                this.bendMatrices[i] = new Matrix4f();
            }
        }
    }

    static final class Pool {
        final RigPlayerModel.Frame[] frames = new RigPlayerModel.Frame[8];
        int next;

        RigPlayerModel.Frame take() {
            int i = this.next;
            this.next = (i + 1) % this.frames.length;
            if (this.frames[i] == null) {
                this.frames[i] = new RigPlayerModel.Frame();
            }

            return this.frames[i];
        }
    }
}
