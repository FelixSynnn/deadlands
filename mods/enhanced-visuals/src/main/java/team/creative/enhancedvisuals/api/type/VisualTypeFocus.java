package team.creative.enhancedvisuals.api.type;

import net.minecraft.resources.Identifier;

public class VisualTypeFocus extends VisualTypeShader {
    public static final Identifier IDENTIFIER = Identifier.tryBuild("enhancedvisuals", "blobs2");

    public VisualTypeFocus(String name) {
        super(name, IDENTIFIER);
    }
}
