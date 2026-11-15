package vectorwing.farmersdelight.data.advancement;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricAdvancementProvider;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementType;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.advancements.Criterion;
import net.minecraft.advancements.Advancement.Builder;
import net.minecraft.advancements.AdvancementRequirements.Strategy;
import net.minecraft.advancements.criterion.ContextAwarePredicate;
import net.minecraft.advancements.criterion.TagPredicate;
import net.minecraft.advancements.criterion.InventoryChangeTrigger.TriggerInstance;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.loot.predicates.LocationCheck;
import vectorwing.farmersdelight.common.advancement.CuttingBoardTrigger;
import vectorwing.farmersdelight.common.block.TomatoBlock;
import vectorwing.farmersdelight.common.registry.ModBlocks;
import vectorwing.farmersdelight.common.registry.ModEffects;
import vectorwing.farmersdelight.common.registry.ModEntityTypes;
import vectorwing.farmersdelight.common.registry.ModItems;
import vectorwing.farmersdelight.common.utility.TextUtils;

public class FDAdvancementGenerator extends FabricAdvancementProvider {
    public FDAdvancementGenerator(FabricDataOutput output, CompletableFuture<Provider> registryLookup) {
        super(output, registryLookup);
    }

    @Override
    public void generateAdvancement(Provider provider, Consumer<AdvancementHolder> consumer) {
        HolderGetter<Item> holderGetter = provider.lookupOrThrow(Registries.ITEM);
        AdvancementHolder farmersDelight = Builder.advancement()
            .display(
                ModItems.COOKING_POT.get(),
                TextUtils.advancement("root.title"),
                TextUtils.advancement("root.description"),
                Identifier.parse("minecraft:block/bricks"),
                AdvancementType.TASK,
                false,
                false,
                false
            )
            .addCriterion("seeds", TriggerInstance.hasItems(new net.minecraft.world.level.ItemLike[0]))
            .save(consumer, this.getNameId("main/root"));
        AdvancementHolder huntAndGather = getAdvancement(farmersDelight, ModItems.FLINT_KNIFE.get(), "craft_knife", AdvancementType.TASK, true, true, false)
            .addCriterion("flint_knife", TriggerInstance.hasItems(ModItems.FLINT_KNIFE.get()))
            .addCriterion("copper_knife", TriggerInstance.hasItems(ModItems.COPPER_KNIFE.get()))
            .addCriterion("iron_knife", TriggerInstance.hasItems(ModItems.IRON_KNIFE.get()))
            .addCriterion("diamond_knife", TriggerInstance.hasItems(ModItems.DIAMOND_KNIFE.get()))
            .addCriterion("golden_knife", TriggerInstance.hasItems(ModItems.GOLDEN_KNIFE.get()))
            .addCriterion("netherite_knife", TriggerInstance.hasItems(ModItems.NETHERITE_KNIFE.get()))
            .requirements(Strategy.OR)
            .save(consumer, this.getNameId("main/craft_knife"));
        AdvancementHolder graspingAtStraws = getAdvancement(huntAndGather, ModItems.STRAW.get(), "harvest_straw", AdvancementType.TASK, true, false, false)
            .addCriterion("harvest_straw", TriggerInstance.hasItems(ModItems.STRAW.get()))
            .save(consumer, this.getNameId("main/harvest_straw"));
        AdvancementHolder advancedComposting = getAdvancement(
                graspingAtStraws, ModItems.ORGANIC_COMPOST.get(), "place_organic_compost", AdvancementType.TASK, true, false, false
            )
            .addCriterion(
                "place_organic_compost",
                net.minecraft.advancements.criterion.ItemUsedOnLocationTrigger.TriggerInstance.placedBlock(ModBlocks.ORGANIC_COMPOST.get())
            )
            .save(consumer, this.getNameId("main/place_organic_compost"));
        AdvancementHolder plantFood = getAdvancement(advancedComposting, ModItems.RICH_SOIL.get(), "get_rich_soil", AdvancementType.GOAL, true, true, false)
            .addCriterion("get_rich_soil", TriggerInstance.hasItems(ModItems.RICH_SOIL.get()))
            .save(consumer, this.getNameId("main/get_rich_soil"));
        AdvancementHolder wildButcher = getAdvancement(huntAndGather, ModItems.HAM.get(), "get_ham", AdvancementType.TASK, true, false, false)
            .addCriterion("ham", TriggerInstance.hasItems(ModItems.HAM.get()))
            .addCriterion("smoked_ham", TriggerInstance.hasItems(ModItems.SMOKED_HAM.get()))
            .requirements(Strategy.OR)
            .save(consumer, this.getNameId("main/get_ham"));
        AdvancementHolder watchYourFingers = getAdvancement(
                huntAndGather, ModItems.CUTTING_BOARD.get(), "use_cutting_board", AdvancementType.TASK, true, false, false
            )
            .addCriterion("cutting_board", CuttingBoardTrigger.TriggerInstance.simple())
            .save(consumer, this.getNameId("main/use_cutting_board"));
        AdvancementHolder cantTakeTheHeat = getAdvancement(
                watchYourFingers, ModItems.NETHERITE_KNIFE.get(), "obtain_netherite_knife", AdvancementType.CHALLENGE, true, true, false
            )
            .addCriterion("obtain_netherite_knife", TriggerInstance.hasItems(ModItems.NETHERITE_KNIFE.get()))
            .rewards(net.minecraft.advancements.AdvancementRewards.Builder.experience(200))
            .save(consumer, this.getNameId("main/obtain_netherite_knife"));
        AdvancementHolder cropsOfTheWild = getAdvancement(farmersDelight, ModItems.WILD_ONIONS.get(), "get_fd_seed", AdvancementType.TASK, true, true, false)
            .addCriterion("cabbage_seeds", TriggerInstance.hasItems(ModItems.CABBAGE_SEEDS.get()))
            .addCriterion("tomato_seeds", TriggerInstance.hasItems(ModItems.TOMATO_SEEDS.get()))
            .addCriterion("onion", TriggerInstance.hasItems(ModItems.ONION.get()))
            .addCriterion("rice", TriggerInstance.hasItems(ModItems.RICE.get()))
            .requirements(Strategy.OR)
            .save(consumer, this.getNameId("main/get_fd_seed"));
        AdvancementHolder fungusAmongUs = getAdvancement(
                cropsOfTheWild, ModItems.RED_MUSHROOM_COLONY.get(), "get_mushroom_colony", AdvancementType.TASK, true, false, false
            )
            .addCriterion("brown_mushroom_colony", TriggerInstance.hasItems(ModItems.BROWN_MUSHROOM_COLONY.get()))
            .addCriterion("red_mushroom_colony", TriggerInstance.hasItems(ModItems.RED_MUSHROOM_COLONY.get()))
            .requirements(Strategy.OR)
            .save(consumer, this.getNameId("main/get_mushroom_colony"));
        AdvancementHolder dippingYourRoots = getAdvancement(cropsOfTheWild, ModItems.RICE.get(), "plant_rice", AdvancementType.TASK, true, false, false)
            .addCriterion("plant_rice", net.minecraft.advancements.criterion.ItemUsedOnLocationTrigger.TriggerInstance.placedBlock(ModBlocks.RICE_CROP.get()))
            .save(consumer, this.getNameId("main/plant_rice"));
        AdvancementHolder tallmato = getAdvancement(
                cropsOfTheWild, ModItems.TOMATO.get(), "harvest_ropelogged_tomato", AdvancementType.TASK, true, false, false
            )
            .addCriterion(
                "harvest_ropelogged_tomato",
                CriteriaTriggers.DEFAULT_BLOCK_USE
                    .createCriterion(
                        new net.minecraft.advancements.criterion.DefaultBlockInteractionTrigger.TriggerInstance(
                            Optional.empty(),
                            Optional.of(
                                ContextAwarePredicate.create(
                                    LocationCheck.checkLocation(
                                            net.minecraft.advancements.criterion.LocationPredicate.Builder.location()
                                                .setBlock(
                                                    net.minecraft.advancements.criterion.BlockPredicate.Builder.block()
                                                        .of(provider.lookupOrThrow(Registries.BLOCK), ModBlocks.TOMATO_CROP_ON_ROPE.get())
                                                        .setProperties(
                                                            net.minecraft.advancements.criterion.StatePropertiesPredicate.Builder.properties()
                                                                .hasProperty(TomatoBlock.VINE_AGE, 0)
                                                        )
                                                )
                                        )
                                        .build()
                                )
                            )
                        )
                    )
            )
            .save(consumer, this.getNameId("main/harvest_ropelogged_tomato"));
        AdvancementHolder booHiss = getAdvancement(
                tallmato, ModItems.ROTTEN_TOMATO.get(), "hit_raider_with_rotten_tomato", AdvancementType.TASK, true, true, false
            )
            .addCriterion(
                "hit_raider_with_rotten_tomato",
                net.minecraft.advancements.criterion.PlayerHurtEntityTrigger.TriggerInstance.playerHurtEntity(
                    Optional.of(
                        net.minecraft.advancements.criterion.DamagePredicate.Builder.damageInstance()
                            .type(
                                net.minecraft.advancements.criterion.DamageSourcePredicate.Builder.damageType()
                                    .tag(TagPredicate.is(DamageTypeTags.IS_PROJECTILE))
                                    .direct(
                                        net.minecraft.advancements.criterion.EntityPredicate.Builder.entity()
                                            .of(provider.lookupOrThrow(Registries.ENTITY_TYPE), ModEntityTypes.ROTTEN_TOMATO.get())
                                    )
                            )
                            .build()
                    ),
                    Optional.of(
                        net.minecraft.advancements.criterion.EntityPredicate.Builder.entity()
                            .of(provider.lookupOrThrow(Registries.ENTITY_TYPE), EntityTypeTags.RAIDERS)
                            .build()
                    )
                )
            )
            .save(consumer, this.getNameId("main/hit_raider_with_rotten_tomato"));
        AdvancementHolder cropRotation = getAdvancement(
                dippingYourRoots, ModItems.CABBAGE.get(), "plant_all_crops", AdvancementType.CHALLENGE, true, true, false
            )
            .addCriterion("wheat", net.minecraft.advancements.criterion.ItemUsedOnLocationTrigger.TriggerInstance.placedBlock(Blocks.WHEAT))
            .addCriterion("beetroot", net.minecraft.advancements.criterion.ItemUsedOnLocationTrigger.TriggerInstance.placedBlock(Blocks.BEETROOTS))
            .addCriterion("carrot", net.minecraft.advancements.criterion.ItemUsedOnLocationTrigger.TriggerInstance.placedBlock(Blocks.CARROTS))
            .addCriterion("potato", net.minecraft.advancements.criterion.ItemUsedOnLocationTrigger.TriggerInstance.placedBlock(Blocks.POTATOES))
            .addCriterion("cabbage", net.minecraft.advancements.criterion.ItemUsedOnLocationTrigger.TriggerInstance.placedBlock(ModBlocks.CABBAGE_CROP.get()))
            .addCriterion(
                "tomato", net.minecraft.advancements.criterion.ItemUsedOnLocationTrigger.TriggerInstance.placedBlock(ModBlocks.BUDDING_TOMATO_CROP.get())
            )
            .addCriterion("onion", net.minecraft.advancements.criterion.ItemUsedOnLocationTrigger.TriggerInstance.placedBlock(ModBlocks.ONION_CROP.get()))
            .addCriterion("rice", net.minecraft.advancements.criterion.ItemUsedOnLocationTrigger.TriggerInstance.placedBlock(ModBlocks.RICE_CROP.get()))
            .addCriterion("melon", net.minecraft.advancements.criterion.ItemUsedOnLocationTrigger.TriggerInstance.placedBlock(Blocks.MELON_STEM))
            .addCriterion("pumpkin", net.minecraft.advancements.criterion.ItemUsedOnLocationTrigger.TriggerInstance.placedBlock(Blocks.PUMPKIN_STEM))
            .addCriterion("sweet_berries", net.minecraft.advancements.criterion.ItemUsedOnLocationTrigger.TriggerInstance.placedBlock(Blocks.SWEET_BERRY_BUSH))
            .addCriterion("sugar_cane", net.minecraft.advancements.criterion.ItemUsedOnLocationTrigger.TriggerInstance.placedBlock(Blocks.SUGAR_CANE))
            .addCriterion("kelp", net.minecraft.advancements.criterion.ItemUsedOnLocationTrigger.TriggerInstance.placedBlock(Blocks.KELP))
            .addCriterion("cocoa", net.minecraft.advancements.criterion.ItemUsedOnLocationTrigger.TriggerInstance.placedBlock(Blocks.COCOA))
            .addCriterion("nether_wart", net.minecraft.advancements.criterion.ItemUsedOnLocationTrigger.TriggerInstance.placedBlock(Blocks.NETHER_WART))
            .addCriterion("chorus_flower", net.minecraft.advancements.criterion.ItemUsedOnLocationTrigger.TriggerInstance.placedBlock(Blocks.CHORUS_FLOWER))
            .addCriterion("brown_mushroom", net.minecraft.advancements.criterion.ItemUsedOnLocationTrigger.TriggerInstance.placedBlock(Blocks.BROWN_MUSHROOM))
            .addCriterion("red_mushroom", net.minecraft.advancements.criterion.ItemUsedOnLocationTrigger.TriggerInstance.placedBlock(Blocks.RED_MUSHROOM))
            .addCriterion("glow_berries", net.minecraft.advancements.criterion.ItemUsedOnLocationTrigger.TriggerInstance.placedBlock(Blocks.CAVE_VINES))
            .rewards(net.minecraft.advancements.AdvancementRewards.Builder.experience(100))
            .save(consumer, this.getNameId("main/plant_all_crops"));
        AdvancementHolder bonfireLit = getAdvancement(farmersDelight, Blocks.CAMPFIRE, "place_campfire", AdvancementType.TASK, true, true, false)
            .addCriterion("campfire", net.minecraft.advancements.criterion.ItemUsedOnLocationTrigger.TriggerInstance.placedBlock(Blocks.CAMPFIRE))
            .addCriterion("soul_campfire", net.minecraft.advancements.criterion.ItemUsedOnLocationTrigger.TriggerInstance.placedBlock(Blocks.SOUL_CAMPFIRE))
            .requirements(Strategy.OR)
            .save(consumer, this.getNameId("main/place_campfire"));
        AdvancementHolder portableCooking = getAdvancement(bonfireLit, ModItems.SKILLET.get(), "use_skillet", AdvancementType.TASK, true, false, false)
            .addCriterion("skillet", usedItem(provider.lookupOrThrow(Registries.ITEM), ModItems.SKILLET.get()))
            .save(consumer, this.getNameId("main/use_skillet"));
        AdvancementHolder sizzlingHot = getAdvancement(portableCooking, ModItems.SKILLET.get(), "place_skillet", AdvancementType.TASK, true, false, false)
            .addCriterion("skillet", net.minecraft.advancements.criterion.ItemUsedOnLocationTrigger.TriggerInstance.placedBlock(ModBlocks.SKILLET.get()))
            .save(consumer, this.getNameId("main/place_skillet"));
        AdvancementHolder dinnerIsServed = getAdvancement(bonfireLit, ModItems.COOKING_POT.get(), "place_cooking_pot", AdvancementType.GOAL, true, true, false)
            .addCriterion(
                "cooking_pot", net.minecraft.advancements.criterion.ItemUsedOnLocationTrigger.TriggerInstance.placedBlock(ModBlocks.COOKING_POT.get())
            )
            .save(consumer, this.getNameId("main/place_cooking_pot"));
        AdvancementHolder nourishing = getAdvancement(
                dinnerIsServed, ModItems.STEAK_AND_POTATOES.get(), "eat_nourishing_food", AdvancementType.TASK, true, false, false
            )
            .addCriterion(
                "nourishment",
                net.minecraft.advancements.criterion.EffectsChangedTrigger.TriggerInstance.hasEffects(
                    net.minecraft.advancements.criterion.MobEffectsPredicate.Builder.effects().and(ModEffects.NOURISHMENT)
                )
            )
            .save(consumer, this.getNameId("main/eat_nourishing_food"));
        AdvancementHolder gloriousFeast = getAdvancement(nourishing, ModItems.ROAST_CHICKEN_BLOCK.get(), "place_feast", AdvancementType.TASK, true, true, false)
            .addCriterion(
                "roast_chicken",
                net.minecraft.advancements.criterion.ItemUsedOnLocationTrigger.TriggerInstance.placedBlock(ModBlocks.ROAST_CHICKEN_BLOCK.get())
            )
            .addCriterion(
                "stuffed_pumpkin",
                net.minecraft.advancements.criterion.ItemUsedOnLocationTrigger.TriggerInstance.placedBlock(ModBlocks.STUFFED_PUMPKIN_BLOCK.get())
            )
            .addCriterion(
                "honey_glazed_ham",
                net.minecraft.advancements.criterion.ItemUsedOnLocationTrigger.TriggerInstance.placedBlock(ModBlocks.HONEY_GLAZED_HAM_BLOCK.get())
            )
            .addCriterion(
                "shepherds_pie",
                net.minecraft.advancements.criterion.ItemUsedOnLocationTrigger.TriggerInstance.placedBlock(ModBlocks.SHEPHERDS_PIE_BLOCK.get())
            )
            .addCriterion(
                "gleaming_salad",
                net.minecraft.advancements.criterion.ItemUsedOnLocationTrigger.TriggerInstance.placedBlock(ModBlocks.GLEAMING_SALAD_BLOCK.get())
            )
            .addCriterion(
                "rice_roll_medley",
                net.minecraft.advancements.criterion.ItemUsedOnLocationTrigger.TriggerInstance.placedBlock(ModBlocks.RICE_ROLL_MEDLEY_BLOCK.get())
            )
            .requirements(Strategy.OR)
            .save(consumer, this.getNameId("main/place_feast"));
        AdvancementHolder masterChef = getAdvancement(
                gloriousFeast, ModItems.HONEY_GLAZED_HAM.get(), "master_chef", AdvancementType.CHALLENGE, true, true, false
            )
            .addCriterion("mixed_salad", usedItem(holderGetter, ModItems.MIXED_SALAD.get()))
            .addCriterion("cooked_rice", usedItem(holderGetter, ModItems.COOKED_RICE.get()))
            .addCriterion("bone_broth", usedItem(holderGetter, ModItems.BONE_BROTH.get()))
            .addCriterion("beef_stew", usedItem(holderGetter, ModItems.BEEF_STEW.get()))
            .addCriterion("vegetable_soup", usedItem(holderGetter, ModItems.VEGETABLE_SOUP.get()))
            .addCriterion("fish_stew", usedItem(holderGetter, ModItems.FISH_STEW.get()))
            .addCriterion("chicken_soup", usedItem(holderGetter, ModItems.CHICKEN_SOUP.get()))
            .addCriterion("fried_rice", usedItem(holderGetter, ModItems.FRIED_RICE.get()))
            .addCriterion("pumpkin_soup", usedItem(holderGetter, ModItems.PUMPKIN_SOUP.get()))
            .addCriterion("baked_cod_stew", usedItem(holderGetter, ModItems.BAKED_COD_STEW.get()))
            .addCriterion("noodle_soup", usedItem(holderGetter, ModItems.NOODLE_SOUP.get()))
            .addCriterion("onion_soup", usedItem(holderGetter, ModItems.ONION_SOUP.get()))
            .addCriterion("bacon_and_eggs", usedItem(holderGetter, ModItems.BACON_AND_EGGS.get()))
            .addCriterion("ratatouille", usedItem(holderGetter, ModItems.RATATOUILLE.get()))
            .addCriterion("steak_and_potatoes", usedItem(holderGetter, ModItems.STEAK_AND_POTATOES.get()))
            .addCriterion("pasta_with_meatballs", usedItem(holderGetter, ModItems.PASTA_WITH_MEATBALLS.get()))
            .addCriterion("pasta_with_mutton_chop", usedItem(holderGetter, ModItems.PASTA_WITH_MUTTON_CHOP.get()))
            .addCriterion("mushroom_rice", usedItem(holderGetter, ModItems.MUSHROOM_RICE.get()))
            .addCriterion("roasted_mutton_chops", usedItem(holderGetter, ModItems.ROASTED_MUTTON_CHOPS.get()))
            .addCriterion("vegetable_noodles", usedItem(holderGetter, ModItems.VEGETABLE_NOODLES.get()))
            .addCriterion("squid_ink_pasta", usedItem(holderGetter, ModItems.SQUID_INK_PASTA.get()))
            .addCriterion("grilled_salmon", usedItem(holderGetter, ModItems.GRILLED_SALMON.get()))
            .addCriterion("roast_chicken", usedItem(holderGetter, ModItems.ROAST_CHICKEN.get()))
            .addCriterion("stuffed_pumpkin", usedItem(holderGetter, ModItems.STUFFED_PUMPKIN.get()))
            .addCriterion("honey_glazed_ham", usedItem(holderGetter, ModItems.HONEY_GLAZED_HAM.get()))
            .addCriterion("shepherds_pie", usedItem(holderGetter, ModItems.SHEPHERDS_PIE.get()))
            .addCriterion("gleaming_salad", usedItem(holderGetter, ModItems.GLEAMING_SALAD.get()))
            .rewards(net.minecraft.advancements.AdvancementRewards.Builder.experience(200))
            .save(consumer, this.getNameId("main/master_chef"));
    }

    protected static Criterion<?> usedItem(HolderGetter<Item> holderGetter, Item item) {
        return net.minecraft.advancements.criterion.ConsumeItemTrigger.TriggerInstance.usedItem(holderGetter, item);
    }

    protected static Builder getAdvancement(
        AdvancementHolder parent, ItemLike display, String name, AdvancementType frame, boolean showToast, boolean announceToChat, boolean hidden
    ) {
        return Builder.advancement()
            .parent(parent)
            .display(
                display, TextUtils.advancement(name + ".title"), TextUtils.advancement(name + ".description"), null, frame, showToast, announceToChat, hidden
            );
    }

    private String getNameId(String id) {
        return "farmersdelight:" + id;
    }
}
