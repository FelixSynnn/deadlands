package vectorwing.farmersdelight.common.utility;

import java.util.Random;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import vectorwing.farmersdelight.refabricated.inventory.ItemStackHandler;

public class MathUtils {
    public static final Random RAND = new Random();

    public static int calcRedstoneFromItemHandler(@Nullable ItemStackHandler handler) {
        if (handler == null) {
            return 0;
        }

        int i = 0;
        float f = 0.0F;

        for (int j = 0; j < handler.getSlotCount(); j++) {
            ItemStack itemstack = handler.getStackInSlot(j);
            if (!itemstack.isEmpty()) {
                f += (float)itemstack.getCount() / Math.min(handler.getSlotLimit(j), itemstack.getMaxStackSize());
                i++;
            }
        }

        f /= handler.getSlotCount();
        return Mth.floor(f * 14.0F) + (i > 0 ? 1 : 0);
    }
}
