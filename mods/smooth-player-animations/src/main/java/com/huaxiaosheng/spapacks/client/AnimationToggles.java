package com.huaxiaosheng.spapacks.client;

import com.huaxiaosheng.spapacks.anim.graph.Plan;
import com.huaxiaosheng.spapacks.anim.graph.Sample;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public final class AnimationToggles {
    public static final List<AnimationToggles.Switch> BENT = List.of(
        vanilla("move", "ground"),
        vanilla("walk_back", "ground"),
        vanilla("sneak", "ground"),
        vanilla("crawl", "ground"),
        vanilla("jump", "air"),
        vanilla("fall", "air"),
        vanilla("swim", "water"),
        vanilla("tread", "water"),
        vanilla("climb", "climb"),
        vanilla("glide", "glide"),
        vanilla("fly", "fly"),
        vanilla("horse", "ride"),
        vanilla("boat", "ride"),
        vanilla("cart", "ride"),
        addition("draw_weapon", "hands"),
        addition("draw_item", "hands"),
        vanilla("punch", "items"),
        vanilla("sword", "items"),
        vanilla("eat", "items"),
        vanilla("hold", "items")
    );
    public static final List<AnimationToggles.Switch> CEM = List.of(
        new AnimationToggles.Switch("sprint_stop", "ground", false, true),
        addition("idle_special", "ground"),
        addition("held_idle", "ground"),
        vanilla("horse", "ride"),
        addition("bounce", "bounce"),
        addition("swap_hands", "hands"),
        addition("draw_weapon", "hands"),
        addition("draw_item", "hands"),
        vanilla("punch", "items"),
        vanilla("sword", "items"),
        vanilla("pickaxe", "items"),
        vanilla("axe", "items"),
        vanilla("shovel", "items"),
        vanilla("hoe", "items"),
        vanilla("bow", "items"),
        vanilla("crossbow", "items"),
        vanilla("shield", "items"),
        vanilla("trident_throw", "items"),
        vanilla("trident_attack", "items"),
        vanilla("eat", "items"),
        vanilla("hold", "items")
    );
    private static final int BODY = 0;
    private static final int HEAD = 1;
    private static final int RIGHT_ARM = 2;
    private static final int LEFT_ARM = 3;
    private static final int RIGHT_LEG = 4;
    private static final int LEFT_LEG = 5;
    private static final Map<String, AnimationToggles.Switch> BENT_BY_ID = byId(BENT);
    private static final Map<String, AnimationToggles.Switch> CEM_BY_ID = byId(CEM);

    private static AnimationToggles.Switch vanilla(String id, String group) {
        return new AnimationToggles.Switch(id, group, true, false);
    }

    private static AnimationToggles.Switch addition(String id, String group) {
        return new AnimationToggles.Switch(id, group, true, true);
    }

    private AnimationToggles() {
    }

    static String movement(String environment, String state, boolean cem) {
        if (environment == null) {
            return null;
        }

        if (cem) {
            return switch (environment) {
                case "riding" -> "mount".equals(state) ? "horse" : null;
                case "bounce" -> "bounce";
                case "ground" -> {
                    if (state == null) {
                        yield null;
                    } else {
                        switch (state) {
                            case "sprint_stop":
                                yield "sprint_stop";
                            case "fidget":
                                yield "idle_special";
                            case "afk_item_intro":
                            case "afk_item":
                                yield "held_idle";
                            default:
                                yield null;
                        }
                    }
                }
                default -> null;
            };
        } else {
            return switch (environment) {
                case "riding" -> {
                    if (state == null) {
                        yield null;
                    } else {
                        switch (state) {
                            case "mount":
                                yield "horse";
                            case "cart":
                                yield "cart";
                            case "boat_turn_left":
                            case "boat_turn_right":
                            case "boat_row_back":
                            case "boat_row":
                            case "boat_idle":
                                yield "boat";
                            default:
                                yield null;
                        }
                    }
                }
                case "flying" -> "fly";
                case "gliding" -> "glide";
                case "water" -> "tread".equals(state) ? "tread" : (state == null ? null : "swim");
                case "climb" -> "climb";
                case "bounce" -> "fall";
                case "step_off" -> "move";
                case "air" -> {
                    if (state == null) {
                        yield null;
                    } else {
                        switch (state) {
                            case "jump":
                            case "jump_arc":
                                yield "jump";
                            default:
                                yield "fall";
                        }
                    }
                }
                case "ground" -> {
                    if (state == null) {
                        yield null;
                    } else {
                        switch (state) {
                            case "sneak":
                                yield "sneak";
                            case "crawl":
                                yield "crawl";
                            case "fidget":
                                yield "idle_special";
                            case "afk_item_intro":
                            case "afk_item":
                                yield "held_idle";
                            case "sprint_stop":
                                yield null;
                            default:
                                yield "move";
                        }
                    }
                }
                default -> null;
            };
        }
    }

    static String movement(Plan plan, boolean cem) {
        String id = movement(plan.environment, plan.state, cem);
        return "move".equals(id) && walkingBack(plan) ? "walk_back" : id;
    }

    private static boolean walkingBack(Plan plan) {
        float back = 0.0F;

        for (Sample s : plan.base) {
            if ("walk_back".equals(s.clip)) {
                back += s.weight;
            }
        }

        return back >= 0.5F;
    }

    static String action(String action, String clip, boolean cem) {
        if (clip != null) {
            String byClip = switch (clip) {
                case "punch", "interact" -> "punch";
                case "tool_sword_swing", "sword_swing_first", "sword_swing_second" -> "sword";
                case "tool_pickaxe_swing" -> "pickaxe";
                case "tool_axe_swing" -> "axe";
                case "tool_shovel_swing" -> "shovel";
                case "tool_hoe_swing" -> "hoe";
                case "tool_trident_swing" -> "trident_attack";
                case "use_bow", "release_bow" -> "bow";
                case "use_crossbow", "release_crossbow", "hold_crossbow" -> "crossbow";
                case "use_shield" -> "shield";
                case "use_trident_throw", "release_trident" -> "trident_throw";
                case "eating", "drinking" -> "eat";
                case "swap_hands" -> "swap_hands";
                case "draw_weapon" -> "draw_weapon";
                case "draw_item" -> "draw_item";
                default -> null;
            };
            if (byClip != null) {
                return byClip;
            }
        }

        if (action == null) {
            return null;
        }

        return switch (action) {
            case "land" -> cem ? null : "jump";
            case "swing" -> "punch";
            case "sword_combo", "hold_sword", "hold_sword_move" -> "sword";
            case "crossbow_ready" -> "crossbow";
            case "hold_main", "hold_off", "hold_both" -> "hold";
            case "draw" -> "draw_item";
            case "swap_hands" -> "swap_hands";
            default -> null;
        };
    }

    private static Map<String, AnimationToggles.Switch> byId(List<AnimationToggles.Switch> set) {
        Map<String, AnimationToggles.Switch> out = new HashMap<>();

        for (AnimationToggles.Switch s : set) {
            if (out.put(s.id(), s) != null) {
                throw new IllegalStateException("two animation switches named " + s.id());
            }
        }

        return Map.copyOf(out);
    }

    static AnimationToggles.Switch offSwitch(String id, boolean cem) {
        AnimationToggles.Switch s = id == null ? null : (cem ? CEM_BY_ID : BENT_BY_ID).get(id);
        if (s == null) {
            return null;
        }

        ModConfig.Switches set = ModConfig.switches(cem);
        boolean off = s.defaultOn() ? set.off().contains(id) : !set.on().contains(id);
        return off ? s : null;
    }

    static boolean drawOff(String clip) {
        boolean cem = PlayerAnimations.cemSide();
        return offSwitch(action("draw", clip, cem), cem) != null;
    }

    static boolean skipped(String environment, String state) {
        AnimationToggles.Switch s = offSwitch(movement(environment, state, PlayerAnimations.cemSide()), PlayerAnimations.cemSide());
        return s != null && s.skip();
    }

    static void apply(Plan plan) {
        if (plan != null) {
            plan.clearToggles();
            boolean cem = PlayerAnimations.cemSide();
            Iterator<Plan.Layer> it = plan.layers.iterator();

            while (it.hasNext()) {
                Plan.Layer l = it.next();
                AnimationToggles.Switch off = offSwitch(action(l.action, l.sample.clip, cem), cem);
                if (off != null || !(l.weight <= 0.0F) && !l.additive) {
                    boolean[] parts = l.additive ? null : and(parts(l.mask, l.sample.mirror), parts(l.override, l.sample.mirror));
                    if (off != null) {
                        it.remove();
                        if (parts != null && !off.skip()) {
                            for (int p = 0; p < 6; p++) {
                                if (parts[p]) {
                                    plan.actionOff[p] = 1.0F;
                                }
                            }
                        }
                    } else if (parts != null) {
                        for (int p = 0; p < 6; p++) {
                            plan.kept[p] = plan.kept[p] | parts[p];
                        }
                    }
                }
            }

            AnimationToggles.Switch off = plan.cem && cem ? null : offSwitch(movement(plan, cem), cem);
            if (off != null && !off.skip()) {
                plan.giveBaseToVanilla();
            }
        }
    }

    static boolean[] parts(String region, boolean mirror) {
        boolean[] p = new boolean[6];
        if (region == null) {
            return p;
        }

        switch (region) {
            case "full":
                Arrays.fill(p, true);
                break;
            case "upper":
                p[0] = p[1] = p[2] = p[3] = true;
                break;
            case "arms":
                p[2] = p[3] = true;
                break;
            case "arms_head":
                p[1] = p[2] = p[3] = true;
                break;
            case "right_arm":
                p[mirror ? 3 : 2] = true;
                break;
            case "left_arm":
                p[mirror ? 2 : 3] = true;
                break;
            case "head":
                p[1] = true;
                break;
            case "spine":
                p[0] = true;
                break;
            case "legs":
                p[4] = p[5] = true;
                break;
            case "lower":
                p[0] = p[4] = p[5] = true;
        }

        return p;
    }

    private static boolean[] and(boolean[] a, boolean[] b) {
        boolean[] out = new boolean[6];

        for (int i = 0; i < 6; i++) {
            out[i] = a[i] && b[i];
        }

        return out;
    }

    public record Switch(String id, String group, boolean defaultOn, boolean skip) {
    }
}
