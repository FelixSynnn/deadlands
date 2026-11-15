package vectorwing.farmersdelight.data;

import com.mojang.serialization.Lifecycle;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.datagen.v1.DataGeneratorEntrypoint;
import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator.Pack;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricDynamicRegistryProvider;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricDynamicRegistryProvider.Entries;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistrySetBuilder;
import net.minecraft.core.Holder.Reference;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.ResourceKey;
import org.jspecify.annotations.NonNull;
import vectorwing.farmersdelight.common.registry.ModDamageTypes;
import vectorwing.farmersdelight.common.world.WildCropGeneration;
import vectorwing.farmersdelight.data.advancement.FDAdvancementGenerator;
import vectorwing.farmersdelight.data.loot.FDBlockLoot;
import vectorwing.farmersdelight.data.loot.FDChestLoot;
import vectorwing.farmersdelight.refabricated.data.RefabricatedMobEffectTags;

public class DataGenerators implements DataGeneratorEntrypoint {
    @Override
    public void onInitializeDataGenerator(FabricDataGenerator generator) {
        Pack pack = generator.createPack();
        pack.addProvider(DataGenerators.DynamicRegistryProvider::new);
        BlockTags blockTags = pack.addProvider(BlockTags::new);
        pack.addProvider((output, registriesFuture) -> new ItemTags(output, registriesFuture, blockTags));
        pack.addProvider(EntityTags::new);
        pack.addProvider(DamageTypeTags::new);
        pack.addProvider(EnchantmentTags::new);
        pack.addProvider(Recipes::new);
        pack.addProvider(FDAdvancementGenerator::new);
        pack.addProvider(FDBlockLoot::new);
        pack.addProvider(FDChestLoot::new);
        pack.addProvider(SoundDefinitions::new);
        pack.addProvider(RefabricatedMobEffectTags::new);
    }

    @Override
    public void buildRegistry(RegistrySetBuilder registryBuilder) {
        registryBuilder.add(Registries.CONFIGURED_FEATURE, WildCropGeneration::bootstrapConfiguredFeatures);
        registryBuilder.add(Registries.PLACED_FEATURE, WildCropGeneration::bootstrapPlacedFeatures);
        registryBuilder.add(Registries.DAMAGE_TYPE, ModDamageTypes::bootstrapDamageTypes);
        registryBuilder.add(Registries.ENCHANTMENT, ModEnchantments::bootstrap);
    }

    private static class DynamicRegistryProvider extends FabricDynamicRegistryProvider {
        public DynamicRegistryProvider(FabricDataOutput output, CompletableFuture<Provider> lookup) {
            super(output, lookup);
        }

        @Override
        protected void configure(Provider registries, Entries entries) {
            WildCropGeneration.bootstrapConfiguredFeatures(createContext(registries, entries));
            WildCropGeneration.bootstrapPlacedFeatures(createContext(registries, entries));
            ModDamageTypes.bootstrapDamageTypes(createContext(registries, entries));
            ModEnchantments.bootstrap(createContext(registries, entries));
        }

        private static <T> BootstrapContext<T> createContext(Provider registries, Entries entries) {
            return new BootstrapContext<T>() {
                @Override
                public Reference<T> register(ResourceKey<T> resourceKey, T object, Lifecycle lifecycle) {
                    return (Reference<T>)entries.<T>add(resourceKey, object);
                }

                @Override
                public <S> HolderGetter<S> lookup(ResourceKey<? extends Registry<? extends S>> resourceKey) {
                    return registries.lookupOrThrow(resourceKey);
                }
            };
        }

        @Override
        public @NonNull String getName() {
            return "Dynamic Registries";
        }
    }
}
