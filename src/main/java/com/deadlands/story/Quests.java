package com.deadlands.story;

import com.deadlands.item.FuelCanItem;
import com.deadlands.npc.NpcCharacter;
import com.deadlands.npc.NpcEntity;
import com.deadlands.npc.NpcWorldData;
import com.deadlands.persistence.ChronicleEntry;
import com.deadlands.persistence.DeadlandsWorldData;
import com.deadlands.registry.DLItems;
import com.deadlands.registry.DLStory;
import com.deadlands.registry.DLVehicles;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * The story's quests: what the journal's QUESTS page shows, and what the four characters say and do
 * when asked about them. Progress lives in {@link StoryState}; the quests are worked out from it.
 *
 * <ol>
 *   <li><b>The Opening.</b> Speak with Harley at her camp.</li>
 *   <li><b>The Missing Research.</b> Investigate a hospital, the Kessler research facility and a
 *       military evacuation site; bring back Harley's field case from her expedition camp; find four
 *       of her scattered research notes. Then hear what they mean: the army knew.</li>
 *   <li><b>The Cure.</b> Bring Harley the hospital's samples and records, the Kessler research
 *       documents, Patient Zero's records, the army's research, the experimental formula, and
 *       anything taken from two of the large forms (the bosses). She needs a day.</li>
 *   <li><b>A Way Out.</b> Learn where the army left a helicopter (Site Calder), reach it, find and fit
 *       its rotor blade, turbine parts, avionics and fuel, then gather everyone and decide.</li>
 *   <li><b>Personal stories.</b> Itzbreadloaf wants parts for his van; Imlilylol wants a radio tower
 *       working; FelixSyn wants the evacuation orders and the Warden at rest. Each who is helped joins
 *       the extraction.</li>
 * </ol>
 */
public final class Quests {
	/** One line of the QUESTS page. Style: 0 heading, 1 to do, 2 done, 3 note. */
	public record Line(Component text, int style) {
	}

	public static final int NOTES_NEEDED = 4;
	public static final int TROPHIES_NEEDED = 2;

	private Quests() {
	}

	// ======================================================================================== journal

	public static List<Line> journal(ServerPlayer player) {
		StoryState story = StoryState.get(player.level().getServer());
		List<Line> lines = new ArrayList<>();
		BlockPos at = player.blockPosition();
		NpcWorldData characters = NpcWorldData.get(player.level().getServer());
		if (!story.ending().isEmpty()) {
			lines.add(new Line(Component.translatable("quest.deadlands.ended." + story.ending()), 0));
		}
		if (!story.has(StoryState.MET + "harley")) {
			lines.add(heading("opening"));
			lines.add(todo(Component.translatable("quest.deadlands.opening.harley").append(hint(at, characters.position(NpcCharacter.HARLEY)))));
		} else if (!story.has(StoryState.MISSING_DONE)) {
			lines.add(heading("missing"));
			for (StoryPlaces.Place place : List.of(StoryPlaces.Place.HOSPITAL, StoryPlaces.Place.RESEARCH_FACILITY, StoryPlaces.Place.EVACUATION_SITE)) {
				boolean done = story.has(StoryState.INVESTIGATED + place.id());
				lines.add(objective(done, Component.translatable("quest.deadlands.missing.investigate", place.displayName())
					.append(done ? Component.empty() : placeHint(story, at, place))));
			}
			boolean caseDone = story.delivered(DLStory.idOf(DLStory.FIELD_CASE));
			lines.add(objective(caseDone, Component.translatable("quest.deadlands.missing.field_case")
				.append(caseDone ? Component.empty() : placeHint(story, at, StoryPlaces.Place.EXPEDITION_CAMP))));
			int notes = story.seriesRead("harley_research");
			lines.add(objective(notes >= NOTES_NEEDED, Component.translatable("quest.deadlands.missing.notes", Math.min(notes, NOTES_NEEDED), NOTES_NEEDED)));
			if (missingResearchComplete(story)) {
				lines.add(todo(Component.translatable("quest.deadlands.missing.return")));
			}
		} else {
			if (!story.has(StoryState.CURE_DONE)) {
				lines.add(heading("cure"));
				if (!story.has(StoryState.CURE_STARTED)) {
					for (Item item : DLStory.cureItems()) {
						lines.add(objective(story.delivered(DLStory.idOf(item)), Component.translatable("quest.deadlands.cure.item", item.getName())));
					}
					int trophies = trophiesDelivered(story);
					lines.add(objective(trophies >= TROPHIES_NEEDED, Component.translatable("quest.deadlands.cure.trophies", Math.min(trophies, TROPHIES_NEEDED),
						TROPHIES_NEEDED)));
					lines.add(note(Component.translatable("quest.deadlands.cure.deliver")));
				} else {
					lines.add(todo(Component.translatable("quest.deadlands.cure.wait")));
				}
			} else {
				lines.add(new Line(Component.translatable("quest.deadlands.cure.done"), 2));
			}
			lines.add(heading("heli"));
			if (!story.has(StoryState.CALDER_KNOWN)) {
				lines.add(todo(Component.translatable("quest.deadlands.heli.learn")));
			} else if (!story.has(StoryState.INVESTIGATED + StoryPlaces.Place.MILITARY_FACILITY.id())) {
				lines.add(todo(Component.translatable("quest.deadlands.heli.reach").append(placeHint(story, at, StoryPlaces.Place.MILITARY_FACILITY))));
			} else if (!story.helicopterReady()) {
				for (Item part : DLStory.helicopterParts()) {
					lines.add(objective(story.heliParts().contains(DLStory.idOf(part)), Component.translatable("quest.deadlands.heli.part", part.getName())));
				}
			} else if (story.ending().isEmpty()) {
				lines.add(todo(Component.translatable(story.has(StoryState.CURE_DONE) ? "quest.deadlands.heli.extract" : "quest.deadlands.heli.extract_early")));
			}
		}
		personal(story, characters, at, lines);
		return lines;
	}

