package com.deadlands.mission;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import net.minecraft.resources.Identifier;

/**
 * Someone who offers side missions ({@code data/<namespace>/mission_giver/<name>.json}). After their
 * last mission is completed, declined or abandoned, their next offer waits a random
 * {@code cooldown_minutes} of active play (paused or idle time does not count); until then they only
 * talk. One of their missions is active at a time. Once {@code stop_flag} is set (for Harley: her
 * death beat) they never offer again. {@code missions} are offered in order, skipping any already
 * done or whose requirements are not met.
 */
public record GiverDef(int cooldownMin, int cooldownMax, Optional<String> stopFlag, List<Identifier> missions) {
	public static final Codec<GiverDef> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		Codec.INT.optionalFieldOf("cooldown_min_minutes", 10).forGetter(GiverDef::cooldownMin),
		Codec.INT.optionalFieldOf("cooldown_max_minutes", 15).forGetter(GiverDef::cooldownMax),
		Codec.STRING.optionalFieldOf("stop_flag").forGetter(GiverDef::stopFlag),
		Identifier.CODEC.listOf().optionalFieldOf("missions", List.of()).forGetter(GiverDef::missions)
	).apply(instance, GiverDef::new));
}
