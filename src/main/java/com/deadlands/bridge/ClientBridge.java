package com.deadlands.bridge;

import com.deadlands.entity.InfectedEntity;

/**
 * Lets common code (items, blocks, entities) trigger client-only behaviour without referencing
 * client classes, which do not exist on a dedicated server. The client entrypoint installs the
 * handlers; on a dedicated server they stay no-ops.
 */
public final class ClientBridge {
	public interface Handler {
		void openJournal();
	}

	/** Client-side infected hooks: voices chosen per listener, and visual effects. */
	public interface InfectedHandler {
		void tick(InfectedEntity entity);

		void ambient(InfectedEntity entity);
	}

	/** Client-side story screens. */
	public interface StoryHandler {
		void openDocument(String loreId, java.util.List<String> args);

		void openRadio();
	}

	private static volatile StoryHandler storyHandler = new StoryHandler() {
		@Override
		public void openDocument(String loreId, java.util.List<String> args) {
		}

		@Override
		public void openRadio() {
		}
	};

	private static volatile Handler handler = () -> {
	};

	private static volatile InfectedHandler infectedHandler = new InfectedHandler() {
		@Override
		public void tick(InfectedEntity entity) {
		}

		@Override
		public void ambient(InfectedEntity entity) {
		}
	};

	private ClientBridge() {
	}

	public static void install(Handler newHandler) {
		handler = newHandler;
	}

	public static void installInfected(InfectedHandler newHandler) {
		infectedHandler = newHandler;
	}

	public static void openJournal() {
		handler.openJournal();
	}

	public static void installStory(StoryHandler newHandler) {
		storyHandler = newHandler;
	}

	public static void openDocument(String loreId, java.util.List<String> args) {
		storyHandler.openDocument(loreId, args);
	}

	public static void openRadio() {
		storyHandler.openRadio();
	}

	/** Client effects common code can ask for. */
	public interface EffectsHandler {
		void recoil(float degrees);

		/** A firearm is being reloaded for {@code ticks} (the gun dips out of view and back). */
		default void reload(int ticks) {
		}
	}

	private static volatile EffectsHandler effects = degrees -> {
	};

	public static void installEffects(EffectsHandler handler) {
		effects = handler;
	}

	/** Kick the local player's view up by about {@code degrees} (a firearm's recoil). */
	public static void recoil(float degrees) {
		effects.recoil(degrees);
	}

	public static void reload(int ticks) {
		effects.reload(ticks);
	}

	public static void infectedTick(InfectedEntity entity) {
		infectedHandler.tick(entity);
	}

	public static void infectedAmbient(InfectedEntity entity) {
		infectedHandler.ambient(entity);
	}
}
