package vectorwing.farmersdelight.common.registry;

import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.EntityType.Builder;
import vectorwing.farmersdelight.FarmersDelight;
import vectorwing.farmersdelight.common.entity.RottenTomatoEntity;
import vectorwing.farmersdelight.refabricated.RegUtils;

public class ModEntityTypes {
    public static final Supplier<EntityType<RottenTomatoEntity>> ROTTEN_TOMATO = RegUtils.regEntity(
        "rotten_tomato",
        () -> Builder.<RottenTomatoEntity>of(RottenTomatoEntity::new, MobCategory.MISC)
            .sized(0.25F, 0.25F)
            .clientTrackingRange(4)
            .updateInterval(10)
            .build(ResourceKey.create(Registries.ENTITY_TYPE, FarmersDelight.id("rotten_tomato")))
    );

    public static void touch() {
    }
}
