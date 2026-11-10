package com.deadlands.client.render.boss;

import com.deadlands.Deadlands;
import com.deadlands.entity.BossEntity;
import com.deadlands.progression.BossKind;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

/**
 * Draws a boss: its own rig ({@link BossGeometry}) and texture, animated by {@link Model}: a heavy
 * walk, breathing, the head following its target, and each attack's wind-up and blow (from the synced
 * attack and how far through it is).
 */
public class BossRenderer extends MobRenderer<BossEntity, BossRenderer.State, BossRenderer.Model> {
	/** What the model needs from one boss, copied each frame. */
	public static class State extends LivingEntityRenderState {
		public BossKind kind = BossKind.ABOMINATION;
		public byte attack;
		public float attackProgress;
	}

	/** The rig, with its parts found by name; parts a rig does not have are skipped. */
	public static class Model extends EntityModel<State> {
		private final @Nullable ModelPart body;
		private final @Nullable ModelPart head;
		private final @Nullable ModelPart armR;
		private final @Nullable ModelPart armL;
		private final @Nullable ModelPart legR;
		private final @Nullable ModelPart legL;
		private final @Nullable ModelPart jaw;
		private final @Nullable ModelPart[] extras = new ModelPart[6];
		private final @Nullable ModelPart[] tendrils = new ModelPart[4];

		public Model(ModelPart root) {
			super(root);
			ModelPart base = root.getChild("root");
			body = find(base, "body");
			head = body == null ? null : find(body, "head");
			armR = body == null ? null : find(body, "arm_r");
			armL = body == null ? null : find(body, "arm_l");
			legR = find(base, "leg_r");
			legL = find(base, "leg_l");
			jaw = head == null ? null : find(head, "jaw");
			String[] extraNames = {"head2", "head3", "arm_r2", "arm_l2"};
			for (int i = 0; i < extraNames.length; i++) {
				extras[i] = body == null ? null : find(body, extraNames[i]);
			}
			for (int i = 0; i < 4; i++) {
				tendrils[i] = body == null ? null : find(body, "tendril_" + (i + 1));
			}
		}

		private static @Nullable ModelPart find(ModelPart parent, String name) {
			return parent.hasChild(name) ? parent.getChild(name) : null;
		}

		@Override
		public void setupAnim(State state) {
			super.setupAnim(state);
			float walk = state.walkAnimationPos;
			float speed = Math.min(1.0F, state.walkAnimationSpeed);
			float age = state.ageInTicks;
			float swing = Mth.cos(walk * 0.45F) * 0.9F * speed;
			if (legR != null) {
				legR.xRot = swing;
			}
			if (legL != null) {
				legL.xRot = -swing;
			}
			if (armR != null) {
				armR.xRot = -swing * 0.6F;
				armR.zRot = 0.08F;
			}
			if (armL != null) {
				armL.xRot = swing * 0.6F;
				armL.zRot = -0.08F;
			}
			if (body != null) {
				body.xRot = (state.kind == BossKind.EXPERIMENT ? 0.45F : state.kind == BossKind.ABOMINATION ? 0.12F : 0.0F) + Mth.sin(age * 0.08F) * 0.03F;
				body.zRot = Mth.sin(walk * 0.22F) * 0.05F * speed;
			}
			if (head != null) {
				head.yRot = state.yRot * Mth.DEG_TO_RAD * 0.8F;
				head.xRot = state.xRot * Mth.DEG_TO_RAD * 0.8F - (body != null ? body.xRot * 0.8F : 0.0F);
			}
			for (int i = 0; i < 4; i++) {
				if (extras[i] != null) {
					extras[i].xRot = Mth.sin(age * 0.12F + i * 1.7F) * (i < 2 ? 0.15F : 0.5F);
				}
				if (tendrils[i] != null) {
					tendrils[i].yRot = Mth.sin(age * 0.15F + i * 1.3F) * 0.6F + (i % 2 == 0 ? -0.3F : 0.3F);
					tendrils[i].xRot = Mth.cos(age * 0.11F + i) * 0.4F - 0.3F;
				}
			}
			if (jaw != null) {
				jaw.xRot = 0.15F + Mth.sin(age * 0.2F) * 0.05F;
			}
			attack(state);
		}

