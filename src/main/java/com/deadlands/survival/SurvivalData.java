package com.deadlands.survival;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.Mth;

/**
 * One survivor's body: thirst, stamina, body temperature, fatigue, infection and injuries.
 * Hunger stays Minecraft's own food level. Attached to the player entity
 * ({@link SurvivalAttachments}), saved with it, and reset to a healthy state on respawn.
 *
 * <p>Scales: thirst, stamina and fatigue run 0 to 100 (thirst and stamina are good when high,
 * fatigue when low). Temperature runs -100 (frozen) to 100 (burning up), 0 is comfortable.
 * Infection runs 0 (clean) to 100 (dead). Bleeding is 0 (none), 1 (light) or 2 (heavy). Fracture,
 * sickness and painkiller are ticks remaining.
 */
public final class SurvivalData {
	public static final float MAX = 100.0F;

	public static final Codec<SurvivalData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		Codec.FLOAT.optionalFieldOf("thirst", MAX).forGetter(d -> d.thirst),
		Codec.FLOAT.optionalFieldOf("stamina", MAX).forGetter(d -> d.stamina),
		Codec.FLOAT.optionalFieldOf("temperature", 0.0F).forGetter(d -> d.temperature),
		Codec.FLOAT.optionalFieldOf("fatigue", 0.0F).forGetter(d -> d.fatigue),
		Codec.FLOAT.optionalFieldOf("infection", 0.0F).forGetter(d -> d.infection),
		Codec.INT.optionalFieldOf("bleeding", 0).forGetter(d -> d.bleeding),
		Codec.INT.optionalFieldOf("fracture", 0).forGetter(d -> d.fracture),
		Codec.INT.optionalFieldOf("sickness", 0).forGetter(d -> d.sickness),
		Codec.INT.optionalFieldOf("painkiller", 0).forGetter(d -> d.painkiller),
		Codec.BOOL.optionalFieldOf("winded", false).forGetter(d -> d.winded)
	).apply(instance, SurvivalData::new));

	private float thirst = MAX;
	private float stamina = MAX;
	private float temperature;
	private float fatigue;
	private float infection;
	private int bleeding;
	private int fracture;
	private int sickness;
	private int painkiller;
	private boolean winded;
	/** Carried weight and capacity in kg. Not saved: recomputed from the inventory every second. */
	private float load;
	private float capacity = CarryWeight.BASE_CAPACITY;

	public SurvivalData() {
	}

	private SurvivalData(float thirst, float stamina, float temperature, float fatigue, float infection, int bleeding, int fracture,
		int sickness, int painkiller, boolean winded) {
		this.thirst = Mth.clamp(thirst, 0.0F, MAX);
		this.stamina = Mth.clamp(stamina, 0.0F, MAX);
		this.temperature = Mth.clamp(temperature, -MAX, MAX);
		this.fatigue = Mth.clamp(fatigue, 0.0F, MAX);
		this.infection = Mth.clamp(infection, 0.0F, MAX);
		this.bleeding = Mth.clamp(bleeding, 0, 2);
		this.fracture = Math.max(0, fracture);
		this.sickness = Math.max(0, sickness);
		this.painkiller = Math.max(0, painkiller);
		this.winded = winded;
	}

	// ------------------------------------------------------------------------------------ values

	public float thirst() {
		return thirst;
	}

	public void addThirst(float amount) {
		thirst = Mth.clamp(thirst + amount, 0.0F, MAX);
	}

	public float stamina() {
		return stamina;
	}

	public void addStamina(float amount) {
		stamina = Mth.clamp(stamina + amount, 0.0F, maxStamina());
	}

	/** Fatigue eats into the stamina a survivor can have: down to 40 when exhausted. */
	public float maxStamina() {
		return MAX - Math.max(0.0F, fatigue - 40.0F);
	}

	public float temperature() {
		return temperature;
	}

	public void setTemperature(float value) {
		temperature = Mth.clamp(value, -MAX, MAX);
	}

	public float fatigue() {
		return fatigue;
	}

	public void addFatigue(float amount) {
		fatigue = Mth.clamp(fatigue + amount, 0.0F, MAX);
		stamina = Math.min(stamina, maxStamina());
	}

	public float infection() {
		return infection;
	}

	public void addInfection(float amount) {
		infection = Mth.clamp(infection + amount, 0.0F, MAX);
	}

	public boolean infected() {
		return infection > 0.0F;
	}

	/** 0 clean; 1 early (no symptoms yet); 2 feverish; 3 failing. */
	public int infectionStage() {
		return infection <= 0.0F ? 0 : infection < 35.0F ? 1 : infection < 70.0F ? 2 : 3;
	}

	public int bleeding() {
		return bleeding;
	}

	public void setBleeding(int severity) {
		bleeding = Mth.clamp(severity, 0, 2);
	}

	public int fracture() {
		return fracture;
	}

	public void setFracture(int ticks) {
		fracture = Math.max(0, ticks);
	}

	public int sickness() {
		return sickness;
	}

	public void setSickness(int ticks) {
		sickness = Math.max(0, ticks);
	}

	public int painkiller() {
		return painkiller;
	}

	public void setPainkiller(int ticks) {
		painkiller = Math.max(0, ticks);
	}

	/** Out of breath: no sprinting until stamina recovers (see {@link SurvivalSystem}). */
	public boolean winded() {
		return winded;
	}

	public void setWinded(boolean value) {
		winded = value;
	}

	public float load() {
		return load;
	}

	public float capacity() {
		return capacity;
	}

	public void setLoad(float load, float capacity) {
		this.load = Math.max(0.0F, load);
		this.capacity = Math.max(1.0F, capacity);
	}

	/** Count down the timed conditions by {@code ticks}. */
	public void elapse(int ticks) {
		fracture = Math.max(0, fracture - ticks);
		sickness = Math.max(0, sickness - ticks);
		painkiller = Math.max(0, painkiller - ticks);
	}

	// ------------------------------------------------------------------------------------ conditions

	/** What is wrong with a survivor right now, for the HUD and for the rules. */
	public int conditions() {
		int flags = 0;
		if (thirst <= 0.0F) {
			flags |= Condition.DEHYDRATED.bit();
		} else if (thirst < 25.0F) {
			flags |= Condition.THIRSTY.bit();
		}
		if (temperature <= -60.0F) {
			flags |= Condition.FREEZING.bit();
		} else if (temperature <= -30.0F) {
			flags |= Condition.COLD.bit();
		}
		if (temperature >= 60.0F) {
			flags |= Condition.OVERHEATING.bit();
		} else if (temperature >= 30.0F) {
			flags |= Condition.HOT.bit();
		}
		if (fatigue >= 90.0F) {
			flags |= Condition.EXHAUSTED.bit();
		} else if (fatigue >= 65.0F) {
			flags |= Condition.TIRED.bit();
		}
		if (bleeding > 0) {
			flags |= (bleeding > 1 ? Condition.HEAVY_BLEEDING : Condition.BLEEDING).bit();
		}
		if (fracture > 0) {
			flags |= Condition.FRACTURE.bit();
		}
		if (sickness > 0) {
			flags |= Condition.SICK.bit();
		}
		if (infection > 0.0F) {
			flags |= Condition.INFECTED.bit();
		}
		if (painkiller > 0) {
			flags |= Condition.PAINKILLERS.bit();
		}
		if (winded) {
			flags |= Condition.WINDED.bit();
		}
		if (load > capacity) {
			flags |= Condition.ENCUMBERED.bit();
		}
		return flags;
	}

	public boolean has(Condition condition) {
		return (conditions() & condition.bit()) != 0;
	}
}
