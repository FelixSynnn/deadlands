package com.deadlands.client.render;

import com.deadlands.Deadlands;
import com.deadlands.npc.NpcCharacter;
import com.deadlands.npc.NpcEntity;
import com.deadlands.npc.NpcRole;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/**
 * Draws a survivor with Minecraft's own player model and a 64x64 skin: one of three per role, or
 * the recurring character's. A character's skin is the shipped one unless there is a file at
 * {@code config/deadlands/skins/<id>.png}, which is loaded once and used instead; nothing is ever
 * looked up by username. A character's skin may have slim arms (read from the skin: a slim skin
 * leaves the outer columns of the arm's back face empty), drawn with the slim player model.
 */
public class NpcRenderer extends HumanoidMobRenderer<NpcEntity, NpcRenderer.State, HumanoidModel<NpcRenderer.State>> {
	/** A humanoid with the skin to draw it in. */
	public static class State extends HumanoidRenderState {
		public Identifier skin = Deadlands.id("textures/entity/survivor/settler_0.png");
		public boolean slim;
		/** The character's 3D gear texture (Phase 3), or null for none. */
		public Identifier gear;
	}

	private record CharacterSkin(Identifier texture, boolean slim) {
	}

	private static final Map<NpcCharacter, CharacterSkin> CHARACTER_SKINS = new EnumMap<>(NpcCharacter.class);
	private final HumanoidModel<State> wideModel;
	private final HumanoidModel<State> slimModel;

	public NpcRenderer(EntityRendererProvider.Context context) {
		super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER)), 0.5F);
		this.wideModel = getModel();
		this.slimModel = new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_SLIM));
		addLayer(new com.deadlands.client.render.character.CharacterGearLayer(this));
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(NpcEntity npc, State state, float partialTick) {
		super.extractRenderState(npc, state, partialTick);
		NpcCharacter character = npc.character();
		if (character != null) {
			CharacterSkin skin = characterSkin(character);
			state.skin = skin.texture();
			state.slim = skin.slim();
			state.gear = character == NpcCharacter.ITZBREADLOAF ? Deadlands.id("textures/entity/survivor/character/itzbreadloaf_gear.png") : null;
		} else {
			state.skin = roleSkin(npc.role(), npc.skin());
			state.slim = false;
			state.gear = null;
		}
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		model = state.slim ? slimModel : wideModel; // the layers (held items) read the same model
		super.submit(state, poseStack, collector, camera);
	}

	@Override
	public Identifier getTextureLocation(State state) {
		return state.skin;
	}

	private static Identifier roleSkin(NpcRole role, int look) {
		return Deadlands.id("textures/entity/survivor/" + role.id() + "_" + look + ".png");
	}

	/** The character's skin: a file the player provided, if there is one and it loads; otherwise the shipped one. */
	private static CharacterSkin characterSkin(NpcCharacter character) {
		return CHARACTER_SKINS.computeIfAbsent(character, c -> {
			Identifier shipped = Deadlands.id("textures/entity/survivor/character/" + c.id() + ".png");
			CharacterSkin builtIn = new CharacterSkin(shipped, slimResource(shipped));
			Path file = FabricLoader.getInstance().getConfigDir().resolve("deadlands").resolve("skins").resolve(c.id() + ".png");
			if (!Files.isRegularFile(file)) {
				return builtIn;
			}
			try (InputStream in = Files.newInputStream(file)) {
				NativeImage image = NativeImage.read(in);
				if (image.getWidth() != 64 || image.getHeight() != 64) {
					Deadlands.LOGGER.warn("Skin {} is {}x{}, not 64x64; using the built-in one", file, image.getWidth(), image.getHeight());
					image.close();
					return builtIn;
				}
				boolean slim = slim(image);
				Identifier id = Deadlands.id("skins/" + c.id());
				Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "deadlands skin " + c.id(), image));
				return new CharacterSkin(id, slim);
			} catch (Exception e) {
				Deadlands.LOGGER.warn("Could not read skin {}: {}", file, e.toString());
				return builtIn;
			}
		});
	}

	/** A slim-armed skin leaves the two outer columns of the right arm's back face empty. */
	private static boolean slim(NativeImage image) {
		return (image.getPixel(54, 20) >>> 24) == 0 && (image.getPixel(55, 31) >>> 24) == 0;
	}

	private static boolean slimResource(Identifier texture) {
		return Minecraft.getInstance().getResourceManager().getResource(texture).map(resource -> {
			try (InputStream in = resource.open(); NativeImage image = NativeImage.read(in)) {
				return image.getWidth() == 64 && image.getHeight() == 64 && slim(image);
			} catch (Exception e) {
				return false;
			}
		}).orElse(false);
	}
}
