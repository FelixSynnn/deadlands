package com.deadlands.registry;

import com.deadlands.Deadlands;
import com.deadlands.entity.BossEntity;
import com.deadlands.progression.BossKind;
import java.util.EnumMap;
import java.util.IdentityHashMap;
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
import org.jspecify.annotations.Nullable;

/** The five boss entity types (Milestone 8), one per {@link BossKind}, with spawn eggs. Call after {@link DLItems}. */
public final class DLBosses {
	private static final Map<BossKind, EntityType<BossEntity>> TYPES = new EnumMap<>(BossKind.class);
	private static final Map<EntityType<?>, BossKind> KINDS = new IdentityHashMap<>();

	public static final EntityType<BossEntity> ABOMINATION = register(BossKind.ABOMINATION, 2.4F, 3.2F);
	public static final EntityType<BossEntity> BUTCHER = register(BossKind.BUTCHER, 1.0F, 2.6F);
	public static final EntityType<BossEntity> WARDEN = register(BossKind.WARDEN, 1.0F, 2.4F);
	public static final EntityType<BossEntity> EXPERIMENT = register(BossKind.EXPERIMENT, 1.2F, 2.8F);
	public static final EntityType<BossEntity> PATIENT_ZERO = register(BossKind.PATIENT_ZERO, 0.8F, 2.2F);

	private DLBosses() {
	}

	private static EntityType<BossEntity> register(BossKind kind, float width, float height) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, Deadlands.id(kind.id()));
		EntityType<BossEntity> type = Registry.register(BuiltInRegistries.ENTITY_TYPE, key,
			EntityType.Builder.<BossEntity>of(BossEntity::new, MobCategory.MONSTER).sized(width, height).eyeHeight(height * 0.88F)
				.clientTrackingRange(10).notInPeaceful().fireImmune().build(key));
		TYPES.put(kind, type);
		KINDS.put(type, kind);
		FabricDefaultAttributeRegistry.register(type, BossEntity.createAttributes(kind));
		DLItems.register(kind.id() + "_spawn_egg", SpawnEggItem::new, new Item.Properties().spawnEgg(type), DLTab.SPAWN_EGGS);
		return type;
	}

	public static EntityType<BossEntity> type(BossKind kind) {
		return TYPES.get(kind);
	}

	public static @Nullable BossKind kindOf(EntityType<?> type) {
		return KINDS.get(type);
	}

	public static void init() {
		// Class loading registers everything above.
	}
}
