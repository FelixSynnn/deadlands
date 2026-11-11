package com.deadlands.client.render.infected;

import com.deadlands.Deadlands;
import com.deadlands.entity.InfectedEntity;
import com.deadlands.entity.InfectedKind;
import com.deadlands.entity.InfectedVariants;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.LivingEntityEmissiveLayer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

/**
 * Draws one infected kind: the rig for the kind, the variant's texture, its size (the kind's height,
 * times the infected's own build and height), and, for the charred and the stalker, a faint glow
 * layer (embers in the burns, the stalker's eyes) from {@code <variant>_glow.png}.
 */
public class InfectedRenderer extends MobRenderer<InfectedEntity, InfectedRenderState, InfectedModel> {
	/** Model height in blocks before scaling: 32 px for the humanoid rig, 38 px for the brute. */
	private static final float HUMANOID_HEIGHT = 2.0F;
	private static final float BRUTE_HEIGHT = 38.0F / 16.0F;

	private final InfectedKind kind;
	private final Identifier[] textures;
	private final @Nullable Identifier[] glows;
	/** The rig's wear overlays (blood, a feeding, mud, gashes, ash, bites), one chosen per infected. */
	private final Identifier[] wear;
	private static final int WEAR_OVERLAYS = 6;
	private final float baseScale;
	private final float widthFactor;

	public InfectedRenderer(EntityRendererProvider.Context context, InfectedKind kind) {
		super(context, createModel(context, kind), kind.width() * 0.6F);
		this.kind = kind;
		List<InfectedVariants.Variant> variants = kind.variants();
		this.textures = new Identifier[variants.size()];
		this.glows = new Identifier[variants.size()];
		boolean anyGlow = false;
		for (int i = 0; i < variants.size(); i++) {
			String base = "textures/entity/infected/" + kind.id() + "/" + variants.get(i).name();
			textures[i] = Deadlands.id(base + ".png");
			Identifier glow = Deadlands.id(base + "_glow.png");
			if (context.getResourceManager().getResource(glow).isPresent()) {
				glows[i] = glow;
				anyGlow = true;
			}
		}
		this.baseScale = baseScale(kind);
		this.widthFactor = widthFactor(kind);
		String rig = kind.rig() == InfectedKind.Rig.BRUTE ? "brute" : "humanoid";
		this.wear = new Identifier[WEAR_OVERLAYS];
		for (int i = 0; i < WEAR_OVERLAYS; i++) {
			wear[i] = Deadlands.id("textures/entity/infected/wear/" + rig + "_" + i + ".png");
		}
		addLayer(new InfectedWearLayer(this));
		if (anyGlow) {
			addLayer(new LivingEntityEmissiveLayer<>(this, state -> state.glowTexture, InfectedRenderer::glowAlpha, getModel(),
				RenderTypes::entityTranslucentEmissive, false));
		}
	}

	private static InfectedModel createModel(EntityRendererProvider.Context context, InfectedKind kind) {
		return kind.rig() == InfectedKind.Rig.BRUTE
			? new BruteInfectedModel(context.bakeLayer(InfectedLayers.BRUTE))
			: new HumanoidInfectedModel(context.bakeLayer(InfectedLayers.HUMANOID));
	}

	/** How much to scale the rig so it stands as tall as the kind's hitbox (crawlers and stalkers are posed lower instead). */
	private static float baseScale(InfectedKind kind) {
		return switch (kind) {
			case BRUTE -> kind.height() / BRUTE_HEIGHT;
			case CRAWLER -> 0.95F;
			case STALKER -> 1.0F;
			default -> kind.height() / HUMANOID_HEIGHT;
		};
	}

	/** Thin screamers and stalkers, wiry runners, a wide bloater. */
	private static float widthFactor(InfectedKind kind) {
		return switch (kind) {
			case SCREAMER -> 0.88F;
			case STALKER -> 0.9F;
			case RUNNER -> 0.94F;
			case BLOATER -> 1.12F;
			default -> 1.0F;
		};
	}

