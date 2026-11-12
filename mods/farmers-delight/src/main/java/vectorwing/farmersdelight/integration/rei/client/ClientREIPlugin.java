package vectorwing.farmersdelight.integration.rei.client;

import java.util.List;
import me.shedaniel.math.Rectangle;
import me.shedaniel.rei.api.client.plugins.REIClientPlugin;
import me.shedaniel.rei.api.client.registry.category.CategoryRegistry;
import me.shedaniel.rei.api.client.registry.display.DisplayCategory;
import me.shedaniel.rei.api.client.registry.display.DisplayRegistry;
import me.shedaniel.rei.api.client.registry.screen.ScreenRegistry;
import me.shedaniel.rei.api.client.registry.transfer.TransferHandlerRegistry;
import me.shedaniel.rei.api.client.registry.transfer.simple.SimpleTransferHandler;
import me.shedaniel.rei.api.client.registry.transfer.simple.SimpleTransferHandler.IntRange;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.entry.EntryStack;
import me.shedaniel.rei.api.common.util.EntryIngredients;
import me.shedaniel.rei.api.common.util.EntryStacks;
import me.shedaniel.rei.plugin.common.displays.DefaultInformationDisplay;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;
import vectorwing.farmersdelight.client.gui.CookingPotScreen;
import vectorwing.farmersdelight.common.block.entity.container.CookingPotMenu;
import vectorwing.farmersdelight.common.registry.ModItems;
import vectorwing.farmersdelight.common.utility.TextUtils;
import vectorwing.farmersdelight.integration.rei.REICategoryIdentifiers;
import vectorwing.farmersdelight.integration.rei.client.categories.CookingPotCategory;
import vectorwing.farmersdelight.integration.rei.client.categories.CuttingCategory;
import vectorwing.farmersdelight.integration.rei.client.categories.DecompositionCategory;

public class ClientREIPlugin implements REIClientPlugin {
    public void registerDisplays(DisplayRegistry registry) {
        registry.add(
            DefaultInformationDisplay.createFromEntries(
                    EntryIngredients.of(ModItems.WHEAT_DOUGH.get()), Component.translatable("item.farmersdelight.wheat_dough")
                )
                .lines(new Component[]{TextUtils.JEI("info.dough")})
        );
        registry.add(
            DefaultInformationDisplay.createFromEntries(EntryIngredients.of(ModItems.STRAW.get()), Component.translatable("item.farmersdelight.straw"))
                .lines(new Component[]{TextUtils.JEI("info.straw")})
        );
        registry.add(
            DefaultInformationDisplay.createFromEntries(EntryIngredients.of(ModItems.HAM.get()), Component.translatable("item.farmersdelight.ham"))
                .lines(new Component[]{TextUtils.JEI("info.ham")})
        );
        registry.add(
            DefaultInformationDisplay.createFromEntries(
                    EntryIngredients.ofItems(
                        List.of(
                            ModItems.FLINT_KNIFE.get(),
                            ModItems.COPPER_KNIFE.get(),
                            ModItems.IRON_KNIFE.get(),
                            ModItems.DIAMOND_KNIFE.get(),
                            ModItems.NETHERITE_KNIFE.get(),
                            ModItems.GOLDEN_KNIFE.get()
                        )
                    ),
                    Component.translatable("tag.item.farmersdelight.tools.knives")
                )
                .lines(new Component[]{TextUtils.JEI("info.knife")})
        );
        registry.add(
            DefaultInformationDisplay.createFromEntries(
                    EntryIngredients.ofItems(List.of(ModItems.WILD_CABBAGES.get(), ModItems.CABBAGE.get(), ModItems.CABBAGE_LEAF.get())),
                    Component.translatable("item.farmersdelight.cabbage")
                )
                .lines(new Component[]{TextUtils.JEI("info.wild_cabbages")})
        );
        registry.add(
            DefaultInformationDisplay.createFromEntries(
                    EntryIngredients.ofItems(List.of(ModItems.WILD_BEETROOTS.get(), Items.BEETROOT)), Component.translatable("item.minecraft.beetroot")
                )
                .lines(new Component[]{TextUtils.JEI("info.wild_beetroots")})
        );
        registry.add(
            DefaultInformationDisplay.createFromEntries(
                    EntryIngredients.ofItems(List.of(ModItems.WILD_CARROTS.get(), Items.CARROT)), Component.translatable("item.minecraft.carrot")
                )
                .lines(new Component[]{TextUtils.JEI("info.wild_carrots")})
        );
        registry.add(
            DefaultInformationDisplay.createFromEntries(
                    EntryIngredients.ofItems(List.of(ModItems.WILD_ONIONS.get(), ModItems.ONION.get())), Component.translatable("item.farmersdelight.onion")
                )
                .lines(new Component[]{TextUtils.JEI("info.wild_onions")})
        );
        registry.add(
            DefaultInformationDisplay.createFromEntries(
                    EntryIngredients.ofItems(List.of(ModItems.WILD_POTATOES.get(), Items.POTATO)), Component.translatable("item.minecraft.potato")
                )
                .lines(new Component[]{TextUtils.JEI("info.wild_potatoes")})
        );
        registry.add(
            DefaultInformationDisplay.createFromEntries(
                    EntryIngredients.ofItems(List.of(ModItems.WILD_TOMATOES.get(), ModItems.TOMATO.get())),
                    Component.translatable("item.farmersdelight.tomato")
                )
                .lines(new Component[]{TextUtils.JEI("info.wild_tomatoes")})
        );
        registry.add(
            DefaultInformationDisplay.createFromEntries(
                    EntryIngredients.ofItems(List.of(ModItems.WILD_RICE.get(), ModItems.RICE_PANICLE.get())),
                    Component.translatable("item.farmersdelight.rice")
                )
                .lines(new Component[]{TextUtils.JEI("info.wild_rice")})
        );
    }

    public void registerScreens(ScreenRegistry registry) {
        registry.registerContainerClickArea(new Rectangle(89, 25, 24, 17), CookingPotScreen.class, new CategoryIdentifier[]{REICategoryIdentifiers.COOKING});
    }

    public void registerCategories(CategoryRegistry registry) {
        registry.add(new DisplayCategory[]{new CookingPotCategory(), new CuttingCategory(), new DecompositionCategory()});
        registry.addWorkstations(REICategoryIdentifiers.COOKING, new EntryStack[]{EntryStacks.of(ModItems.COOKING_POT.get())});
        registry.addWorkstations(REICategoryIdentifiers.CUTTING, new EntryStack[]{EntryStacks.of(ModItems.CUTTING_BOARD.get())});
    }

    public void registerTransferHandlers(TransferHandlerRegistry registry) {
        registry.register(SimpleTransferHandler.create(CookingPotMenu.class, REICategoryIdentifiers.COOKING, new IntRange(0, 6)));
    }
}
