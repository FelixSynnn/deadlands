package com.huaxiaosheng.spapacks.client;

import com.huaxiaosheng.spapacks.platform.Platform;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.Entity;

final class FiguraCompat {
    private static final boolean PRESENT = Platform.isModLoaded("figura");
    private static boolean resolved;
    private static Method getAvatar;
    private static Field luaRuntime;
    private static Field vanillaModel;
    private static Field headPart;
    private static Method checkVisible;
    private static Field permissions;
    private static Object vanillaModelEdit;
    private static Method permissionsGet;

    private FiguraCompat() {
    }

    static boolean hidesVanillaHead(AbstractClientPlayer player) {
        if (!PRESENT) {
            return false;
        }

        resolve();
        if (getAvatar == null) {
            return false;
        }

        try {
            Object avatar = getAvatar.invoke(null, player);
            if (avatar != null && editAllowed(avatar)) {
                Object runtime = luaRuntime.get(avatar);
                Object model = runtime == null ? null : vanillaModel.get(runtime);
                Object head = model == null ? null : headPart.get(model);
                return head != null && !(Boolean)checkVisible.invoke(head);
            } else {
                return false;
            }
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            return false;
        }
    }

    private static boolean editAllowed(Object avatar) {
        if (permissions == null) {
            return true;
        }

        try {
            Object bag = permissions.get(avatar);
            return bag == null || permissionsGet.invoke(bag, vanillaModelEdit) instanceof Integer level && level == 1;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            return true;
        }
    }

    private static void resolve() {
        if (!resolved) {
            resolved = true;

            try {
                Class<?> avatarType = Class.forName("org.figuramc.figura.avatar.Avatar");
                getAvatar = Class.forName("org.figuramc.figura.avatar.AvatarManager").getMethod("getAvatar", Entity.class);
                luaRuntime = avatarType.getDeclaredField("luaRuntime");
                luaRuntime.setAccessible(true);
                vanillaModel = luaRuntime.getType().getDeclaredField("vanilla_model");
                vanillaModel.setAccessible(true);
                headPart = Class.forName("org.figuramc.figura.lua.api.vanilla_model.VanillaModelAPI").getDeclaredField("HEAD");
                headPart.setAccessible(true);
                checkVisible = Class.forName("org.figuramc.figura.lua.api.vanilla_model.VanillaPart").getMethod("checkVisible");
            } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
                getAvatar = null;
                return;
            }

            try {
                Class<?> perm = Class.forName("org.figuramc.figura.permissions.Permissions");
                permissions = Class.forName("org.figuramc.figura.avatar.Avatar").getDeclaredField("permissions");
                permissions.setAccessible(true);
                vanillaModelEdit = perm.getField("VANILLA_MODEL_EDIT").get(null);
                permissionsGet = permissions.getType().getMethod("get", perm);
            } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
                permissions = null;
            }
        }
    }
}
