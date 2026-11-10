package com.deadlands.npc;

import com.deadlands.block.WorkbenchBlock;
import com.deadlands.registry.DLSounds;
import com.deadlands.vehicle.VehicleEntity;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A survivor's day job, done where it can be seen. By day, between wanders, those who are not
 * fighters look for their work near home, walk to it and get on with it for ten seconds or so:
 *
 * <ul>
 *   <li><b>farmers</b> tend crops (which grow a stage now and then under their hands) and sow bare
 *       farmland;</li>
 *   <li><b>mechanics</b> work on the nearest vehicle that needs it, slowly mending bodywork and
 *       engine;</li>
 *   <li><b>doctors</b> see to a neighbour who is hurt;</li>
 *   <li>everyone else finds a workbench, a crafting table or the fire.</li>
 * </ul>
 */
public class NpcWorkGoal extends Goal {
	private static final int SEARCH = 10;
	private final NpcEntity npc;
	private @Nullable BlockPos spot;
	private @Nullable Entity subject;
	private int workTicks;
	private int cooldown;

	public NpcWorkGoal(NpcEntity npc) {
		this.npc = npc;
		this.cooldown = 60 + npc.getRandom().nextInt(200);
		setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		if (cooldown > 0) {
			cooldown--;
			return false;
		}
		if (npc.role().fighter() || npc.getTarget() != null || npc.getTradingPlayer() != null || !npc.level().isBrightOutside()) {
			cooldown = 100;
			return false;
		}
		if (!find()) {
			cooldown = 200 + npc.getRandom().nextInt(200);
			return false;
		}
		return true;
	}

	private boolean find() {
		spot = null;
		subject = null;
		BlockPos center = npc.home() == null ? npc.blockPosition() : npc.home();
		switch (npc.role()) {
			case FARMER -> spot = BlockPos.findClosestMatch(center, SEARCH, 4, pos -> {
				BlockState state = npc.level().getBlockState(pos);
				return (state.getBlock() instanceof CropBlock crop && !crop.isMaxAge(state))
					|| (state.is(Blocks.FARMLAND) && npc.level().getBlockState(pos.above()).isAir());
			}).orElse(null);
			case MECHANIC -> subject = nearest(VehicleEntity.class, vehicle -> !vehicle.burned() && (vehicle.body() < 100.0F || vehicle.engine() < 100.0F));
			case DOCTOR -> subject = nearest(NpcEntity.class, other -> other != npc && other.isAlive() && other.faction() == npc.faction()
				&& other.getHealth() < other.getMaxHealth());
			default -> {
			}
		}
		if (spot == null && subject == null) {
			spot = BlockPos.findClosestMatch(center, SEARCH, 4, pos -> {
				BlockState state = npc.level().getBlockState(pos);
				return state.getBlock() instanceof WorkbenchBlock || state.is(Blocks.CRAFTING_TABLE) || state.is(Blocks.CAMPFIRE);
			}).orElse(null);
		}
		return spot != null || subject != null;
	}

	private <T extends Entity> @Nullable T nearest(Class<T> type, java.util.function.Predicate<T> test) {
		T best = null;
		double bestSqr = Double.MAX_VALUE;
		for (T candidate : npc.level().getEntitiesOfClass(type, npc.getBoundingBox().inflate(14.0), test::test)) {
			double d = candidate.distanceToSqr(npc);
			if (d < bestSqr) {
				bestSqr = d;
				best = candidate;
			}
		}
		return best;
	}

	@Override
	public void start() {
		workTicks = 160 + npc.getRandom().nextInt(160);
	}

	@Override
	public boolean canContinueToUse() {
		return workTicks > 0 && npc.getTarget() == null && npc.level().isBrightOutside() && (subject == null || subject.isAlive())
			&& (spot != null || subject != null);
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void tick() {
		workTicks--;
		Vec3 target = subject != null ? subject.position() : Vec3.atCenterOf(spot);
		double reach = subject instanceof VehicleEntity vehicle ? vehicle.kind().width() + 1.5 : 2.2;
		if (npc.distanceToSqr(target) > reach * reach) {
			if (workTicks % 20 == 0 || npc.getNavigation().isDone()) {
				npc.getNavigation().moveTo(target.x, target.y, target.z, 0.9);
			}
			return;
		}
		npc.getNavigation().stop();
		npc.getLookControl().setLookAt(target.x, target.y + 0.4, target.z);
		if (workTicks % 16 == 0) {
			npc.swing(InteractionHand.MAIN_HAND);
		}
		if (workTicks % 40 == 0 && npc.level() instanceof ServerLevel level) {
			work(level);
		}
	}

	private void work(ServerLevel level) {
		if (subject instanceof VehicleEntity vehicle) {
			vehicle.setBody(vehicle.body() + 1.5F);
			vehicle.setEngine(vehicle.engine() + 0.5F);
			level.playSound(null, vehicle.getX(), vehicle.getY(), vehicle.getZ(), DLSounds.VEHICLE_REPAIR.value(), SoundSource.NEUTRAL, 0.5F, 1.0F);
			if (vehicle.body() >= 100.0F && vehicle.engine() >= 100.0F) {
				workTicks = 0;
			}
		} else if (subject instanceof NpcEntity patient) {
			patient.heal(2.0F);
			if (patient.getHealth() >= patient.getMaxHealth()) {
				workTicks = 0;
			}
		} else if (spot != null && npc.role() == NpcRole.FARMER) {
			BlockState state = level.getBlockState(spot);
			if (state.getBlock() instanceof CropBlock crop) {
				if (!crop.isMaxAge(state) && npc.getRandom().nextFloat() < 0.3F) {
					level.setBlock(spot, crop.getStateForAge(crop.getAge(state) + 1), 2);
				}
			} else if (state.is(Blocks.FARMLAND) && level.getBlockState(spot.above()).isAir() && npc.getRandom().nextFloat() < 0.5F) {
				level.setBlock(spot.above(), Blocks.WHEAT.defaultBlockState(), 2);
				workTicks = 0;
			}
		}
	}

	@Override
	public void stop() {
		spot = null;
		subject = null;
		cooldown = 200 + npc.getRandom().nextInt(400);
	}
}
