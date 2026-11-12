package com.deadlands.npc;

import com.deadlands.Deadlands;
import com.deadlands.network.DialogueChoicePayload;
import com.deadlands.network.DialoguePayload;
import com.deadlands.registry.DLSurvivalItems;
import com.deadlands.registry.DLVehicles;
import com.deadlands.registry.DLWeapons;
import com.deadlands.survival.SurvivalAttachments;
import com.deadlands.survival.SurvivalData;
import com.deadlands.vehicle.VehicleEntity;
import com.deadlands.world.power.PowerGrid;
import com.deadlands.world.town.TownIdentity;
import com.deadlands.world.town.TownLookup;
import com.deadlands.world.town.TownSite;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Conversations with survivors, decided on the server. Opening one sends the survivor's greeting
 * (which depends on their temperament, on how their faction regards the player, and on whether
 * they are grieving) and the replies on offer; each reply the player picks is answered with new
 * text and the same replies.
 *
 * <p>Replies: <b>trade</b> (those with something to sell), <b>rumour</b> (word of another town:
 * its name, which way it lies and what became of it), <b>place</b> (leaders: this town's history,
 * whether it has power, and how the settlement is doing), <b>give</b> (leaders: hand over what you
 * are holding; food and medicine fill the stores, building materials the defences), <b>family</b>
 * (those who have someone: who they are, or that they are gone), <b>heal</b> (doctors: wounds,
 * bleeding, fractures and sickness, for six rounds, free to friends), <b>repair</b> (mechanics:
 * the nearest vehicle's engine, tires and body, for ten rounds, free to the trusted), <b>story</b>
 * (the recurring characters tell their history a part at a time; once it is told they point the
 * way to the next of the four), and <b>leave</b>.
 *
 * <p>How far each survivor has got through each character's story is saved with them and kept
 * through death.
 */
public final class NpcDialogue {
	public static final String TRADE = "trade";
	public static final String RUMOUR = "rumour";
	public static final String PLACE = "place";
	public static final String GIVE = "give";
	public static final String FAMILY = "family";
	public static final String HEAL = "heal";
	public static final String REPAIR = "repair";
	public static final String STORY = "story";
	public static final String LEAVE = "leave";
	public static final String QUEST = "quest";
	public static final String ELIAS = "elias";
	private static final int HEAL_COST = 6;
	private static final int REPAIR_COST = 10;
	private static final int RUMOURS = 6;
	private static final String[] COMPASS = {"south", "southwest", "west", "northwest", "north", "northeast", "east", "southeast"};

	/** How far the player has got through each character's story: character id to parts heard. */
	public static final AttachmentType<Map<String, Integer>> STORY_PROGRESS = AttachmentRegistry.create(Deadlands.id("story_progress"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Codec.INT)).initializer(() -> Map.of()).copyOnDeath());

	private NpcDialogue() {
	}

	public static void init() {
		ServerPlayNetworking.registerGlobalReceiver(DialogueChoicePayload.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			Entity entity = player.level().getEntity(payload.entityId());
			if (entity instanceof NpcEntity npc && npc.isAlive() && npc.distanceToSqr(player) < 100.0 && !npc.isHostileTo(player)) {
				choose(player, npc, payload.option());
			}
		});
	}

	public static void open(ServerPlayer player, NpcEntity npc) {
		NpcCharacter character = npc.character();
		if (character == NpcCharacter.HARLEY) {
			com.deadlands.achievement.Achievements.award(player, "story/met_harley");
		}
		if (character == NpcCharacter.HARLEY || character == NpcCharacter.ADAM) {
			com.deadlands.story.HarleyQuest.talked(player, npc); // Phase 5: her questline
		}
		Reputation.Standing standing = Reputation.standing(player, npc.faction());
		String mood = standing == Reputation.Standing.WARY ? "wary" : standing.ordinal() >= Reputation.Standing.FRIENDLY.ordinal() ? "friendly" : "neutral";
		Component greeting;
		if (character != null) {
			com.deadlands.story.StoryState story = com.deadlands.story.StoryState.get(player.level().getServer());
			boolean first = story.set(com.deadlands.story.StoryState.MET + character.id());
			if (first) {
				if (character == NpcCharacter.HARLEY) {
					story.setHarleyMetDay(player.level().getDayTime() / 24000L + 1L);
				}
				player.displayClientMessage(Component.translatable("message.deadlands.story.quest_" + character.id()), false);
			}
			boolean readTheirs = false;
			for (com.deadlands.story.LoreEntry entry : com.deadlands.story.Lore.all()) {
				if (entry.category() == com.deadlands.story.LoreCategory.CHARACTER && entry.id().startsWith(journalPrefix(character))
					&& com.deadlands.story.Archive.discovered(player).containsKey(entry.id())) {
					readTheirs = true;
					break;
				}
			}
			greeting = Component.translatable("npc.deadlands.character." + character.id() + (first && readTheirs ? ".recognise" : ".greeting"), player.getName());
		} else if (npc.grieving()) {
			greeting = Component.translatable("npc.deadlands.greeting.grieving", npc.relativeName());
		} else {
			greeting = Component.translatable("npc.deadlands.greeting." + mood + "." + npc.temperament(), player.getName());
		}
		send(player, npc, greeting);
	}

	private static void send(ServerPlayer player, NpcEntity npc, Component text) {
		if (!ServerPlayNetworking.canSend(player, DialoguePayload.TYPE)) {
			return;
		}
		Reputation.Standing standing = Reputation.standing(player, npc.faction());
		Component subtitle = Component.translatable("npc.deadlands.subtitle", Component.translatable(npc.role().translationKey()),
			Component.translatable(npc.faction().translationKey()), Component.translatable(standing.translationKey()));
		ServerPlayNetworking.send(player, new DialoguePayload(npc.getId(), npc.getName(), subtitle, text, options(npc)));
	}

	/** The id prefix of a character's own journal entries. */
	private static String journalPrefix(NpcCharacter character) {
		return switch (character) {
			case HARLEY -> "harley_journal";
			case ITZBREADLOAF -> "itz_log";
			case IMLILYLOL -> "lily_letter";
			case FELIXSYN -> "felix_notes";
			case ADAM -> "adam_notes"; // none yet: his research documents come with Phase 5
		};
	}

	static List<String> options(NpcEntity npc) {
		List<String> options = new ArrayList<>();
		if (npc.character() != null) {
			options.add(QUEST);
			options.add(STORY);
			if (npc.character() == NpcCharacter.HARLEY && npc.level() instanceof ServerLevel server
				&& com.deadlands.story.Endings.secretReady(com.deadlands.story.StoryState.get(server.getServer()))) {
				options.add(ELIAS);
			}
		}
		if (npc.role().trades()) {
			options.add(TRADE);
		}
		if (npc.role() == NpcRole.DOCTOR) {
			options.add(HEAL);
		}
		if (npc.role() == NpcRole.MECHANIC) {
			options.add(REPAIR);
		}
		if (npc.role() == NpcRole.LEADER) {
			options.add(PLACE);
			if (npc.townKey() != 0L) {
				options.add(GIVE);
			}
		}
		if (npc.relation() != 0) {
			options.add(FAMILY);
		}
		options.add(RUMOUR);
		options.add(LEAVE);
		return options;
	}

	private static void choose(ServerPlayer player, NpcEntity npc, String option) {
		if (!options(npc).contains(option)) {
			return;
		}
		switch (option) {
			case TRADE -> npc.trade(player);
			case RUMOUR -> send(player, npc, rumour(player, npc));
			case PLACE -> send(player, npc, place(npc));
			case GIVE -> send(player, npc, give(player, npc));
			case FAMILY -> send(player, npc, family(npc));
			case HEAL -> send(player, npc, heal(player, npc));
			case REPAIR -> send(player, npc, repair(player, npc));
			case STORY -> send(player, npc, story(player, npc));
			case QUEST -> send(player, npc, com.deadlands.story.Quests.talk(player, npc, npc.character()));
			case ELIAS -> send(player, npc, com.deadlands.story.Endings.secret(player));
			default -> {
			}
		}
	}

	// ======================================================================================== replies

	/** Which way and how far, rounded to the nearest hundred blocks (at least a hundred). */
	private static Component[] direction(double fromX, double fromZ, double toX, double toZ) {
		double angle = Math.atan2(-(toX - fromX), toZ - fromZ); // 0 = south, like yaw
		int sector = Math.floorMod((int) Math.round(angle / (Math.PI / 4.0)), 8);
		int distance = (int) Math.round(Math.hypot(toX - fromX, toZ - fromZ) / 100.0) * 100;
		return new Component[] {Component.translatable("npc.deadlands.compass." + COMPASS[sector]), Component.literal(Integer.toString(Math.max(100, distance)))};
	}

	private static Component rumour(ServerPlayer player, NpcEntity npc) {
		ServerLevel level = player.level();
		TownSite here = TownLookup.nearest(level, npc.blockPosition(), 170.0).orElse(null);
		List<TownSite> towns = new ArrayList<>(TownLookup.around(level, npc.blockPosition(), 2));
		towns.removeIf(town -> here != null && town.key() == here.key());
		if (towns.isEmpty() || npc.getRandom().nextFloat() < 0.3F) {
			return Component.translatable("npc.deadlands.rumour." + npc.getRandom().nextInt(RUMOURS));
		}
		TownSite town = towns.get(npc.getRandom().nextInt(towns.size()));
		Component[] way = direction(npc.getX(), npc.getZ(), town.centerX(), town.centerZ());
		Settlements.Settlement settlement = Settlements.get(level.getServer()).of(town.key());
		String condition = settlement != null && settlement.fallen() ? "fallen" : town.condition().id();
		return Component.translatable("npc.deadlands.rumour.town", TownIdentity.of(level.getSeed(), town).name(), way[0], way[1],
			Component.translatable("npc.deadlands.rumour.condition." + condition));
	}

	private static Component place(NpcEntity npc) {
		ServerLevel level = (ServerLevel) npc.level();
		TownSite town = TownLookup.nearest(level, npc.blockPosition(), 170.0).orElse(null);
		if (town == null) {
			return Component.translatable("npc.deadlands.place.none");
		}
		TownIdentity identity = TownIdentity.of(level.getSeed(), town);
		Component history = Component.translatable(PowerGrid.isPowered(level, town) ? "npc.deadlands.place.powered" : "npc.deadlands.place.dark",
			identity.name(), Component.translatable(identity.historyKey(), identity.name()));
		Settlements.Settlement settlement = npc.townKey() == 0L ? null : Settlements.get(level.getServer()).of(npc.townKey());
		return settlement == null ? history : Component.empty().append(history).append(" ").append(settlement.status());
	}

	private static final java.util.Set<net.minecraft.world.item.Item> MEDICINE = java.util.Set.of(DLSurvivalItems.BANDAGE, DLSurvivalItems.ANTISEPTIC,
		DLSurvivalItems.ANTIBIOTICS, DLSurvivalItems.PAINKILLERS, DLSurvivalItems.SPLINT, DLSurvivalItems.MEDKIT, DLSurvivalItems.ANTIVIRAL);

	/** Hand the leader what is in your main hand, all of it: food and medicine for the stores, materials for the walls. */
	private static Component give(ServerPlayer player, NpcEntity npc) {
		Settlements settlements = Settlements.get(player.level().getServer());
		Settlements.Settlement settlement = settlements.of(npc.townKey());
		if (settlement == null || !settlement.inhabited()) {
			return Component.translatable("npc.deadlands.give.none_here");
		}
		ItemStack held = player.getMainHandItem();
		int count = held.getCount();
		int supplies = 0;
		int defence = 0;
		if (held.has(DataComponents.FOOD) || held.is(DLSurvivalItems.WATER_BOTTLE)) {
			supplies = count;
		} else if (MEDICINE.contains(held.getItem())) {
			supplies = count * 2;
		} else if (held.is(ItemTags.PLANKS) || held.is(ItemTags.LOGS)) {
			defence = Math.max(1, count / 4);
		} else if (held.is(Items.COBBLESTONE) || held.is(Items.IRON_INGOT) || held.is(Items.IRON_BARS) || held.is(DLVehicles.BODY_PANEL)) {
			defence = Math.max(1, count / 2);
		}
		if (supplies == 0 && defence == 0) {
			return Component.translatable("npc.deadlands.give.nothing");
		}
		held.shrink(count);
		settlements.give(settlement, supplies, defence);
		Reputation.change(player, npc.faction(), Math.min(8, Math.max(1, (supplies + defence * 2) / 4)));
		return Component.translatable(supplies > 0 ? "npc.deadlands.give.supplies" : "npc.deadlands.give.defence", settlement.status());
	}

	private static Component family(NpcEntity npc) {
		String relation = NpcEntity.RELATIONS[Math.floorMod(npc.relation(), NpcEntity.RELATIONS.length)];
		return Component.translatable("npc.deadlands.family." + (npc.grieving() ? "grieving." : "") + relation, npc.relativeName());
	}

	private static Component heal(ServerPlayer player, NpcEntity npc) {
		SurvivalData body = SurvivalAttachments.get(player);
		boolean hurt = player.getHealth() < player.getMaxHealth() || body.bleeding() > 0 || body.fracture() > 0 || body.sickness() > 0;
		if (!hurt) {
			return Component.translatable(body.infected() ? "npc.deadlands.heal.infected" : "npc.deadlands.heal.fine");
		}
		boolean free = Reputation.standing(player, npc.faction()).ordinal() >= Reputation.Standing.FRIENDLY.ordinal();
		if (!free && !pay(player, HEAL_COST)) {
			return Component.translatable("npc.deadlands.cannot_pay", HEAL_COST);
		}
		player.heal(player.getMaxHealth());
		body.setBleeding(0);
		body.setFracture(0);
		body.setSickness(0);
		return Component.translatable(body.infected() ? "npc.deadlands.heal.done_infected" : "npc.deadlands.heal.done");
	}

	private static Component repair(ServerPlayer player, NpcEntity npc) {
		VehicleEntity vehicle = null;
		double best = Double.MAX_VALUE;
		for (VehicleEntity candidate : player.level().getEntitiesOfClass(VehicleEntity.class, npc.getBoundingBox().inflate(12.0), v -> !v.burned())) {
			double distance = candidate.distanceToSqr(npc);
			if (distance < best) {
				best = distance;
				vehicle = candidate;
			}
		}
		if (vehicle == null) {
			return Component.translatable("npc.deadlands.repair.none");
		}
		if (vehicle.engine() >= 100.0F && vehicle.tires() >= 100.0F && vehicle.body() >= 100.0F) {
			return Component.translatable("npc.deadlands.repair.fine");
		}
		boolean free = Reputation.standing(player, npc.faction()) == Reputation.Standing.TRUSTED;
		if (!free && !pay(player, REPAIR_COST)) {
			return Component.translatable("npc.deadlands.cannot_pay", REPAIR_COST);
		}
		vehicle.setEngine(vehicle.engine() + 25.0F);
		vehicle.setTires(vehicle.tires() + 25.0F);
		vehicle.setBody(vehicle.body() + 25.0F);
		return Component.translatable(vehicle.drivable() ? "npc.deadlands.repair.done" : "npc.deadlands.repair.more", Component.translatable(vehicle.kind().translationKey()));
	}

	private static Component story(ServerPlayer player, NpcEntity npc) {
		NpcCharacter character = npc.character();
		if (character == null) {
			return Component.translatable("npc.deadlands.rumour.0");
		}
		Map<String, Integer> saved = player.getAttachedOrCreate(STORY_PROGRESS);
		int part = saved.getOrDefault(character.id(), 0);
		if (part < character.storyLines()) {
			Map<String, Integer> next = new HashMap<>(saved);
			next.put(character.id(), part + 1);
			player.setAttached(STORY_PROGRESS, next);
			return Component.translatable("npc.deadlands.character." + character.id() + ".story." + part);
		}
		return clue(player, npc, character);
	}

	/** Once a character's history is told, they point the way to the next of the four. */
	private static Component clue(ServerPlayer player, NpcEntity npc, NpcCharacter character) {
		NpcCharacter[] all = NpcCharacter.values();
		NpcCharacter next = all[(character.ordinal() + 1) % all.length];
		BlockPos where = NpcWorldData.get(player.level().getServer()).position(next);
		if (where == null) {
			return Component.translatable("npc.deadlands.clue.unknown", next.displayName());
		}
		if (where.distSqr(npc.blockPosition()) < 48.0 * 48.0) {
			return Component.translatable("npc.deadlands.clue.near", next.displayName());
		}
		Component[] way = direction(npc.getX(), npc.getZ(), where.getX(), where.getZ());
		return Component.translatable("npc.deadlands.clue.known", next.displayName(), way[0], way[1]);
	}

	/** Parts of a character's story this player has heard. */
	public static int storyHeard(ServerPlayer player, NpcCharacter character) {
		return player.getAttachedOrCreate(STORY_PROGRESS).getOrDefault(character.id(), 0);
	}

	/** Take {@code rounds} 9 mm rounds from the player, or nothing if they have fewer. */
	static boolean pay(ServerPlayer player, int rounds) {
		Inventory inventory = player.getInventory();
		int have = 0;
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (stack.is(DLWeapons.PISTOL_AMMO)) {
				have += stack.getCount();
			}
		}
		if (have < rounds) {
			return false;
		}
		int left = rounds;
		for (int slot = 0; slot < inventory.getContainerSize() && left > 0; slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (stack.is(DLWeapons.PISTOL_AMMO)) {
				int take = Math.min(left, stack.getCount());
				stack.shrink(take);
				left -= take;
			}
		}
		return true;
	}
}
