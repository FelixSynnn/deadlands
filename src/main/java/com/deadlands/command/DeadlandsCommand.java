package com.deadlands.command;

import com.deadlands.config.ConfigManager;
import com.deadlands.network.DLNetworking;
import com.deadlands.persistence.ChronicleEntry;
import com.deadlands.persistence.DeadlandsWorldData;
import com.deadlands.persistence.PlayerRecord;
import com.deadlands.player.SurvivorTracker;
import com.deadlands.progression.EncounterSchedule;
import com.deadlands.progression.ScheduleSettings;
import com.deadlands.progression.TimeOfDay;
import com.deadlands.progression.WorldClock;
import com.mojang.brigadier.CommandDispatcher;
import com.deadlands.entity.InfectedEntity;
import com.deadlands.horde.HordeDirector;
import com.deadlands.horde.MigrationSystem;
import com.deadlands.nest.NestRegistry;
import com.deadlands.noise.Loudness;
import com.deadlands.noise.NoiseSystem;
import com.deadlands.persistence.HordeRecord;
import com.deadlands.spawning.InfectedManager;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.List;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /deadlands}: status for everyone, plus game-master tools for testing persistence and the
 * day cycle (day next/set, config reload, journal) and the infected (horde start/stop/status,
 * infected count/clear, noise).
 */
public final class DeadlandsCommand {
	private DeadlandsCommand() {
	}

