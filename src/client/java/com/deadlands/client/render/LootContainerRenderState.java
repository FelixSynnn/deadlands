package com.deadlands.client.render;

import com.deadlands.container.ContainerCondition;
import com.deadlands.container.ContainerKind;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;

/** Snapshot of a loot container for one frame. */
public class LootContainerRenderState extends BlockEntityRenderState {
	public ContainerKind kind = ContainerKind.MILITARY_CRATE;
	public ContainerCondition condition = ContainerCondition.CLEAN;
	public float yRotation;
	public float openness;
}
