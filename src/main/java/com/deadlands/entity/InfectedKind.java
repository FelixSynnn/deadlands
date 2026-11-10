package com.deadlands.entity;

import com.deadlands.registry.DLSounds;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import org.jspecify.annotations.Nullable;

/**
 * The infected roster: one entity type per kind, each with its own statistics, rig, sounds and
 * visual variants. Behaviour that goes beyond these numbers lives in the kind's entity class.
 *
 * <p>Seven kinds share the jointed {@link Rig#HUMANOID} rig (with per-kind proportions, gear and
 * poses); the Brute has its own heavy rig. Variants come from {@link InfectedVariants}, which
 * tools/assetgen/gen_infected.py writes together with the textures.
 */
public enum InfectedKind {
	// Speed: a mob covers about 43 x speed x speed blocks a second (a vanilla zombie, 0.23, does 2.3;
	// a survivor walks 4.3 and sprints 5.6). So walkers do 1.9, military and charred 2.1, the
	// screamer 2.5, the stalker 3.6, the runner 4.4 (6.0 in a burst, see RunnerEntity), the brute
	// 1.7, crawlers and bloaters 1.2.
	//      id          rig            health speed  damage armor kbres follow knock  xp breach hear   width height eye    fireproof
	WALKER("walker", Rig.HUMANOID, 22, 0.21, 4.0, 2, 0.0, 28, 0.0, 5, 1.0F, 1.0F, 0.6F, 1.95F, 1.74F, false),
	RUNNER("runner", Rig.HUMANOID, 16, 0.32, 3.5, 0, 0.0, 36, 0.0, 7, 0.6F, 1.2F, 0.6F, 1.9F, 1.68F, false),
	BRUTE("brute", Rig.BRUTE, 70, 0.20, 11.0, 8, 0.85, 26, 1.5, 20, 4.0F, 0.8F, 1.2F, 2.7F, 2.3F, false),
	SCREAMER("screamer", Rig.HUMANOID, 16, 0.24, 2.0, 0, 0.0, 32, 0.0, 12, 0.0F, 1.4F, 0.6F, 2.1F, 1.92F, false),
	MILITARY("military", Rig.HUMANOID, 32, 0.22, 5.0, 7, 0.2, 30, 0.0, 10, 1.4F, 1.0F, 0.6F, 1.95F, 1.74F, false),
	CRAWLER("crawler", Rig.HUMANOID, 14, 0.17, 3.0, 0, 0.3, 18, 0.0, 4, 0.5F, 1.0F, 0.9F, 0.7F, 0.5F, false),
	BLOATER("bloater", Rig.HUMANOID, 26, 0.17, 2.0, 0, 0.4, 22, 0.0, 9, 0.5F, 0.9F, 0.9F, 2.0F, 1.75F, false),
	STALKER("stalker", Rig.HUMANOID, 24, 0.29, 6.0, 1, 0.1, 40, 0.0, 14, 0.3F, 1.5F, 0.6F, 1.8F, 1.6F, false),
	CHARRED("charred", Rig.HUMANOID, 24, 0.22, 4.5, 3, 0.1, 28, 0.0, 8, 1.0F, 1.0F, 0.6F, 1.95F, 1.74F, true);

	/** Which model the kind is drawn with. */
	public enum Rig {
		HUMANOID, BRUTE
	}

	private static final Map<EntityType<?>, InfectedKind> BY_TYPE = new IdentityHashMap<>();

	private final String id;
	private final Rig rig;
	private final double health;
	private final double speed;
	private final double damage;
	private final double armor;
	private final double knockbackResistance;
	private final double followRange;
	private final double attackKnockback;
	private final int xp;
	private final float breachPower;
	private final float hearing;
	private final float width;
	private final float height;
	private final float eyeHeight;
	private final boolean fireImmune;

	InfectedKind(String id, Rig rig, double health, double speed, double damage, double armor, double knockbackResistance, double followRange,
		double attackKnockback, int xp, float breachPower, float hearing, float width, float height, float eyeHeight, boolean fireImmune) {
		this.id = id;
		this.rig = rig;
		this.health = health;
		this.speed = speed;
		this.damage = damage;
		this.armor = armor;
		this.knockbackResistance = knockbackResistance;
		this.followRange = followRange;
		this.attackKnockback = attackKnockback;
		this.xp = xp;
		this.breachPower = breachPower;
		this.hearing = hearing;
		this.width = width;
		this.height = height;
		this.eyeHeight = eyeHeight;
		this.fireImmune = fireImmune;
	}

