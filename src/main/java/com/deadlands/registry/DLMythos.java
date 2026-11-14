package com.deadlands.registry;

import com.deadlands.Deadlands;
import com.deadlands.entity.MythosEntity;
import com.deadlands.entity.SubAgentEntity;
import com.deadlands.item.MeleeItem;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.ToolMaterial;

/**
 * Mythos, the storm-borne boss, and his sub-agents: entity types (fire-proof), default attributes (from the config),
 * spawn eggs, and his reward, the Haxxed Blade (between an iron and a diamond sword). Call after {@link DLItems}.
 */
public final class DLMythos {
	public static final EntityType<MythosEntity> MYTHOS = register("mythos",
		EntityType.Builder.of(MythosEntity::new, MobCategory.MONSTER).sized(1.6F, 4.0F).eyeHeight(3.5F).clientTrackingRange(16)
			.fireImmune().notInPeaceful());
	public static final EntityType<SubAgentEntity> SUB_AGENT = register("sub_agent",
		EntityType.Builder.of(SubAgentEntity::new, MobCategory.MONSTER).sized(0.8F, 0.6F).eyeHeight(0.45F).clientTrackingRange(10)
			.fireImmune().notInPeaceful());
	public static final Item MYTHOS_SPAWN_EGG = DLItems.register("mythos_spawn_egg", SpawnEggItem::new, new Item.Properties().spawnEgg(MYTHOS),
		DLTab.SPAWN_EGGS);
	public static final Item SUB_AGENT_SPAWN_EGG = DLItems.register("sub_agent_spawn_egg", SpawnEggItem::new,
		new Item.Properties().spawnEgg(SUB_AGENT), DLTab.SPAWN_EGGS);
	public static final Item HAXXED_BLADE = DLItems.register("haxxed_blade", MeleeItem::new,
		new Item.Properties().sword(ToolMaterial.IRON, 3.5F, -2.3F).durability(900).fireResistant(), DLTab.WEAPONS);

	static {
		FabricDefaultAttributeRegistry.register(MYTHOS, MythosEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(SUB_AGENT, SubAgentEntity.createAttributes());
	}

	private DLMythos() {
	}

	private static <T extends net.minecraft.world.entity.Entity> EntityType<T> register(String name, EntityType.Builder<T> builder) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, Deadlands.id(name));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
	}

	public static void init() {
		// Class loading registers everything.
	}
}
