package vectorwing.farmersdelight.data;

import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricTagProvider.EntityTypeTagProvider;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.world.entity.EntityType;
import vectorwing.farmersdelight.common.tag.ModTags;
import vectorwing.farmersdelight.refabricated.FDRefabricatedTags;

public class EntityTags extends EntityTypeTagProvider {
    public EntityTags(FabricDataOutput output, CompletableFuture<Provider> lookupProvider) {
        super(output, lookupProvider);
    }

    @Override
    protected void addTags(Provider provider) {
        this.valueLookupBuilder(ModTags.EntityTypes.DOG_FOOD_USERS).add(EntityType.WOLF);
        this.valueLookupBuilder(ModTags.EntityTypes.HORSE_FEED_USERS)
            .add(EntityType.HORSE, EntityType.SKELETON_HORSE, EntityType.ZOMBIE_HORSE, EntityType.DONKEY, EntityType.MULE, EntityType.LLAMA);
        this.valueLookupBuilder(ModTags.EntityTypes.HORSE_FEED_TEMPTED).add(EntityType.HORSE, EntityType.DONKEY, EntityType.MULE);
        this.valueLookupBuilder(FDRefabricatedTags.EntityTypes.DROPS_LEATHER)
            .add(EntityType.COW, EntityType.MOOSHROOM, EntityType.HORSE, EntityType.DONKEY, EntityType.MULE, EntityType.LLAMA, EntityType.TRADER_LLAMA);
    }
}
