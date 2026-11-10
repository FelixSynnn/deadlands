package com.deadlands.npc;

import com.deadlands.Deadlands;
import com.mojang.serialization.Codec;
import java.util.EnumMap;
import java.util.Map;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * A survivor's standing with each faction, -100 to 100, saved with the player and kept through
 * death. It rises by trading, by killing infected near a faction's people and by restoring their
 * power; it falls by hurting and killing them. Standing decides prices, what they will talk about
 * and whether their fighters attack on sight.
 */
public final class Reputation {
	/** How a faction treats someone. */
	public enum Standing {
		HOSTILE("hostile", 1.0F), WARY("wary", 1.5F), NEUTRAL("neutral", 1.0F), FRIENDLY("friendly", 0.8F), TRUSTED("trusted", 0.65F);

		private final String id;
		private final float price;

		Standing(String id, float price) {
			this.id = id;
			this.price = price;
		}

		/** Multiplier on what traders ask. */
		public float price() {
			return price;
		}

		public String translationKey() {
			return "npc.deadlands.standing." + id;
		}

		public static Standing of(int reputation) {
			return reputation <= -30 ? HOSTILE : reputation < 0 ? WARY : reputation < 30 ? NEUTRAL : reputation < 70 ? FRIENDLY : TRUSTED;
		}
	}

	/** The saved values. */
	public static final class Data {
		public static final Codec<Data> CODEC = Codec.unboundedMap(Faction.CODEC, Codec.INT).xmap(Data::new, data -> Map.copyOf(data.values));
		private final Map<Faction, Integer> values = new EnumMap<>(Faction.class);

		public Data() {
		}

		private Data(Map<Faction, Integer> saved) {
			values.putAll(saved);
		}

		public int get(Faction faction) {
			return values.getOrDefault(faction, faction.startingReputation());
		}

		public void set(Faction faction, int value) {
			values.put(faction, Mth.clamp(value, -100, 100));
		}
	}

	public static final AttachmentType<Data> DATA = AttachmentRegistry.create(Deadlands.id("reputation"),
		builder -> builder.persistent(Data.CODEC).initializer(Data::new).copyOnDeath());

	public static final int TRADE = 1;
	public static final int DEFENDED = 1;
	public static final int POWER_RESTORED = 15;
	public static final int HURT_RESIDENT = -12;
	public static final int KILLED_RESIDENT = -40;
	/** Seen going through a settlement's containers. */
	public static final int THEFT = -8;
	/** Killing infected near a faction's people while a horde is on. */
	public static final int DEFENDED_HORDE = 3;
	/** Killing someone's partner, sibling or friend: on top of the killing itself. */
	public static final int GRIEF = -10;

	private Reputation() {
	}

	public static void init() {
		// Class loading registers the attachment.
	}

	public static int get(Player player, Faction faction) {
		return player.getAttachedOrCreate(DATA).get(faction);
	}

	public static Standing standing(Player player, Faction faction) {
		return Standing.of(get(player, faction));
	}

	/** Change a survivor's reputation and tell them when their standing changes. */
	public static void change(ServerPlayer player, Faction faction, int amount) {
		if (amount == 0 || player.isCreative() || player.isSpectator()) {
			return;
		}
		Data data = player.getAttachedOrCreate(DATA);
		Standing before = Standing.of(data.get(faction));
		data.set(faction, data.get(faction) + amount);
		Standing after = Standing.of(data.get(faction));
		if (after != before) {
			player.displayClientMessage(Component.translatable("message.deadlands.reputation.changed", Component.translatable(faction.translationKey()),
				Component.translatable(after.translationKey())), false);
		}
	}
}
