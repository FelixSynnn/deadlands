package com.huaxiaosheng.spapacks.client;

import com.huaxiaosheng.spapacks.SpaPacks;
import com.huaxiaosheng.spapacks.mixin.RenderLayerAccessor;
import com.huaxiaosheng.spapacks.mixin.RenderPhasesAccessor;
import com.huaxiaosheng.spapacks.mixin.SpriteAtlasAccessor;
import com.huaxiaosheng.spapacks.mixin.TextureBaseAccessor;
import com.huaxiaosheng.spapacks.platform.Game;
import com.mojang.blaze3d.platform.NativeImage;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;

public final class TextureMasks {
    public static final TextureMasks.Mask SOLID = new TextureMasks.Mask() {
        @Override
        public boolean solid(float u, float v) {
            return true;
        }

        @Override
        public float texels(float du, float dv, float u, float v) {
            return 0.0F;
        }
    };
    private static final Map<RenderType, TextureMasks.Mask> BY_LAYER = new IdentityHashMap<>();
    private static final Map<Identifier, TextureMasks.Mask> BY_TEXTURE = new HashMap<>();

    private TextureMasks() {
    }

    public static TextureMasks.Mask of(RenderType layer) {
        TextureMasks.Mask m = BY_LAYER.get(layer);
        if (m == null) {
            Identifier id = texture(layer);
            m = id == null ? SOLID : BY_TEXTURE.computeIfAbsent(id, TextureMasks::load);
            BY_LAYER.put(layer, m);
        }

        return m;
    }

    static void clear() {
        for (TextureMasks.Mask m : BY_TEXTURE.values()) {
            if (m instanceof TextureMasks.ImageMask image) {
                image.image.close();
            }
        }

        BY_TEXTURE.clear();
        BY_LAYER.clear();
    }

    private static Identifier texture(RenderType layer) {
        try {
            if (layer instanceof RenderLayerAccessor multi
                && ((RenderPhasesAccessor)(Object)multi.spaPacks$phases()).spaPacks$textures().get("Sampler0") instanceof TextureBaseAccessor texture) {
                return texture.spaPacks$location();
            }
        } catch (RuntimeException | LinkageError var4) {
        }

        return null;
    }

    private static TextureMasks.Mask load(Identifier id) {
        if (Minecraft.getInstance().getTextureManager().getTexture(id) instanceof TextureAtlas atlas) {
            return new TextureMasks.AtlasMask(((SpriteAtlasAccessor)atlas).spaPacks$sprites().values());
        } else {
            try (InputStream in = Minecraft.getInstance().getResourceManager().open(id)) {
                return new TextureMasks.ImageMask(NativeImage.read(in));
            } catch (Exception e) {
                SpaPacks.LOGGER.debug("[spa] no mask for {}: {}", id, e.toString());
                return SOLID;
            }
        }
    }

    private static final class AtlasMask implements TextureMasks.Mask {
        private static final int GRID = 64;
        private final List<TextureAtlasSprite>[] cells = new List[4096];

        AtlasMask(Iterable<TextureAtlasSprite> sprites) {
            for (TextureAtlasSprite s : sprites) {
                int x0 = cell(s.getU0());
                int x1 = cell(s.getU1());
                int y0 = cell(s.getV0());
                int y1 = cell(s.getV1());

                for (int y = y0; y <= y1; y++) {
                    for (int x = x0; x <= x1; x++) {
                        List<TextureAtlasSprite> list = this.cells[y * 64 + x];
                        if (list == null) {
                            this.cells[y * 64 + x] = list = new ArrayList<>(2);
                        }

                        list.add(s);
                    }
                }
            }
        }

        private static int cell(float t) {
            return Math.max(0, Math.min(63, (int)(t * 64.0F)));
        }

        private TextureAtlasSprite at(float u, float v) {
            List<TextureAtlasSprite> list = this.cells[cell(v) * 64 + cell(u)];
            if (list != null) {
                for (TextureAtlasSprite s : list) {
                    if (u >= s.getU0() && u < s.getU1() && v >= s.getV0() && v < s.getV1()) {
                        return s;
                    }
                }
            }

            return null;
        }

        @Override
        public boolean solid(float u, float v) {
            TextureAtlasSprite s = this.at(u, v);
            if (s == null) {
                return true;
            }

            SpriteContents c = s.contents();
            int x = Math.min(c.width() - 1, (int)((u - s.getU0()) / (s.getU1() - s.getU0()) * c.width()));
            int y = Math.min(c.height() - 1, (int)((v - s.getV0()) / (s.getV1() - s.getV0()) * c.height()));
            return !c.isTransparent(0, x, y);
        }

        @Override
        public float texels(float du, float dv, float u, float v) {
            TextureAtlasSprite s = this.at(u, v);
            if (s == null) {
                return 0.0F;
            }

            SpriteContents c = s.contents();
            return (float)Math.hypot(du / (s.getU1() - s.getU0()) * c.width(), dv / (s.getV1() - s.getV0()) * c.height());
        }
    }

    private static final class ImageMask implements TextureMasks.Mask {
        final NativeImage image;

        ImageMask(NativeImage image) {
            this.image = image;
        }

        @Override
        public boolean solid(float u, float v) {
            int x = Math.floorMod((int)Math.floor(u * this.image.getWidth()), this.image.getWidth());
            int y = Math.floorMod((int)Math.floor(v * this.image.getHeight()), this.image.getHeight());
            return Game.opaque(this.image, x, y);
        }

        @Override
        public float texels(float du, float dv, float u, float v) {
            return (float)Math.hypot(du * this.image.getWidth(), dv * this.image.getHeight());
        }
    }

    public interface Mask {
        boolean solid(float var1, float var2);

        float texels(float var1, float var2, float var3, float var4);
    }
}
