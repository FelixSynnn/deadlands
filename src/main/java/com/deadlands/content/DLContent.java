package com.deadlands.content;

import com.deadlands.Deadlands;
import com.deadlands.horror.Scare;
import com.deadlands.mission.GiverDef;
import com.deadlands.mission.MissionDef;
import com.mojang.serialization.Codec;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

/**
 * The data-driven content layer (Phase 2). Story content is defined in data files, not code, so
 * it can grow without touching Java and be reloaded with {@code /reload}:
 *
 * <ul>
 *   <li>{@code data/<namespace>/jumpscare/*.json}: the Horror Director's scares ({@link Scare});</li>
 *   <li>{@code mission/*.json} and {@code mission_giver/*.json}: missions and who offers them;</li>
 *   <li>{@code dialogue/*.json}: conversations ({@link DialogueDef});</li>
 *   <li>{@code ending/*.json}: campaign endings and what leads to them ({@link EndingDef}).</li>
 * </ul>
 * Achievements are Minecraft advancements ({@code data/deadlands/advancement/}); cutscenes are
 * client resources ({@code assets/deadlands/cutscene/}, see {@code CutsceneLibrary}) because the
 * client plays them.
 */
public final class DLContent {
	public static final Folder<Scare> JUMPSCARES = new Folder<>("jumpscare", Scare.CODEC);
	public static final Folder<MissionDef> MISSIONS = new Folder<>("mission", MissionDef.CODEC);
	public static final Folder<GiverDef> GIVERS = new Folder<>("mission_giver", GiverDef.CODEC);
	public static final Folder<DialogueDef> DIALOGUE = new Folder<>("dialogue", DialogueDef.CODEC);
	public static final Folder<EndingDef> ENDINGS = new Folder<>("ending", EndingDef.CODEC);

	private DLContent() {
	}

	public static void init() {
		for (Folder<?> folder : new Folder<?>[] {JUMPSCARES, MISSIONS, GIVERS, DIALOGUE, ENDINGS}) {
			ResourceLoader.get(PackType.SERVER_DATA).registerReloader(Deadlands.id(folder.name), folder);
		}
	}

	/** One data folder: every file in it, decoded, by id (namespace:path without .json). */
	public static final class Folder<T> extends SimpleJsonResourceReloadListener<T> {
		private final String name;
		private Map<Identifier, T> entries = Map.of();

		Folder(String name, Codec<T> codec) {
			super(codec, FileToIdConverter.json(name));
			this.name = name;
		}

		@Override
		protected void apply(Map<Identifier, T> loaded, ResourceManager manager, ProfilerFiller profiler) {
			entries = Map.copyOf(loaded);
			Deadlands.LOGGER.info("DEADLANDS content: {} {}", loaded.size(), name);
		}

		public String name() {
			return name;
		}

		public Map<Identifier, T> all() {
			return entries;
		}

		public Optional<T> get(Identifier id) {
			return Optional.ofNullable(entries.get(id));
		}
	}
}
