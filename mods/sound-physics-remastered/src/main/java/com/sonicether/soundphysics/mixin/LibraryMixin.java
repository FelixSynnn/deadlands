package com.sonicether.soundphysics.mixin;

import com.mojang.blaze3d.audio.Library;
import java.nio.IntBuffer;
import java.util.Arrays;
import org.lwjgl.system.MemoryStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(Library.class)
public class LibraryMixin {
    @ModifyArgs(method = "init", at = @At(value = "INVOKE", target = "Lorg/lwjgl/openal/ALC10;alcCreateContext(JLjava/nio/IntBuffer;)J"))
    private void modifyContext(Args args) {
        int[] original = this.toArray(((IntBuffer)args.get(1)).duplicate());
        MemoryStack stack = MemoryStack.stackPush();

        try {
            IntBuffer buffer = stack.mallocInt(original.length + 3);
            buffer.put(original, 0, original.length - 1);
            buffer.put(131075).put(4).put(0);
            args.set(1, buffer.put(0).flip());
        } catch (Throwable var7) {
            if (stack != null) {
                try {
                    stack.close();
                } catch (Throwable var6) {
                    var7.addSuppressed(var6);
                }
            }

            throw var7;
        }

        if (stack != null) {
            stack.close();
        }
    }

    @Unique
    private int[] toArray(IntBuffer buffer) {
        if (buffer.hasArray()) {
            return buffer.arrayOffset() == 0 ? buffer.array() : Arrays.copyOfRange(buffer.array(), buffer.arrayOffset(), buffer.array().length);
        }

        buffer.rewind();
        int[] arr = new int[buffer.remaining()];
        buffer.get(arr);
        return arr;
    }
}
