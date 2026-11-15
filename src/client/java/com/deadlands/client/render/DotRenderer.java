package com.deadlands.client.render;

import com.deadlands.Deadlands;
import com.deadlands.entity.DotEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;

/**
 * D.O.T., Patient-4: the owner's model with its DEADLANDS texture (tools/assetgen/import_dot.py), drawn by
 * {@link RiggedMesh.Layer} on its rig ({@link DotGeometry}) and posed by {@link DotModel} with the animations made in
 * Blender ({@code tools/assetgen/blender}). Nothing on him glows: in
 * the dark he is a shape you can barely see.
 */
public class DotRenderer extends MobRenderer<DotEntity, DotRenderer.State, DotModel> {
	private static final Identifier MESH = Deadlands.id("models/entity/dot.bin");
	private static final Identifier TEXTURE = Deadlands.id("textures/entity/dot/model/m_plaguedoctor_body.png");

	public static class State extends LivingEntityRenderState {
		public DotEntity.Mode mode = DotEntity.Mode.WALK;
		public DotEntity.Action action = DotEntity.Action.NONE;
		public float actionAge;
		/** 0 walking, 1 provoked: eased, frame by frame. */
		public float aggro;
		/** 0 on the ground, 1 on a wall. */
		public float climb;
		/** Through his stride: 0 right foot down, 0.5 left (the prowl and the sprint share it). */
		public float walkPhase;
		/** A number of his own, so his twitches don't line up with another's. */
		public int seed;
	}

	public DotRenderer(EntityRendererProvider.Context context) {
		super(context, new DotModel(context.bakeLayer(DotGeometry.LAYER)), 0.7F);
		addLayer(new RiggedMesh.Layer<>(this, getModel().root(), DotGeometry.BONES, DotGeometry.PARENTS, MESH, "dot/model"));
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(DotEntity dot, State state, float partialTick) {
		super.extractRenderState(dot, state, partialTick);
		state.mode = dot.mode();
		state.action = dot.action();
		state.actionAge = dot.actionAge(partialTick);
		state.aggro = dot.aggro(partialTick);
		state.climb = dot.climb(partialTick);
		state.seed = dot.getId();
		state.walkPhase = dot.walkPhase(partialTick);
	}

	@Override
	public Identifier getTextureLocation(State state) {
		return TEXTURE;
	}
}
