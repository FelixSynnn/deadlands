package vectorwing.farmersdelight.refabricated.mlconfigs.integration.yacl;

import dev.isxander.yacl3.api.Binding;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.OptionGroup;
import dev.isxander.yacl3.api.YetAnotherConfigLib;
import dev.isxander.yacl3.api.Option.Builder;
import dev.isxander.yacl3.api.controller.ColorControllerBuilder;
import dev.isxander.yacl3.api.controller.DoubleSliderControllerBuilder;
import dev.isxander.yacl3.api.controller.EnumControllerBuilder;
import dev.isxander.yacl3.api.controller.FloatSliderControllerBuilder;
import dev.isxander.yacl3.api.controller.IntegerSliderControllerBuilder;
import dev.isxander.yacl3.api.controller.StringControllerBuilder;
import dev.isxander.yacl3.api.controller.TickBoxControllerBuilder;
import dev.isxander.yacl3.api.controller.ValueFormatter;
import dev.isxander.yacl3.gui.controllers.LabelController;
import java.awt.Color;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.apache.commons.lang3.text.WordUtils;
import org.jspecify.annotations.Nullable;
import vectorwing.farmersdelight.refabricated.mlconfigs.fabric.ConfigEntry;
import vectorwing.farmersdelight.refabricated.mlconfigs.fabric.ConfigSubCategory;
import vectorwing.farmersdelight.refabricated.mlconfigs.fabric.FabricConfigHolder;
import vectorwing.farmersdelight.refabricated.mlconfigs.fabric.values.BoolConfigValue;
import vectorwing.farmersdelight.refabricated.mlconfigs.fabric.values.ColorConfigValue;
import vectorwing.farmersdelight.refabricated.mlconfigs.fabric.values.ConfigValue;
import vectorwing.farmersdelight.refabricated.mlconfigs.fabric.values.DoubleConfigValue;
import vectorwing.farmersdelight.refabricated.mlconfigs.fabric.values.EnumConfigValue;
import vectorwing.farmersdelight.refabricated.mlconfigs.fabric.values.FloatConfigValue;
import vectorwing.farmersdelight.refabricated.mlconfigs.fabric.values.IntConfigValue;
import vectorwing.farmersdelight.refabricated.mlconfigs.fabric.values.JsonConfigValue;
import vectorwing.farmersdelight.refabricated.mlconfigs.fabric.values.ListStringConfigValue;
import vectorwing.farmersdelight.refabricated.mlconfigs.fabric.values.ObjectConfigValue;
import vectorwing.farmersdelight.refabricated.mlconfigs.fabric.values.StringConfigValue;

public class YACLCompat {
    private static final ValueFormatter<Double> DOUBLE_FORMATTER = value -> Component.nullToEmpty(String.format("%,.4f", value).replaceAll("[  ]", " "));
    private static final ValueFormatter<Float> FLOAT_FORMATTER = value -> Component.nullToEmpty(String.format("%,.4f", value).replaceAll("[  ]", " "));

    public static Screen makeScreen(Screen parent, FabricConfigHolder spec) {
        return makeScreen(parent, spec, null);
    }

    public static Screen makeScreen(Screen parent, FabricConfigHolder spec, @Nullable Identifier background) {
        spec.forceLoad();
        dev.isxander.yacl3.api.YetAnotherConfigLib.Builder builder = YetAnotherConfigLib.createBuilder();
        builder.title(spec.getReadableName());
        builder.save(spec::saveConfig);

        for (ConfigEntry en : spec.getMainEntry().getEntries()) {
            if (en instanceof ConfigSubCategory c) {
                dev.isxander.yacl3.api.ConfigCategory.Builder mainCat = ConfigCategory.createBuilder()
                    .name(Component.translatableWithFallback(c.getName(), WordUtils.capitalizeFully(c.getName())));

                for (ConfigEntry entry : c.getEntries()) {
                    if (entry instanceof ConfigSubCategory subCat) {
                        dev.isxander.yacl3.api.OptionGroup.Builder subBuilder = OptionGroup.createBuilder()
                            .name(Component.translatable(subCat.getName()))
                            .collapsed(true);
                        addEntriesRecursive(mainCat, subBuilder, subCat);
                        mainCat.group(subBuilder.build());
                    } else {
                        mainCat.option(buildEntry(entry));
                    }
                }

                builder.category(mainCat.build());
            }
        }

        return builder.build().generateScreen(parent);
    }

    private static void addEntriesRecursive(
        dev.isxander.yacl3.api.ConfigCategory.Builder builder, dev.isxander.yacl3.api.OptionGroup.Builder subCategoryBuilder, ConfigSubCategory c
    ) {
        for (ConfigEntry entry : c.getEntries()) {
            if (entry instanceof ConfigSubCategory cc) {
                dev.isxander.yacl3.api.OptionGroup.Builder scb = OptionGroup.createBuilder()
                    .name(Component.translatable(entry.getName()))
                    .description(OptionDescription.of(new Component[]{Component.literal("Unsupported")}));
                addEntriesRecursive(builder, subCategoryBuilder, cc);
            } else {
                subCategoryBuilder.option(buildEntry(entry));
            }
        }
    }

