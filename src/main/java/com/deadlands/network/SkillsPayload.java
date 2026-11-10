package com.deadlands.network;

import com.deadlands.Deadlands;
import com.deadlands.skill.Skill;
import com.deadlands.skill.SkillData;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to client: the receiving survivor's experience in each skill, in {@link Skill} order, for
 * the journal. Sent when experience is gained and when the journal asks for the world status.
 */
public record SkillsPayload(int[] xp) implements CustomPacketPayload {
	public static final Type<SkillsPayload> TYPE = new Type<>(Deadlands.id("skills"));
	public static final StreamCodec<RegistryFriendlyByteBuf, SkillsPayload> CODEC = StreamCodec.ofMember(SkillsPayload::write, SkillsPayload::read);

	public static SkillsPayload of(SkillData data) {
		Skill[] skills = Skill.values();
		int[] xp = new int[skills.length];
		for (int i = 0; i < skills.length; i++) {
			xp[i] = data.xp(skills[i]);
		}
		return new SkillsPayload(xp);
	}

	public int xp(Skill skill) {
		return skill.ordinal() < xp.length ? xp[skill.ordinal()] : 0;
	}

	public int level(Skill skill) {
		return Skill.levelFor(xp(skill));
	}

	private void write(RegistryFriendlyByteBuf buf) {
		buf.writeVarInt(xp.length);
		for (int value : xp) {
			buf.writeVarInt(value);
		}
	}

	private static SkillsPayload read(RegistryFriendlyByteBuf buf) {
		int count = Math.min(buf.readVarInt(), 32);
		int[] xp = new int[count];
		for (int i = 0; i < count; i++) {
			xp[i] = buf.readVarInt();
		}
		return new SkillsPayload(xp);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
