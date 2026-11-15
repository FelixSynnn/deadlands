package com.deadlands.client.render;

import com.deadlands.Deadlands;
import com.deadlands.entity.MythosEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;

/**
 * Mythos: the owner's Mythos.glb, scaled up to 4.2 blocks, rigged and animated in Blender
 * (tools/assetgen/blender/mythos_build.py, mythos_anims.py), drawn by {@link RiggedMesh.Layer} on {@link MythosGeometry}
 * and posed by {@link MythosModel}. His eyes, corruption spots, core and claw tips glow. He glitches: now and then he
 * drops out for a frame, more often enraged.
 */
public class MythosRenderer extends MobRenderer<MythosEntity, MythosRenderer.State, MythosModel> {
	private static final Identifier MESH = Deadlands.id("models/entity/mythos.bin");
	private static final Identifier TEXTURE = Deadlands.id("textures/entity/mythos/mythos_0.png");

	public static class State extends LivingEntityRenderState {
		public MythosEntity.Action action = MythosEntity.Action.NONE;
		public float actionAge;
		public int phase = 1;
		public float chase;
		public float walkPhase;
		public int seed;
	}

	public MythosRenderer(EntityRendererProvider.Context context) {
		super(context, new MythosModel(context.bakeLayer(MythosGeometry.LAYER)), 1.2F);
		addLayer(new RiggedMesh.Layer<>(this, getModel().root(), MythosGeometry.BONES, MythosGeometry.PARENTS, MESH, "mythos"));
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(MythosEntity mythos, State state, float partialTick) {
		super.extractRenderState(mythos, state, partialTick);
		state.action = mythos.action();
		state.actionAge = mythos.actionAge(partialTick);
		state.phase = mythos.phase();
		state.chase = mythos.chase(partialTick);
		state.walkPhase = mythos.walkPhase(partialTick);
		state.seed = mythos.getId();
		// The glitch: a dropped frame now and then (every second or two; enraged, twice as often).
		long frame = (long) (state.ageInTicks * 2.0F);
		long hash = (frame + state.seed * 31L) * 0x9E3779B97F4A7C15L;
		hash ^= hash >>> 29;
		int odds = state.phase == 3 ? 18 : 36;
		if (state.deathTime <= 0.0F && Math.floorMod(hash, odds) == 0) {
			state.isInvisible = true;
		}
	}

	@Override
	protected float getFlipDegrees() {
		return 0.0F; // he has his own death
	}

	@Override
	public Identifier getTextureLocation(State state) {
		return TEXTURE;
	}
}