	/** The glow breathes slowly; stalker eyes are steadier than charred embers. */
	private static float glowAlpha(InfectedRenderState state, float age) {
		if (state.glowTexture == null) {
			return 0.0F;
		}
		if (state.kind == InfectedKind.STALKER) {
			return 0.85F + 0.1F * Mth.sin(age * 0.05F);
		}
		return 0.55F + 0.3F * Math.max(0.0F, Mth.sin(age * 0.11F + state.seed)) + 0.1F * Mth.sin(age * 0.7F);
	}

	@Override
	public InfectedRenderState createRenderState() {
		return new InfectedRenderState();
	}

	@Override
	public void extractRenderState(InfectedEntity entity, InfectedRenderState state, float partialTick) {
		super.extractRenderState(entity, state, partialTick);
		int variant = Mth.clamp(entity.variantIndex(), 0, textures.length - 1);
		InfectedVariants.Variant info = kind.variants().get(variant);
		state.kind = kind;
		state.texture = textures[variant];
		state.glowTexture = glows[variant];
		state.parts = info.parts();
		state.build = entity.buildScale();
		state.heightScale = entity.heightScale();
		state.seed = entity.getUUID().hashCode() & 0x7FFFFFFF;
		// Six overlays and two chances in eight of none: with the looks, builds and heights, no two match.
		int wearPick = (state.seed >>> 11) % (WEAR_OVERLAYS + 2);
		state.wearTexture = wearPick < WEAR_OVERLAYS ? wear[wearPick] : null;
		state.breaching = entity.hasFlag(InfectedEntity.FLAG_BREACHING);
		state.screaming = entity.hasFlag(InfectedEntity.FLAG_SCREAMING);
		state.climbing = entity.hasFlag(InfectedEntity.FLAG_CLIMBING);
		state.swelling = entity.hasFlag(InfectedEntity.FLAG_SWELLING);
		state.sprinting = entity.hasFlag(InfectedEntity.FLAG_SPRINTING);
		state.attackAnimationState.copyFrom(entity.attackAnimationState);
		state.screamAnimationState.copyFrom(entity.screamAnimationState);
		state.slamAnimationState.copyFrom(entity.slamAnimationState);
		state.pounceAnimationState.copyFrom(entity.pounceAnimationState);
		state.breachAnimationState.copyFrom(entity.breachAnimationState);
		state.emergeAnimationState.copyFrom(entity.emergeAnimationState);
		state.swellAnimationState.copyFrom(entity.swellAnimationState);
	}

	/** Each infected's skin and clothes are a shade lighter or darker, warmer or colder, than the next one's. */
	@Override
	protected int getModelTint(InfectedRenderState state) {
		int seed = state.seed;
		float shade = 0.86F + ((seed >>> 3) & 15) / 15.0F * 0.14F;
		float warm = (((seed >>> 7) & 15) / 15.0F - 0.5F) * 0.07F;
		int r = Mth.clamp(Math.round((shade + warm) * 255.0F), 0, 255);
		int g = Mth.clamp(Math.round(shade * 255.0F), 0, 255);
		int b = Mth.clamp(Math.round((shade - warm) * 255.0F), 0, 255);
		return ARGB.color(255, r, g, b);
	}

	@Override
	public Identifier getTextureLocation(InfectedRenderState state) {
		return state.texture != null ? state.texture : textures[0];
	}

	@Override
	protected void scale(InfectedRenderState state, PoseStack poseStack) {
		float width = baseScale * widthFactor * state.build;
		poseStack.scale(width, baseScale * state.heightScale, width);
	}

	/** A bloater about to burst shudders. */
	@Override
	protected boolean isShaking(InfectedRenderState state) {
		return super.isShaking(state) || state.swelling;
	}
}
