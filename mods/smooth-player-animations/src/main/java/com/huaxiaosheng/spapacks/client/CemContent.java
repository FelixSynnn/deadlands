package com.huaxiaosheng.spapacks.client;

import com.huaxiaosheng.spapacks.SpaPacks;
import com.huaxiaosheng.spapacks.animation.cem.CemAnimation;
import com.huaxiaosheng.spapacks.animation.cem.CemLoader;
import com.huaxiaosheng.spapacks.platform.Game;
import com.huaxiaosheng.spapacks.platform.Platform;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.regex.Pattern;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

public final class CemContent {
    private static final String[] DIRECTORIES = new String[]{"emf/cem", "optifine/cem"};
    private static final String ENTRY = "player.jem";
    private static CemAnimation wide;
    private static CemAnimation slim;
    private static boolean told;
    private static final Pattern FRESH_ANIMATIONS = Pattern.compile("fresh[\\s_+\\-]*anim|freshlx|(^|[^a-z0-9])fa([^a-z0-9]|$)", 2);
    static final String PLAYER_EXTENSION_URL = "https://modrinth.com/resourcepack/fa-player-extension";

    private CemContent() {
    }

    static void install() {
        Platform.onResourceReload(Game.id("spa_packs", "cem"), CemContent::reload);
        Platform.onClientTick(client -> {
            boolean missing = ModConfig.on() && ModConfig.get().cemEnabled && wide == null && !PunchyMoves.drives();
            if (!missing) {
                told = false;
            } else {
                Screen open = Game.screen(client);
                if (!told && (open == null || open instanceof TitleScreen || open instanceof PauseScreen)) {
                    told = true;
                    Game.setScreen(client, prompt(client, open));
                }
            }
        });
    }

    private static Screen prompt(Minecraft client, Screen back) {
        Component name = Component.literal("Fresh Animations: Player Extension").withStyle(ChatFormatting.BLUE, ChatFormatting.UNDERLINE);
        return new ConfirmScreen(
            open -> {
                if (open) {
                    ConfirmLinkScreen.confirmLinkNow(back, URI.create("https://modrinth.com/resourcepack/fa-player-extension"), true);
                } else {
                    Game.setScreen(client, back);
                }
            },
            Component.translatable("spa_packs.cem.need_fa.title"),
            Component.translatable("spa_packs.cem.need_fa", name),
            Component.translatable("spa_packs.cem.need_fa.open"),
            Component.translatable("spa_packs.cem.need_fa.close")
        );
    }

    static boolean isFreshAnimations(String pack, String jem) {
        return pack != null && FRESH_ANIMATIONS.matcher(pack).find() || jem != null && jem.toLowerCase(Locale.ROOT).contains("freshlx");
    }

    private static void reload(ResourceManager manager) {
        CemContent.Adopted body = load(manager, "player.jem", false);
        CemContent.Adopted bodySlim = load(manager, "player_slim.jem", false);
        CemAnimation loadedWide = body == null ? null : body.animation();
        CemAnimation loadedSlim = bodySlim == null ? null : bodySlim.animation();
        wide = loadedWide != null ? loadedWide : loadedSlim;
        slim = loadedSlim != null ? loadedSlim : loadedWide;
        if (wide == null) {
            SpaPacks.LOGGER.info("[cem] Fresh Animations is not enabled: the vanilla model keeps vanilla's movement");
        }

        CemContent.Adopted face = load(manager, "player.jem", true);
        FaceOverlay.reload(face != null ? face : body);
    }

    public static boolean full() {
        return true;
    }

    public static CemAnimation animation(boolean slimArms) {
        return slimArms ? slim : wide;
    }

    private static CemContent.Adopted load(ResourceManager manager, String entry, boolean face) {
        for (String directory : DIRECTORIES) {
            List<Resource> stack = manager.getResourceStack(Game.id("minecraft", directory + "/" + entry));

            for (int i = stack.size() - 1; i >= 0; i--) {
                Resource resource = stack.get(i);
                String jem = read(resource);
                if (jem != null) {
                    String pack = resource.sourcePackId();
                    Function<String, String> siblings = file -> sibling(manager, directory, file, pack);

                    try {
                        CemAnimation loaded = CemLoader.load(
                            jem, siblings, warning -> SpaPacks.LOGGER.warn("[cem] {} in pack {}: {}", new Object[]{entry, pack, warning})
                        );
                        if (!face && loaded.drivesLocomotion() && !isFreshAnimations(pack, jem)) {
                            SpaPacks.LOGGER.info("[cem] {}/{} from pack {} is not Fresh Animations: not used", new Object[]{directory, entry, pack});
                        } else if (face ? isFace(loaded, jem) : loaded.drivesLocomotion()) {
                            SpaPacks.LOGGER
                                .info(
                                    "[cem] {} {}/{} from pack {} ({} expressions)",
                                    new Object[]{face ? "face" : "playing", directory, entry, pack, loaded.assignments()}
                                );
                            return new CemContent.Adopted(loaded, jem, siblings, pack);
                        }
                    } catch (RuntimeException e) {
                        SpaPacks.LOGGER.error("[cem] cannot read {}/{} from pack {}", new Object[]{directory, entry, pack, e});
                    }
                }
            }
        }

        return null;
    }

    private static boolean isFace(CemAnimation animation, String jem) {
        return !animation.drivesLocomotion() && (animation.drivesFace() || jem.contains("player_face"));
    }

    private static String sibling(ResourceManager manager, String directory, String file, String pack) {
        Identifier id = file.contains(":")
            ? Identifier.tryParse(file)
            : Identifier.tryBuild("minecraft", !file.startsWith("optifine/") && !file.startsWith("emf/") ? directory + "/" + file : file);
        if (id == null) {
            return null;
        }

        List<Resource> stack = manager.getResourceStack(id);

        for (int i = stack.size() - 1; i >= 0; i--) {
            if (stack.get(i).sourcePackId().equals(pack)) {
                return read(stack.get(i));
            }
        }

        return stack.isEmpty() ? null : read(stack.get(stack.size() - 1));
    }

    private static String read(Resource resource) {
        try (InputStream in = resource.open()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    record Adopted(CemAnimation animation, String jem, Function<String, String> siblings, String pack) {
    }
}