	private static void personal(StoryState story, NpcWorldData characters, BlockPos at, List<Line> lines) {
		for (NpcCharacter character : List.of(NpcCharacter.ITZBREADLOAF, NpcCharacter.IMLILYLOL, NpcCharacter.FELIXSYN)) {
			if (!story.has(StoryState.MET + character.id())) {
				continue;
			}
			lines.add(new Line(Component.translatable("quest.deadlands." + character.id()), 0));
			if (story.has(StoryState.JOINED + character.id())) {
				lines.add(new Line(Component.translatable("quest.deadlands." + character.id() + ".done"), 2));
				continue;
			}
			switch (character) {
				case ITZBREADLOAF -> lines.add(todo(Component.translatable("quest.deadlands.itzbreadloaf.parts")));
				case IMLILYLOL -> {
					lines.add(objective(!story.towers().isEmpty(), Component.translatable("quest.deadlands.imlilylol.tower")));
					if (!story.towers().isEmpty()) {
						lines.add(todo(Component.translatable("quest.deadlands.return", character.displayName()).append(hint(at, characters.position(character)))));
					}
				}
				case FELIXSYN -> {
					lines.add(objective(story.delivered(DLStory.idOf(DLStory.EVACUATION_ORDERS)), Component.translatable("quest.deadlands.felixsyn.orders")
						.append(placeHint(story, at, StoryPlaces.Place.EVACUATION_SITE))));
					lines.add(objective(story.has(StoryState.BOSS + "warden"), Component.translatable("quest.deadlands.felixsyn.warden")));
				}
				default -> {
				}
			}
		}
	}

	private static Line heading(String quest) {
		return new Line(Component.translatable("quest.deadlands." + quest), 0);
	}

	private static Line todo(Component text) {
		return new Line(text, 1);
	}

	private static Line note(Component text) {
		return new Line(text, 3);
	}

	private static Line objective(boolean done, Component text) {
		return new Line(text, done ? 2 : 1);
	}

	/** " (northwest, about 400 blocks)", or nothing if the place is not known. */
	private static Component hint(BlockPos from, @Nullable BlockPos to) {
		if (to == null) {
			return Component.empty();
		}
		return Component.translatable("quest.deadlands.hint", Component.translatable("npc.deadlands.compass." + StoryLoot.Directions.compass(from, to)),
			StoryLoot.Directions.distance(from, to));
	}

	private static Component placeHint(StoryState story, BlockPos from, StoryPlaces.Place place) {
		BlockPos where = story.place(place);
		if (where == null) {
			return story.placeLooked(place) ? Component.translatable("quest.deadlands.hint.far") : Component.empty();
		}
		return hint(from, where);
	}

	public static boolean missingResearchComplete(StoryState story) {
		return story.has(StoryState.INVESTIGATED + StoryPlaces.Place.HOSPITAL.id())
			&& story.has(StoryState.INVESTIGATED + StoryPlaces.Place.RESEARCH_FACILITY.id())
			&& story.has(StoryState.INVESTIGATED + StoryPlaces.Place.EVACUATION_SITE.id())
			&& story.delivered(DLStory.idOf(DLStory.FIELD_CASE))
			&& story.seriesRead("harley_research") >= NOTES_NEEDED;
	}

