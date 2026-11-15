package vectorwing.farmersdelight.refabricated.data;

import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricTagProvider;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import vectorwing.farmersdelight.refabricated.FDRefabricatedTags;

public class RefabricatedMobEffectTags extends FabricTagProvider<MobEffect> {
    public RefabricatedMobEffectTags(FabricDataOutput output, CompletableFuture<Provider> lookupProvider) {
        super(output, Registries.MOB_EFFECT, lookupProvider);
    }

    @Override
    protected void addTags(Provider provider) {
        this.builder(FDRefabricatedTags.MobEffects.MILK_BOTTLE_IGNORED);
        this.builder(FDRefabricatedTags.MobEffects.HOT_COCOA_IGNORED);
    }
}
