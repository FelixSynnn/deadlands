package vectorwing.farmersdelight.common.registry;

import java.util.function.Supplier;
import vectorwing.farmersdelight.common.advancement.CuttingBoardTrigger;
import vectorwing.farmersdelight.refabricated.RegUtils;

public class ModAdvancements {
    public static final Supplier<CuttingBoardTrigger> USE_CUTTING_BOARD = RegUtils.regTrigger("use_cutting_board", CuttingBoardTrigger::new);

    public static void touch() {
    }
}
