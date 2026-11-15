package com.huaxiaosheng.spapacks.client;

import com.huaxiaosheng.spapacks.platform.Platform;
import java.lang.reflect.Method;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

final class EtfFaceCompat {
    private static final boolean PRESENT = Platform.isModLoaded("entity_texture_features");
    private static boolean resolved;
    private static Object manager;
    private static Method playerTexture;
    private static Method baseTexture;
    private static Method emissiveTexture;
    private static Method apiVariant;
    private static Method apiEmissive;

    private EtfFaceCompat() {
    }

    static EtfFaceCompat.Skin resolve(AbstractClientPlayer player, Identifier vanilla) {
        if (!PRESENT) {
            return new EtfFaceCompat.Skin(vanilla, null);
        }

        resolveMethods();
        Identifier base = vanilla;
        Identifier emissive = null;

        try {
            if (manager != null) {
                Object texture = playerTexture.invoke(manager, player, vanilla);
                if (texture != null) {
                    if (baseTexture.invoke(texture, player) instanceof Identifier id) {
                        base = id;
                    }

                    if (emissiveTexture.invoke(texture) instanceof Identifier id) {
                        emissive = id;
                    }
                }
            }

            if (vanilla.equals(base) && apiVariant != null && apiVariant.invoke(null, player, vanilla) instanceof Identifier id) {
                base = id;
            }

            if (emissive == null && apiEmissive != null && apiEmissive.invoke(null, player, vanilla) instanceof Identifier id) {
                emissive = id;
            }
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            return new EtfFaceCompat.Skin(vanilla, null);
        }

        return new EtfFaceCompat.Skin(base, emissive);
    }

    private static void resolveMethods() {
        if (!resolved) {
            resolved = true;

            try {
                Class<?> managerClass = Class.forName("traben.entity_texture_features.features.ETFManager");
                Class<?> textureClass = Class.forName("traben.entity_texture_features.features.player.ETFPlayerTexture");
                playerTexture = managerClass.getMethod("getPlayerTexture", Player.class, Identifier.class);
                baseTexture = textureClass.getMethod("getBaseTextureIdentifierOrNullForVanilla", Player.class);
                emissiveTexture = textureClass.getMethod("getBaseTextureEmissiveIdentifierOrNullForNone");
                manager = managerClass.getMethod("getInstance").invoke(null);
            } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
                manager = null;
            }

            try {
                Class<?> api = Class.forName("traben.entity_texture_features.ETFApi");
                apiVariant = api.getMethod("getCurrentETFVariantTextureOfEntity", Entity.class, Identifier.class);
                apiEmissive = api.getMethod("getCurrentETFEmissiveTextureOfEntityOrNull", Entity.class, Identifier.class);
            } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
                apiVariant = null;
                apiEmissive = null;
            }
        }
    }

    record Skin(Identifier base, Identifier emissive) {
    }
}
