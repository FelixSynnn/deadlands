package com.deadlands.client.render;

import com.deadlands.Deadlands;
import com.deadlands.entity.AsesinoEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;

/**
 * The Aberrant: the imported fan-made model ({@link AberrantMesh}, credited in CREDITS.md) drawn by
 * {@link AberrantMeshLayer} on a rig ({@link AberrantGeometry}) that his animations ({@link AsesinoModel})
 * pose; its glowing parts show in the dark. (The earlier box model, {@link AsesinoGeometry}, is kept but unused.)
 */
public class AsesinoRenderer extends MobRenderer<AsesinoEntity, AsesinoRenderer.State, AsesinoModel> {
	private static final Identifier TEXTURE = Deadlands.id("textures/entity/asesino/asesino_model.png");

	/** What the animations need. */
	public static class State extends LivingEntityRenderState {
		public AsesinoEntity.Mode mode = AsesinoEntity.Mode.ROAM;
		public AsesinoEntity.Action action = AsesinoEntity.Action.NONE;
		public float actionAge;
		public float hunch;
		public float sprint;
		/** On a wall, climbing. */
		public boolean climbing;
	}

	public AsesinoRenderer(EntityRendererProvider.Context context) {
		super(context, new AsesinoModel(context.bakeLayer(AberrantGeometry.LAYER)), 0.6F);
		// The Aberrant's imported model, drawn on the animated rig (the rig itself has no boxes).
		addLayer(new AberrantMeshLayer(this, getModel().root()));
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(AsesinoEntity asesino, State state, float partialTick) {
		super.extractRenderState(asesino, state, partialTick);
		state.mode = asesino.mode();
		state.action = asesino.action();
		state.actionAge = asesino.actionAge(partialTick);
		state.hunch = asesino.hunch(partialTick);
		state.sprint = asesino.sprint(partialTick);
		state.climbing = asesino.climbing();
	}

	@Override
	public Identifier getTextureLocation(State state) {
		return TEXTURE;
	}
}
