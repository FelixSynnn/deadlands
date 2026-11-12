package com.deadlands.client.render;

import com.deadlands.Deadlands;
import com.deadlands.entity.GuestEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;

/**
 * The Guest: the imported model ("Sixer aka Guest 666 - Forsaken" by Fourteen, CC-BY-4.0; see CREDITS.md),
 * recoloured to the owner's palette, drawn by {@link RiggedMesh.Layer} on its rig ({@link GuestGeometry}),
 * posed by {@link GuestModel}. Its bright reds glow full bright, so it shows in the dark.
 */
public class GuestRenderer extends MobRenderer<GuestEntity, GuestRenderer.State, GuestModel> {
	private static final Identifier MESH = Deadlands.id("models/entity/guest.bin");
	private static final Identifier TEXTURE = Deadlands.id("textures/entity/guest/model/material.png");

	public static class State extends LivingEntityRenderState {
		public GuestEntity.Mode mode = GuestEntity.Mode.ROAM;
		/** In water: it swims like a dog, head up. */
		public boolean swimming;
		public GuestEntity.Action action = GuestEntity.Action.NONE;
		public float actionAge;
		public float run;
	}

	public GuestRenderer(EntityRendererProvider.Context context) {
		super(context, new GuestModel(context.bakeLayer(GuestGeometry.LAYER)), 1.0F);
		addLayer(new RiggedMesh.Layer<>(this, getModel().root(), GuestGeometry.BONES, GuestGeometry.PARENTS, MESH, "guest/model"));
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(GuestEntity guest, State state, float partialTick) {
		super.extractRenderState(guest, state, partialTick);
		state.swimming = guest.isInWater();
		state.mode = guest.mode();
		state.action = guest.action();
		state.actionAge = guest.actionAge(partialTick);
		state.run = guest.run(partialTick);
	}

	@Override
	public Identifier getTextureLocation(State state) {
		return TEXTURE;
	}
}
