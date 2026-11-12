package com.deadlands.story;

import com.deadlands.Deadlands;
import com.deadlands.block.DecorKind;
import com.deadlands.container.LootContainerBlockEntity;
import com.deadlands.network.IntroDonePayload;
import com.deadlands.network.IntroPayload;
import com.deadlands.npc.NpcDialogue;
import com.deadlands.npc.NpcEntity;
import com.deadlands.registry.DLContainers;
import com.deadlands.registry.DLDecor;
import com.deadlands.registry.DLStory;
import com.deadlands.registry.DLSurvivalItems;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The opening. A survivor's first arrival: Harley is placed a few blocks away (by {@code NpcSpawns}),
 * and the first time, her camp is built around her: a tent, a lit fire, a workbench and a crate of
 * supplies. The survivor is given a radio and a little to eat, drink and patch up with, and the
 * client plays the introduction (skippable). When it ends, Harley speaks.
 */
public final class StoryIntro {
	public static final String CAMP_BUILT = "harley_camp_built";

	private StoryIntro() {
	}

	public static void init() {
		ServerPlayNetworking.registerGlobalReceiver(IntroDonePayload.TYPE, (payload, context) -> done(context.player()));
	}

	/** First arrival: camp, supplies, introduction. Call after Harley has been placed. */
	public static void begin(ServerPlayer player) {
		ServerLevel level = player.level();
		StoryState story = StoryState.get(level.getServer());
		NpcEntity harley = harleyNear(player, 64.0); // her camp may be up to 48 blocks off when the ground nearby is unsafe
		if (harley != null && story.set(CAMP_BUILT)) {
			buildCamp(level, harley.blockPosition());
		}
		for (ItemStack stack : new ItemStack[] {new ItemStack(DLStory.RADIO), new ItemStack(DLSurvivalItems.BANDAGE, 2),
			new ItemStack(DLSurvivalItems.WATER_BOTTLE, 2), new ItemStack(DLSurvivalItems.CANNED_BEANS, 2)}) {
			Quests.give(player, stack);
		}
		if (ServerPlayNetworking.canSend(player, IntroPayload.TYPE)) {
			com.deadlands.cutscene.Cutscenes.play(player, com.deadlands.cutscene.Cutscenes.OPENING, player.position(), 0.0F); // marks it seen
			net.minecraft.world.phys.Vec3 camp = harley != null ? harley.position() : player.position();
			ServerPlayNetworking.send(player, new IntroPayload(harley != null, camp.x, camp.y, camp.z));
		} else {
			done(player);
		}
	}

	/** A world whose Harley never came (the old spawn bug): place her now and build her camp. */
	public static void repairHarley(ServerPlayer player) {
		NpcEntity harley = com.deadlands.npc.NpcSpawns.onFirstArrival(player);
		if (harley != null && StoryState.get(player.level().getServer()).set(CAMP_BUILT)) {
			buildCamp(player.level(), harley.blockPosition());
		}
	}

	/** The introduction is over (watched or skipped): Harley speaks, if she is near. */
	public static void done(ServerPlayer player) {
		NpcEntity harley = harleyNear(player, 64.0);
		if (harley != null) {
			harley.getLookControl().setLookAt(player);
			NpcDialogue.open(player, harley);
		} else {
			player.displayClientMessage(Component.translatable("message.deadlands.story.find_harley"), false);
		}
	}

	private static NpcEntity harleyNear(ServerPlayer player, double range) {
		for (NpcEntity npc : player.level().getEntitiesOfClass(NpcEntity.class, player.getBoundingBox().inflate(range),
			npc -> npc.character() == com.deadlands.npc.NpcCharacter.HARLEY)) {
			return npc;
		}
		return null;
	}

	/** A small research camp around Harley. Only touches air above solid ground. */
	static void buildCamp(ServerLevel level, BlockPos center) {
		// Tent: a wool ridge over a carpet, open to the south.
		for (int k = 0; k < 3; k++) {
			put(level, center.offset(-4, 0, 1 + k), Blocks.LIGHT_GRAY_CARPET.defaultBlockState(), 0);
			put(level, center.offset(-5, 0, 1 + k), Blocks.WHITE_WOOL.defaultBlockState(), 0);
			put(level, center.offset(-3, 0, 1 + k), Blocks.WHITE_WOOL.defaultBlockState(), 0);
			put(level, center.offset(-4, 0, 1 + k), Blocks.WHITE_WOOL.defaultBlockState(), 1);
		}
		for (int x = -5; x <= -3; x++) {
			put(level, center.offset(x, 0, 4), Blocks.WHITE_WOOL.defaultBlockState(), 0);
		}
		put(level, center.offset(3, 0, 0), Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true), 0);
		put(level, center.offset(2, 0, -3), DLDecor.get(DecorKind.WORKBENCH).defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH), 0);
		BlockPos crate = put(level, center.offset(-2, 0, -3), DLContainers.MILITARY_CRATE.defaultBlockState(), 0);
		if (crate != null && level.getBlockEntity(crate) instanceof LootContainerBlockEntity container) {
			container.setLootTable(ResourceKey.create(Registries.LOOT_TABLE, Deadlands.id("story/harley_camp")), level.getRandom().nextLong());
		}
	}

	/**
	 * Place a block {@code up} blocks above the camp's ground level (Harley's feet), if that spot is
	 * free and, for ground-level blocks, stands on something solid. Returns where, or null.
	 */
	private static BlockPos put(ServerLevel level, BlockPos column, BlockState state, int up) {
		BlockPos pos = column.above(up);
		if (!level.getBlockState(pos).canBeReplaced()) {
			return null;
		}
		if (up == 0 && !level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)) {
			return null;
		}
		level.setBlock(pos, state, Block.UPDATE_ALL);
		return pos;
	}
}