	public static int trophiesDelivered(StoryState story) {
		int count = 0;
		for (Item trophy : DLStory.trophies()) {
			if (story.delivered(DLStory.idOf(trophy))) {
				count++;
			}
		}
		return count;
	}

	public static boolean cureComplete(StoryState story) {
		for (Item item : DLStory.cureItems()) {
			if (!story.delivered(DLStory.idOf(item))) {
				return false;
			}
		}
		return trophiesDelivered(story) >= TROPHIES_NEEDED;
	}

	// ======================================================================================== talking

	/** The character's answer to "About what you asked..." (and whatever they do about it). */
	public static Component talk(ServerPlayer player, NpcEntity npc, NpcCharacter character) {
		StoryState story = StoryState.get(player.level().getServer());
		return switch (character) {
			case HARLEY -> harley(player, story);
			case ITZBREADLOAF -> itzbreadloaf(player, story);
			case IMLILYLOL -> imlilylol(player, npc, story);
			case FELIXSYN -> felixsyn(player, story);
		};
	}

	private static Component harley(ServerPlayer player, StoryState story) {
		long day = player.level().getDayTime() / 24000L + 1L;
		if (!story.has(StoryState.MISSING_DONE)) {
			if (!story.delivered(DLStory.idOf(DLStory.FIELD_CASE)) && take(player, DLStory.FIELD_CASE)) {
				story.deliver(DLStory.idOf(DLStory.FIELD_CASE));
				if (!missingResearchComplete(story)) {
					return Component.translatable("quest.deadlands.harley.field_case");
				}
			}
			if (missingResearchComplete(story)) {
				story.set(StoryState.MISSING_DONE);
				story.set(StoryState.MILITARY_KNEW);
				give(player, new ItemStack(DLItems.KEYCARD_GREEN));
				chronicle(player, "chronicle.deadlands.story.missing_done");
				com.deadlands.cutscene.Cutscenes.play(player, com.deadlands.cutscene.Cutscenes.RESEARCH, player.position(), player.getYRot());
				return Component.translatable("quest.deadlands.harley.reveal");
			}
			return Component.translatable("quest.deadlands.harley.missing");
		}
		if (!story.has(StoryState.CURE_STARTED)) {
			int given = 0;
			for (Item item : DLStory.cureItems()) {
				if (!story.delivered(DLStory.idOf(item)) && take(player, item)) {
					story.deliver(DLStory.idOf(item));
					given++;
				}
			}
			for (Item trophy : DLStory.trophies()) {
				if (story.delivered(DLStory.idOf(trophy))) {
					continue;
				}
				// She studies the Warden's keycard and hands it back; the rest she keeps.
				boolean has = trophy == DLStory.WARDENS_KEYCARD ? player.getInventory().contains(new ItemStack(trophy)) : take(player, trophy);
				if (has) {
					story.deliver(DLStory.idOf(trophy));
					given++;
				}
			}
			if (cureComplete(story)) {
				story.set(StoryState.CURE_STARTED);
				story.setCureReadyDay(day + 1);
				chronicle(player, "chronicle.deadlands.story.cure_started");
				return Component.translatable("quest.deadlands.harley.cure_start");
			}
			int missing = remainingCure(story);
			return given > 0 ? Component.translatable("quest.deadlands.harley.delivered", given, missing)
				: Component.translatable("quest.deadlands.harley.cure_need", missing);
		}
		if (!story.has(StoryState.CURE_DONE)) {
			return Component.translatable("quest.deadlands.harley.cure_wait");
		}
		if (!story.helicopterReady()) {
			return Component.translatable(story.has(StoryState.CALDER_KNOWN) ? "quest.deadlands.harley.cure_done_calder" : "quest.deadlands.harley.cure_done");
		}
		return Component.translatable("quest.deadlands.harley.ready");
	}

	private static int remainingCure(StoryState story) {
		int missing = 0;
		for (Item item : DLStory.cureItems()) {
			if (!story.delivered(DLStory.idOf(item))) {
				missing++;
			}
		}
		return missing + Math.max(0, TROPHIES_NEEDED - trophiesDelivered(story));
	}

