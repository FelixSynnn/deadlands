package com.deadlands.client.cutscene;

import com.deadlands.Deadlands;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.phys.Vec3;

/**
 * Cutscenes as data (Phase 2): {@code assets/<namespace>/cutscene/<id>.json}, client resources
 * because the client plays them (the server only names one and gives its anchor).
 *
 * <pre>{
 *   "cue": "deadlands:event.day_begin",       optional sound at the start
 *   "frame": "anchor",                         "anchor" (turned by the anchor's yaw) or "bearing"
 *   "shots": [{"ticks": 110, "from": [14, 9, 12], "to": [-12, 6, 12], "look": [0, 1, 0], "look_rise": 0}],
 *   "lines": [{"from": 10, "to": 95, "key": "cutscene.deadlands.opening.0"}]
 * }</pre>
 * In the {@code bearing} frame the server's yaw is a bearing in radians and +X points along it
 * (used by the horde scene, which looks out toward where the horde comes from).
 */
public final class CutsceneLibrary extends SimpleJsonResourceReloadListener<CutsceneLibrary.Def> {
	public record ShotDef(int ticks, Vec3 from, Vec3 to, Vec3 look, double lookRise) {
		static final Codec<ShotDef> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.INT.fieldOf("ticks").forGetter(ShotDef::ticks),
			Vec3.CODEC.fieldOf("from").forGetter(ShotDef::from),
			Vec3.CODEC.fieldOf("to").forGetter(ShotDef::to),
			Vec3.CODEC.fieldOf("look").forGetter(ShotDef::look),
			Codec.DOUBLE.optionalFieldOf("look_rise", 0.0).forGetter(ShotDef::lookRise)
		).apply(instance, ShotDef::new));
	}

	public record LineDef(int from, int to, String key) {
		static final Codec<LineDef> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.INT.fieldOf("from").forGetter(LineDef::from),
			Codec.INT.fieldOf("to").forGetter(LineDef::to),
			Codec.STRING.fieldOf("key").forGetter(LineDef::key)
		).apply(instance, LineDef::new));
	}

	public record Def(Optional<Identifier> cue, String frame, List<ShotDef> shots, List<LineDef> lines) {
		static final Codec<Def> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Identifier.CODEC.optionalFieldOf("cue").forGetter(Def::cue),
			Codec.STRING.optionalFieldOf("frame", "anchor").forGetter(Def::frame),
			ShotDef.CODEC.listOf().fieldOf("shots").forGetter(Def::shots),
			LineDef.CODEC.listOf().optionalFieldOf("lines", List.of()).forGetter(Def::lines)
		).apply(instance, Def::new));
	}

	public static final CutsceneLibrary INSTANCE = new CutsceneLibrary();
	private static Map<Identifier, Def> scenes = Map.of();

	private CutsceneLibrary() {
		super(Def.CODEC, FileToIdConverter.json("cutscene"));
	}

	@Override
	protected void apply(Map<Identifier, Def> loaded, ResourceManager manager, ProfilerFiller profiler) {
		scenes = Map.copyOf(loaded);
		Deadlands.LOGGER.info("DEADLANDS content: {} cutscene", loaded.size());
	}

	/** A cutscene by its short id ({@code opening}) or full id ({@code deadlands:opening}). */
	public static Optional<Def> get(String id) {
		Identifier key = id.contains(":") ? Identifier.tryParse(id) : Deadlands.id(id);
		return key == null ? Optional.empty() : Optional.ofNullable(scenes.get(key));
	}
}
