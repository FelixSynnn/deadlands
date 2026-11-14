package com.deadlands.client.render;

import com.deadlands.Deadlands;
import com.deadlands.entity.SubAgentEntity;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;

/**
 * Mythos' sub-agents: modelled and animated in Blender (tools/assetgen/blender/subagent_build.py, subagent_anims.py),
 * drawn by {@link RiggedMesh.Layer} on {@link SubAgentGeometry}. Idle and skitter blend by speed; the bite and the death
 * play over them. Eyes, spots and claw tips glow.
 */
public class SubAgentRenderer extends MobRenderer<SubAgentEntity, SubAgentRenderer.State, SubAgentRenderer.Model> {
	private static final Identifier MESH = Deadlands.id("models/entity/sub_agent.bin");
	private static final Identifier TEXTURE = Deadlands.id("textures/entity/sub_agent/sub_agent.png");

	public static class State extends LivingEntityRenderState {
		public float attackAge = 100.0F;
		public int seed;
	}

	public static class Model extends EntityModel<State> {
		private final ClipRig rig;

		public Model(ModelPart root) {
			super(root);
			rig = new ClipRig(root, SubAgentGeometry.BONES, SubAgentGeometry.PARENTS, Deadlands.id("models/entity/sub_agent.anim"));
		}

		@Override
		public void setupAnim(State state) {
			super.setupAnim(state);
			if (rig.anim() == null) {
				return;
			}
			rig.sample("idle", state.ageInTicks + state.seed * 7, rig.pose);
			float move = ClipRig.smooth(Math.min(1.0F, state.walkAnimationSpeed * 2.0F));
			if (move > 0.001F && rig.sample("run", state.walkAnimationPos * 0.8F, rig.layer)) {
				ClipRig.mix(rig.pose, rig.layer, move);
			}
			float bite = rig.length("attack");
			if (state.deathTime > 0.0F) {
				if (rig.sample("death", state.deathTime, rig.layer)) {
					ClipRig.mix(rig.pose, rig.layer, 1.0F);
				}
			} else if (state.attackAge < bite + 3.0F && rig.sample("attack", state.attackAge, rig.layer)) {
				ClipRig.mix(rig.pose, rig.layer, ClipRig.smooth(state.attackAge / 1.5F) * ClipRig.smooth((bite + 3.0F - state.attackAge) / 3.0F));
			}
			rig.apply();
		}
	}

	public SubAgentRenderer(EntityRendererProvider.Context context) {
		super(context, new Model(context.bakeLayer(SubAgentGeometry.LAYER)), 0.4F);
		addLayer(new RiggedMesh.Layer<>(this, getModel().root(), SubAgentGeometry.BONES, SubAgentGeometry.PARENTS, MESH, "sub_agent"));
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(SubAgentEntity agent, State state, float partialTick) {
		super.extractRenderState(agent, state, partialTick);
		state.attackAge = agent.attackAge(partialTick);
		state.seed = agent.getId();
	}

	@Override
	protected float getFlipDegrees() {
		return 0.0F; // it has its own death
	}

	@Override
	public Identifier getTextureLocation(State state) {
		return TEXTURE;
	}
}
