package com.deadlands.entity;

import com.deadlands.entity.ai.InfectedAttackGoal;
import com.deadlands.entity.ai.ScreamGoal;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Rare and fragile, but it brings the others: it screams when it sees a survivor
 * ({@link ScreamGoal}) and keeps its distance. It only fights back when cornered.
 */
public class ScreamerEntity extends InfectedEntity {
	private static final int SUMMONS_PER_SCREAMER = 2;
	private static final int SCREAM_COOLDOWN_MIN = 600;
	private static final int SCREAM_COOLDOWN_SPREAD = 300;

	private int summonsLeft = SUMMONS_PER_SCREAMER;
	private long screamReadyAt;

	public ScreamerEntity(EntityType<? extends ScreamerEntity> type, Level level) {
		super(type, level);
	}

	@Override
	protected void addKindGoals() {
		this.goalSelector.addGoal(1, new ScreamGoal(this));
		this.goalSelector.addGoal(2, new AvoidEntityGoal<>(this, Player.class, 6.0F, 1.0, 1.25));
	}

	@Override
	protected void addAttackGoal() {
		this.goalSelector.addGoal(3, new InfectedAttackGoal(this, 0.9, 30, 6));
	}

	public boolean canScream() {
		return level().getGameTime() >= screamReadyAt;
	}

	public void startScreamCooldown() {
		screamReadyAt = level().getGameTime() + SCREAM_COOLDOWN_MIN + random.nextInt(SCREAM_COOLDOWN_SPREAD);
	}

	/** Uses up one call for reinforcements; false when none are left. */
	public boolean useSummon() {
		if (summonsLeft <= 0) {
			return false;
		}
		summonsLeft--;
		return true;
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putInt("Summons", summonsLeft);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		summonsLeft = input.getIntOr("Summons", SUMMONS_PER_SCREAMER);
	}
}
