package com.deadlands.client.render.infected;

import com.deadlands.Deadlands;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.minecraft.client.model.geom.ModelLayerLocation;

/** Model layers for the two infected rigs. */
public final class InfectedLayers {
	public static final ModelLayerLocation HUMANOID = new ModelLayerLocation(Deadlands.id("infected_humanoid"), "main");
	public static final ModelLayerLocation BRUTE = new ModelLayerLocation(Deadlands.id("infected_brute"), "main");

	private InfectedLayers() {
	}

	public static void register() {
		EntityModelLayerRegistry.registerModelLayer(HUMANOID, InfectedGeometry::humanoid);
		EntityModelLayerRegistry.registerModelLayer(BRUTE, InfectedGeometry::brute);
	}
}
