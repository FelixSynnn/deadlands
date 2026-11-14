package com.huaxiaosheng.spapacks.platform;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

public final class MixinAudit implements IMixinConfigPlugin {
    private static final Logger LOGGER = LoggerFactory.getLogger("SmoothPlayerAnimations");
    private static final String INJECTION = "Lorg/spongepowered/asm/mixin/injection/";
    private static final Set<String> CHECKED = Set.of(
        "Lorg/spongepowered/asm/mixin/injection/Inject;",
        "Lorg/spongepowered/asm/mixin/injection/Redirect;",
        "Lorg/spongepowered/asm/mixin/injection/ModifyArg;",
        "Lorg/spongepowered/asm/mixin/injection/ModifyArgs;",
        "Lorg/spongepowered/asm/mixin/injection/ModifyVariable;",
        "Lorg/spongepowered/asm/mixin/injection/ModifyConstant;"
    );

    public void onLoad(String mixinPackage) {
    }

    public String getRefMapperConfig() {
        return null;
    }

    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return true;
    }

    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    public List<String> getMixins() {
        return null;
    }

    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        try {
            for (String handler : missing(targetClass, mixinInfo.getClassNode(0))) {
                LOGGER.warn(
                    "{} could not hook into {} ({}): another mod probably changed that spot. Only that part of the mod is off; the game goes on.",
                    new Object[]{mixinInfo.getName(), targetClassName, handler}
                );
            }
        } catch (Throwable e) {
            LOGGER.debug("Mixin audit of {} failed", targetClassName, e);
        }
    }

    static List<String> missing(ClassNode target, ClassNode mixin) {
        List<String> missing = new ArrayList<>();

        for (MethodNode handler : mixin.methods) {
            if (injector(handler)) {
                String merged = null;

                for (MethodNode m : target.methods) {
                    if (m.name.equals(handler.name) || m.name.endsWith("$" + handler.name)) {
                        merged = m.name;
                        break;
                    }
                }

                if (merged == null || !called(target, merged)) {
                    missing.add(handler.name);
                }
            }
        }

        return missing;
    }

    private static boolean injector(MethodNode m) {
        for (List<AnnotationNode> list : List.of(nonNull(m.visibleAnnotations), nonNull(m.invisibleAnnotations))) {
            for (AnnotationNode a : list) {
                if (CHECKED.contains(a.desc)) {
                    return true;
                }
            }
        }

        return false;
    }

    private static List<AnnotationNode> nonNull(List<AnnotationNode> list) {
        return list == null ? List.of() : list;
    }

    private static boolean called(ClassNode target, String name) {
        for (MethodNode m : target.methods) {
            for (AbstractInsnNode insn : m.instructions) {
                if (insn instanceof MethodInsnNode call && call.name.equals(name) && call.owner.equals(target.name)) {
                    return true;
                }
            }
        }

        return false;
    }
}