		/** Wind-up over the first part of the attack, the blow after it, recovery to the end. */
		private void attack(State state) {
			float p = state.attackProgress;
			float windUp = p < 0.5F ? p / 0.5F : 1.0F;
			float blow = p < 0.5F ? 0.0F : Math.min(1.0F, (p - 0.5F) / 0.15F);
			float recover = p > 0.7F ? (p - 0.7F) / 0.3F : 0.0F;
			float raise = windUp * (1.0F - blow);
			switch (state.attack) {
				case BossEntity.SWING -> {
					if (armR != null) {
						armR.xRot = Mth.lerp(recover, -2.6F * raise + 0.4F * blow, armR.xRot);
					}
				}
				case BossEntity.SLAM, BossEntity.POUND -> {
					float arms = -3.0F * raise + 0.3F * blow;
					if (armR != null) {
						armR.xRot = Mth.lerp(recover, arms, armR.xRot);
					}
					if (armL != null) {
						armL.xRot = Mth.lerp(recover, arms, armL.xRot);
					}
					if (body != null) {
						body.xRot += (-0.25F * raise + 0.45F * blow) * (1.0F - recover);
					}
					if (state.attack == BossEntity.POUND && legR != null) {
						legR.xRot = -1.0F * raise * (1.0F - recover);
					}
				}
				case BossEntity.THROW -> {
					if (armR != null && armL != null) {
						armR.xRot = -1.5F * windUp - 1.4F * blow * (1.0F - recover);
						armL.xRot = armR.xRot;
					}
				}
				case BossEntity.HOOK -> {
					if (armL != null) {
						armL.xRot = (-2.4F * raise - 1.6F * blow) * (1.0F - recover);
						armL.zRot = -0.3F * raise;
					}
				}
				case BossEntity.BASH -> {
					if (armL != null) {
						armL.xRot = (-0.6F * raise - 1.4F * blow) * (1.0F - recover);
						armL.yRot = 0.5F * blow * (1.0F - recover);
					}
				}
				case BossEntity.CALL, BossEntity.ROAR, BossEntity.SUMMON -> {
					float open = Mth.sin(p * Mth.PI);
					if (head != null) {
						head.xRot -= 0.7F * open;
					}
					if (armR != null) {
						armR.zRot = 1.1F * open;
					}
					if (armL != null) {
						armL.zRot = -1.1F * open;
					}
					if (jaw != null) {
						jaw.xRot = 0.15F + 0.8F * open;
					}
				}
				case BossEntity.LEAP -> {
					float crouch = p < 0.2F ? p / 0.2F : Math.max(0.0F, 1.0F - (p - 0.2F) / 0.1F);
					if (legR != null && legL != null) {
						legR.xRot = -0.9F * crouch + 0.6F * blow;
						legL.xRot = legR.xRot;
					}
					if (armR != null && armL != null) {
						armR.xRot = -2.8F * (1.0F - crouch) * (1.0F - recover);
						armL.xRot = armR.xRot;
					}
				}
				case BossEntity.SPIT -> {
					if (head != null) {
						head.xRot += 0.4F * raise - 0.2F * blow;
					}
					if (jaw != null) {
						jaw.xRot = 0.15F + 1.0F * blow * (1.0F - recover);
					}
				}
				case BossEntity.BLINK -> {
					float fold = Mth.sin(p * Mth.PI);
					if (armR != null && armL != null) {
						armR.zRot = -0.6F * fold;
						armL.zRot = 0.6F * fold;
					}
					for (ModelPart tendril : tendrils) {
						if (tendril != null) {
							tendril.xRot = -1.4F * fold;
						}
					}
				}
				default -> {
				}
			}
		}
	}

	private final Identifier texture;

	public BossRenderer(EntityRendererProvider.Context context, BossKind kind) {
		super(context, new Model(context.bakeLayer(layer(kind))), kind == BossKind.ABOMINATION ? 1.6F : 0.8F);
		this.texture = Deadlands.id("textures/entity/boss/" + kind.id() + ".png");
	}

	public static ModelLayerLocation layer(BossKind kind) {
		return new ModelLayerLocation(Deadlands.id("boss_" + kind.id()), "main");
	}

	public static void registerLayers() {
		register(BossKind.ABOMINATION, BossGeometry::abomination);
		register(BossKind.BUTCHER, BossGeometry::butcher);
		register(BossKind.WARDEN, BossGeometry::warden);
		register(BossKind.EXPERIMENT, BossGeometry::experiment);
		register(BossKind.PATIENT_ZERO, BossGeometry::patient_zero);
	}

	private static void register(BossKind kind, Supplier<LayerDefinition> geometry) {
		EntityModelLayerRegistry.registerModelLayer(layer(kind), geometry::get);
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(BossEntity boss, State state, float partialTick) {
		super.extractRenderState(boss, state, partialTick);
		state.kind = boss.kind();
		state.attack = boss.attack();
		state.attackProgress = boss.attackProgress(partialTick);
	}

	@Override
	public Identifier getTextureLocation(State state) {
		return texture;
	}
}
