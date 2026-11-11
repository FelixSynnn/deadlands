package com.deadlands.content;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;

/**
 * A conversation, as data ({@code data/<namespace>/dialogue/*.json}). Lines are spoken in order;
 * each may need story flags, set flags when spoken, and pause before the next. Phase 5 writes the
 * campaign's dialogue in this form.
 *
 * <pre>{"lines": [{"speaker": "harley", "text": "dialogue.deadlands.example.0", "requires": [], "sets": [], "pause": 20}]}</pre>
 */
public record DialogueDef(List<Line> lines) {
	public record Line(String speaker, String text, List<String> requires, List<String> sets, int pause) {
		public static final Codec<Line> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.STRING.fieldOf("speaker").forGetter(Line::speaker),
			Codec.STRING.fieldOf("text").forGetter(Line::text),
			Codec.STRING.listOf().optionalFieldOf("requires", List.of()).forGetter(Line::requires),
			Codec.STRING.listOf().optionalFieldOf("sets", List.of()).forGetter(Line::sets),
			Codec.INT.optionalFieldOf("pause", 20).forGetter(Line::pause)
		).apply(instance, Line::new));
	}

	public static final Codec<DialogueDef> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		Line.CODEC.listOf().fieldOf("lines").forGetter(DialogueDef::lines)
	).apply(instance, DialogueDef::new));
}
