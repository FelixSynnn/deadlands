package com.deadlands.mission;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import net.minecraft.resources.Identifier;

/**
 * A mission, as data ({@code data/<namespace>/mission/*.json}). Texts are translation keys.
 *
 * <ul>
 *   <li>{@code giver}: who offers it ({@code mission_giver/<giver>.json}); none for story missions.</li>
 *   <li>{@code requires}: story flags that must be set before it can start.</li>
 *   <li>{@code objectives}, in order: {@code have_item} (an item and a count, carried by any
 *       survivor), {@code kill} (an entity type and a count), {@code flag} (a story flag is set),
 *       {@code visit} (stand inside a structure).</li>
 *   <li>{@code reward_items} go to every survivor once (never twice, even after a reconnect);
 *       {@code reward_flags} are set; {@code lore} lists related lore entries for the Missions tab.</li>
 * </ul>
 */
public record MissionDef(String title, String description, Optional<String> giver, List<String> requires, List<Objective> objectives,
	List<RewardItem> rewardItems, List<String> rewardFlags, List<String> lore) {

	public record Objective(String id, String type, String text, Optional<Identifier> target, int count, Optional<String> flag) {
		public static final Codec<Objective> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.STRING.fieldOf("id").forGetter(Objective::id),
			Codec.STRING.fieldOf("type").forGetter(Objective::type),
			Codec.STRING.fieldOf("text").forGetter(Objective::text),
			Identifier.CODEC.optionalFieldOf("target").forGetter(Objective::target),
			Codec.INT.optionalFieldOf("count", 1).forGetter(Objective::count),
			Codec.STRING.optionalFieldOf("flag").forGetter(Objective::flag)
		).apply(instance, Objective::new));
	}

	public record RewardItem(Identifier item, int count) {
		public static final Codec<RewardItem> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Identifier.CODEC.fieldOf("item").forGetter(RewardItem::item),
			Codec.INT.optionalFieldOf("count", 1).forGetter(RewardItem::count)
		).apply(instance, RewardItem::new));
	}

	public static final Codec<MissionDef> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		Codec.STRING.fieldOf("title").forGetter(MissionDef::title),
		Codec.STRING.fieldOf("description").forGetter(MissionDef::description),
		Codec.STRING.optionalFieldOf("giver").forGetter(MissionDef::giver),
		Codec.STRING.listOf().optionalFieldOf("requires", List.of()).forGetter(MissionDef::requires),
		Objective.CODEC.listOf().fieldOf("objectives").forGetter(MissionDef::objectives),
		RewardItem.CODEC.listOf().optionalFieldOf("reward_items", List.of()).forGetter(MissionDef::rewardItems),
		Codec.STRING.listOf().optionalFieldOf("reward_flags", List.of()).forGetter(MissionDef::rewardFlags),
		Codec.STRING.listOf().optionalFieldOf("lore", List.of()).forGetter(MissionDef::lore)
	).apply(instance, MissionDef::new));
}
