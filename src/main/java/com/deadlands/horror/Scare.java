package com.deadlands.horror;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import net.minecraft.resources.Identifier;
import net.minecraft.util.StringRepresentable;

/**
 * One scare, as data ({@code data/<namespace>/jumpscare/*.json}), chosen by the {@link HorrorDirector}.
 *
 * <p><b>When:</b> {@code kind} (major or minor), {@code time} (night, day or any), {@code place}
 * (outdoors, indoors or any), and optionally {@code dark} (the survivor stands in darkness),
 * {@code near_water} or {@code flashlight} (only while their flashlight shines). {@code weight}
 * makes it more or less likely than the others that fit.
 *
 * <p><b>What:</b> {@code steps}, each at a tick from the start: {@code sound} (an event, heard at
 * the ear, behind, far off, ahead or everywhere), {@code silence} (ambience and music dip for a
 * while: the breath before the payoff), {@code flicker} and {@code beam_off} (the flashlight),
 * {@code jolt} (the camera flinches; scaled by the camera-shake setting) and {@code slam_door}
 * (a nearby open door slams shut, on the server).
 */
public record Scare(Kind kind, Time time, Place place, boolean dark, boolean nearWater, boolean flashlight, int weight, List<Step> steps) {
	public enum Kind implements StringRepresentable {
		MAJOR, MINOR;

		public static final Codec<Kind> CODEC = StringRepresentable.fromEnum(Kind::values);

		@Override
		public String getSerializedName() {
			return name().toLowerCase(java.util.Locale.ROOT);
		}
	}

	public enum Time implements StringRepresentable {
		NIGHT, DAY, ANY;

		public static final Codec<Time> CODEC = StringRepresentable.fromEnum(Time::values);

		@Override
		public String getSerializedName() {
			return name().toLowerCase(java.util.Locale.ROOT);
		}
	}

	public enum Place implements StringRepresentable {
		OUTDOORS, INDOORS, ANY;

		public static final Codec<Place> CODEC = StringRepresentable.fromEnum(Place::values);

		@Override
		public String getSerializedName() {
			return name().toLowerCase(java.util.Locale.ROOT);
		}
	}

	/**
	 * One moment of a scare. {@code amount} is a duration in ticks for silence, flicker and beam_off,
	 * and a strength (0 to 1) for jolt. {@code where} is ear, behind, far, ahead or everywhere;
	 * {@code distance} in blocks.
	 */
	public record Step(int at, String type, Optional<Identifier> sound, float volume, float pitch, String where, float distance, float amount) {
		public static final Codec<Step> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.INT.optionalFieldOf("at", 0).forGetter(Step::at),
			Codec.STRING.fieldOf("type").forGetter(Step::type),
			Identifier.CODEC.optionalFieldOf("sound").forGetter(Step::sound),
			Codec.FLOAT.optionalFieldOf("volume", 1.0F).forGetter(Step::volume),
			Codec.FLOAT.optionalFieldOf("pitch", 1.0F).forGetter(Step::pitch),
			Codec.STRING.optionalFieldOf("where", "everywhere").forGetter(Step::where),
			Codec.FLOAT.optionalFieldOf("distance", 0.0F).forGetter(Step::distance),
			Codec.FLOAT.optionalFieldOf("amount", 0.0F).forGetter(Step::amount)
		).apply(instance, Step::new));
	}

	public static final Codec<Scare> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		Kind.CODEC.fieldOf("kind").forGetter(Scare::kind),
		Time.CODEC.optionalFieldOf("time", Time.ANY).forGetter(Scare::time),
		Place.CODEC.optionalFieldOf("place", Place.ANY).forGetter(Scare::place),
		Codec.BOOL.optionalFieldOf("dark", false).forGetter(Scare::dark),
		Codec.BOOL.optionalFieldOf("near_water", false).forGetter(Scare::nearWater),
		Codec.BOOL.optionalFieldOf("flashlight", false).forGetter(Scare::flashlight),
		Codec.INT.optionalFieldOf("weight", 10).forGetter(Scare::weight),
		Step.CODEC.listOf().fieldOf("steps").forGetter(Scare::steps)
	).apply(instance, Scare::new));
}
