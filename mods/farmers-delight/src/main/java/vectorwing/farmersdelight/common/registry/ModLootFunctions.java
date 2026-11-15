package vectorwing.farmersdelight.common.registry;

import java.util.function.Supplier;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctionType;
import vectorwing.farmersdelight.common.loot.function.CopySkilletFunction;
import vectorwing.farmersdelight.common.loot.function.SmokerCookFunction;
import vectorwing.farmersdelight.refabricated.RegUtils;

public class ModLootFunctions {
    public static final Supplier<LootItemFunctionType<CopySkilletFunction>> COPY_SKILLET = RegUtils.regLootFunc(
        "copy_skillet", () -> new LootItemFunctionType<>(CopySkilletFunction.CODEC)
    );
    public static final Supplier<LootItemFunctionType<SmokerCookFunction>> SMOKER_COOK = RegUtils.regLootFunc(
        "smoker_cook", () -> new LootItemFunctionType<>(SmokerCookFunction.CODEC)
    );

    public static void touch() {
    }
}
