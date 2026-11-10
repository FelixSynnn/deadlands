package com.deadlands.vehicle;

import com.deadlands.registry.DLVehicles;
import com.deadlands.world.Hashing;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * How much fuel a pump has left. Decided the first time anyone uses it, from its position (so two
 * pumps at one station differ, and the answer is the same whoever asks first): a little over half
 * are dry, the rest hold 25 to 150 litres. After that it only goes down, and it is saved.
 */
public class FuelPumpBlockEntity extends BlockEntity {
	private float litres = -1.0F;

	public FuelPumpBlockEntity(BlockPos pos, BlockState state) {
		super(DLVehicles.FUEL_PUMP, pos, state);
	}

	public float litres() {
		if (litres < 0.0F) {
			long hash = Hashing.mix(worldPosition.asLong() ^ 0x70756D70L);
			double roll = Hashing.unit(hash);
			litres = roll < 0.55 ? 0.0F : 25.0F + (float) (Hashing.unit(Hashing.mix(hash)) * 125.0);
			setChanged();
		}
		return litres;
	}

	/** Take up to {@code wanted} litres. Returns what there was to take. */
	public float take(float wanted) {
		float taken = Math.max(0.0F, Math.min(wanted, litres()));
		if (taken > 0.0F) {
			litres -= taken;
			setChanged();
		}
		return taken;
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		litres = input.getFloatOr("Litres", -1.0F);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.putFloat("Litres", litres);
	}
}
