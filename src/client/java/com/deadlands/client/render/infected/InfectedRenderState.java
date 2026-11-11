package com.deadlands.client.render.infected;

import com.deadlands.entity.InfectedKind;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.AnimationState;
import org.jspecify.annotations.Nullable;

/** Everything the infected models and renderer need from one infected, copied each frame. */
public class InfectedRenderState extends LivingEntityRenderState {
	public InfectedKind kind = InfectedKind.WALKER;
	public @Nullable Identifier texture;
	public @Nullable Identifier glowTexture;
	/** What happened to this one (blood, mud, wounds, ash, bites), laid over its look; null for none. */
	public @Nullable Identifier wearTexture;
	/** Optional model parts the variant shows ({@link com.deadlands.entity.InfectedVariants} flags). */
	public int parts;
	public float build = 1.0F;
	public float heightScale = 1.0F;
	/** Stable per-entity number for the small asymmetries (a limp, a raised arm, a tilted head). */
	public int seed;
	public boolean breaching;
	public boolean screaming;
	public boolean climbing;
	public boolean swelling;
	public boolean sprinting;

	public final AnimationState attackAnimationState = new AnimationState();
	public final AnimationState screamAnimationState = new AnimationState();
	public final AnimationState slamAnimationState = new AnimationState();
	public final AnimationState pounceAnimationState = new AnimationState();
	public final AnimationState breachAnimationState = new AnimationState();
	public final AnimationState emergeAnimationState = new AnimationState();
	public final AnimationState swellAnimationState = new AnimationState();

	public boolean has(int part) {
		return (parts & part) != 0;
	}

	/** A seeded value in [0, 1) for {@code salt}, the same every frame for one infected. */
	public float seeded(int salt) {
		int h = seed * 0x9E3779B1 + salt * 0x85EBCA77;
		h ^= h >>> 15;
		h *= 0x2C1B3C6D;
		h ^= h >>> 12;
		return (h & 0xFFFF) / 65536.0F;
	}
}
