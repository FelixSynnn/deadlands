package com.deadlands.story;

import com.deadlands.Deadlands;
import com.deadlands.achievement.Achievements;
import com.deadlands.content.DLContent;
import com.deadlands.flags.StoryFlags;
import com.deadlands.mission.MissionDef;
import com.deadlands.mission.MissionManager;
import com.deadlands.npc.NpcCharacter;
import com.deadlands.npc.NpcEntity;
import com.deadlands.npc.NpcSpawns;
import com.deadlands.npc.NpcWorldData;
import com.deadlands.npc.SafeSpots;
import com.deadlands.registry.DLStory;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Phase 5, Itzbreadloaf's campaign, chapter one: Harley's questline (Story Canon: she asks for specific
 * items; on return she gives an object of real story significance to deliver to Adam; Adam recognises
 * its importance; the main story advances).
 *
 * <ol>
 *   <li><b>Static</b> ({@code harley/static}): two radio parts and a fuse for her receiver.</li>
 *   <li><b>Cold Chain</b> ({@code harley/cold_chain}): a car battery and two purification tablets. On return
 *       she gives the sealed sample case from the regional lab, with Adam's project number on the seal, and
 *       says where he is holed up: Adam is placed at a safe spot 120 to 200 blocks away, and the
 *       coordinates go on screen and in chat.</li>
 *   <li><b>The Case</b> ({@code story/the_case}): bring it to Adam. He takes it and recognises it.</li>
 * </ol>
 *
 * Talking to Harley offers her next mission (her cooldown and "one at a time" rules come from her giver
 * file), or, when the asked-for items are carried, completes it (rewards first) and takes them.
 */
public final class HarleyQuest {
	public static final Identifier STATIC = Deadlands.id("harley/static");
	public static final Identifier COLD_CHAIN = Deadlands.id("harley/cold_chain");
	public static final Identifier THE_CASE = Deadlands.id("story/the_case");
	public static final String GAVE_CASE = "harley_gave_case";
	public static final String ADAM_SAW_CASE = "adam_saw_case";

	private HarleyQuest() {
	}

	/** A survivor opened a conversation with Harley or Adam. */
	public static void talked(ServerPlayer player, NpcEntity npc) {
		MinecraftServer server = player.level().getServer();
		if (npc.character() == NpcCharacter.HARLEY) {
			harley(server, player);
		} else if (npc.character() == NpcCharacter.ADAM) {
			adam(server, player);
		}
	}

	private static void harley(MinecraftServer server, ServerPlayer player) {
		for (Identifier id : List.of(STATIC, COLD_CHAIN)) {
			if (!MissionManager.isActive(server, id)) {
				continue;
			}
			MissionDef def = DLContent.MISSIONS.get(id).orElse(null);
			if (def == null) {
				return;
			}
			String key = "mission.deadlands." + id.getPath().replace('/', '.');
			if (!carries(player.getInventory(), def)) {
				say(player, "Harley", key + ".reminder");
				return;
			}
			MissionManager.complete(server, id); // rewards first: taking the items would undo "have the items"
			take(player.getInventory(), def);
			say(player, "Harley", key + ".thanks");
			if (id.equals(COLD_CHAIN)) {
				StoryFlags.get(server).set(GAVE_CASE);
				placeAdam(player);
				MissionManager.start(server, THE_CASE, player);
			}
			return;
		}
		MissionManager.offer(server, "harley", player).ifPresent(id -> {
			if (MissionManager.start(server, id, player)) {
				say(player, "Harley", "mission.deadlands." + id.getPath().replace('/', '.') + ".ask");
			}
		});
	}

	private static void adam(MinecraftServer server, ServerPlayer player) {
		if (!MissionManager.isActive(server, THE_CASE) || StoryFlags.get(server).has(ADAM_SAW_CASE)) {
			return;
		}
		Inventory inventory = player.getInventory();
		if (inventory.countItem(DLStory.SEALED_SAMPLE_CASE) <= 0) {
			say(player, "Adam", "mission.deadlands.story.the_case.waiting");
			return;
		}
		takeOne(inventory, DLStory.SEALED_SAMPLE_CASE);
		StoryFlags.get(server).set(ADAM_SAW_CASE);
		for (int line = 1; line <= 3; line++) {
			say(player, "Adam", "mission.deadlands.story.the_case.adam." + line);
		}
		Achievements.award(player, "story/the_case");
	}

	/** Adam: somewhere safe 120 to 200 blocks from Harley's camp, once. Coordinates on screen and in chat. */
	private static void placeAdam(ServerPlayer player) {
		ServerLevel level = player.level();
		NpcWorldData data = NpcWorldData.get(level.getServer());
		BlockPos where = data.position(NpcCharacter.ADAM);
		if (!data.isPlaced(NpcCharacter.ADAM)) {
			BlockPos camp = data.position(NpcCharacter.HARLEY) != null ? data.position(NpcCharacter.HARLEY) : player.blockPosition();
			double angle = player.getRandom().nextDouble() * Math.PI * 2.0;
			double distance = 120.0 + player.getRandom().nextDouble() * 80.0;
			BlockPos around = camp.offset((int) (Math.cos(angle) * distance), 0, (int) (Math.sin(angle) * distance));
			around = new BlockPos(around.getX(), level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
				around.getX(), around.getZ()), around.getZ());
			BlockPos spot = SafeSpots.find(level, around, 0, 32, player.getRandom());
			NpcEntity adam = NpcSpawns.place(level, NpcCharacter.ADAM, spot != null ? spot : around, player.getRandom().nextFloat() * 360.0F);
			where = adam != null ? adam.blockPosition() : around;
		}
		Component coords = Component.translatable("mission.deadlands.harley.cold_chain.where", where.getX(), where.getY(), where.getZ());
		player.sendSystemMessage(coords.copy().withStyle(ChatFormatting.GOLD));
		player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 140, 30));
		player.connection.send(new ClientboundSetSubtitleTextPacket(coords));
		player.connection.send(new ClientboundSetTitleTextPacket(Component.translatable("mission.deadlands.harley.cold_chain.where_title")));
	}

	private static boolean carries(Inventory inventory, MissionDef def) {
		for (MissionDef.Objective objective : def.objectives()) {
			if (objective.type().equals("have_item")) {
				Item item = objective.target().map(BuiltInRegistries.ITEM::getValue).orElse(null);
				if (item == null || inventory.countItem(item) < Math.max(1, objective.count())) {
					return false;
				}
			}
		}
		return true;
	}

	private static void take(Inventory inventory, MissionDef def) {
		for (MissionDef.Objective objective : def.objectives()) {
			if (objective.type().equals("have_item")) {
				Item item = objective.target().map(BuiltInRegistries.ITEM::getValue).orElse(null);
				for (int i = 0; item != null && i < Math.max(1, objective.count()); i++) {
					takeOne(inventory, item);
				}
			}
		}
	}

	private static void takeOne(Inventory inventory, Item item) {
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (stack.is(item)) {
				stack.shrink(1);
				return;
			}
		}
	}

	private static void say(ServerPlayer player, String who, String key) {
		player.sendSystemMessage(Component.literal(who + ": ").withStyle(ChatFormatting.YELLOW).append(Component.translatable(key).withStyle(ChatFormatting.WHITE)));
	}
}
