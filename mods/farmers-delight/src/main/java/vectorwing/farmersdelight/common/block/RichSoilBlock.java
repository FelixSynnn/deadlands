package vectorwing.farmersdelight.common.block;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.registry.TillableBlockRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import vectorwing.farmersdelight.common.Configuration;
import vectorwing.farmersdelight.common.network.payload.RichSoilBoostParticlesPayload;
import vectorwing.farmersdelight.common.registry.ModBlocks;
import vectorwing.farmersdelight.common.tag.ModTags;

public class RichSoilBlock extends Block {
    public RichSoilBlock(Properties properties) {
        super(properties);
    }

    public static void init() {
        TillableBlockRegistry.register(
            ModBlocks.RICH_SOIL.get(), HoeItem::onlyIfAirAbove, HoeItem.changeIntoState(ModBlocks.RICH_SOIL_FARMLAND.get().defaultBlockState())
        );
    }

    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        BlockPos abovePos = pos.above();
        BlockState aboveState = level.getBlockState(abovePos);
        if (!this.convertMushroomToColony(aboveState, abovePos, level)) {
            tryBoostingPlantsAboveAndBelow(level, pos, random);
        }
    }

    public static void tryBoostingPlantsAboveAndBelow(ServerLevel level, BlockPos pos, RandomSource random) {
        if (Configuration.RICH_SOIL_BOOST_CHANCE.get() != 0.0 && !(random.nextFloat() > Configuration.RICH_SOIL_BOOST_CHANCE.get())) {
            BlockPos abovePos = pos.above();
            BlockState aboveState = level.getBlockState(abovePos);
            if (aboveState.is(ModTags.Blocks.PLANTED_FROM_BELOW) || !boostPlant(aboveState, abovePos, level)) {
                BlockPos belowPos = pos.below();
                BlockState belowState = level.getBlockState(belowPos);
                if (belowState.is(ModTags.Blocks.PLANTED_FROM_BELOW)) {
                    boostPlant(belowState, belowPos, level);
                }
            }
        }
    }

    public static boolean boostPlant(BlockState plantState, BlockPos plantPos, ServerLevel level) {
        if (plantState.is(ModTags.Blocks.UNAFFECTED_BY_RICH_SOIL)) {
            return false;
        }

        if (plantState.getBlock() instanceof BonemealableBlock growable && growable.isValidBonemealTarget(level, plantPos, plantState)) {
            growable.performBonemeal(level, level.getRandom(), plantPos, plantState);

            for (ServerPlayer player : level.getChunkSource().chunkMap.getPlayers(level.getChunkAt(plantPos).getPos(), false)) {
                ServerPlayNetworking.send(player, new RichSoilBoostParticlesPayload(plantPos));
            }

            return true;
        } else {
            return false;
        }
    }

    public boolean convertMushroomToColony(BlockState targetState, BlockPos targetPos, ServerLevel level) {
        if (targetState.is(Blocks.BROWN_MUSHROOM)) {
            level.setBlockAndUpdate(targetPos, ModBlocks.BROWN_MUSHROOM_COLONY.get().defaultBlockState());
            return true;
        } else if (targetState.is(Blocks.RED_MUSHROOM)) {
            level.setBlockAndUpdate(targetPos, ModBlocks.RED_MUSHROOM_COLONY.get().defaultBlockState());
            return true;
        } else {
            return false;
        }
    }
}
