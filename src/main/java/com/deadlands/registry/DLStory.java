package com.deadlands.registry;

import com.deadlands.Deadlands;
import com.deadlands.block.KeycardReaderBlock;
import com.deadlands.block.RadioTransmitterBlock;
import com.deadlands.item.DocumentItem;
import com.deadlands.item.KeycardItem;
import com.deadlands.item.RadioItem;
import com.deadlands.item.StoryItem;
import com.mojang.serialization.Codec;
import java.util.List;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.DoubleHighBlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

/**
 * Milestone 8: documents and the lore they carry, the radio, the story's quest items, boss trophies,
 * helicopter parts, keycard readers, security doors and radio transmitters. Call after {@link DLBlocks}.
 */
public final class DLStory {
	/** Which lore entry a document is ({@code LoreEntries}). */
	public static final DataComponentType<String> LORE = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, Deadlands.id("lore"),
		DataComponentType.<String>builder().persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8).build());
	/** Words filled into a document's text (a trail note's town, direction and distance). */
	public static final DataComponentType<List<String>> LORE_ARGS = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, Deadlands.id("lore_args"),
		DataComponentType.<List<String>>builder().persistent(Codec.STRING.listOf())
			.networkSynchronized(ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list())).build());

	public static final Item DOCUMENT = DLItems.register("document", DocumentItem::new, new Item.Properties().stacksTo(1), DLTab.LORE);
	public static final Item RADIO = DLItems.register("radio", RadioItem::new, new Item.Properties().stacksTo(1), DLTab.RADIO);
	public static final Item RADIO_PARTS = DLItems.register("radio_parts", Item::new, new Item.Properties().stacksTo(8), DLTab.RADIO);

	// The missing research and the cure.
	public static final Item FIELD_CASE = story("field_case", Rarity.UNCOMMON);
	public static final Item MEDICAL_SAMPLES = story("medical_samples", Rarity.UNCOMMON);
	public static final Item HOSPITAL_RECORDS = story("hospital_records", Rarity.UNCOMMON);
	public static final Item RESEARCH_DOCUMENTS = story("research_documents", Rarity.UNCOMMON);
	public static final Item PATIENT_ZERO_RECORDS = story("patient_zero_records", Rarity.RARE);
	public static final Item MILITARY_RESEARCH = story("military_research", Rarity.RARE);
	public static final Item EXPERIMENTAL_FORMULA = story("experimental_formula", Rarity.RARE);
	public static final Item EVACUATION_ORDERS = story("evacuation_orders", Rarity.UNCOMMON);

	// Phase 5: Harley's questline. The case she gives the survivor to take to Adam.
	public static final Item SEALED_SAMPLE_CASE = story("sealed_sample_case", Rarity.RARE);
	/** Phase 5, chapter two: Adam's notebook, found at Kessler while "Adam's Lab" is under way. */
	public static final Item ADAM_FIELD_NOTEBOOK = story("adam_field_notebook", Rarity.RARE);

	// What the bosses leave behind.
	public static final Item FUSED_TISSUE = story("fused_tissue", Rarity.EPIC);
	public static final Item BUTCHERS_BADGE = story("butchers_badge", Rarity.EPIC);
	public static final Item WARDENS_KEYCARD = DLItems.register("wardens_keycard", p -> new KeycardItem(3, p),
		new Item.Properties().stacksTo(1).rarity(Rarity.EPIC), DLTab.QUEST_ITEMS);
	public static final Item SERUM_VIAL = story("serum_vial", Rarity.EPIC);
	public static final Item PATIENT_ZERO_BLOOD = story("patient_zero_blood", Rarity.EPIC);

	// The helicopter at Site Calder.
	public static final Item ROTOR_BLADE = story("rotor_blade", Rarity.UNCOMMON);
	public static final Item TURBINE_PARTS = story("turbine_parts", Rarity.UNCOMMON);
	public static final Item AVIONICS = story("avionics", Rarity.UNCOMMON);
	public static final Item AVIATION_FUEL = story("aviation_fuel", Rarity.UNCOMMON);

	public static final Block KEYCARD_READER = DLBlocks.register("keycard_reader", KeycardReaderBlock::new,
		BlockBehaviour.Properties.of().mapColor(MapColor.METAL).noCollision().strength(-1.0F, 3600000.0F).sound(SoundType.METAL)
			.pushReaction(PushReaction.BLOCK), DLTab.CUSTOM_BLOCKS);
	public static final Block RADIO_TRANSMITTER = DLBlocks.register("radio_transmitter", RadioTransmitterBlock::new,
		BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(4.0F, 12.0F).sound(SoundType.METAL).noOcclusion(), DLTab.RADIO);
	public static final Block SECURITY_DOOR = securityDoor();

	public static final net.minecraft.world.entity.EntityType<com.deadlands.story.HelicopterEntity> HELICOPTER = helicopter();

	private DLStory() {
	}

	private static Item story(String name, Rarity rarity) {
		return DLItems.register(name, StoryItem::new, new Item.Properties().stacksTo(1).rarity(rarity), DLTab.QUEST_ITEMS);
	}

	/** A heavy door that only power opens: a keycard reader beside it. */
	private static Block securityDoor() {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, Deadlands.id("security_door"));
		Block door = new DoorBlock(BlockSetType.IRON, BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(-1.0F, 3600000.0F)
			.noOcclusion().sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).setId(key));
		Registry.register(BuiltInRegistries.BLOCK, key, door);
		DLItems.register("security_door", p -> new DoubleHighBlockItem(door, p.useBlockDescriptionPrefix()), new Item.Properties(), DLTab.CUSTOM_BLOCKS);
		return door;
	}

	private static net.minecraft.world.entity.EntityType<com.deadlands.story.HelicopterEntity> helicopter() {
		ResourceKey<net.minecraft.world.entity.EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, Deadlands.id("helicopter"));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, net.minecraft.world.entity.EntityType.Builder.<com.deadlands.story.HelicopterEntity>of(
			com.deadlands.story.HelicopterEntity::new, net.minecraft.world.entity.MobCategory.MISC).sized(4.5F, 3.0F).clientTrackingRange(12).build(key));
	}

	/** The cure's documents and samples, in the order Harley lists them. */
	public static List<Item> cureItems() {
		return List.of(MEDICAL_SAMPLES, HOSPITAL_RECORDS, RESEARCH_DOCUMENTS, PATIENT_ZERO_RECORDS, MILITARY_RESEARCH, EXPERIMENTAL_FORMULA);
	}

	/** What each boss leaves, in boss order. */
	public static List<Item> trophies() {
		return List.of(FUSED_TISSUE, BUTCHERS_BADGE, WARDENS_KEYCARD, SERUM_VIAL, PATIENT_ZERO_BLOOD);
	}

	public static List<Item> helicopterParts() {
		return List.of(ROTOR_BLADE, TURBINE_PARTS, AVIONICS, AVIATION_FUEL);
	}

	public static String idOf(Item item) {
		return BuiltInRegistries.ITEM.getKey(item).getPath();
	}

	public static void init() {
		// Class loading registers everything above.
	}
}
