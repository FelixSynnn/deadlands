package com.deadlands.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a field of a {@link DeadlandsConfig} section as a user-facing setting.
 *
 * <p>The same metadata drives the commented config file, load-time validation (ranges are
 * clamped) and the in-game settings screen, so a setting is declared exactly once.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface ConfigEntry {
	/** One-line explanation, written into the config file and shown as the settings tooltip. */
	String comment();

	/** Smallest allowed value for numeric settings. */
	double min() default -Double.MAX_VALUE;

	/** Largest allowed value for numeric settings. */
	double max() default Double.MAX_VALUE;

	/** True when the setting only changes this client's own presentation (audio, interface). */
	boolean client() default false;

	/**
	 * The development milestone whose system reads this value, while that system is not built yet.
	 * Empty when the setting already takes effect. Shown in the config file and settings screen so
	 * nobody mistakes a planned knob for a working one.
	 */
	String pendingMilestone() default "";
}