	public static void init() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> register(dispatcher));
	}

	/** A story choice from a chat button (chapter four's answer). */
	private static int choose(CommandSourceStack source, String option) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		if (com.deadlands.story.Pinecrest.choose(source.getPlayerOrException(), option)) {
			return 1;
		}
		source.sendFailure(net.minecraft.network.chat.Component.translatable("command.deadlands.choose.nothing"));
		return 0;
	}

	static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("deadlands")
			.then(Commands.literal("status").executes(ctx -> status(ctx.getSource())))
			.then(Commands.literal("chronicle").executes(ctx -> chronicle(ctx.getSource())))
			.then(Commands.literal("story").executes(ctx -> com.deadlands.story.StoryArchive.story(ctx.getSource().getPlayerOrException())))
			.then(Commands.literal("cinematics").executes(ctx -> com.deadlands.story.StoryArchive.cinematics(ctx.getSource().getPlayerOrException())))
			.then(Commands.literal("watch").then(Commands.argument("cutscene", StringArgumentType.word())
				.executes(ctx -> {
					if (com.deadlands.story.StoryArchive.watch(ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "cutscene"))) {
						return 1;
					}
					ctx.getSource().sendFailure(Component.translatable("story.deadlands.cinematics.not_seen"));
					return 0;
				})))
			.then(Commands.literal("choose").then(Commands.argument("option", StringArgumentType.word())
				.executes(ctx -> choose(ctx.getSource(), StringArgumentType.getString(ctx, "option")))))
			.then(Commands.literal("config")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.literal("reload").executes(ctx -> reloadConfig(ctx.getSource()))))
			.then(Commands.literal("day")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.literal("next").executes(ctx -> nextDay(ctx.getSource())))
				.then(Commands.literal("set")
					.then(Commands.argument("day", IntegerArgumentType.integer(1, 1_000_000))
						.executes(ctx -> setDay(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "day"))))))
			.then(Commands.literal("skills").executes(ctx -> skills(ctx.getSource())))
			.then(Commands.literal("event")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.argument("kind", StringArgumentType.word())
					.suggests((ctx, builder) -> {
						for (com.deadlands.event.WorldEvents.Kind kind : com.deadlands.event.WorldEvents.Kind.values()) {
							builder.suggest(kind.id());
						}
						return builder.buildFuture();
					})
					.executes(ctx -> worldEvent(ctx.getSource(), StringArgumentType.getString(ctx, "kind")))))
			.then(Commands.literal("horror")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.literal("status").executes(ctx -> horrorStatus(ctx.getSource())))
				.then(Commands.literal("play").then(Commands.argument("scare", StringArgumentType.greedyString())
					.suggests((ctx, builder) -> {
						com.deadlands.content.DLContent.JUMPSCARES.all().keySet().forEach(id -> builder.suggest(id.toString()));
						return builder.buildFuture();
					})
					.executes(ctx -> horrorPlay(ctx.getSource(), StringArgumentType.getString(ctx, "scare"))))))
			.then(Commands.literal("flag")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.literal("list").executes(ctx -> flagList(ctx.getSource())))
				.then(Commands.literal("set").then(Commands.argument("flag", StringArgumentType.word())
					.executes(ctx -> flagSet(ctx.getSource(), StringArgumentType.getString(ctx, "flag"), true))))
				.then(Commands.literal("clear").then(Commands.argument("flag", StringArgumentType.word())
					.executes(ctx -> flagSet(ctx.getSource(), StringArgumentType.getString(ctx, "flag"), false)))))
			.then(Commands.literal("mission")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.literal("list").executes(ctx -> missionList(ctx.getSource())))
				.then(Commands.literal("start").then(Commands.argument("mission", StringArgumentType.greedyString())
					.suggests((ctx, builder) -> {
						com.deadlands.content.DLContent.MISSIONS.all().keySet().forEach(id -> builder.suggest(id.toString()));
						return builder.buildFuture();
					})
					.executes(ctx -> missionAction(ctx.getSource(), "start", StringArgumentType.getString(ctx, "mission")))))
				.then(Commands.literal("complete").then(Commands.argument("mission", StringArgumentType.greedyString())
					.executes(ctx -> missionAction(ctx.getSource(), "complete", StringArgumentType.getString(ctx, "mission")))))
				.then(Commands.literal("fail").then(Commands.argument("mission", StringArgumentType.greedyString())
					.executes(ctx -> missionAction(ctx.getSource(), "fail", StringArgumentType.getString(ctx, "mission")))))
				.then(Commands.literal("offer").then(Commands.argument("giver", StringArgumentType.word())
					.executes(ctx -> missionAction(ctx.getSource(), "offer", StringArgumentType.getString(ctx, "giver"))))))
			.then(Commands.literal("achievement")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.argument("path", StringArgumentType.greedyString())
					.executes(ctx -> achievement(ctx.getSource(), StringArgumentType.getString(ctx, "path")))))
			.then(Commands.literal("campaign")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.literal("enter").executes(ctx -> campaign(ctx.getSource(), "enter")))
				.then(Commands.literal("leave").executes(ctx -> campaign(ctx.getSource(), "leave")))
				.then(Commands.literal("status").executes(ctx -> campaign(ctx.getSource(), "status"))))
			.then(Commands.literal("content")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.executes(ctx -> content(ctx.getSource())))
			.then(Commands.literal("lights")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.executes(ctx -> {
					ctx.getSource().sendSuccess(() -> Component.translatable("command.deadlands.lights", com.deadlands.block.FluorescentLightBlock.toggles(),
						com.deadlands.horror.DarkBuildings.status(ctx.getSource().getServer().overworld())), false);
					return 1;
				}))
			.then(Commands.literal("mobs")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.executes(ctx -> mobs(ctx.getSource())))
			.then(Commands.literal("weather")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.argument("kind", StringArgumentType.word())
					.suggests((ctx, builder) -> {
						for (com.deadlands.world.weather.WeatherDirector.Kind kind : com.deadlands.world.weather.WeatherDirector.Kind.values()) {
							builder.suggest(kind.name().toLowerCase(java.util.Locale.ROOT));
						}
						return builder.buildFuture();
					})
					.executes(ctx -> weather(ctx.getSource(), StringArgumentType.getString(ctx, "kind")))))
			.then(Commands.literal("boss")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.argument("kind", StringArgumentType.word())
					.suggests((ctx, builder) -> {
						for (com.deadlands.progression.BossKind kind : com.deadlands.progression.BossKind.values()) {
							builder.suggest(kind.id());
						}
						return builder.buildFuture();
					})
					.executes(ctx -> boss(ctx.getSource(), StringArgumentType.getString(ctx, "kind")))))
			.then(Commands.literal("npc")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.argument("who", StringArgumentType.word())
					.suggests((ctx, builder) -> {
						for (com.deadlands.npc.NpcRole role : com.deadlands.npc.NpcRole.values()) {
							builder.suggest(role.id());
						}
						for (com.deadlands.npc.NpcCharacter character : com.deadlands.npc.NpcCharacter.values()) {
							builder.suggest(character.id());
						}
						return builder.buildFuture();
					})
					.executes(ctx -> npc(ctx.getSource(), StringArgumentType.getString(ctx, "who")))))
			.then(Commands.literal("vehicle")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.argument("kind", StringArgumentType.word())
					.suggests((ctx, builder) -> {
						for (com.deadlands.vehicle.VehicleKind kind : com.deadlands.vehicle.VehicleKind.values()) {
							builder.suggest(kind.id());
						}
						return builder.buildFuture();
					})
					.executes(ctx -> vehicle(ctx.getSource(), StringArgumentType.getString(ctx, "kind"), false))
					.then(Commands.literal("abandoned").executes(ctx -> vehicle(ctx.getSource(), StringArgumentType.getString(ctx, "kind"), true)))))
			.then(Commands.literal("journal")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.executes(ctx -> journal(ctx.getSource())))
			.then(Commands.literal("horde")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.literal("start").executes(ctx -> hordeStart(ctx.getSource())))
				.then(Commands.literal("stop").executes(ctx -> hordeStop(ctx.getSource())))
				.then(Commands.literal("status").executes(ctx -> hordeStatus(ctx.getSource()))))
			.then(Commands.literal("infected")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.literal("count").executes(ctx -> infectedCount(ctx.getSource())))
				.then(Commands.literal("clear").executes(ctx -> infectedClear(ctx.getSource()))))
			.then(Commands.literal("noise")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.argument("loudness", StringArgumentType.word())
					.suggests((ctx, builder) -> {
						for (Loudness loudness : Loudness.values()) {
							builder.suggest(loudness.name().toLowerCase(java.util.Locale.ROOT));
						}
						return builder.buildFuture();
					})
					.executes(ctx -> noise(ctx.getSource(), StringArgumentType.getString(ctx, "loudness"))))));
	}

	/** Start a world event near the player running the command. */
	private static int worldEvent(CommandSourceStack source, String id) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		com.deadlands.event.WorldEvents.Kind kind = com.deadlands.event.WorldEvents.byId(id);
		if (kind == null) {
			source.sendFailure(Component.translatable("command.deadlands.event.unknown", id));
			return 0;
		}
		if (!com.deadlands.event.WorldEvents.start(source.getLevel(), source.getPlayerOrException(), kind)) {
			source.sendFailure(Component.translatable("command.deadlands.event.no_room"));
			return 0;
		}
		return 1;
	}

	/** Set the DEADLANDS weather for ten minutes. */
	private static int weather(CommandSourceStack source, String id) {
		for (com.deadlands.world.weather.WeatherDirector.Kind kind : com.deadlands.world.weather.WeatherDirector.Kind.values()) {
			if (kind.name().equalsIgnoreCase(id)) {
				com.deadlands.world.weather.WeatherDirector.force(source.getServer(), kind, 12000);
				source.sendSuccess(() -> Component.translatable("command.deadlands.weather", Component.translatable("weather.deadlands." + id.toLowerCase(java.util.Locale.ROOT))), true);
				return 1;
			}
		}
		source.sendFailure(Component.translatable("command.deadlands.weather.unknown", id));
		return 0;
	}

	/** Bring a boss near the player running the command, outside the schedule (it counts for the story when killed). */
	private static int boss(CommandSourceStack source, String id) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		com.deadlands.progression.BossKind kind = com.deadlands.horde.BossDirector.byId(id);
		if (kind == null) {
			source.sendFailure(Component.translatable("command.deadlands.boss.unknown", id));
			return 0;
		}
		net.minecraft.server.level.ServerPlayer player = source.getPlayerOrException();
		if (com.deadlands.horde.BossDirector.start(source.getLevel(), player, kind, 0, 0L) == null) {
			source.sendFailure(Component.translatable("command.deadlands.boss.no_room"));
			return 0;
		}
		source.sendSuccess(() -> Component.translatable("command.deadlands.boss", kind.displayName()), true);
		return 1;
	}

	private static int horrorStatus(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		net.minecraft.server.level.ServerPlayer player = source.getPlayerOrException();
		String status = com.deadlands.horror.HorrorDirector.status(player);
		source.sendSuccess(() -> Component.literal(status), false);
		return 1;
	}

	private static int horrorPlay(CommandSourceStack source, String id) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		net.minecraft.server.level.ServerPlayer player = source.getPlayerOrException();
		net.minecraft.resources.Identifier key = net.minecraft.resources.Identifier.tryParse(id);
		com.deadlands.horror.Scare scare = key == null ? null : com.deadlands.content.DLContent.JUMPSCARES.get(key).orElse(null);
		if (scare == null) {
			source.sendFailure(Component.translatable("command.deadlands.unknown", id));
			return 0;
		}
		com.deadlands.horror.HorrorDirector.perform(player, key, scare);
		source.sendSuccess(() -> Component.translatable("command.deadlands.horror.play", id), false);
		return 1;
	}

	private static int flagList(CommandSourceStack source) {
		java.util.Set<String> flags = com.deadlands.flags.StoryFlags.get(source.getServer()).all();
		String joined = String.join(", ", new java.util.TreeSet<>(flags));
		source.sendSuccess(() -> Component.translatable("command.deadlands.flag.list", flags.size(), joined), false);
		return flags.size();
	}

	private static int flagSet(CommandSourceStack source, String flag, boolean on) {
		com.deadlands.flags.StoryFlags flags = com.deadlands.flags.StoryFlags.get(source.getServer());
		boolean changed = on ? flags.set(flag) : flags.clear(flag);
		source.sendSuccess(() -> Component.translatable(on ? "command.deadlands.flag.set" : "command.deadlands.flag.clear", flag), true);
		return changed ? 1 : 0;
	}

	private static int missionList(CommandSourceStack source) {
		String text = com.deadlands.mission.MissionManager.describe(source.getServer());
		source.sendSuccess(() -> Component.literal(text), false);
		return 1;
	}

	private static int missionAction(CommandSourceStack source, String action, String arg) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		net.minecraft.server.MinecraftServer server = source.getServer();
		if (action.equals("offer")) {
			net.minecraft.server.level.ServerPlayer player = source.getPlayerOrException();
			java.util.Optional<net.minecraft.resources.Identifier> offer = com.deadlands.mission.MissionManager.offer(server, arg, player);
			String offered = offer.map(Object::toString).orElse("-");
			source.sendSuccess(() -> Component.translatable("command.deadlands.mission.offer", arg, offered), false);
			return offer.isPresent() ? 1 : 0;
		}
		net.minecraft.resources.Identifier id = net.minecraft.resources.Identifier.tryParse(arg);
		boolean done = false;
		if (id != null) {
			done = switch (action) {
				case "start" -> com.deadlands.mission.MissionManager.start(server, id, source.getPlayerOrException());
				case "complete" -> com.deadlands.mission.MissionManager.complete(server, id);
				default -> com.deadlands.mission.MissionManager.fail(server, id);
			};
		}
		if (!done) {
			source.sendFailure(Component.translatable("command.deadlands.mission.refused", arg));
			return 0;
		}
		source.sendSuccess(() -> Component.translatable("command.deadlands.mission." + action, arg), true);
		return 1;
	}

	private static int achievement(CommandSourceStack source, String path) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		com.deadlands.achievement.Achievements.award(source.getPlayerOrException(), path);
		source.sendSuccess(() -> Component.translatable("command.deadlands.achievement", path), true);
		return 1;
	}

	private static int campaign(CommandSourceStack source, String action) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		com.deadlands.campaign.Campaign.State state = com.deadlands.campaign.Campaign.state(source.getServer());
		if (action.equals("status")) {
			boolean loaded = source.getServer().getLevel(com.deadlands.campaign.Campaign.LEVEL) != null;
			String checkpoint = state.checkpoint().toShortString();
			source.sendSuccess(() -> Component.translatable("command.deadlands.campaign.status", state.campaign(), state.chapter(), checkpoint, loaded), false);
			return loaded ? 1 : 0;
		}
		net.minecraft.server.level.ServerPlayer player = source.getPlayerOrException();
		boolean done = action.equals("enter") ? com.deadlands.campaign.Campaign.enter(player) : com.deadlands.campaign.Campaign.leave(player);
		if (!done) {
			source.sendFailure(Component.translatable("command.deadlands.campaign.failed"));
			return 0;
		}
		return 1;
	}

	/** Checks the content layer from the console: what loaded from data files, and that vanilla achievements are gone. */
	private static int content(CommandSourceStack source) {
		int scares = com.deadlands.content.DLContent.JUMPSCARES.all().size();
		int missions = com.deadlands.content.DLContent.MISSIONS.all().size();
		int givers = com.deadlands.content.DLContent.GIVERS.all().size();
		int dialogue = com.deadlands.content.DLContent.DIALOGUE.all().size();
		int endings = com.deadlands.content.DLContent.ENDINGS.all().size();
		long achievements = source.getServer().getAdvancements().getAllAdvancements().stream()
			.filter(holder -> holder.id().getNamespace().equals("deadlands")).count();
		long vanilla = source.getServer().getAdvancements().getAllAdvancements().stream()
			.filter(holder -> holder.id().getNamespace().equals("minecraft") && !holder.id().getPath().startsWith("recipes/")).count();
		source.sendSuccess(() -> Component.translatable("command.deadlands.content", scares, missions, givers, dialogue, endings, achievements, vanilla), false);
		return scares;
	}

	/** Checks Hard Rule 2 and the chase music from the console: vanilla mobs loaded, infected loaded, the vanilla event rules, who is being chased. */
	private static int mobs(CommandSourceStack source) {
		net.minecraft.server.MinecraftServer server = source.getServer();
		int vanilla = com.deadlands.world.VanillaMobs.countLoaded(server);
		int infected = 0;
		for (net.minecraft.server.level.ServerLevel level : server.getAllLevels()) {
			for (net.minecraft.world.entity.Entity entity : level.getAllEntities()) {
				if (entity instanceof com.deadlands.entity.InfectedEntity) {
					infected++;
				}
			}
		}
		net.minecraft.world.level.gamerules.GameRules rules = server.overworld().getGameRules();
		int vanillaCount = vanilla;
		int infectedCount = infected;
		source.sendSuccess(() -> Component.translatable("command.deadlands.mobs", vanillaCount, infectedCount,
			rules.get(net.minecraft.world.level.gamerules.GameRules.RAIDS), rules.get(net.minecraft.world.level.gamerules.GameRules.SPAWN_PATROLS),
			rules.get(net.minecraft.world.level.gamerules.GameRules.SPAWN_PHANTOMS), rules.get(net.minecraft.world.level.gamerules.GameRules.SPAWN_WANDERING_TRADERS)), false);
		for (net.minecraft.server.level.ServerPlayer player : server.getPlayerList().getPlayers()) {
			int chasers = com.deadlands.audio.ChaseTracker.chasers(player);
			boolean chased = com.deadlands.audio.ChaseTracker.chased(player);
			source.sendSuccess(() -> Component.translatable("command.deadlands.mobs.player", player.getName(), chasers, chased), false);
		}
		return vanilla;
	}

	/** Put a survivor of a role, or one of the recurring characters, where the source stands. */
	private static int npc(CommandSourceStack source, String who) {
		net.minecraft.core.BlockPos pos = net.minecraft.core.BlockPos.containing(source.getPosition());
		com.deadlands.npc.NpcCharacter character = com.deadlands.npc.NpcCharacter.byId(who);
		com.deadlands.npc.NpcEntity npc = null;
		if (character != null) {
			com.deadlands.npc.NpcWorldData characters = com.deadlands.npc.NpcWorldData.get(source.getServer());
			if (characters.isPlaced(character) && !characters.isLost(character)) {
				// Each recurring character exists once per world; only a lost one can be put back.
				source.sendFailure(Component.translatable("command.deadlands.npc.already", character.displayName()));
				return 0;
			}
			npc = com.deadlands.npc.NpcSpawns.place(source.getLevel(), character, pos, source.getRotation().y);
		} else {
			for (com.deadlands.npc.NpcRole role : com.deadlands.npc.NpcRole.values()) {
				if (role.id().equals(who)) {
					com.deadlands.npc.Faction faction = role == com.deadlands.npc.NpcRole.BANDIT ? com.deadlands.npc.Faction.BANDITS
						: role == com.deadlands.npc.NpcRole.WANDERER ? com.deadlands.npc.Faction.DRIFTERS : com.deadlands.npc.Faction.SETTLERS;
					npc = com.deadlands.npc.NpcSpawns.create(source.getLevel(), role, faction, null, pos, source.getRotation().y);
				}
			}
		}
		if (npc == null) {
			source.sendFailure(Component.translatable("command.deadlands.npc.unknown", who));
			return 0;
		}
		Component name = npc.getName();
		source.sendSuccess(() -> Component.translatable("command.deadlands.npc", name), true);
		return 1;
	}

	/** Put a vehicle where the source stands: restored with a full tank, or in a random abandoned state. */
	private static int vehicle(CommandSourceStack source, String id, boolean abandoned) {
		for (com.deadlands.vehicle.VehicleKind kind : com.deadlands.vehicle.VehicleKind.values()) {
			if (kind.id().equals(id)) {
				net.minecraft.world.phys.Vec3 at = source.getPosition();
				com.deadlands.vehicle.VehicleEntity vehicle = com.deadlands.vehicle.VehicleSpawns.abandon(source.getLevel(), kind, at.x, at.y, at.z,
					source.getRotation().y, source.getLevel().getRandom());
				if (vehicle == null) {
					return 0;
				}
				if (!abandoned) {
					vehicle.setFlag(com.deadlands.vehicle.VehicleEntity.FLAG_BURNED, false);
					vehicle.setFlag(com.deadlands.vehicle.VehicleEntity.FLAG_BATTERY, true);
					vehicle.setEngine(100.0F);
					vehicle.setTires(100.0F);
					vehicle.setBody(100.0F);
					vehicle.setPaint(2);
					vehicle.setFuel(kind.tank());
				}
				source.sendSuccess(() -> Component.translatable("command.deadlands.vehicle", Component.translatable(kind.translationKey())), true);
				return 1;
			}
		}
		source.sendFailure(Component.translatable("command.deadlands.vehicle.unknown", id));
		return 0;
	}

	/** A survivor's own skill levels and how far along each is. */
	private static int skills(CommandSourceStack source) {
		net.minecraft.server.level.ServerPlayer player = source.getPlayer();
		if (player == null) {
			return 0;
		}
		com.deadlands.skill.SkillData data = com.deadlands.skill.Skills.get(player);
		for (com.deadlands.skill.Skill skill : com.deadlands.skill.Skill.values()) {
			int level = data.level(skill);
			String next = level >= com.deadlands.skill.Skill.MAX_LEVEL ? "-" : String.valueOf(com.deadlands.skill.Skill.xpFor(level + 1));
			source.sendSuccess(() -> Component.translatable("command.deadlands.skills.line", Component.translatable(skill.translationKey()), level,
				data.xp(skill), next), false);
		}
		return 1;
	}

	private static int status(CommandSourceStack source) {
		MinecraftServer server = source.getServer();
		long dayTime = server.overworld().getDayTime();
		long day = WorldClock.currentDay(server);
		source.sendSuccess(() -> Component.translatable("command.deadlands.status.day", day, TimeOfDay.clock(dayTime)).withStyle(ChatFormatting.GOLD), false);

		ScheduleSettings schedule = ScheduleSettings.fromConfig(ConfigManager.get());
		EncounterSchedule.Encounter next = EncounterSchedule.next(day, schedule);
		Component threat = switch (next.kind()) {
			case HORDE -> Component.translatable("command.deadlands.status.next.horde", next.day(), next.hordeSize());
			case BOSS -> Component.translatable("command.deadlands.status.next.boss", next.day(), next.boss().displayName());
			case NONE -> Component.translatable("command.deadlands.status.next.none");
		};
		source.sendSuccess(() -> threat, false);
		source.sendSuccess(() -> Component.translatable("command.deadlands.status.pending").withStyle(ChatFormatting.DARK_GRAY), false);

		if (source.getEntity() instanceof ServerPlayer player) {
			DeadlandsWorldData.get(server).player(player.getUUID()).ifPresent(record -> {
				long dayIndex = day - 1;
				source.sendSuccess(() -> Component.translatable("command.deadlands.status.survivor",
					record.daysSurvived(dayIndex), record.deaths(), record.firstJoinDay() + 1), false);
			});
		}
		return 1;
	}

	private static int chronicle(CommandSourceStack source) {
		List<ChronicleEntry> lines = DeadlandsWorldData.get(source.getServer()).recentChronicle(10);
		if (lines.isEmpty()) {
			source.sendSuccess(() -> Component.translatable("command.deadlands.chronicle.empty"), false);
			return 0;
		}
		source.sendSuccess(() -> Component.translatable("command.deadlands.chronicle.header", lines.size()).withStyle(ChatFormatting.GOLD), false);
		for (ChronicleEntry entry : lines) {
			source.sendSuccess(() -> Component.translatable("command.deadlands.chronicle.line", entry.day(), entry.describe()), false);
		}
		return lines.size();
	}

	private static int reloadConfig(CommandSourceStack source) {
		ConfigManager.load();
		source.sendSuccess(() -> Component.translatable("command.deadlands.config.reloaded", ConfigManager.path().getFileName().toString()), true);
		return 1;
	}

	private static int nextDay(CommandSourceStack source) {
		MinecraftServer server = source.getServer();
		long dayTime = server.overworld().getDayTime();
		long nextMorning = (Math.floorDiv(dayTime, WorldClock.TICKS_PER_DAY) + 1) * WorldClock.TICKS_PER_DAY;
		WorldClock.setDayTime(server, nextMorning);
		WorldClock.tick(server);
		long day = WorldClock.currentDay(server);
		source.sendSuccess(() -> Component.translatable("command.deadlands.day.next", day), true);
		return (int) Math.min(Integer.MAX_VALUE, day);
	}

	private static int setDay(CommandSourceStack source, int day) {
		MinecraftServer server = source.getServer();
		long timeOfDay = Math.floorMod(server.overworld().getDayTime(), WorldClock.TICKS_PER_DAY);
		WorldClock.setDayTime(server, (day - 1L) * WorldClock.TICKS_PER_DAY + timeOfDay);
		WorldClock.tick(server);
		DLNetworking.broadcastStatus(server);
		source.sendSuccess(() -> Component.translatable("command.deadlands.day.set", day), true);
		return day;
	}

	private static int hordeStart(CommandSourceStack source) {
		if (!HordeDirector.forceStart(source.getServer())) {
			source.sendFailure(Component.translatable("command.deadlands.horde.cannot_start"));
			return 0;
		}
		HordeRecord horde = HordeDirector.active(source.getServer());
		source.sendSuccess(() -> Component.translatable("command.deadlands.horde.started", horde == null ? 0 : horde.total()), true);
		return 1;
	}

	private static int hordeStop(CommandSourceStack source) {
		if (!HordeDirector.forceStop(source.getServer())) {
			source.sendFailure(Component.translatable("command.deadlands.horde.none"));
			return 0;
		}
		source.sendSuccess(() -> Component.translatable("command.deadlands.horde.stopped"), true);
		return 1;
	}

	private static int hordeStatus(CommandSourceStack source) {
		HordeRecord horde = HordeDirector.active(source.getServer());
		if (horde == null) {
			source.sendSuccess(() -> Component.translatable("command.deadlands.horde.none"), false);
			return 0;
		}
		int alive = HordeDirector.countMembers(source.getServer().overworld(), horde.day());
		source.sendSuccess(() -> Component.translatable("command.deadlands.horde.status", horde.day(), horde.total(), horde.spawned(), alive,
			horde.killed()), false);
		return 1;
	}

	private static int infectedCount(CommandSourceStack source) {
		MinecraftServer server = source.getServer();
		int loaded = InfectedManager.loadedCount(server);
		int nests = NestRegistry.loadedCount(server.overworld());
		int migrations = DeadlandsWorldData.get(server).migrations().size();
		source.sendSuccess(() -> Component.translatable("command.deadlands.infected.count", loaded, ConfigManager.get().zombies.globalCap, nests,
			migrations, MigrationSystem.materializedGroups()), false);
		return loaded;
	}

	private static int infectedClear(CommandSourceStack source) {
		int removed = 0;
		for (ServerLevel level : source.getServer().getAllLevels()) {
			for (InfectedEntity infected : InfectedManager.loaded(level)) {
				infected.discard();
				removed++;
			}
		}
		int count = removed;
		source.sendSuccess(() -> Component.translatable("command.deadlands.infected.cleared", count), true);
		return removed;
	}

	private static int noise(CommandSourceStack source, String name) {
		Loudness loudness;
		try {
			loudness = Loudness.valueOf(name.toUpperCase(java.util.Locale.ROOT));
		} catch (IllegalArgumentException e) {
			source.sendFailure(Component.translatable("command.deadlands.noise.unknown", name));
			return 0;
		}
		NoiseSystem.emit(source.getLevel(), source.getPosition(), loudness, source.getEntity(), true);
		source.sendSuccess(() -> Component.translatable("command.deadlands.noise.made", name, (int) loudness.radius()), false);
		return 1;
	}

	private static int journal(CommandSourceStack source) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		SurvivorTracker.giveJournal(player);
		source.sendSuccess(() -> Component.translatable("command.deadlands.journal", player.getDisplayName()), true);
		return 1;
	}
}
