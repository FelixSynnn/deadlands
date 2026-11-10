package com.deadlands.client.render.vehicle;

import com.deadlands.Deadlands;
import com.deadlands.vehicle.VehicleEntity;
import com.deadlands.vehicle.VehicleKind;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * Draws a vehicle: the kind's rig ({@link VehicleGeometry}) in one of four textures (faded, rusted,
 * clean, or burned out) with see-through glass, so the seats and whoever is in them show. Wheels
 * turn with the distance covered, the front pair follows the steering, wheels are missing where
 * tires are gone, and the front doors swing when someone gets in or out.
 *
 * <p>Over that come up to three more passes of the same model: dents and broken glass once the
 * body is worn (two stages), growth on a vehicle that has stood too long, and the head and tail
 * lights, drawn at full brightness, when they are on.
 */
public class VehicleRenderer extends EntityRenderer<VehicleEntity, VehicleRenderer.State> {
	private static final String[] FINISHES = {"faded", "rusted", "clean"};
	/** Blocks of travel per radian of wheel turn (an average wheel is about 0.37 blocks in radius). */
	private static final float WHEEL_RADIUS = 0.37F;
	private static final int FULL_BRIGHT = 0xF000F0;

	/** What the model needs from one vehicle, copied each frame. */
	public static class State extends EntityRenderState {
		public float yRot;
		public float wheelSpin;
		public float steer;
		public float tires = 100.0F;
		public float doorOpen;
		public boolean lights;
		public boolean overgrown;
		public int damage;
		public Identifier texture;
	}

	/** The rig with its wheels and doors found by name. */
	public static class Model extends EntityModel<State> {
		private final List<ModelPart> wheels = new ArrayList<>();
		private final List<ModelPart> frontWheels = new ArrayList<>();
		private final @Nullable ModelPart doorLeft;
		private final @Nullable ModelPart doorRight;

		public Model(ModelPart root) {
			super(root, RenderTypes::entityTranslucent);
			ModelPart chassis = root.getChild("chassis");
			for (String axle : new String[] {"f", "m", "r"}) {
				for (String side : new String[] {"l", "r"}) {
					String name = "wheel_" + axle + side;
					if (chassis.hasChild(name)) {
						ModelPart wheel = chassis.getChild(name);
						wheels.add(wheel);
						if (axle.equals("f")) {
							frontWheels.add(wheel);
						}
					}
				}
			}
			this.doorLeft = chassis.hasChild("door_l") ? chassis.getChild("door_l") : null;
			this.doorRight = chassis.hasChild("door_r") ? chassis.getChild("door_r") : null;
		}

		@Override
		public void setupAnim(State state) {
			super.setupAnim(state);
			int fitted = Math.round(state.tires / 100.0F * wheels.size());
			for (int i = 0; i < wheels.size(); i++) {
				ModelPart wheel = wheels.get(i);
				wheel.xRot = -state.wheelSpin / WHEEL_RADIUS;
				// Wheels come off from the back: the list runs front to rear.
				wheel.visible = i < fitted;
			}
			for (ModelPart wheel : frontWheels) {
				wheel.yRot = state.steer * 0.45F;
			}
			if (doorLeft != null) {
				doorLeft.yRot = state.doorOpen * 1.1F;
			}
			if (doorRight != null) {
				doorRight.yRot = -state.doorOpen * 1.1F;
			}
		}
	}

	private final Model model;
	private final Identifier[] textures = new Identifier[FINISHES.length];
	private final Identifier burned;
	private final Identifier[] damage = new Identifier[2];
	private final Identifier overgrown;
	private final Identifier lights;

	public VehicleRenderer(EntityRendererProvider.Context context, VehicleKind kind) {
		super(context);
		this.model = new Model(context.bakeLayer(layer(kind)));
		this.shadowRadius = kind.width() * 0.6F;
		String base = "textures/entity/vehicle/" + kind.id() + "_";
		for (int i = 0; i < FINISHES.length; i++) {
			textures[i] = Deadlands.id(base + FINISHES[i] + ".png");
		}
		this.burned = Deadlands.id(base + "burned.png");
		this.damage[0] = Deadlands.id(base + "damage_1.png");
		this.damage[1] = Deadlands.id(base + "damage_2.png");
		this.overgrown = Deadlands.id(base + "overgrown.png");
		this.lights = Deadlands.id(base + "lights.png");
	}

	public static ModelLayerLocation layer(VehicleKind kind) {
		return new ModelLayerLocation(Deadlands.id("vehicle_" + kind.id()), "main");
	}

