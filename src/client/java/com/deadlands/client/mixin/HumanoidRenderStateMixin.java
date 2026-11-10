package com.deadlands.client.mixin;

import com.deadlands.client.render.VehicleSeat;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Gives every humanoid render state a {@link VehicleSeat}. */
@Mixin(HumanoidRenderState.class)
public abstract class HumanoidRenderStateMixin implements VehicleSeat {
	@Unique
	private int deadlands$seat;

	@Override
	public int deadlands$seat() {
		return deadlands$seat;
	}

	@Override
	public void deadlands$setSeat(int seat) {
		this.deadlands$seat = seat;
	}
}
