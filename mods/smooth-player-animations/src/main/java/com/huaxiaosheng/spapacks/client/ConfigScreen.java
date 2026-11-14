package com.huaxiaosheng.spapacks.client;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import me.shedaniel.clothconfig2.api.Requirement;
import me.shedaniel.clothconfig2.gui.entries.BooleanListEntry;
import me.shedaniel.clothconfig2.gui.entries.MultiElementListEntry;
import me.shedaniel.clothconfig2.gui.entries.NestedListListEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;

public final class ConfigScreen {
    private ConfigScreen() {
    }

    public static Screen create(Screen parent) {
        ModConfig config = ModConfig.get();
        boolean wasEnabled = config.enabled;
        ConfigBuilder builder = ConfigBuilder.create()
            .setParentScreen(parent)
            .setTitle(Component.translatable("spa_packs.config.title"))
            .setSavingRunnable(() -> SafeMode.run("saving the settings", () -> {
                ModConfig.save();
                if (config.enabled != wasEnabled) {
                    Minecraft.getInstance().reloadResourcePacks();
                }
            }));
        ConfigEntryBuilder entries = builder.entryBuilder();
        ConfigCategory general = builder.getOrCreateCategory(Component.translatable("spa_packs.config.general"));
        general.addEntry(
            entries.startBooleanToggle(Component.translatable("spa_packs.config.enabled"), config.enabled)
                .setDefaultValue(true)
                .setTooltip(Component.translatable("spa_packs.config.enabled.tooltip"))
                .setSaveConsumer(value -> config.enabled = value)
                .build()
        );
        BooleanListEntry cem = entries.startBooleanToggle(Component.translatable("spa_packs.config.cem.enabled"), config.cemEnabled)
            .setDefaultValue(false)
            .setTooltip(Component.translatable("spa_packs.config.cem.enabled.tooltip"))
            .setSaveConsumer(value -> config.cemEnabled = value)
            .build();
        general.addEntry(cem);
        general.addEntry(
            entries.startBooleanToggle(Component.translatable("spa_packs.config.cape_physics"), config.capePhysics)
                .setDefaultValue(true)
                .setTooltip(Component.translatable("spa_packs.config.cape_physics.tooltip"))
                .setSaveConsumer(value -> config.capePhysics = value)
                .build()
        );
        general.addEntry(
            entries.startBooleanToggle(Component.translatable("spa_packs.config.lantern_physics"), config.lanternPhysics)
                .setDefaultValue(true)
                .setTooltip(Component.translatable("spa_packs.config.lantern_physics.tooltip"))
                .setSaveConsumer(value -> config.lanternPhysics = value)
                .build()
        );
        general.addEntry(
            entries.startBooleanToggle(Component.translatable("spa_packs.config.foot_ik"), config.footIk)
                .setDefaultValue(true)
                .setTooltip(Component.translatable("spa_packs.config.foot_ik.tooltip"))
                .setSaveConsumer(value -> config.footIk = value)
                .build()
        );
        general.addEntry(
            entries.startBooleanToggle(Component.translatable("spa_packs.config.hand_reach"), config.handReach)
                .setDefaultValue(true)
                .setTooltip(Component.translatable("spa_packs.config.hand_reach.tooltip"))
                .setSaveConsumer(value -> config.handReach = value)
                .build()
        );
        ConfigCategory compat = builder.getOrCreateCategory(Component.translatable("spa_packs.config.compat"));
        compat.addEntry(entries.startTextDescription(Component.translatable("spa_packs.config.items.description")).build());
        String held = heldItem();
        compat.addEntry(
            entries.startTextDescription(
                    held.isEmpty() ? Component.translatable("spa_packs.config.items.held.none") : Component.translatable("spa_packs.config.items.held", held)
                )
                .build()
        );
        compat.addEntry(itemList(entries, config));
        animations(builder.getOrCreateCategory(Component.translatable("spa_packs.config.animations")), entries, config, cem);
        return builder.build();
    }

    private static void animations(ConfigCategory category, ConfigEntryBuilder entries, ModConfig config, BooleanListEntry cem) {
        switches(
            category,
            entries,
            AnimationToggles.BENT,
            "",
            Requirement.isFalse(cem),
            new LinkedHashSet<>(config.disabledAnimations),
            list -> config.disabledAnimations = list,
            new LinkedHashSet<>(config.enabledAnimations),
            list -> config.enabledAnimations = list
        );
        switches(
            category,
            entries,
            AnimationToggles.CEM,
            ".cem",
            Requirement.isTrue(cem),
            new LinkedHashSet<>(config.disabledAnimationsCem),
            list -> config.disabledAnimationsCem = list,
            new LinkedHashSet<>(config.enabledAnimationsCem),
            list -> config.enabledAnimationsCem = list
        );
    }

