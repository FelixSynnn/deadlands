package vectorwing.farmersdelight;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.recipe.v1.sync.RecipeSynchronization;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.gamerules.GameRules;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vectorwing.farmersdelight.common.CommonSetup;
import vectorwing.farmersdelight.common.Configuration;
import vectorwing.farmersdelight.common.block.CuttingBoardBlock;
import vectorwing.farmersdelight.common.block.RichSoilBlock;
import vectorwing.farmersdelight.common.block.entity.CookingPotBlockEntity;
import vectorwing.farmersdelight.common.block.entity.CuttingBoardBlockEntity;
import vectorwing.farmersdelight.common.crafting.condition.VanillaCrateEnabledCondition;
import vectorwing.farmersdelight.common.crafting.ingredient.ItemAbilityIngredient;
import vectorwing.farmersdelight.common.event.CommonModBusEvents;
import vectorwing.farmersdelight.common.event.VillagerEvents;
import vectorwing.farmersdelight.common.item.DogFoodItem;
import vectorwing.farmersdelight.common.item.HorseFeedItem;
import vectorwing.farmersdelight.common.item.KnifeItem;
import vectorwing.farmersdelight.common.network.ModNetworking;
import vectorwing.farmersdelight.common.network.payload.SendNaturalRegenerationValuePayload;
import vectorwing.farmersdelight.common.registry.ModAdvancements;
import vectorwing.farmersdelight.common.registry.ModBiomeFeatures;
import vectorwing.farmersdelight.common.registry.ModBiomeModifiers;
import vectorwing.farmersdelight.common.registry.ModBlockEntityTypes;
import vectorwing.farmersdelight.common.registry.ModBlocks;
import vectorwing.farmersdelight.common.registry.ModConsumeEffectTypes;
import vectorwing.farmersdelight.common.registry.ModCreativeTabs;
import vectorwing.farmersdelight.common.registry.ModDataComponents;
import vectorwing.farmersdelight.common.registry.ModEffects;
import vectorwing.farmersdelight.common.registry.ModEntityTypes;
import vectorwing.farmersdelight.common.registry.ModItems;
import vectorwing.farmersdelight.common.registry.ModLootFunctions;
import vectorwing.farmersdelight.common.registry.ModMenuTypes;
import vectorwing.farmersdelight.common.registry.ModParticleTypes;
import vectorwing.farmersdelight.common.registry.ModPlacementModifiers;
import vectorwing.farmersdelight.common.registry.ModRecipeBookCategories;
import vectorwing.farmersdelight.common.registry.ModRecipeDisplays;
import vectorwing.farmersdelight.common.registry.ModRecipeSerializers;
import vectorwing.farmersdelight.common.registry.ModRecipeTypes;
import vectorwing.farmersdelight.common.registry.ModSounds;
import vectorwing.farmersdelight.common.registry.RegistryAliases;
import vectorwing.farmersdelight.common.world.VillageStructures;
import vectorwing.farmersdelight.refabricated.CanItemPerformAbility;
import vectorwing.farmersdelight.refabricated.CompostableHelper;
import vectorwing.farmersdelight.refabricated.LootModificationEvents;

public class FarmersDelight implements ModInitializer {
    public static final String MODID = "farmersdelight";
    public static final Logger LOGGER = LoggerFactory.getLogger(FarmersDelight.class);

    public static Identifier id(String name) {
        return Identifier.fromNamespaceAndPath("farmersdelight", name);
    }

    public void onInitialize() {
        Configuration.touch();
        ModSounds.touch();
        ModBlocks.touch();
        ModEffects.touch();
        ModParticleTypes.touch();
        ModItems.touch();
        ModDataComponents.touch();
        ModEntityTypes.touch();
        ModBlockEntityTypes.touch();
        ModMenuTypes.touch();
        ModRecipeTypes.touch();
        ModRecipeSerializers.touch();
        ModBiomeFeatures.touch();
        ModAdvancements.touch();
        ModPlacementModifiers.touch();
        ModLootFunctions.touch();
        ModCreativeTabs.touch();
        VillageStructures.init();
        CommonModBusEvents.init();
        VillagerEvents.init();
        CommonSetup.init();
        RegistryAliases.addRegistryAliases();
        VanillaCrateEnabledCondition.init();
        LootModificationEvents.init();
        ModBiomeModifiers.init();
        CookingPotBlockEntity.init();
        CuttingBoardBlock.init();
        CuttingBoardBlockEntity.init();
        DogFoodItem.init();
        HorseFeedItem.init();
        KnifeItem.init();
        ModNetworking.init();
        RichSoilBlock.init();
        CanItemPerformAbility.init();
        ItemAbilityIngredient.touch();
        ModConsumeEffectTypes.touch();
        ModRecipeDisplays.touch();
        ModRecipeBookCategories.touch();
        CompostableHelper.apply();
        ServerPlayerEvents.JOIN
            .register(
                serverPlayer -> ServerPlayNetworking.send(
                    serverPlayer, new SendNaturalRegenerationValuePayload(serverPlayer.level().getGameRules().get(GameRules.NATURAL_HEALTH_REGENERATION))
                )
            );
        RecipeSynchronization.synchronizeRecipeSerializer(ModRecipeSerializers.COOKING.get());
        RecipeSynchronization.synchronizeRecipeSerializer(ModRecipeSerializers.CUTTING.get());
        RecipeSynchronization.synchronizeRecipeSerializer(ModRecipeSerializers.DOUGH.get());
    }
}
