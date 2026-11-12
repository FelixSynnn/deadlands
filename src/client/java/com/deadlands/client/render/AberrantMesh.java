package com.deadlands.client.render;

import com.deadlands.Deadlands;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import org.jspecify.annotations.Nullable;

/**
 * The Aberrant's imported mesh ({@code assets/deadlands/models/entity/aberrant.bin}, written by
 * {@code tools/assetgen/import_aberrant.py}): triangles grouped by bone and material, each vertex in pixels
 * relative to its bone's pivot, with a UV and a normal. Loaded once, on first use.
 */
public final class AberrantMesh {
	private static final Identifier FILE = Deadlands.id("models/entity/aberrant.bin");

	/** One material: its texture and, if it glows, its glow map. */
	public record Material(Identifier texture, @Nullable Identifier glow) {
	}

	/** Triangles of one material on one bone: 3 vertices x (x, y, z, u, v, nx, ny, nz) each. */
	public record Group(int bone, int material, float[] data) {
		public int triangles() {
			return data.length / 24;
		}
	}

	private static @Nullable AberrantMesh loaded;
	private static boolean failed;

	public final List<Material> materials;
	public final List<Group> groups;

	private AberrantMesh(List<Material> materials, List<Group> groups) {
		this.materials = materials;
		this.groups = groups;
	}

	/** The mesh, or null if it could not be read (logged once; the Aberrant is then not drawn). */
	public static @Nullable AberrantMesh get() {
		if (loaded == null && !failed) {
			try {
				loaded = read();
			} catch (IOException | RuntimeException failure) {
				failed = true;
				Deadlands.LOGGER.error("DEADLANDS: could not read the Aberrant's mesh {}", FILE, failure);
			}
		}
		return loaded;
	}

	private static AberrantMesh read() throws IOException {
		Resource resource = Minecraft.getInstance().getResourceManager().getResourceOrThrow(FILE);
		try (InputStream raw = resource.open(); DataInputStream in = new DataInputStream(new java.io.BufferedInputStream(raw))) {
			byte[] magic = new byte[8];
			in.readFully(magic);
			if (!new String(magic, 0, 7, StandardCharsets.US_ASCII).equals("DLMESH1")) {
				throw new IOException("not a DEADLANDS mesh");
			}
			int count = in.readInt();
			List<Material> materials = new ArrayList<>(count);
			for (int i = 0; i < count; i++) {
				byte[] name = new byte[in.readShort()];
				in.readFully(name);
				String id = new String(name, StandardCharsets.UTF_8);
				boolean glow = in.readBoolean();
				materials.add(new Material(Deadlands.id("textures/entity/aberrant/" + id + ".png"),
					glow ? Deadlands.id("textures/entity/aberrant/" + id + "_glow.png") : null));
			}
			int groups = in.readInt();
			List<Group> out = new ArrayList<>(groups);
			for (int g = 0; g < groups; g++) {
				int bone = in.readShort();
				int material = in.readShort();
				int triangles = in.readInt();
				float[] data = new float[triangles * 24];
				for (int k = 0; k < data.length; k++) {
					data[k] = in.readFloat();
				}
				out.add(new Group(bone, material, data));
			}
			return new AberrantMesh(List.copyOf(materials), List.copyOf(out));
		}
	}
}
