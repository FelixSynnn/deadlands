package com.deadlands.entity.ai;

import com.deadlands.config.ConfigManager;
import com.deadlands.entity.InfectedEntity;
import com.deadlands.registry.DLSounds;
import com.deadlands.registry.DLTags;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Tear down a barricade between this infected and what it wants (its target, or the noise it is
 * investigating). Only starts when the infected is actually blocked, and only on blocks tagged
 * {@code #deadlands:infected_breakable} (doors, trapdoors, fences, windows, boarded planks) or,
 * for Brutes, {@code #deadlands:brute_breakable}. Damage is shared through {@link BreachTracker}.
 *
 * <p>Respects the {@code zombies.breakBarricades} setting and the mobGriefing game rule.
 */
public class BreachGoal extends Goal {
	private static final int MAX_TICKS = 1200;
	private static final double REACH = 2.8;

	private final InfectedEntity mob;
	private @Nullable BlockPos barricade;
	private int ticks;

	public BreachGoal(InfectedEntity mob) {
		this.mob = mob;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		if (mob.kind().breachPower() <= 0.0F || !ConfigManager.get().zombies.breakBarricades || (mob.tickCount + mob.getId()) % 5 != 0) {
			return false;
		}
		if (!(mob.level() instanceof ServerLevel level) || !level.getGameRules().get(GameRules.MOB_GRIEFING)) {
			return false;
		}
		Vec3 goal = goal();
		if (goal == null || mob.distanceToSqr(goal) < 4.0) {
			return false;
		}
		boolean blocked = mob.horizontalCollision || mob.getNavigation().isStuck()
			|| mob.getNavigation().isDone() && mob.distanceToSqr(goal) > 9.0;
		if (!blocked) {
			return false;
		}
		barricade = findBarricade(goal);
		return barricade != null;
	}

	private @Nullable Vec3 goal() {
		LivingEntity target = mob.getTarget();
		if (target != null && target.isAlive()) {
			return target.position();
		}
		return mob.investigatePos();
	}

	private @Nullable BlockPos findBarricade(Vec3 goal) {
		Path path = mob.getNavigation().getPath();
		if (path != null && !path.isDone()) {
			int end = Math.min(path.getNextNodeIndex() + 2, path.getNodeCount());
			for (int i = path.getNextNodeIndex(); i < end; i++) {
				Node node = path.getNode(i);
				for (int dy = 0; dy <= 1; dy++) {
					BlockPos pos = new BlockPos(node.x, node.y + dy, node.z);
					if (inReach(pos) && breakable(pos)) {
						return pos;
					}
				}
			}
		}
		Vec3 dir = goal.subtract(mob.position()).multiply(1.0, 0.0, 1.0);
		if (dir.lengthSqr() < 1.0E-4) {
			return null;
		}
		dir = dir.normalize();
		for (double step : new double[] {0.8, 1.5}) {
			for (int dy = 0; dy <= 1; dy++) {
				BlockPos pos = BlockPos.containing(mob.getX() + dir.x * step, mob.getY() + dy + 0.1, mob.getZ() + dir.z * step);
				if (breakable(pos)) {
					return pos;
				}
			}
		}
		return null;
	}

	private boolean inReach(BlockPos pos) {
		return mob.distanceToSqr(Vec3.atCenterOf(pos)) < REACH * REACH;
	}

	private boolean breakable(BlockPos pos) {
		BlockState state = mob.level().getBlockState(pos);
		if (state.isAir() || state.getDestroySpeed(mob.level(), pos) < 0.0F) {
			return false;
		}
		return state.is(DLTags.INFECTED_BREAKABLE) || mob.kind().breachPower() >= 3.0F && state.is(DLTags.BRUTE_BREAKABLE);
	}

	@Override
	public void start() {
		ticks = 0;
		mob.getNavigation().stop();
		mob.setFlag(InfectedEntity.FLAG_BREACHING, true);
	}

	@Override
	public boolean canContinueToUse() {
		return barricade != null && ticks < MAX_TICKS && ConfigManager.get().zombies.breakBarricades && inReach(barricade) && breakable(barricade)
			&& goal() != null;
	}

	@Override
	public void stop() {
		mob.setFlag(InfectedEntity.FLAG_BREACHING, false);
		barricade = null;
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void tick() {
		if (barricade == null || !(mob.level() instanceof ServerLevel level)) {
			return;
		}
		ticks++;
		Vec3 center = Vec3.atCenterOf(barricade);
		mob.getLookControl().setLookAt(center.x, center.y, center.z);
		if (ticks % 18 == 0) {
			mob.swing(InteractionHand.MAIN_HAND);
			BlockState state = level.getBlockState(barricade);
			boolean metal = state.getSoundType() == SoundType.METAL || state.getSoundType() == SoundType.IRON || state.getSoundType() == SoundType.CHAIN;
			level.playSound(null, barricade, (metal ? DLSounds.INFECTED_BASH_METAL : DLSounds.INFECTED_BASH_WOOD).value(), mob.getSoundSource(),
				0.9F, 0.85F + mob.getRandom().nextFloat() * 0.3F);
		} else if (ticks % 18 == 9 && mob.getRandom().nextInt(3) == 0) {
			level.playSound(null, barricade, DLSounds.INFECTED_SCRATCH.value(), mob.getSoundSource(), 0.6F, 0.9F + mob.getRandom().nextFloat() * 0.2F);
		}
		if (BreachTracker.hit(level, barricade, mob)) {
			barricade = null;
		}
	}
}
