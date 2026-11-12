package vectorwing.farmersdelight.data;

import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricTagProvider;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.damagesource.DamageType;
import vectorwing.farmersdelight.common.registry.ModDamageTypes;

public class DamageTypeTags extends FabricTagProvider<DamageType> {
    public DamageTypeTags(FabricDataOutput output, CompletableFuture<Provider> lookupProvider) {
        super(output, Registries.DAMAGE_TYPE, lookupProvider);
    }

    @Override
    protected void addTags(Provider provider) {
        this.builder(net.minecraft.tags.DamageTypeTags.IS_FIRE).add(ModDamageTypes.STOVE_BURN);
        this.builder(net.minecraft.tags.DamageTypeTags.NO_KNOCKBACK).add(ModDamageTypes.STOVE_BURN);
        this.builder(net.minecraft.tags.DamageTypeTags.BURN_FROM_STEPPING).add(ModDamageTypes.STOVE_BURN);
        this.builder(net.minecraft.tags.DamageTypeTags.PANIC_ENVIRONMENTAL_CAUSES).add(ModDamageTypes.STOVE_BURN);
    }
}
