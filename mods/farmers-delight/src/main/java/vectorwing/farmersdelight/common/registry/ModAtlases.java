package vectorwing.farmersdelight.common.registry;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.resources.model.Material;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.DyeColor;
import org.jspecify.annotations.Nullable;

public class ModAtlases {
    public static final Material BLANK_CANVAS_SIGN_MATERIAL = new Material(
        Sheets.SIGN_SHEET, Identifier.fromNamespaceAndPath("farmersdelight", "entity/signs/canvas")
    );
    public static final Material BLANK_HANGING_CANVAS_SIGN_MATERIAL = new Material(
        Sheets.SIGN_SHEET, Identifier.fromNamespaceAndPath("farmersdelight", "entity/signs/hanging/canvas")
    );
    public static final Map<DyeColor, Material> DYED_CANVAS_SIGN_MATERIALS = Arrays.stream(DyeColor.values())
        .collect(Collectors.toMap(Function.identity(), ModAtlases::createCanvasSignMaterial));
    public static final Map<DyeColor, Material> DYED_HANGING_CANVAS_SIGN_MATERIALS = Arrays.stream(DyeColor.values())
        .collect(Collectors.toMap(Function.identity(), ModAtlases::createHangingCanvasSignMaterial));

    public static Material createCanvasSignMaterial(DyeColor dyeType) {
        return new Material(Sheets.SIGN_SHEET, Identifier.fromNamespaceAndPath("farmersdelight", "entity/signs/canvas_" + dyeType.getName()));
    }

    public static Material createHangingCanvasSignMaterial(DyeColor dyeType) {
        return new Material(Sheets.SIGN_SHEET, Identifier.fromNamespaceAndPath("farmersdelight", "entity/signs/hanging/canvas_" + dyeType.getName()));
    }

    public static Material getCanvasSignMaterial(@Nullable DyeColor dyeColor) {
        return dyeColor != null ? DYED_CANVAS_SIGN_MATERIALS.get(dyeColor) : BLANK_CANVAS_SIGN_MATERIAL;
    }

    public static Material getHangingCanvasSignMaterial(@Nullable DyeColor dyeColor) {
        return dyeColor != null ? DYED_HANGING_CANVAS_SIGN_MATERIALS.get(dyeColor) : BLANK_HANGING_CANVAS_SIGN_MATERIAL;
    }
}
