package com.deadlands.client.firstperson;

import com.deadlands.client.sound.MusicDirector;
import com.deadlands.config.ConfigManager;
import com.deadlands.item.FirearmItem;
import com.deadlands.item.FlashlightItem;
import com.deadlands.item.MeleeItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;

/**
 * Phase 4: first-person animation for (almost) everything the hands do, layered over Minecraft's own
 * hand pose ({@code ItemInHandRendererMixin} wraps each hand in {@link #apply}).
 *
 * <ul>
 *   <li><b>Melee</b>, by weapon: blunt weapons (bat, pipe, crowbar, wrench) wind up and come down in a
 *       heavy overhead arc; blades (machete, axe) slash diagonally; knives stab forward. A whoosh as the
 *       swing starts (only the swinger hears it).</li>
 *   <li><b>Firearms</b>: a kick on firing (bigger for long guns), a dip out of view while reloading, and
 *       the gun carried low across the body while sprinting.</li>
 *   <li><b>Drinking</b> tips the bottle up to the mouth with swallowing bobs; <b>eating</b> chews with a
 *       little side to side; <b>bandaging</b> (medkits, bandages, splints: the "brush" use) wraps in
 *       circles.</li>
 *   <li><b>Picking something up</b>: a quick grab; <b>opening a door</b>: a push.</li>
 *   <li><b>The body</b>: a dip on landing from a fall, a flinch when hit, hand over hand on ladders, lower
 *       when crouching, a lift when jumping, hands that lag a little when turning, slow breathing (faster
 *       after sprinting), a tremor when hurt or chased, and a newly drawn item rising into view.</li>
 *   <li><b>Inspect</b> (the I key) turns the held weapon or flashlight over in the hand.</li>
 * </ul>
 *
 * Everything except inspect (which the player asked for) is scaled by {@code horror.firstPersonMotion}.
 */
public final class FirstPersonMotion {
	private static final Set<String> KNIVES = Set.of("survival_knife", "hunting_knife");
	private static final Set<String> BLADES = Set.of("machete", "rusty_machete", "fire_axe");
	private static final int INSPECT_TICKS = 60;

	private static ItemStack lastMain = ItemStack.EMPTY;
	private static float raise = 1.0F;
	private static float prevRaise = 1.0F;
	private static float breath;
	private static float exertion;
	private static int age;
	private static float kick;
	private static int reloadLeft;
	private static int reloadTotal = 1;
	private static int inspectLeft;
	private static int grabLeft;
	private static int pushLeft;
	private static float landing;
	private static float flinch;
	private static float sprint;
	private static float crouch;
	private static float lagYaw;
	private static float lagPitch;
	private static float lastYaw;
	private static float lastPitch;
	private static boolean wasOnGround = true;
	private static double fallStart;
	private static int lastHurt;
	private static int lastCount = -1;

	private FirstPersonMotion() {
	}

