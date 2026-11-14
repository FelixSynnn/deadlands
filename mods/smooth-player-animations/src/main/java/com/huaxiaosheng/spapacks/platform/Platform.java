package com.huaxiaosheng.spapacks.platform;

import com.huaxiaosheng.spapacks.client.SafeMode;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;

public final class Platform {
    static final List<Consumer<Minecraft>> TICKS = new ArrayList<>();
    static final List<Platform.Reload> RELOADS = new ArrayList<>();
    static final List<Platform.Hud> HUDS = new ArrayList<>();
    static final List<Platform.PlayerLayer> PLAYER_LAYERS = new ArrayList<>();

    private Platform() {
    }

    public static void onClientTick(Consumer<Minecraft> listener) {
        TICKS.add(listener);
    }

    public static void onResourceReload(Identifier id, Consumer<ResourceManager> listener) {
        RELOADS.add(new Platform.Reload(id, manager -> SafeMode.run("resource reload " + id, () -> listener.accept(manager))));
    }

    public static void onHud(Platform.Hud listener) {
        HUDS.add(listener);
    }

    public static void onPlayerLayer(Platform.PlayerLayer layer) {
        PLAYER_LAYERS.add(layer);
    }

    static void tick(Minecraft client) {
        for (Consumer<Minecraft> t : TICKS) {
            try {
                t.accept(client);
            } catch (Throwable e) {
                SafeMode.fail("client tick", e);
            }
        }

        SafeMode.tick(client);
    }

    static RenderLayer<AvatarRenderState, PlayerModel> make(Platform.PlayerLayer layer, AvatarRenderer<?> renderer) {
        try {
            return layer.create(renderer);
        } catch (Throwable e) {
            SafeMode.fail("player layer", e);
            return null;
        }
    }

    static void hud(GuiGraphics graphics, DeltaTracker delta) {
        hud(graphics, delta.getGameTimeDeltaPartialTick(true));
    }

    static void hud(GuiGraphics graphics, float partialTick) {
        hud(new Platform.Canvas() {
            @Override
            public int width() {
                return graphics.guiWidth();
            }

            @Override
            public int height() {
                return graphics.guiHeight();
            }

            @Override
            public void fill(int x0, int y0, int x1, int y1, int color) {
                graphics.fill(x0, y0, x1, y1, color);
            }

            @Override
            public void text(Font font, String text, int x, int y, int color) {
                graphics.drawString(font, text, x, y, color);
            }
        }, partialTick);
    }

    private static void hud(Platform.Canvas canvas, float partialTick) {
        for (Platform.Hud h : HUDS) {
            try {
                h.draw(canvas, partialTick);
            } catch (Throwable e) {
                SafeMode.fail("hud", e);
            }
        }
    }

    public static boolean isModLoaded(String id) {
        return FabricLoader.getInstance().isModLoaded(id);
    }

    public static Path configDir() {
        return FabricLoader.getInstance().getConfigDir();
    }

    public static String clothConfigId() {
        return "cloth-config";
    }

    public interface Canvas {
        int width();

        int height();

        void fill(int var1, int var2, int var3, int var4, int var5);

        void text(Font var1, String var2, int var3, int var4, int var5);
    }

    public interface Hud {
        void draw(Platform.Canvas var1, float var2);
    }

    public interface PlayerLayer {
        RenderLayer<AvatarRenderState, PlayerModel> create(AvatarRenderer<?> var1);
    }

    public record Reload(Identifier id, Consumer<ResourceManager> listener) {
    }
}