    private static void switches(
        ConfigCategory category,
        ConfigEntryBuilder entries,
        List<AnimationToggles.Switch> set,
        String mode,
        Requirement shown,
        Set<String> off,
        Consumer<List<String>> saveOff,
        Set<String> on,
        Consumer<List<String>> saveOn
    ) {
        category.addEntry(
            entries.startTextDescription(Component.translatable("spa_packs.config.animations" + mode + ".description")).setDisplayRequirement(shown).build()
        );
        String group = null;

        for (AnimationToggles.Switch s : set) {
            if (!s.group().equals(group)) {
                group = s.group();
                category.addEntry(
                    entries.startTextDescription(Component.translatable("spa_packs.config.animations.group." + group)).setDisplayRequirement(shown).build()
                );
            }

            String key = s.id();
            boolean value = s.defaultOn() ? !off.contains(key) : on.contains(key);
            String tooltip = "spa_packs.anim" + mode + "." + key + ".tooltip";
            if (!Language.getInstance().has(tooltip)) {
                tooltip = "spa_packs.anim." + key + ".tooltip";
            }

            category.addEntry(
                entries.startBooleanToggle(Component.translatable("spa_packs.anim." + key), value)
                    .setDefaultValue(s.defaultOn())
                    .setTooltip(Component.translatable(tooltip))
                    .setDisplayRequirement(shown)
                    .setSaveConsumer(now -> {
                        if (s.defaultOn()) {
                            if (now) {
                                off.remove(key);
                            } else {
                                off.add(key);
                            }

                            saveOff.accept(new ArrayList<>(off));
                        } else {
                            if (now) {
                                on.add(key);
                            } else {
                                on.remove(key);
                            }

                            saveOn.accept(new ArrayList<>(on));
                        }
                    })
                    .build()
            );
        }
    }

    private static NestedListListEntry<ModConfig.ItemAnimation, MultiElementListEntry<ModConfig.ItemAnimation>> itemList(
        ConfigEntryBuilder entries, ModConfig config
    ) {
        List<ModConfig.ItemAnimation> rows = new ArrayList<>();

        for (ModConfig.ItemAnimation a : config.itemAnimations) {
            if (a != null) {
                rows.add(a.copy());
            }
        }

        return new NestedListListEntry<>(
            Component.translatable("spa_packs.config.items"),
            rows,
            true,
            () -> Optional.of(new Component[]{Component.translatable("spa_packs.config.items.tooltip")}),
            list -> config.itemAnimations = new ArrayList<>(list),
            ArrayList::new,
            entries.getResetButtonKey(),
            true,
            false,
            (value, list) -> {
                ModConfig.ItemAnimation row = value;
                if (row == null) {
                    row = new ModConfig.ItemAnimation();
                    row.item = heldItem();
                }

                ModConfig.ItemAnimation edited = row;
                ItemChoice current = ItemChoice.of(row.animation);
                List<AbstractConfigListEntry<?>> fields = new ArrayList<>();
                fields.add(
                    entries.startStrField(Component.translatable("spa_packs.config.items.item"), row.item)
                        .setDefaultValue("")
                        .setTooltip(Component.translatable("spa_packs.config.items.item.tooltip"))
                        .setSaveConsumer(s -> edited.item = s == null ? "" : s.trim())
                        .build()
                );
                fields.add(
                    entries.startSelector(
                            Component.translatable("spa_packs.config.items.animation"), ItemChoice.values(), current == null ? ItemChoice.SWORD : current
                        )
                        .setNameProvider(ConfigScreen::label)
                        .setDefaultValue(ItemChoice.SWORD)
                        .setTooltip(Component.translatable("spa_packs.config.items.animation.tooltip"))
                        .setSaveConsumer(choice -> edited.animation = choice.key)
                        .build()
                );
                Component title = row.item.isBlank() ? Component.translatable("spa_packs.config.items.new") : Component.literal(row.item);
                return new MultiElementListEntry<>(title, row, fields, true);
            }
        );
    }

    private static Component label(ItemChoice choice) {
        return Component.translatable("spa_packs.config.items.type." + choice.type)
            .append(" · ")
            .append(Component.translatable("spa_packs.config.items.choice." + choice.key.replace(':', '.')));
    }

    private static String heldItem() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player != null && !player.getMainHandItem().isEmpty() ? BuiltInRegistries.ITEM.getKey(player.getMainHandItem().getItem()).toString() : "";
    }
}
