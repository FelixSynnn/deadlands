package com.deadlands.campaign;

import com.deadlands.Deadlands;
import com.deadlands.registry.DLBlocks;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import java.util.Set;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * The Campaign mode (Phase 2 skeleton, decision 1 of Phase 0): the guided campaigns are played in
 * their own dimension, {@code deadlands:campaign} (an empty world; chapter areas are built into it
 * as structure templates in Phase 5), while the open world stays as Survival.
 *
 * <p>Which campaign and chapter the world is in, and the chapter's checkpoint, are saved in
 * {@code <world>/data/deadlands_campaign.dat}. Entering stores where each survivor came from so they
 * can return. {@code /deadlands campaign enter|leave|status} drives it until the main menu's
 * campaign selection exists (Phase 5).
 */
public final class Campaign {
	public static final ResourceKey<Level> LEVEL = ResourceKey.create(Registries.DIMENSION, Deadlands.id("campaign"));
	private static final BlockPos DEFAULT_START = new BlockPos(0, 64, 0);

	/** Where a survivor stood before entering the campaign. */
	public record Return(Identifier dimension, double x, double y, double z) {
		public static final Codec<Return> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Identifier.CODEC.fieldOf("dimension").forGetter(Return::dimension),
			Codec.DOUBLE.fieldOf("x").forGetter(Return::x),
			Codec.DOUBLE.fieldOf("y").forGetter(Return::y),
			Codec.DOUBLE.fieldOf("z").forGetter(Return::z)
		).apply(instance, Return::new));
	}

	public static final AttachmentType<Return> RETURN = AttachmentRegistry.create(Deadlands.id("campaign_return"),
		builder -> builder.persistent(Return.CODEC).copyOnDeath());

	/** The world's campaign progress. */
	public static final class State extends SavedData {
		public static final Codec<State> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.STRING.optionalFieldOf("campaign", "itzbreadloaf").forGetter(s -> s.campaign),
			Codec.STRING.optionalFieldOf("chapter", "prologue").forGetter(s -> s.chapter),
			BlockPos.CODEC.optionalFieldOf("checkpoint").forGetter(s -> Optional.ofNullable(s.checkpoint))
		).apply(instance, State::new));
		public static final SavedDataType<State> TYPE = new SavedDataType<>("deadlands_campaign", State::new, CODEC, null);

		String campaign = "itzbreadloaf";
		String chapter = "prologue";
		BlockPos checkpoint;

		public State() {
		}

		private State(String campaign, String chapter, Optional<BlockPos> checkpoint) {
			this.campaign = campaign;
			this.chapter = chapter;
			this.checkpoint = checkpoint.orElse(null);
		}

		public String campaign() {
			return campaign;
		}

		public String chapter() {
			return chapter;
		}

		public BlockPos checkpoint() {
			return checkpoint == null ? DEFAULT_START : checkpoint;
		}

		/** Move to a chapter; its checkpoint is where survivors (re)enter. */
		public void startChapter(String campaign, String chapter, BlockPos checkpoint) {
			this.campaign = campaign;
			this.chapter = chapter;
			this.checkpoint = checkpoint.immutable();
			setDirty();
		}
	}

	private Campaign() {
	}

	public static State state(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(State.TYPE);
	}

	/** Take a survivor to the campaign's checkpoint; false if the dimension is missing. */
	public static boolean enter(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		ServerLevel campaign = server.getLevel(LEVEL);
		if (campaign == null) {
			return false;
		}
		if (player.level() != campaign) {
			player.setAttached(RETURN, new Return(player.level().dimension().identifier(), player.getX(), player.getY(), player.getZ()));
		}
		BlockPos start = state(server).checkpoint();
		if (campaign.getBlockState(start.below()).isAir()) {
			// Nothing built here yet (chapters arrive in Phase 5): a small slab of concrete to stand on.
			for (BlockPos pos : BlockPos.betweenClosed(start.offset(-2, -1, -2), start.offset(2, -1, 2))) {
				campaign.setBlockAndUpdate(pos, DLBlocks.CONCRETE.defaultBlockState());
			}
		}
		return player.teleportTo(campaign, start.getX() + 0.5, start.getY(), start.getZ() + 0.5, Set.of(), player.getYRot(), player.getXRot(), true);
	}

	/** Back to where the survivor entered from (the overworld spawn if unknown). */
	public static boolean leave(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		Return back = player.getAttached(RETURN);
		ServerLevel level = back == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, back.dimension()));
		if (level == null) {
			level = server.overworld();
			BlockPos spawn = level.getRespawnData().pos();
			return player.teleportTo(level, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, Set.of(), player.getYRot(), player.getXRot(), true);
		}
		return player.teleportTo(level, back.x(), back.y(), back.z(), Set.of(), player.getYRot(), player.getXRot(), true);
	}
}
