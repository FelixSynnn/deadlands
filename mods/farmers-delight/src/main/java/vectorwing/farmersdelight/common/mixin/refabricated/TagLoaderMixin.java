package vectorwing.farmersdelight.common.mixin.refabricated;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.tags.TagLoader;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import vectorwing.farmersdelight.refabricated.duck.TagLoaderSpecificResourceDuck;

@Mixin(TagLoader.class)
public class TagLoaderMixin implements TagLoaderSpecificResourceDuck {
    @Unique
    private @Nullable Identifier fdrf$singleTagPath = null;

    @ModifyExpressionValue(
        method = "load",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/resources/FileToIdConverter;listMatchingResourceStacks(Lnet/minecraft/server/packs/resources/ResourceManager;)Ljava/util/Map;"
        )
    )
    private Map<Identifier, List<Resource>> fdrf$onlyLoadSingleTagIfSpecified(
        Map<Identifier, List<Resource>> original, @Local(argsOnly = true) ResourceManager resourceManager
    ) {
        return this.fdrf$singleTagPath != null ? Map.of(this.fdrf$singleTagPath, resourceManager.getResourceStack(this.fdrf$singleTagPath)) : original;
    }

    @Override
    public void fdrf$setSingleTag(Identifier tagPath) {
        this.fdrf$singleTagPath = tagPath;
    }
}
