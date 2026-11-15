package com.deadlands.player;

import com.deadlands.Deadlands;
import com.deadlands.config.ConfigManager;
import com.deadlands.entity.ai.Stalking;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.gamerules.GameRules;

/**
 * Dying doesn't cost you everything ({@code survival.keepInventory}, {@code survival.backCommand}):
 *
 * <ul>
 *   <li><b>Keep inventory:</b> the {@code keepInventory} game rule is switched on when the server starts, so you respawn
 *       with everything you were carrying.</li>
 *   <li><b>/back:</b> where you died is remembered (saved with you, through death and logging out), and {@code /back}
 *       takes you there once: to the nearest safe floor beside the spot, in a puff of smoke. Used, it's forgotten.</li>
 * </ul>
 */
public final class DeathReturn {
	/** Where a survivor last died. */
	public record Spot(Identifier dimension, BlockPos pos) {
		public static final Codec<Spot> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Identifier.CODEC.fieldOf("dimension").forGetter(Spot::dimension),
			BlockPos.CODEC.fieldOf("pos").forGetter(Spot::pos)
		).apply(instance, Spot::new));
	}

	public static final AttachmentType<Spot> LAST_DEATH = AttachmentRegistry.create(Deadlands.id("last_death"),
		builder -> builder.persistent(Spot.CODEC).copyOnDeath());

	private DeathReturn() {
	}

	public static void init() {
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			if (ConfigManager.get().survival.keepInventory) {
				server.overworld().getGameRules().set(GameRules.KEEP_INVENTORY, true, server);
			}
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer player) {
				player.setAttached(LAST_DEATH, new Spot(player.level().dimension().identifier(), player.blockPosition()));
			}
		});
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
			dispatcher.register(Commands.literal("back").executes(ctx -> back(ctx.getSource().getPlayerOrException()) ? 1 : 0)));
	}

	/** Take a survivor back to where they died. False, with a message, when it can't. */
	public static boolean back(ServerPlayer player) {
		if (!ConfigManager.get().survival.backCommand) {
			player.sendSystemMessage(Component.translatable("back.deadlands.off"));
			return false;
		}
		Spot spot = player.getAttached(LAST_DEATH);
		if (spot == null) {
			player.sendSystemMessage(Component.translatable("back.deadlands.none"));
			return false;
		}
		ServerLevel level = player.level().getServer().getLevel(ResourceKey.create(Registries.DIMENSION, spot.dimension()));
		if (level == null) {
			player.sendSystemMessage(Component.translatable("back.deadlands.none"));
			return false;
		}
		level.getChunk(spot.pos()); // load it so there is a floor to find
		BlockPos floor = null;
		for (int r = 0; r <= 3 && floor == null; r++) {
			for (int dx = -r; dx <= r && floor == null; dx++) {
				for (int dz = -r; dz <= r && floor == null; dz++) {
					floor = Stalking.standable(level, spot.pos().offset(dx, 0, dz), 2);
				}
			}
		}
		BlockPos to = floor != null ? floor : spot.pos().atY(Math.max(spot.pos().getY(), level.getMinY() + 1));
		player.teleportTo(level, to.getX() + 0.5, to.getY(), to.getZ() + 0.5, java.util.Set.of(), player.getYRot(), player.getXRot(), true);
		player.removeAttached(LAST_DEATH);
		player.sendSystemMessage(Component.translatable("back.deadlands.done"));
		return true;
	}

}
