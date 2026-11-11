package com.deadlands.content;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;

/**
 * A campaign ending, as data ({@code data/<namespace>/ending/*.json}): which campaign it closes,
 * its text, the story flags it needs and the ones that rule it out. When a campaign ends, the
 * highest-priority ending whose conditions hold is chosen. Phase 5 writes the endings approved in
 * Phase 0 in this form.
 */
public record EndingDef(String campaign, String title, String body, List<String> requires, List<String> forbids, int priority) {
	public static final Codec<EndingDef> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		Codec.STRING.fieldOf("campaign").forGetter(EndingDef::campaign),
		Codec.STRING.fieldOf("title").forGetter(EndingDef::title),
		Codec.STRING.fieldOf("body").forGetter(EndingDef::body),
		Codec.STRING.listOf().optionalFieldOf("requires", List.of()).forGetter(EndingDef::requires),
		Codec.STRING.listOf().optionalFieldOf("forbids", List.of()).forGetter(EndingDef::forbids),
		Codec.INT.optionalFieldOf("priority", 0).forGetter(EndingDef::priority)
	).apply(instance, EndingDef::new));
}
