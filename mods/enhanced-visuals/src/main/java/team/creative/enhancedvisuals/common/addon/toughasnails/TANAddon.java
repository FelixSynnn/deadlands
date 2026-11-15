package team.creative.enhancedvisuals.common.addon.toughasnails;

import net.minecraft.resources.Identifier;
import team.creative.enhancedvisuals.common.visual.VisualRegistry;

public class TANAddon {
    public static ThirstHandler thirst;
    public static TemperatureHandler temperature;

    public static void load() {
        VisualRegistry.registerHandler(Identifier.tryBuild("enhancedvisuals", "thirst"), thirst = new ThirstHandler());
        VisualRegistry.registerHandler(Identifier.tryBuild("enhancedvisuals", "temperature"), temperature = new TemperatureHandler());
    }
}
