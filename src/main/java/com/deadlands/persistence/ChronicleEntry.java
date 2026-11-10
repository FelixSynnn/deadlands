package com.deadlands.persistence;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;

/**
 * One line of the world chronicle ("Day 4: Alex died."). Stored as a translation key plus string
 * arguments so every client reads it in its own language.
 *
 * @param day 1-based day number
 */
public record ChronicleEntry(long day, String key, List<String> args) {
	public static final int MAX_KEY = 96;
	public static final int MAX_ARG = 64;
	public static final int MAX_ARGS = 4;

	public static final Codec<ChronicleEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		Codec.LONG.fieldOf("day").forGetter(ChronicleEntry::day),
		Codec.STRING.fieldOf("key").forGetter(ChronicleEntry::key),
		Codec.STRING.listOf().optionalFieldOf("args", List.of()).forGetter(ChronicleEntry::args)
	).apply(instance, ChronicleEntry::new));

	public ChronicleEntry {
		args = List.copyOf(args.size() > MAX_ARGS ? args.subList(0, MAX_ARGS) : args);
	}

	public Component describe() {
		return Component.translatable(key, args.toArray());
	}

	public void write(FriendlyByteBuf buf) {
		buf.writeVarLong(day);
		buf.writeUtf(key, MAX_KEY);
		buf.writeVarInt(args.size());
		for (String arg : args) {
			buf.writeUtf(arg.length() > MAX_ARG ? arg.substring(0, MAX_ARG) : arg, MAX_ARG);
		}
	}

	public static ChronicleEntry read(FriendlyByteBuf buf) {
		long day = buf.readVarLong();
		String key = buf.readUtf(MAX_KEY);
		int count = Math.min(buf.readVarInt(), MAX_ARGS);
		List<String> args = new ArrayList<>(count);
		for (int i = 0; i < count; i++) {
			args.add(buf.readUtf(MAX_ARG));
		}
		return new ChronicleEntry(day, key, args);
	}
}