	/** Register the model layer of every kind. */
	public static void registerLayers() {
		register(VehicleKind.SEDAN, VehicleGeometry::sedan);
		register(VehicleKind.PICKUP, VehicleGeometry::pickup);
		register(VehicleKind.SUV, VehicleGeometry::suv);
		register(VehicleKind.MILITARY_JEEP, VehicleGeometry::military_jeep);
		register(VehicleKind.AMBULANCE, VehicleGeometry::ambulance);
		register(VehicleKind.POLICE_CAR, VehicleGeometry::police_car);
		register(VehicleKind.DELIVERY_VAN, VehicleGeometry::delivery_van);
		register(VehicleKind.BUS, VehicleGeometry::bus);
		register(VehicleKind.FIRE_TRUCK, VehicleGeometry::fire_truck);
		register(VehicleKind.CARGO_TRUCK, VehicleGeometry::cargo_truck);
		register(VehicleKind.MOTORCYCLE, VehicleGeometry::motorcycle);
		register(VehicleKind.TRACTOR, VehicleGeometry::tractor);
	}

	private static void register(VehicleKind kind, Supplier<LayerDefinition> geometry) {
		EntityModelLayerRegistry.registerModelLayer(layer(kind), geometry::get);
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(VehicleEntity vehicle, State state, float partialTick) {
		super.extractRenderState(vehicle, state, partialTick);
		state.yRot = vehicle.getYRot(partialTick);
		state.wheelSpin = vehicle.wheelSpin();
		state.steer = vehicle.steer();
		state.tires = vehicle.tires();
		state.doorOpen = vehicle.doorOpen();
		state.lights = vehicle.lights();
		state.overgrown = vehicle.overgrown();
		state.damage = vehicle.damageLevel();
		state.texture = vehicle.burned() ? burned : textures[vehicle.paint()];
		// The tow rope: from this vehicle's front to the back of the one pulling it.
		net.minecraft.world.entity.Entity lead = vehicle.towedById() < 0 ? null : vehicle.level().getEntity(vehicle.towedById());
		if (lead instanceof VehicleEntity puller) {
			if (state.leashStates == null || state.leashStates.size() != 1) {
				state.leashStates = new java.util.ArrayList<>(1);
				state.leashStates.add(new EntityRenderState.LeashState());
			}
			EntityRenderState.LeashState rope = state.leashStates.getFirst();
			net.minecraft.world.phys.Vec3 front = net.minecraft.world.phys.Vec3.directionFromRotation(0.0F, vehicle.getYRot(partialTick))
				.scale(vehicle.kind().width() * 0.9).add(0.0, 0.5, 0.0);
			net.minecraft.world.phys.Vec3 back = net.minecraft.world.phys.Vec3.directionFromRotation(0.0F, puller.getYRot(partialTick))
				.scale(-puller.kind().width() * 0.9).add(0.0, 0.5, 0.0);
			rope.offset = front;
			rope.start = vehicle.getPosition(partialTick).add(front);
			rope.end = puller.getPosition(partialTick).add(back);
			net.minecraft.core.BlockPos at = net.minecraft.core.BlockPos.containing(rope.start);
			rope.startBlockLight = vehicle.level().getBrightness(net.minecraft.world.level.LightLayer.BLOCK, at);
			rope.endBlockLight = rope.startBlockLight;
			rope.startSkyLight = vehicle.level().getBrightness(net.minecraft.world.level.LightLayer.SKY, at);
			rope.endSkyLight = rope.startSkyLight;
			rope.slack = false;
		} else {
			state.leashStates = null;
		}
	}

	@Override
	public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
		pose.pushPose();
		pose.mulPose(Axis.YP.rotationDegrees(180.0F - state.yRot));
		pose.scale(-1.0F, -1.0F, 1.0F);
		pose.translate(0.0F, -1.501F, 0.0F);
		collector.submitModel(model, state, pose, model.renderType(state.texture), state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor, null);
		if (state.damage > 0) {
			collector.order(1).submitModel(model, state, pose, RenderTypes.entityCutoutNoCull(damage[state.damage - 1]), state.lightCoords,
				OverlayTexture.NO_OVERLAY, state.outlineColor, null);
		}
		if (state.overgrown) {
			collector.order(2).submitModel(model, state, pose, RenderTypes.entityCutoutNoCull(overgrown), state.lightCoords, OverlayTexture.NO_OVERLAY,
				state.outlineColor, null);
		}
		if (state.lights) {
			collector.order(3).submitModel(model, state, pose, RenderTypes.eyes(lights), FULL_BRIGHT, OverlayTexture.NO_OVERLAY, state.outlineColor, null);
		}
		pose.popPose();
		super.submit(state, pose, collector, camera);
	}

	/** For logs and debugging. */
	@Override
	public String toString() {
		return String.format(Locale.ROOT, "VehicleRenderer[%s]", textures[0]);
	}
}
