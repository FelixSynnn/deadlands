package vectorwing.farmersdelight.common.world;

import com.mojang.datafixers.util.Pair;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool.Projection;
import net.minecraft.world.level.levelgen.structure.templatesystem.AlwaysTrueTest;
import net.minecraft.world.level.levelgen.structure.templatesystem.ProcessorRule;
import net.minecraft.world.level.levelgen.structure.templatesystem.RandomBlockMatchTest;
import net.minecraft.world.level.levelgen.structure.templatesystem.RuleProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;
import vectorwing.farmersdelight.common.Configuration;
import vectorwing.farmersdelight.common.registry.ModBlocks;

public class VillageStructures {
    public static void init() {
        ServerLifecycleEvents.SERVER_STARTING.register(VillageStructures::addNewVillageBuilding);
    }

    public static void addNewVillageBuilding(MinecraftServer server) {
        if (Configuration.GENERATE_VILLAGE_COMPOST_HEAPS.get()) {
            Registry<StructureTemplatePool> templatePools = server.registryAccess().lookupOrThrow(Registries.TEMPLATE_POOL);
            Registry<StructureProcessorList> processorLists = server.registryAccess().lookupOrThrow(Registries.PROCESSOR_LIST);
            addBuildingToPool(
                templatePools, processorLists, Identifier.parse("minecraft:village/plains/houses"), "farmersdelight:village/houses/plains_compost_pile", 5
            );
            addBuildingToPool(
                templatePools, processorLists, Identifier.parse("minecraft:village/snowy/houses"), "farmersdelight:village/houses/snowy_compost_pile", 3
            );
            addBuildingToPool(
                templatePools, processorLists, Identifier.parse("minecraft:village/savanna/houses"), "farmersdelight:village/houses/savanna_compost_pile", 4
            );
            addBuildingToPool(
                templatePools, processorLists, Identifier.parse("minecraft:village/desert/houses"), "farmersdelight:village/houses/desert_compost_pile", 3
            );
            addBuildingToPool(
                templatePools, processorLists, Identifier.parse("minecraft:village/taiga/houses"), "farmersdelight:village/houses/taiga_compost_pile", 4
            );
        }

        if (Configuration.GENERATE_VILLAGE_FARM_FD_CROPS.get()) {
            Registry<StructureProcessorList> processorLists = server.registryAccess().lookupOrThrow(Registries.PROCESSOR_LIST);
            StructureProcessor temperateCropProcessor = new RuleProcessor(
                List.of(
                    new ProcessorRule(new RandomBlockMatchTest(Blocks.WHEAT, 0.3F), AlwaysTrueTest.INSTANCE, ModBlocks.CABBAGE_CROP.get().defaultBlockState()),
                    new ProcessorRule(new RandomBlockMatchTest(Blocks.WHEAT, 0.3F), AlwaysTrueTest.INSTANCE, ModBlocks.TOMATO_CROP.get().defaultBlockState()),
                    new ProcessorRule(new RandomBlockMatchTest(Blocks.WHEAT, 0.3F), AlwaysTrueTest.INSTANCE, ModBlocks.ONION_CROP.get().defaultBlockState())
                )
            );
            StructureProcessor coldCropProcessor = new RuleProcessor(
                List.of(
                    new ProcessorRule(new RandomBlockMatchTest(Blocks.WHEAT, 0.3F), AlwaysTrueTest.INSTANCE, ModBlocks.CABBAGE_CROP.get().defaultBlockState()),
                    new ProcessorRule(new RandomBlockMatchTest(Blocks.WHEAT, 0.3F), AlwaysTrueTest.INSTANCE, ModBlocks.ONION_CROP.get().defaultBlockState()),
                    new ProcessorRule(
                        new RandomBlockMatchTest(Blocks.POTATOES, 0.2F), AlwaysTrueTest.INSTANCE, ModBlocks.CABBAGE_CROP.get().defaultBlockState()
                    ),
                    new ProcessorRule(new RandomBlockMatchTest(Blocks.POTATOES, 0.2F), AlwaysTrueTest.INSTANCE, ModBlocks.ONION_CROP.get().defaultBlockState())
                )
            );
            StructureProcessor aridCropProcessor = new RuleProcessor(
                List.of(
                    new ProcessorRule(new RandomBlockMatchTest(Blocks.WHEAT, 0.3F), AlwaysTrueTest.INSTANCE, ModBlocks.CABBAGE_CROP.get().defaultBlockState()),
                    new ProcessorRule(new RandomBlockMatchTest(Blocks.WHEAT, 0.3F), AlwaysTrueTest.INSTANCE, ModBlocks.TOMATO_CROP.get().defaultBlockState())
                )
            );
            addNewRuleToProcessorList(Identifier.parse("minecraft:farm_plains"), temperateCropProcessor, processorLists);
            addNewRuleToProcessorList(Identifier.parse("minecraft:farm_savanna"), aridCropProcessor, processorLists);
            addNewRuleToProcessorList(Identifier.parse("minecraft:farm_snowy"), coldCropProcessor, processorLists);
            addNewRuleToProcessorList(Identifier.parse("minecraft:farm_taiga"), temperateCropProcessor, processorLists);
            addNewRuleToProcessorList(Identifier.parse("minecraft:farm_desert"), aridCropProcessor, processorLists);
        }
    }

    public static void addBuildingToPool(
        Registry<StructureTemplatePool> templatePoolRegistry,
        Registry<StructureProcessorList> processorListRegistry,
        Identifier poolRL,
        String nbtPieceRL,
        int weight
    ) {
        StructureTemplatePool pool = templatePoolRegistry.getValue(poolRL);
        if (pool != null) {
            Identifier emptyProcessor = Identifier.withDefaultNamespace("empty");
            Holder<StructureProcessorList> processorHolder = processorListRegistry.getOrThrow(ResourceKey.create(Registries.PROCESSOR_LIST, emptyProcessor));
            SinglePoolElement piece = SinglePoolElement.single(nbtPieceRL, processorHolder).apply(Projection.RIGID);

            for (int i = 0; i < weight; i++) {
                pool.templates.add(piece);
            }

            List<Pair<StructurePoolElement, Integer>> listOfPieceEntries = new ArrayList<>(pool.rawTemplates);
            listOfPieceEntries.add(new Pair<>(piece, weight));
            pool.rawTemplates = listOfPieceEntries;
        }
    }

    private static void addNewRuleToProcessorList(
        Identifier targetProcessorList, StructureProcessor processorToAdd, Registry<StructureProcessorList> processorListRegistry
    ) {
        processorListRegistry.getOptional(targetProcessorList).ifPresent(processorList -> {
            List<StructureProcessor> newSafeList = new ArrayList<>(processorList.list());
            newSafeList.add(processorToAdd);
            processorList.list = newSafeList;
        });
    }
}
