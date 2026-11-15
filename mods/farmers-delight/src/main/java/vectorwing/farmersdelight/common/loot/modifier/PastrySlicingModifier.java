package vectorwing.farmersdelight.common.loot.modifier;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CakeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import org.jspecify.annotations.NonNull;
import vectorwing.farmersdelight.common.block.PieBlock;
import vectorwing.farmersdelight.refabricated.LootModifier;

public class PastrySlicingModifier extends LootModifier {
    public static final int MAX_CAKE_BITES = 7;
    public static final int MAX_PIE_BITES = 4;
    private final Item pastrySlice;

    public PastrySlicingModifier(LootItemCondition[] conditions, Item pastrySlice) {
        super(conditions);
        this.pastrySlice = pastrySlice;
    }

    @Override
    protected @NonNull ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
        BlockState state = context.getOptionalParameter(LootContextParams.BLOCK_STATE);
        if (state != null) {
            Block targetBlock = state.getBlock();
            if (targetBlock instanceof CakeBlock) {
                int bites = state.getValue(CakeBlock.BITES);
                generatedLoot.add(new ItemStack(this.pastrySlice, 7 - bites));
            } else if (targetBlock instanceof PieBlock) {
                int bites = state.getValue(PieBlock.BITES);
                generatedLoot.add(new ItemStack(this.pastrySlice, 4 - bites));
            }
        }

        return generatedLoot;
    }
}
