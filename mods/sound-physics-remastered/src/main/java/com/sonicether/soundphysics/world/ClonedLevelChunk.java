package com.sonicether.soundphysics.world;

import java.util.ArrayList;
import java.util.function.Function;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.ChunkAccess.PackedTicks;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.ticks.LevelChunkTicks;
import net.minecraft.world.ticks.TickContainerAccess;

public class ClonedLevelChunk extends ChunkAccess {
    private final PackedTicks packedTicks;
    private final LevelChunkTicks<Block> blockTicks;
    private final LevelChunkTicks<Fluid> fluidTicks;

    public ClonedLevelChunk(Level level, ChunkPos chunkPos, @Nullable LevelChunkSection[] levelChunkSections) {
        super(chunkPos, null, new ClonedLevelHeightAccessor(level), level.palettedContainerFactory(), 0L, levelChunkSections, null);

        for (Types types : Types.values()) {
            if (ChunkStatus.FULL.heightmapsAfter().contains(types)) {
                this.heightmaps.put(types, new Heightmap(this, types));
            }
        }

        this.packedTicks = new PackedTicks(new ArrayList<>(), new ArrayList<>());
        this.blockTicks = new LevelChunkTicks<>();
        this.fluidTicks = new LevelChunkTicks<>();
    }

    @Override
    public BlockEntity getBlockEntity(@Nonnull BlockPos blockPos) {
        return this.blockEntities.get(blockPos);
    }

    @Override
    public BlockState getBlockState(@Nonnull BlockPos blockPos) {
        return this.withLevelChunkSectionAtPosition(
            blockPos,
            section -> section != null && !section.hasOnlyAir()
                ? section.getBlockState(blockPos.getX() & 15, blockPos.getY() & 15, blockPos.getZ() & 15)
                : Blocks.AIR.defaultBlockState()
        );
    }

    @Override
    public FluidState getFluidState(@Nonnull BlockPos blockPos) {
        return this.withLevelChunkSectionAtPosition(
            blockPos,
            section -> section != null && !section.hasOnlyAir()
                ? section.getFluidState(blockPos.getX() & 15, blockPos.getY() & 15, blockPos.getZ() & 15)
                : Fluids.EMPTY.defaultFluidState()
        );
    }

    private <T> T withLevelChunkSectionAtPosition(BlockPos blockPos, Function<LevelChunkSection, T> block) {
        try {
            int sectionIndex = this.getSectionIndex(blockPos.getY());
            if (sectionIndex >= 0 && sectionIndex < this.sections.length) {
                LevelChunkSection section = this.sections[sectionIndex];
                return block.apply(section);
            } else {
                return block.apply(null);
            }
        } catch (Throwable exception) {
            CrashReport crashReport = CrashReport.forThrowable(exception, "Getting section in cloned level chunk");
            CrashReportCategory crashReportCategory = crashReport.addCategory("Chunk Section Get");
            crashReportCategory.setDetail("Location", () -> CrashReportCategory.formatLocation(this, blockPos.getX(), blockPos.getY(), blockPos.getZ()));
            throw new ReportedException(crashReport);
        }
    }

    @Override
    public TickContainerAccess<Block> getBlockTicks() {
        return this.blockTicks;
    }

    @Override
    public TickContainerAccess<Fluid> getFluidTicks() {
        return this.fluidTicks;
    }

    @Override
    public PackedTicks getTicksForSerialization(long l) {
        return this.packedTicks;
    }

    @Override
    public ChunkStatus getPersistedStatus() {
        return ChunkStatus.FULL;
    }

    @Override
    public void addEntity(@Nonnull Entity entity) {
        throw new UnsupportedOperationException("Can not add entity to read-only level clone");
    }

    @Override
    public CompoundTag getBlockEntityNbtForSaving(BlockPos blockPos, Provider provider) {
        throw new UnsupportedOperationException("Can not read block entity NBT data from read-only level clone");
    }

    @Override
    public void removeBlockEntity(@Nonnull BlockPos blockPos) {
        throw new UnsupportedOperationException("Can not remove entity from read-only level clone");
    }

    @Nullable
    @Override
    public BlockState setBlockState(BlockPos blockPos, BlockState blockState, int i) {
        throw new UnsupportedOperationException("Can not set block state in read-only level clone");
    }

    @Override
    public void setBlockEntity(@Nonnull BlockEntity blockEntity) {
        throw new UnsupportedOperationException("Can not set block entity in read-only level clone");
    }
}
