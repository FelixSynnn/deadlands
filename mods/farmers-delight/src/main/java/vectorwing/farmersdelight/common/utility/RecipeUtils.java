package vectorwing.farmersdelight.common.utility;

import net.minecraft.resources.Identifier;

public class RecipeUtils {
    public static Identifier FDLocation(String name) {
        return Identifier.fromNamespaceAndPath("farmersdelight", name);
    }
}
