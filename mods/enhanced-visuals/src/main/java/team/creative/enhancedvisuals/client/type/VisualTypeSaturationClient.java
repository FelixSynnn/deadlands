package team.creative.enhancedvisuals.client.type;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.systems.RenderSystem;
import java.nio.ByteBuffer;
import java.util.Map;
import net.minecraft.client.renderer.PostPass;
import org.lwjgl.system.MemoryStack;
import team.creative.enhancedvisuals.api.type.VisualTypeSaturation;
import team.creative.enhancedvisuals.mixin.PostChainAccessor;
import team.creative.enhancedvisuals.mixin.PostPassAccessor;

public class VisualTypeSaturationClient extends VisualTypeShaderClient<VisualTypeSaturation> {
    private static final int UNIFORM_SIZE = new Std140SizeCalculator().putFloat().get();

    public VisualTypeSaturationClient(VisualTypeSaturation type) {
        super(type);
    }

    @Override
    public void changeProperties(float intensity) {
        for (PostPass pass : ((PostChainAccessor)this.postChain).getPasses()) {
            Map<String, GpuBuffer> uniforms = ((PostPassAccessor)pass).getCustomUniforms();
            uniforms.compute("ConvolveInfo", (key, buffer) -> {
                if (buffer == null) {
                    buffer = RenderSystem.getDevice().createBuffer(() -> "Convolve / ConvolveInfo", 136, UNIFORM_SIZE);
                }

                MemoryStack memoryStack = MemoryStack.stackPush();

                try {
                    ByteBuffer byteBuffer = Std140Builder.onStack(memoryStack, UNIFORM_SIZE).putFloat(intensity).get();
                    RenderSystem.getDevice().createCommandEncoder().writeToBuffer(buffer.slice(), byteBuffer);
                } catch (Throwable var7) {
                    if (memoryStack != null) {
                        try {
                            memoryStack.close();
                        } catch (Throwable var6) {
                            var7.addSuppressed(var6);
                        }
                    }

                    throw var7;
                }

                if (memoryStack != null) {
                    memoryStack.close();
                }

                return (GpuBuffer)buffer;
            });
        }
    }
}
