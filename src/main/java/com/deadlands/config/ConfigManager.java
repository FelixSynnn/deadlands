package com.deadlands.config;

import com.deadlands.Deadlands;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Loads, validates, saves and publishes the DEADLANDS configuration.
 *
 * <p>The live config is an immutable-by-convention snapshot: readers call {@link #get()} each time
 * and never cache it, writers build an edited copy and hand it to {@link #replace}. The file is
 * JSON with {@code //} comments (read leniently), regenerated with comments whenever settings are
 * added, missing or invalid.
 */
public final class ConfigManager {
	public static final int CURRENT_VERSION = 4;
	public static final String FILE_NAME = "deadlands.jsonc";

	private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
	private static final List<Consumer<DeadlandsConfig>> LISTENERS = new CopyOnWriteArrayList<>();
	private static volatile DeadlandsConfig current = new DeadlandsConfig();
	private static List<Section> layout;

	private ConfigManager() {
	}

	/** One block of the config file / one settings category. */
	public record Section(String key, Field field, List<Entry> entries) {
		public Object instance(DeadlandsConfig config) {
			try {
				return field.get(config);
			} catch (IllegalAccessException e) {
				throw new IllegalStateException(e);
			}
		}

		public String translationKey() {
			return "config.deadlands.section." + key;
		}
	}

	/** One setting. */
	public record Entry(String sectionKey, String key, Field field, ConfigEntry meta) {
		public Class<?> type() {
			return field.getType();
		}

		public Object get(Object sectionInstance) {
			try {
				return field.get(sectionInstance);
			} catch (IllegalAccessException e) {
				throw new IllegalStateException(e);
			}
		}

		public void set(Object sectionInstance, Object value) {
			try {
				field.set(sectionInstance, coerce(this, value));
			} catch (IllegalAccessException e) {
				throw new IllegalStateException(e);
			}
		}

		public String translationKey() {
			return "config.deadlands." + sectionKey + "." + key;
		}

		public boolean isPending() {
			return !meta.pendingMilestone().isEmpty();
		}
	}

	// ------------------------------------------------------------------------------------ access

	public static DeadlandsConfig get() {
		return current;
	}

	public static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
	}

	/** Called (on the caller's thread) whenever a new config snapshot is published. */
	public static void addListener(Consumer<DeadlandsConfig> listener) {
		LISTENERS.add(listener);
	}

	public static synchronized List<Section> layout() {
		if (layout == null) {
			layout = buildLayout();
		}
		return layout;
	}

	public static DeadlandsConfig copy(DeadlandsConfig config) {
		return GSON.fromJson(GSON.toJson(config), DeadlandsConfig.class);
	}

	// ------------------------------------------------------------------------------------ lifecycle

	/** Read the file (creating it with defaults if absent) and publish the result. */
	public static synchronized void load() {
		Path file = path();
		DeadlandsConfig config = new DeadlandsConfig();
		boolean rewrite = true;
		if (Files.exists(file)) {
			try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
				JsonReader json = new JsonReader(reader);
				json.setStrictness(Strictness.LENIENT);
				JsonElement root = JsonParser.parseReader(json);
				if (!root.isJsonObject()) {
					throw new IOException("top level is not an object");
				}
				List<String> problems = new ArrayList<>();
				rewrite = apply(config, root.getAsJsonObject(), problems);
				migrate(config, root.getAsJsonObject());
				problems.forEach(p -> Deadlands.LOGGER.warn("Config {}: {}", FILE_NAME, p));
			} catch (Exception e) {
				Deadlands.LOGGER.error("Could not read {}; using defaults and keeping a copy of the broken file", file, e);
				backup(file);
				config = new DeadlandsConfig();
				rewrite = true;
			}
		}
		config.configVersion = CURRENT_VERSION;
		publish(config);
		if (rewrite) {
			write(config);
		}
	}

	/** Validate, save and publish an edited copy (from the settings screen). */
	public static synchronized void replace(DeadlandsConfig edited) {
		DeadlandsConfig clean = copy(edited);
		for (Section section : layout()) {
			Object instance = section.instance(clean);
			for (Entry entry : section.entries()) {
				entry.set(instance, entry.get(instance));
			}
		}
		clean.configVersion = CURRENT_VERSION;
		publish(clean);
		write(clean);
	}

	private static void publish(DeadlandsConfig config) {
		current = config;
		for (Consumer<DeadlandsConfig> listener : LISTENERS) {
			try {
				listener.accept(config);
			} catch (RuntimeException e) {
				Deadlands.LOGGER.error("Config listener failed", e);
			}
		}
	}

	// ------------------------------------------------------------------------------------ parsing

	/** Parse config text (JSON with comments) without touching the live config. Problems are appended. */
	public static DeadlandsConfig parse(String jsonc, List<String> problems) {
		JsonReader json = new JsonReader(new java.io.StringReader(jsonc));
		json.setStrictness(Strictness.LENIENT);
		JsonElement root = JsonParser.parseReader(json);
		DeadlandsConfig config = new DeadlandsConfig();
		if (root.isJsonObject()) {
			apply(config, root.getAsJsonObject(), problems);
		} else {
			problems.add("top level is not an object");
		}
		return config;
	}

	/**
	 * Older files: values that were the old defaults move to the new ones (version 2: the Aberrant's
	 * damage, 13 to 9). Values the player changed themselves are left alone.
	 */
	static void migrate(DeadlandsConfig config, JsonObject root) {
		JsonElement version = root.get("configVersion");
		int from = version != null && version.isJsonPrimitive() ? version.getAsInt() : 0;
		if (from < 2 && config.asesino.damage == 13.0) {
			config.asesino.damage = 9.0;
		}
		// D.O.T. became a fast predator (the old walking speeds were the defaults before version 3).
		if (from < 3 && config.dot.speed == 0.2) {
			config.dot.speed = 0.26;
		}
		if (from < 3 && config.dot.aggroSpeed == 0.28) {
			config.dot.aggroSpeed = 0.36;
		}
		// 4: no killer is invulnerable, and each can be killed in 30-60 seconds with iron gear (old defaults only).
		if (from < 4) {
			if (config.dot.health == 800.0) {
				config.dot.health = 300.0;
			}
			if (config.dot.damageResistance == 0.75) {
				config.dot.damageResistance = 0.0;
			}
			if (config.dot.damage == 10.0) {
				config.dot.damage = 8.0;
			}
			if (config.guest.health == 300.0) {
				config.guest.health = 260.0;
			}
			if (config.asesino.health == 160.0) {
				config.asesino.health = 260.0;
			}
			if (config.dot.aggroSpeed == 0.36) {
				config.dot.aggroSpeed = 0.37; // about 105% of sprint speed
			}
		}
	}

	/** Copy known values from JSON into the config. Returns true if the file should be rewritten. */
	static boolean apply(DeadlandsConfig config, JsonObject root, List<String> problems) {
		boolean rewrite = false;
		JsonElement version = root.get("configVersion");
		if (version == null || !version.isJsonPrimitive() || version.getAsInt() != CURRENT_VERSION) {
			rewrite = true;
		}
		Set<String> knownSections = new HashSet<>();
		knownSections.add("configVersion");
		for (Section section : layout()) {
			knownSections.add(section.key());
			JsonElement element = root.get(section.key());
			if (element == null || !element.isJsonObject()) {
				rewrite = true;
				continue;
			}
			JsonObject object = element.getAsJsonObject();
			Object instance = section.instance(config);
			Set<String> knownEntries = new HashSet<>();
			for (Entry entry : section.entries()) {
				knownEntries.add(entry.key());
				JsonElement value = object.get(entry.key());
				if (value == null) {
					rewrite = true;
					continue;
				}
				try {
					Object parsed = read(entry, value);
					Object coerced = coerce(entry, parsed);
					if (!coerced.equals(parsed)) {
						problems.add(section.key() + "." + entry.key() + " = " + parsed + " is out of range, using " + coerced);
						rewrite = true;
					}
					entry.field().set(instance, coerced);
				} catch (IllegalArgumentException | IllegalAccessException e) {
					problems.add(section.key() + "." + entry.key() + ": " + e.getMessage() + "; keeping default " + entry.get(instance));
					rewrite = true;
				}
			}
			for (String key : object.keySet()) {
				if (!knownEntries.contains(key)) {
					problems.add("unknown setting " + section.key() + "." + key + " (ignored)");
				}
			}
		}
		for (String key : root.keySet()) {
			if (!knownSections.contains(key)) {
				problems.add("unknown section " + key + " (ignored)");
			}
		}
		return rewrite;
	}

	private static Object read(Entry entry, JsonElement value) {
		Class<?> type = entry.type();
		if (!value.isJsonPrimitive()) {
			throw new IllegalArgumentException("expected a plain value");
		}
		JsonPrimitive primitive = value.getAsJsonPrimitive();
		if (type == boolean.class) {
			if (!primitive.isBoolean()) {
				throw new IllegalArgumentException("expected true or false");
			}
			return primitive.getAsBoolean();
		}
		if (type == int.class || type == double.class) {
			if (!primitive.isNumber()) {
				throw new IllegalArgumentException("expected a number");
			}
			double number = primitive.getAsDouble();
			return type == int.class ? (Object) (int) Math.round(number) : (Object) number;
		}
		if (type == String.class) {
			return primitive.getAsString();
		}
		if (type.isEnum()) {
			String name = primitive.getAsString();
			for (Object constant : type.getEnumConstants()) {
				if (((Enum<?>) constant).name().equalsIgnoreCase(name)) {
					return constant;
				}
			}
			throw new IllegalArgumentException("expected one of " + Arrays.toString(type.getEnumConstants()));
		}
		throw new IllegalStateException("unsupported setting type " + type);
	}

	/** Clamp numbers to the declared range; pass other values through. */
	static Object coerce(Entry entry, Object value) {
		ConfigEntry meta = entry.meta();
		if (entry.type() == int.class) {
			int v = ((Number) value).intValue();
			return (int) Math.max(Math.ceil(meta.min()), Math.min(Math.floor(meta.max()), v));
		}
		if (entry.type() == double.class) {
			double v = ((Number) value).doubleValue();
			if (Double.isNaN(v)) {
				v = meta.min();
			}
			return Math.max(meta.min(), Math.min(meta.max(), v));
		}
		return value;
	}

	// ------------------------------------------------------------------------------------ writing

	private static void write(DeadlandsConfig config) {
		Path file = path();
		try {
			Files.createDirectories(file.getParent());
			Path temp = file.resolveSibling(FILE_NAME + ".tmp");
			Files.writeString(temp, toJsonc(config), StandardCharsets.UTF_8);
			try {
				Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (IOException atomicUnsupported) {
				Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (IOException e) {
			Deadlands.LOGGER.error("Could not save {}", file, e);
		}
	}

	private static void backup(Path file) {
		try {
			String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
			Files.copy(file, file.resolveSibling(FILE_NAME + ".broken-" + stamp), StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			Deadlands.LOGGER.error("Could not back up {}", file, e);
		}
	}

	public static String toJsonc(DeadlandsConfig config) {
		StringBuilder out = new StringBuilder(8192);
		out.append("// DEADLANDS configuration\n");
		out.append("// Lines starting with // are comments. Out-of-range values are clamped when the game loads.\n");
		out.append("// [client]: only changes your own game. Everything else is decided by the server in multiplayer.\n");
		out.append("// [Milestone N]: read by a system that arrives in that development milestone; no effect yet.\n");
		out.append("{\n\t\"configVersion\": ").append(CURRENT_VERSION).append(",\n");
		List<Section> sections = layout();
		for (int s = 0; s < sections.size(); s++) {
			Section section = sections.get(s);
			Object instance = section.instance(config);
			out.append("\n\t\"").append(section.key()).append("\": {\n");
			List<Entry> entries = section.entries();
			for (int e = 0; e < entries.size(); e++) {
				Entry entry = entries.get(e);
				out.append("\t\t// ").append(describe(entry)).append('\n');
				out.append("\t\t\"").append(entry.key()).append("\": ").append(literal(entry.get(instance)));
				out.append(e + 1 < entries.size() ? ",\n" : "\n");
			}
			out.append("\t}").append(s + 1 < sections.size() ? ",\n" : "\n");
		}
		out.append("}\n");
		return out.toString();
	}

	public static String describe(Entry entry) {
		ConfigEntry meta = entry.meta();
		StringBuilder text = new StringBuilder(meta.comment());
		Class<?> type = entry.type();
		if (type == int.class) {
			text.append(" Range ").append(plain(meta.min())).append(" to ").append(plain(meta.max())).append('.');
		} else if (type == double.class) {
			text.append(" Range ").append(decimal(meta.min())).append(" to ").append(decimal(meta.max())).append('.');
		} else if (type.isEnum()) {
			text.append(" One of ");
			Object[] constants = type.getEnumConstants();
			for (int i = 0; i < constants.length; i++) {
				text.append(i == 0 ? "" : ", ").append(((Enum<?>) constants[i]).name());
			}
			text.append('.');
		}
		if (meta.client()) {
			text.append(" [client]");
		}
		if (!meta.pendingMilestone().isEmpty()) {
			text.append(" [Milestone ").append(meta.pendingMilestone()).append(']');
		}
		return text.toString();
	}

	private static String literal(Object value) {
		if (value instanceof Double d) {
			return decimal(d);
		}
		if (value instanceof Enum<?> e) {
			return GSON.toJson(e.name());
		}
		if (value instanceof String s) {
			return GSON.toJson(s);
		}
		return String.valueOf(value);
	}

	/** Shortest exact form, e.g. 8 or 0.15. */
	public static String plain(double d) {
		return BigDecimal.valueOf(d).stripTrailingZeros().toPlainString();
	}

	/** Like {@link #plain} but always with a decimal point, e.g. 1.0 or 0.15. */
	public static String decimal(double d) {
		String text = plain(d);
		return text.contains(".") ? text : text + ".0";
	}

	// ------------------------------------------------------------------------------------ layout

	private static List<Section> buildLayout() {
		List<Section> sections = new ArrayList<>();
		for (Field sectionField : DeadlandsConfig.class.getDeclaredFields()) {
			int mod = sectionField.getModifiers();
			if (Modifier.isStatic(mod) || !Modifier.isPublic(mod) || sectionField.getType().isPrimitive()) {
				continue;
			}
			List<Entry> entries = new ArrayList<>();
			for (Field field : sectionField.getType().getDeclaredFields()) {
				ConfigEntry meta = field.getAnnotation(ConfigEntry.class);
				if (meta == null || Modifier.isStatic(field.getModifiers())) {
					continue;
				}
				entries.add(new Entry(sectionField.getName(), field.getName(), field, meta));
			}
			sections.add(new Section(sectionField.getName(), sectionField, List.copyOf(entries)));
		}
		return List.copyOf(sections);
	}
}