    private static Option<?> buildEntry(ConfigEntry entry) {
        if (entry instanceof ColorConfigValue col) {
            Builder<Color> e = Option.<Color>createBuilder()
                .name(col.getTranslation())
                .binding(new Color(col.getDefaultValue()), () -> new Color(col.get()), v -> col.set(v.getRGB()))
                .controller(ColorControllerBuilder::create);
            Component description = col.getDescription();
            if (description != null) {
                e.description(OptionDescription.of(new Component[]{description}));
            }

            return e.build();
        } else if (entry instanceof IntConfigValue ic) {
            Builder<Integer> e = Option.<Integer>createBuilder()
                .name(ic.getTranslation())
                .binding(ic.getDefaultValue(), ic, ic::set)
                .controller(o -> ((IntegerSliderControllerBuilder)IntegerSliderControllerBuilder.create(o).range(ic.getMin(), ic.getMax())).step(1));
            Component description = ic.getDescription();
            if (description != null) {
                e.description(OptionDescription.of(new Component[]{description}));
            }

            return e.build();
        } else if (entry instanceof DoubleConfigValue dc) {
            Builder<Double> e = Option.<Double>createBuilder()
                .name(dc.getTranslation())
                .binding(dc.getDefaultValue(), dc, dc::set)
                .controller(
                    o -> ((DoubleSliderControllerBuilder)((DoubleSliderControllerBuilder)DoubleSliderControllerBuilder.create(o)
                                .range(dc.getMin(), dc.getMax()))
                            .step(1.0E-4))
                        .formatValue(DOUBLE_FORMATTER)
                );
            Component description = dc.getDescription();
            if (description != null) {
                e.description(OptionDescription.of(new Component[]{description}));
            }

            return e.build();
        } else if (entry instanceof FloatConfigValue fc) {
            Builder<Float> e = Option.<Float>createBuilder()
                .name(fc.getTranslation())
                .binding(fc.getDefaultValue(), fc, fc::set)
                .controller(
                    o -> ((FloatSliderControllerBuilder)((FloatSliderControllerBuilder)FloatSliderControllerBuilder.create(o).range(fc.getMin(), fc.getMax()))
                            .step(1.0E-4F))
                        .formatValue(FLOAT_FORMATTER)
                );
            Component description = fc.getDescription();
            if (description != null) {
                e.description(OptionDescription.of(new Component[]{description}));
            }

            return e.build();
        } else if (entry instanceof StringConfigValue sc) {
            Builder<String> e = Option.createBuilder(String.class)
                .name(sc.getTranslation())
                .binding(sc.getDefaultValue(), sc, sc::set)
                .controller(StringControllerBuilder::create);
            Component description = sc.getDescription();
            if (description != null) {
                e.description(OptionDescription.of(new Component[]{description}));
            }

            return e.build();
        } else if (entry instanceof BoolConfigValue bc) {
            Builder<Boolean> e = Option.<Boolean>createBuilder()
                .name(bc.getTranslation())
                .binding(bc.getDefaultValue(), bc, bc::set)
                .controller(TickBoxControllerBuilder::create);
            Component description = bc.getDescription();
            if (description != null) {
                e.description(OptionDescription.of(new Component[]{description}));
            }

            return e.build();
        } else if (entry instanceof EnumConfigValue<?> ec) {
            return addEnum(ec);
        } else if (entry instanceof ListStringConfigValue<?> lc) {
            Builder<Component> e = Option.<Component>createBuilder()
                .name(lc.getTranslation())
                .binding(Binding.immutable(Component.literal("String Lists are not supported")))
                .customController(LabelController::new);
            Component description = lc.getDescription();
            if (description != null) {
                e.description(OptionDescription.of(new Component[]{description}));
            }

            return e.build();
        } else {
            if (!(entry instanceof JsonConfigValue) && !(entry instanceof ObjectConfigValue)) {
                throw new UnsupportedOperationException("unknown entry: " + entry.getClass().getName());
            }

            ConfigValue lc = (ConfigValue)entry;
            Builder<Component> e = Option.<Component>createBuilder()
                .name(lc.getTranslation())
                .binding(Binding.immutable(Component.literal("Object fields are not supported. Edit the config manually instead")))
                .customController(LabelController::new);
            Component description = lc.getDescription();
            if (description != null) {
                e.description(OptionDescription.of(new Component[]{description}));
            }

            return e.build();
        }
    }

    private static <T extends Enum<T>> Option<T> addEnum(EnumConfigValue<T> ec) {
        Builder<T> e = Option.createBuilder(ec.getEnumClass())
            .name(ec.getTranslation())
            .binding(ec.getDefaultValue(), ec, ec::set)
            .controller(EnumControllerBuilder::create);
        Component description = ec.getDescription();
        if (description != null) {
            e.description(OptionDescription.of(new Component[]{description}));
        }

        return e.build();
    }
}