	private static Component itzbreadloaf(ServerPlayer player, StoryState story) {
		if (story.has(StoryState.ITZ_DONE)) {
			return Component.translatable("quest.deadlands.itzbreadloaf.after");
		}
		int parts = count(player, DLVehicles.ENGINE_PARTS);
		int tires = count(player, DLVehicles.TIRE);
		if (parts >= 3 && tires >= 2) {
			remove(player, DLVehicles.ENGINE_PARTS, 3);
			remove(player, DLVehicles.TIRE, 2);
			story.set(StoryState.ITZ_DONE);
			story.set(StoryState.JOINED + "itzbreadloaf");
			ItemStack can = new ItemStack(DLVehicles.FUEL_CAN);
			FuelCanItem.setLitres(can, FuelCanItem.CAPACITY);
			give(player, can);
			give(player, Lore.document("itz_log_4"));
			chronicle(player, "chronicle.deadlands.story.itzbreadloaf");
			return Component.translatable("quest.deadlands.itzbreadloaf.thanks");
		}
		return Component.translatable("quest.deadlands.itzbreadloaf.ask", parts, tires);
	}

	private static Component imlilylol(ServerPlayer player, NpcEntity npc, StoryState story) {
		if (story.has(StoryState.LILY_DONE)) {
			return Component.translatable("quest.deadlands.imlilylol.after");
		}
		if (story.towers().isEmpty()) {
			return Component.translatable("quest.deadlands.imlilylol.ask");
		}
		story.set(StoryState.LILY_DONE);
		story.set(StoryState.JOINED + "imlilylol");
		story.set(StoryState.CALDER_KNOWN);
		give(player, Lore.document("lily_letter_4"));
		chronicle(player, "chronicle.deadlands.story.imlilylol");
		BlockPos felix = StoryDirector.planFallback(player.level(), NpcCharacter.FELIXSYN);
		if (felix != null && !story.has(StoryState.MET + "felixsyn")) {
			List<String> words = StoryLoot.Directions.words(player.level(), npc.blockPosition(), felix);
			return Component.translatable("quest.deadlands.imlilylol.thanks_felix", words.get(0),
				Component.translatable("npc.deadlands.compass." + words.get(1)), words.get(2));
		}
		return Component.translatable("quest.deadlands.imlilylol.thanks");
	}

	private static Component felixsyn(ServerPlayer player, StoryState story) {
		if (story.has(StoryState.FELIX_DONE)) {
			return Component.translatable("quest.deadlands.felixsyn.after");
		}
		String orders = DLStory.idOf(DLStory.EVACUATION_ORDERS);
		if (!story.delivered(orders) && take(player, DLStory.EVACUATION_ORDERS)) {
			story.deliver(orders);
			if (!story.has(StoryState.BOSS + "warden")) {
				return Component.translatable("quest.deadlands.felixsyn.orders_read");
			}
		}
		if (story.delivered(orders) && story.has(StoryState.BOSS + "warden")) {
			story.set(StoryState.FELIX_DONE);
			story.set(StoryState.JOINED + "felixsyn");
			give(player, new ItemStack(DLItems.KEYCARD_BLUE));
			chronicle(player, "chronicle.deadlands.story.felixsyn");
			return Component.translatable("quest.deadlands.felixsyn.thanks");
		}
		return Component.translatable(story.delivered(orders) ? "quest.deadlands.felixsyn.need_warden" : "quest.deadlands.felixsyn.ask");
	}

	// ======================================================================================== inventory helpers

	static int count(ServerPlayer player, Item item) {
		Inventory inventory = player.getInventory();
		int total = 0;
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (stack.is(item)) {
				total += stack.getCount();
			}
		}
		return total;
	}

	static void remove(ServerPlayer player, Item item, int amount) {
		Inventory inventory = player.getInventory();
		for (int slot = 0; slot < inventory.getContainerSize() && amount > 0; slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (stack.is(item)) {
				int take = Math.min(amount, stack.getCount());
				stack.shrink(take);
				amount -= take;
			}
		}
	}

	/** Take one of an item from the player; false if they had none. */
	static boolean take(ServerPlayer player, Item item) {
		if (count(player, item) <= 0) {
			return false;
		}
		remove(player, item, 1);
		return true;
	}

	static void give(ServerPlayer player, ItemStack stack) {
		if (!player.getInventory().add(stack)) {
			player.drop(stack, false);
		}
	}

	static void chronicle(ServerPlayer player, String key) {
		DeadlandsWorldData data = DeadlandsWorldData.get(player.level().getServer());
		data.addChronicle(new ChronicleEntry(player.level().getDayTime() / 24000L + 1L, key, List.of(player.getName().getString())), 64);
		data.setDirty();
	}

	/** "Name: text" for system messages about the story. */
	static MutableComponent named(String who, Component text) {
		return Component.literal(who + ": ").append(text);
	}
}