	public static void tick(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		if (player == null || minecraft.isPaused()) {
			return;
		}
		age++;
		ItemStack main = player.getMainHandItem();
		if (!ItemStack.isSameItem(main, lastMain)) {
			lastMain = main.copy();
			raise = 0.0F;
			prevRaise = 0.0F;
		}
		while (com.deadlands.client.input.DLKeyMappings.INSPECT.consumeClick()) {
			if (inspectable(main)) {
				inspectLeft = INSPECT_TICKS;
			}
		}
		inspectLeft = Math.max(0, inspectLeft - 1);
		reloadLeft = Math.max(0, reloadLeft - 1);
		grabLeft = Math.max(0, grabLeft - 1);
		pushLeft = Math.max(0, pushLeft - 1);
		kick *= 0.6F;
		landing *= 0.8F;
		flinch *= 0.75F;
		if (player.swinging && player.swingTime == 0 && main.getItem() instanceof MeleeItem) {
			player.playSound(com.deadlands.registry.DLSounds.WEAPON_SWING.value(), 0.7F, 0.9F + player.getRandom().nextFloat() * 0.25F);
		}
		// Landing: how far the fall was decides the dip.
		if (!player.onGround() && wasOnGround) {
			fallStart = player.getY();
		} else if (player.onGround() && !wasOnGround) {
			landing = Math.min(1.0F, landing + (float) Math.max(0.0, fallStart - player.getY()) / 6.0F);
		}
		wasOnGround = player.onGround();
		// A flinch when hit.
		if (player.hurtTime > 0 && player.hurtTime > lastHurt) {
			flinch = 1.0F;
		}
		lastHurt = player.hurtTime;
		// A grab when something new reaches the inventory.
		int count = 0;
		for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
			count += stack.getCount();
		}
		if (lastCount >= 0 && count > lastCount && minecraft.screen == null) {
			grabLeft = 8;
		}
		lastCount = count;
		prevRaise = raise;
		raise = Math.min(1.0F, raise + 0.1F);
		exertion = Mth.approach(exertion, player.isSprinting() ? 1.0F : 0.0F, player.isSprinting() ? 0.02F : 0.005F);
		sprint = Mth.approach(sprint, player.isSprinting() && !player.isUsingItem() ? 1.0F : 0.0F, 0.12F);
		crouch = Mth.approach(crouch, player.isCrouching() ? 1.0F : 0.0F, 0.15F);
		breath += 0.045F + 0.06F * exertion;
		// Turning: the hands trail the view a little and catch up.
		float dYaw = Mth.wrapDegrees(player.getYRot() - lastYaw);
		float dPitch = player.getXRot() - lastPitch;
		lastYaw = player.getYRot();
		lastPitch = player.getXRot();
		lagYaw = Mth.clamp(lagYaw * 0.7F + dYaw * 0.3F, -12.0F, 12.0F);
		lagPitch = Mth.clamp(lagPitch * 0.7F + dPitch * 0.3F, -10.0F, 10.0F);
	}

	/** Moves this hand's pose before Minecraft draws the arm and item. */
	public static void apply(PoseStack pose, InteractionHand hand, ItemStack stack, float swing, float partialTick) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		if (player == null) {
			return;
		}
		float s = (float) ConfigManager.get().horror.firstPersonMotion;
		boolean main = hand == InteractionHand.MAIN_HAND;
		if (main && inspectLeft > 0) {
			inspect(pose, partialTick);
		}
		if (s <= 0.0F) {
			return;
		}
		float t = age + partialTick;
		if (main) {
			float r = Mth.lerp(partialTick, prevRaise, raise);
			float lower = (1.0F - r) * (1.0F - r);
			pose.translate(0.0F, -0.45F * lower * s, 0.0F);
			pose.mulPose(Axis.XP.rotationDegrees(-25.0F * lower * s));
		}
		// Turning lag, crouch, jump and landing, flinch: the whole arm.
		pose.mulPose(Axis.YP.rotationDegrees(-lagYaw * 0.35F * s));
		pose.mulPose(Axis.XP.rotationDegrees(-lagPitch * 0.35F * s));
		pose.translate(0.0F, (-0.04F * crouch - 0.12F * landing - 0.08F * flinch) * s, 0.0F);
		if (!player.onGround() && !player.onClimbable()) {
			pose.translate(0.0F, 0.025F * s, 0.0F);
		}
		if (flinch > 0.01F) {
			pose.mulPose(Axis.ZP.rotationDegrees(6.0F * flinch * s * (main ? 1.0F : -1.0F)));
		}
		// Climbing: hand over hand.
		if (player.onClimbable() && !player.onGround()) {
			float climb = Mth.sin((float) player.getY() * 6.0F + (main ? 0.0F : Mth.PI)); // one reach per rung or so
			pose.translate(0.0F, 0.12F * climb * s, -0.05F * s);
		}
		if (main) {
			melee(pose, stack, swing, s);
			firearm(pose, stack, partialTick, s);
			if (grabLeft > 0 && !player.isUsingItem()) {
				float g = Mth.sin((8 - grabLeft + partialTick) / 8.0F * Mth.PI);
				pose.translate(-0.05F * g * s, -0.12F * g * s, -0.1F * g * s);
				pose.mulPose(Axis.XP.rotationDegrees(-20.0F * g * s));
			}
			if (pushLeft > 0) {
				float p = Mth.sin((10 - pushLeft + partialTick) / 10.0F * Mth.PI);
				pose.translate(-0.15F * p * s, 0.05F * p * s, -0.25F * p * s);
				pose.mulPose(Axis.YP.rotationDegrees(20.0F * p * s));
			}
		}
		use(pose, player, hand, stack, partialTick, s);
		// Breathing.
		float b = (float) Math.sin(breath + partialTick * 0.05F);
		pose.translate(0.0F, (0.006F + 0.01F * exertion) * b * s, 0.0F);
		pose.mulPose(Axis.ZP.rotationDegrees(0.4F * b * s));
		// Tremor when hurt or chased.
		float hurt = Mth.clamp(1.0F - player.getHealth() / (player.getMaxHealth() * 0.4F), 0.0F, 1.0F);
		float fear = Math.max(hurt, MusicDirector.chased() ? 0.6F : 0.0F);
		if (fear > 0.0F) {
			float jx = (float) (Math.sin(t * 1.9) * Math.sin(t * 3.7)) * 0.004F * fear * s;
			float jy = (float) (Math.sin(t * 2.3 + 1.0) * Math.sin(t * 4.1)) * 0.004F * fear * s;
			pose.translate(jx, jy, 0.0F);
		}
	}

	/** Swing arcs by weapon kind, on top of Minecraft's own swing. */
	private static void melee(PoseStack pose, ItemStack stack, float swing, float s) {
		if (!(stack.getItem() instanceof MeleeItem) || swing <= 0.0F) {
			return;
		}
		String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
		float arc = Mth.sin(swing * Mth.PI);
		if (KNIVES.contains(id)) {
			// Stab: straight forward and back.
			pose.translate(0.0F, 0.05F * arc * s, -0.35F * arc * s);
			pose.mulPose(Axis.XP.rotationDegrees(-15.0F * arc * s));
		} else if (BLADES.contains(id)) {
			// Slash: wind up high to the right, cut down and across.
			float sweep = Mth.sin((swing - 0.15F) * Mth.PI * 1.2F);
			pose.translate(-0.15F * sweep * s, 0.1F * arc * s, -0.1F * arc * s);
			pose.mulPose(Axis.YP.rotationDegrees(35.0F * sweep * s));
			pose.mulPose(Axis.ZP.rotationDegrees(-30.0F * sweep * s));
		} else {
			// Blunt: lift back over the shoulder, then a heavy overhead blow.
			float windup = swing < 0.3F ? swing / 0.3F : 1.0F - (swing - 0.3F) / 0.7F;
			pose.translate(0.0F, 0.15F * windup * s - 0.1F * arc * s, 0.05F * windup * s);
			pose.mulPose(Axis.XP.rotationDegrees((35.0F * windup - 45.0F * arc) * s));
		}
	}

	private static void firearm(PoseStack pose, ItemStack stack, float partialTick, float s) {
		if (!(stack.getItem() instanceof FirearmItem)) {
			return;
		}
		if (kick > 0.01F) {
			pose.translate(0.0F, 0.0F, 0.05F * kick * s);
			pose.mulPose(Axis.XP.rotationDegrees(4.0F * kick * s));
		}
		if (reloadLeft > 0) {
			float p = 1.0F - (reloadLeft - partialTick) / reloadTotal;
			float dip = Mth.sin(Mth.clamp(p, 0.0F, 1.0F) * Mth.PI);
			pose.translate(0.0F, -0.35F * dip, 0.0F);
			pose.mulPose(Axis.XP.rotationDegrees(-30.0F * dip));
			pose.mulPose(Axis.ZP.rotationDegrees(15.0F * dip));
		}
		// Sprinting: carried low across the body.
		if (sprint > 0.01F) {
			pose.translate(-0.1F * sprint * s, -0.15F * sprint * s, 0.05F * sprint * s);
			pose.mulPose(Axis.YP.rotationDegrees(35.0F * sprint * s));
			pose.mulPose(Axis.XP.rotationDegrees(-20.0F * sprint * s));
		}
	}

	/** Drinking, eating and bandaging, while the item is in use. */
	private static void use(PoseStack pose, LocalPlayer player, InteractionHand hand, ItemStack stack, float partialTick, float s) {
		if (!player.isUsingItem() || player.getUsedItemHand() != hand || stack.isEmpty()) {
			return;
		}
		int total = Math.max(1, stack.getUseDuration(player));
		float used = total - player.getUseItemRemainingTicks() + partialTick;
		float in = Mth.clamp(used / 6.0F, 0.0F, 1.0F); // bring it up over a third of a second
		ItemUseAnimation animation = stack.getUseAnimation();
		if (animation == ItemUseAnimation.DRINK) {
			float swallow = Mth.sin(used * 0.9F) * 0.5F + 0.5F;
			pose.translate(-0.08F * in * s, 0.08F * in * s, 0.0F);
			pose.mulPose(Axis.XP.rotationDegrees((35.0F + 6.0F * swallow) * in * s));
		} else if (animation == ItemUseAnimation.EAT) {
			float chew = Mth.sin(used * 1.4F);
			pose.translate(0.02F * chew * in * s, 0.03F * Math.abs(chew) * in * s, 0.0F);
			pose.mulPose(Axis.ZP.rotationDegrees(5.0F * chew * in * s));
		} else if (animation == ItemUseAnimation.BRUSH) {
			// Wrapping a bandage: small circles toward the middle.
			float a = used * 0.6F;
			pose.translate((-0.15F + 0.05F * Mth.cos(a)) * in * s, (0.05F + 0.04F * Mth.sin(a)) * in * s, -0.05F * in * s);
			pose.mulPose(Axis.ZP.rotationDegrees(20.0F * Mth.sin(a) * in * s));
		}
	}

	private static void inspect(PoseStack pose, float partialTick) {
		float p = 1.0F - (inspectLeft - partialTick) / INSPECT_TICKS;
		float hold = Mth.sin(Mth.clamp(p, 0.0F, 1.0F) * Mth.PI);
		pose.translate(-0.12F * hold, 0.1F * hold, 0.0F);
		pose.mulPose(Axis.YP.rotationDegrees(-55.0F * hold));
		pose.mulPose(Axis.ZP.rotationDegrees(20.0F * hold * Mth.sin(p * Mth.TWO_PI)));
	}

	public static void kick(float degrees) {
		kick = Math.min(1.5F, kick + degrees / 4.0F);
	}

	public static void reload(int ticks) {
		reloadTotal = Math.max(1, ticks);
		reloadLeft = reloadTotal;
		inspectLeft = 0;
	}

	/** Opening a door (from a use-block callback): the hand pushes. */
	public static void push() {
		pushLeft = 10;
	}

	private static boolean inspectable(ItemStack stack) {
		return stack.getItem() instanceof MeleeItem || stack.getItem() instanceof FirearmItem || stack.getItem() instanceof FlashlightItem;
	}

	public static void reset() {
		lastMain = ItemStack.EMPTY;
		raise = 1.0F;
		prevRaise = 1.0F;
		exertion = 0.0F;
		lastCount = -1;
		reloadLeft = 0;
		inspectLeft = 0;
	}
}
