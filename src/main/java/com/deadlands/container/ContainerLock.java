package com.deadlands.container;

import com.deadlands.item.KeycardItem;
import com.deadlands.registry.DLDataComponents;
import com.deadlands.registry.DLItems;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Locale;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.ItemStack;

/**
 * A DEADLANDS lock. KEY locks open with a key whose key id matches; KEYCARD locks open with a
 * keycard of at least the required level. Stored on the block entity as {@code deadlands_lock},
 * so structures can lock containers in their NBT.
 */
public record ContainerLock(Type type, String keyId, int level) {
	public static final ContainerLock NONE = new ContainerLock(Type.NONE, "", 0);

	public enum Type implements StringRepresentable {
		NONE, KEY, KEYCARD;

		public static final Codec<Type> CODEC = StringRepresentable.fromEnum(Type::values);

		@Override
		public String getSerializedName() {
			return name().toLowerCase(Locale.ROOT);
		}
	}

	public static final Codec<ContainerLock> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		Type.CODEC.fieldOf("type").forGetter(ContainerLock::type),
		Codec.STRING.optionalFieldOf("key", "").forGetter(ContainerLock::keyId),
		Codec.INT.optionalFieldOf("level", 1).forGetter(ContainerLock::level)
	).apply(instance, ContainerLock::new));

	public static ContainerLock key(String keyId) {
		return new ContainerLock(Type.KEY, keyId, 0);
	}

	public static ContainerLock keycard(int level) {
		return new ContainerLock(Type.KEYCARD, "", level);
	}

	public boolean isLocked() {
		return type != Type.NONE;
	}

	public boolean opensWith(ItemStack stack) {
		return switch (type) {
			case NONE -> true;
			case KEY -> stack.is(DLItems.KEY) && keyId.equals(stack.get(DLDataComponents.KEY_ID));
			case KEYCARD -> stack.getItem() instanceof KeycardItem card && card.accessLevel() >= level;
		};
	}

	/** "the Ranger Station key" / "a level 2 keycard". */
	public Component requirement() {
		return switch (type) {
			case NONE -> Component.empty();
			case KEY -> Component.translatable("lock.deadlands.needs_key", keyLabel(keyId));
			case KEYCARD -> Component.translatable("lock.deadlands.needs_keycard", KeycardItem.levelName(level));
		};
	}

	/** Human name for a key id, e.g. "ranger_station" -> "Ranger Station" (translatable when a lang entry exists). */
	public static Component keyLabel(String keyId) {
		StringBuilder fallback = new StringBuilder();
		for (String word : keyId.split("_")) {
			if (!word.isEmpty()) {
				fallback.append(fallback.isEmpty() ? "" : " ").append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
			}
		}
		return Component.translatableWithFallback("key.deadlands.id." + keyId, fallback.toString());
	}
}
