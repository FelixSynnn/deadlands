package com.deadlands.registry;

import com.deadlands.Deadlands;
import com.deadlands.entity.BloaterEntity;
import com.deadlands.entity.BruteEntity;
import com.deadlands.entity.CharredEntity;
import com.deadlands.entity.CrawlerEntity;
import com.deadlands.entity.InfectedEntity;
import com.deadlands.entity.InfectedKind;
import com.deadlands.entity.RunnerEntity;
import com.deadlands.entity.ScreamerEntity;
import com.deadlands.entity.StalkerEntity;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;

/**
 * The infected entity types (one per {@link InfectedKind}), their default attributes and their
 * spawn eggs (Spawn Eggs creative tab). Call after {@link DLItems}.
 */
public final class DLEntities {
	private static final Map<InfectedKind, EntityType<? extends InfectedEntity>> TYPES = new EnumMap<>(InfectedKind.class);
	private static final List<Item> SPAWN_EGGS = new ArrayList<>();

	public static final EntityType<InfectedEntity> WALKER = register(InfectedKind.WALKER, InfectedEntity::new);
	public static final EntityType<RunnerEntity> RUNNER = register(InfectedKind.RUNNER, RunnerEntity::new);
	public static final EntityType<BruteEntity> BRUTE = register(InfectedKind.BRUTE, BruteEntity::new);
	public static final EntityType<ScreamerEntity> SCREAMER = register(InfectedKind.SCREAMER, ScreamerEntity::new);
	public static final EntityType<InfectedEntity> MILITARY = register(InfectedKind.MILITARY, InfectedEntity::new);
	public static final EntityType<CrawlerEntity> CRAWLER = register(InfectedKind.CRAWLER, CrawlerEntity::new);
	public static final EntityType<BloaterEntity> BLOATER = register(InfectedKind.BLOATER, BloaterEntity::new);
	public static final EntityType<StalkerEntity> STALKER = register(InfectedKind.STALKER, StalkerEntity::new);
	public static final EntityType<CharredEntity> CHARRED = register(InfectedKind.CHARRED, CharredEntity::new);

	private DLEntities() {
	}

	private static <T extends InfectedEntity> EntityType<T> register(InfectedKind kind, EntityType.EntityFactory<T> factory) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, Deadlands.id(kind.id()));
		EntityType.Builder<T> builder = EntityType.Builder.of(factory, MobCategory.MONSTER)
			.sized(kind.width(), kind.height())
			.eyeHeight(kind.eyeHeight())
			.clientTrackingRange(8)
			.notInPeaceful();
		if (kind.fireImmune()) {
			builder = builder.fireImmune();
		}
		EntityType<T> type = Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
		FabricDefaultAttributeRegistry.register(type, InfectedEntity.createAttributes(kind));
		InfectedKind.bind(kind, type);
		TYPES.put(kind, type);
		SPAWN_EGGS.add(DLItems.register(kind.id() + "_spawn_egg", SpawnEggItem::new, new Item.Properties().spawnEgg(type), DLTab.SPAWN_EGGS));
		return type;
	}

	public static EntityType<? extends InfectedEntity> type(InfectedKind kind) {
		return TYPES.get(kind);
	}

	public static List<Item> spawnEggs() {
		return Collections.unmodifiableList(SPAWN_EGGS);
	}

	public static void init() {
		// Class loading registers everything above.
	}
}