	public String id() {
		return id;
	}

	public Rig rig() {
		return rig;
	}

	public double health() {
		return health;
	}

	public double speed() {
		return speed;
	}

	public double damage() {
		return damage;
	}

	public double armor() {
		return armor;
	}

	public double knockbackResistance() {
		return knockbackResistance;
	}

	public double followRange() {
		return followRange;
	}

	public double attackKnockback() {
		return attackKnockback;
	}

	public int xp() {
		return xp;
	}

	/** How fast this kind breaks barricades, relative to a Walker (0 = never tries). */
	public float breachPower() {
		return breachPower;
	}

	/** Multiplier on how far away this kind hears noise. */
	public float hearing() {
		return hearing;
	}

	public float width() {
		return width;
	}

	public float height() {
		return height;
	}

	public float eyeHeight() {
		return eyeHeight;
	}

	public boolean fireImmune() {
		return fireImmune;
	}

	public String translationKey() {
		return "entity.deadlands." + id;
	}

	public List<InfectedVariants.Variant> variants() {
		return InfectedVariants.forKind(id);
	}

	/** Kinds that groan and snarl; the Stalker hunts almost silently. */
	public boolean vocal() {
		return this != STALKER;
	}

	/** Whether natural spawning should mostly happen at night for this kind. */
	public boolean nocturnal() {
		return this == RUNNER || this == STALKER || this == SCREAMER;
	}

	// ------------------------------------------------------------------------------------ sounds

	public Holder<SoundEvent> ambientSound() {
		return switch (this) {
			case WALKER -> DLSounds.INFECTED_WALKER_AMBIENT;
			case CHARRED -> DLSounds.INFECTED_CHARRED_AMBIENT;
			case RUNNER -> DLSounds.INFECTED_RUNNER_AMBIENT;
			case BRUTE -> DLSounds.INFECTED_BRUTE_AMBIENT;
			case SCREAMER -> DLSounds.INFECTED_SCREAMER_AMBIENT;
			case MILITARY -> DLSounds.INFECTED_MILITARY_AMBIENT;
			case CRAWLER -> DLSounds.INFECTED_CRAWLER_AMBIENT;
			case BLOATER -> DLSounds.INFECTED_BLOATER_AMBIENT;
			case STALKER -> DLSounds.INFECTED_STALKER_AMBIENT;
		};
	}

	public Holder<SoundEvent> hurtSound() {
		return switch (this) {
			case WALKER, MILITARY, CHARRED -> DLSounds.INFECTED_WALKER_HURT;
			case RUNNER -> DLSounds.INFECTED_RUNNER_HURT;
			case BRUTE -> DLSounds.INFECTED_BRUTE_HURT;
			case SCREAMER -> DLSounds.INFECTED_SCREAMER_HURT;
			case CRAWLER -> DLSounds.INFECTED_CRAWLER_HURT;
			case BLOATER -> DLSounds.INFECTED_BLOATER_HURT;
			case STALKER -> DLSounds.INFECTED_STALKER_HURT;
		};
	}

	public Holder<SoundEvent> deathSound() {
		return switch (this) {
			case WALKER, MILITARY, CHARRED -> DLSounds.INFECTED_WALKER_DEATH;
			case RUNNER -> DLSounds.INFECTED_RUNNER_DEATH;
			case BRUTE -> DLSounds.INFECTED_BRUTE_DEATH;
			case SCREAMER -> DLSounds.INFECTED_SCREAMER_DEATH;
			case CRAWLER, BLOATER -> DLSounds.INFECTED_CRAWLER_DEATH;
			case STALKER -> DLSounds.INFECTED_STALKER_DEATH;
		};
	}

	// ------------------------------------------------------------------------------------ lookup

	/** Called by the entity registry for each kind it registers. */
	public static void bind(InfectedKind kind, EntityType<?> type) {
		BY_TYPE.put(type, kind);
	}

	public static @Nullable InfectedKind of(EntityType<?> type) {
		return BY_TYPE.get(type);
	}

	public static @Nullable InfectedKind byId(String id) {
		for (InfectedKind kind : values()) {
			if (kind.id.equals(id.toLowerCase(Locale.ROOT))) {
				return kind;
			}
		}
		return null;
	}
}
