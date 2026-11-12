package com.deadlands.client.render;

import com.deadlands.Deadlands;
import com.deadlands.entity.AsesinoEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.LivingEntityEmissiveLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

/**
 * The Asesino: Minecraft's player model in the owner's 64x64 skin, with glowing red eyes drawn on an
 * emissive layer ({@code asesino_eyes.png}, full bright), so they show in the dark.
 */
public class AsesinoRenderer extends HumanoidMobRenderer<AsesinoEntity, HumanoidRenderState, HumanoidModel<HumanoidRenderState>> {
	private static final Identifier SKIN = Deadlands.id("textures/entity/asesino/asesino.png");
	private static final Identifier EYES = Deadlands.id("textures/entity/asesino/asesino_eyes.png");

	public AsesinoRenderer(EntityRendererProvider.Context context) {
		super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER)), 0.5F);
		addLayer(new LivingEntityEmissiveLayer<>(this, state -> EYES, (state, age) -> 1.0F, getModel(), RenderTypes::eyes, true));
	}

	@Override
	public HumanoidRenderState createRenderState() {
		return new HumanoidRenderState();
	}

	@Override
	public Identifier getTextureLocation(HumanoidRenderState state) {
		return SKIN;
	}
}
