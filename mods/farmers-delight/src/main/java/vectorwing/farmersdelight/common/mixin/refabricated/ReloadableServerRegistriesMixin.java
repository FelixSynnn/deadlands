package vectorwing.farmersdelight.common.mixin.refabricated;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.ReloadableServerRegistries;
import net.minecraft.server.ReloadableServerRegistries.LoadResult;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.storage.loot.LootDataType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vectorwing.farmersdelight.refabricated.RefabricatedEarlyTagUtils;

@Mixin(ReloadableServerRegistries.class)
public class ReloadableServerRegistriesMixin {
    @Inject(method = "method_58276", at = @At("HEAD"))
    private static <T> void fdrf$setLootTableAccess(
        RegistryOps registryOps,
        ResourceManager resourceManager,
        Executor executor,
        LootDataType<T> lootDataType,
        CallbackInfoReturnable<CompletableFuture> cir
    ) {
        if (lootDataType == LootDataType.TABLE) {
            RefabricatedEarlyTagUtils.setLootTableResourceManager(resourceManager);
        }
    }

    @Inject(method = "method_61247", at = @At("RETURN"))
    private static void fdrf$clearLootTableAccess(
        LayeredRegistryAccess layeredRegistryAccess, Provider provider, List list, CallbackInfoReturnable<LoadResult> cir
    ) {
        RefabricatedEarlyTagUtils.resetEarlyTagCollections();
    }
}
