package com.deadlands.mixin;

import java.util.Map;
import net.minecraft.advancements.Advancement;
import net.minecraft.resources.Identifier;
import net.minecraft.server.ServerAdvancementManager;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Vanilla advancements are gone: only DEADLANDS achievements exist. Vanilla's recipe advancements
 * stay, because they are what unlocks recipes in the recipe book (they are hidden and never toast).
 */
@Mixin(ServerAdvancementManager.class)
public abstract class ServerAdvancementManagerMixin {
	@Inject(method = "apply(Ljava/util/Map;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V", at = @At("HEAD"))
	private void deadlands$onlyDeadlandsAchievements(Map<Identifier, Advancement> map, ResourceManager manager, ProfilerFiller profiler, CallbackInfo ci) {
		map.keySet().removeIf(id -> id.getNamespace().equals("minecraft") && !id.getPath().startsWith("recipes/"));
	}
}
