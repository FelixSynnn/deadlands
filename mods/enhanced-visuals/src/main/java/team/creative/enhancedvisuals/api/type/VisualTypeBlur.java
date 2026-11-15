package team.creative.enhancedvisuals.api.type;

import net.minecraft.resources.Identifier;

public class VisualTypeBlur extends VisualTypeShader {
    public static final Identifier BLUR_SHADER = Identifier.tryBuild("enhancedvisuals", "blur");

    public VisualTypeBlur(String name) {
        super(name, BLUR_SHADER);
    }
}
