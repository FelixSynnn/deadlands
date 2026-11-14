package com.huaxiaosheng.spapacks.rig;

import java.util.Arrays;

public final class RigSurface {
    private static final int[][] BASE = new int[][]{{0, 0}, {16, 16}, {40, 16}, {32, 48}, {0, 16}, {16, 48}};
    private static final int[][] OUTER = new int[][]{{32, 0}, {16, 32}, {40, 32}, {48, 48}, {0, 32}, {0, 48}};
    private final SkinnedMesh mesh;
    private final int[] starts;
    public final float[] rest;
    public final float[] positions;
    public final float[] faceNormals;
    private final float[] shellOffsets;
    private final boolean[] jointShell;
    private final int[] upperBones = new int[4];
    private final int[] lowerBones = new int[4];
    private final float[][] shellAxes = new float[4][9];
    public final float[][][] uv;
    public final LimbTextureMapping mapping;
    public final TorsoSurface torso;

    public RigSurface(SkinnedMesh mesh, boolean slim, boolean outer, float armorDilation) {
        this.mesh = mesh;
        this.starts = mesh.partStarts();
        this.rest = new float[mesh.vertexCount() * 3];
        this.positions = new float[this.rest.length];
        this.shellOffsets = new float[this.rest.length];
        this.jointShell = new boolean[mesh.vertexCount()];
        this.faceNormals = new float[mesh.faceCount() * 3];
        String[] limbs = new String[]{"right_arm", "left_arm", "right_leg", "left_leg"};

        for (int i = 0; i < 4; i++) {
            this.upperBones[i] = mesh.boneIndex(limbs[i]);
            this.lowerBones[i] = mesh.boneIndex(limbs[i] + "_lower_def");
        }

        for (int v = 0; v < mesh.vertexCount(); v++) {
            for (int b : mesh.vertices()[v].bones()) {
                String name = mesh.boneName(b);
                this.jointShell[v] = this.jointShell[v] | (name.contains("_joint_") || name.contains("_cap_") || name.endsWith("_cuff"));
            }
        }

        boolean armor = armorDilation > 0.0F;
        this.uv = new float[mesh.triangles().length][3][2];

        for (int part = 0; part < 6; part++) {
            float[] min = new float[]{Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE};
            float[] max = new float[]{-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};

            for (int v = this.starts[part]; v < this.starts[part + 1]; v++) {
                for (int a = 0; a < 3; a++) {
                    min[a] = Math.min(min[a], mesh.vertices()[v].p()[a]);
                    max[a] = Math.max(max[a], mesh.vertices()[v].p()[a]);
                }
            }

            float dilation = (armor ? armorDilation : (outer ? (part == 0 ? 0.5F : 0.25F) : 0.0F)) * 0.9375F / 16.0F;

            for (int v = this.starts[part]; v < this.starts[part + 1]; v++) {
                for (int a = 0; a < 3; a++) {
                    float value = mesh.vertices()[v].p()[a];
                    float unit = (value - min[a]) / (max[a] - min[a]);
                    if (a == 0 && (part == 2 || part == 3) && (!slim || armor)) {
                        value = (part == 2 ? max[a] : min[a]) + (value - (part == 2 ? max[a] : min[a])) * 4.0F / 3.0F;
                    }

                    if (a != 1 || unit < 1.0E-5F || unit > 0.99999F) {
                        this.shellOffsets[v * 3 + a] = (unit * 2.0F - 1.0F) * dilation;
                    }

                    this.rest[v * 3 + a] = value;
                }
            }
        }

        for (int i = 0; i < this.uv.length; i++) {
            SkinnedMesh.Triangle tri = mesh.triangles()[i];
            int part = this.partIndex(tri.v()[0]);
            boolean arm = part == 2 || part == 3;
            float meanU = 0.0F;
            float meanV = 0.0F;

            for (float[] t : tri.uv()) {
                meanU += t[0] * 64.0F - BASE[part][0];
                meanV += t[1] * 64.0F - BASE[part][1];
            }

            meanU /= 3.0F;
            meanV /= 3.0F;
            boolean top = meanV < (part == 0 ? 8 : 4) - 1.0E-4F;
            int[] origin = outer ? OUTER[part] : BASE[part];
            if (armor) {
                origin = BASE[part == 3 ? 2 : (part == 5 ? 4 : part)];
            }

            float center = arm && (!slim || armor) ? wideU(meanU, top) : meanU;

            for (int c = 0; c < 3; c++) {
                float u = tri.uv()[c][0] * 64.0F - BASE[part][0];
                float v = tri.uv()[c][1] * 64.0F - BASE[part][1];
                if (arm && (!slim || armor)) {
                    u = wideU(u, top);
                }

                if (armor && (part == 3 || part == 5)) {
                    u = top ? (center < 8.0F ? 12.0F - u : 20.0F - u) : (center < 12.0F ? 12.0F - u : 28.0F - u);
                }

                this.uv[i][c][0] = (u + origin[0]) / 64.0F;
                this.uv[i][c][1] = (v + origin[1]) / (armor ? 32 : 64);
            }

            if (Math.abs(this.uv[i][0][1] - this.uv[i][1][1]) < 1.0E-7F && Math.abs(this.uv[i][0][1] - this.uv[i][2][1]) < 1.0E-7F) {
                float height = armor ? 32.0F : 64.0F;
                float rowCenter = (Math.round(this.uv[i][0][1] * height) - 0.5F) / height;

                for (int c = 0; c < 3; c++) {
                    this.uv[i][c][1] = rowCenter;
                }
            }
        }

        this.mapping = armor ? null : new LimbTextureMapping(mesh, this, outer);
        this.torso = this.starts[2] - this.starts[1] > 8 ? new TorsoSurface(mesh, this, this.shellOffsets) : null;
    }

    private static float wideU(float u, boolean top) {
        if (u <= 4.0F) {
            return u;
        } else if (top) {
            return 4.0F + (u - 4.0F) * 4.0F / 3.0F;
        } else if (u <= 7.0F) {
            return 4.0F + (u - 4.0F) * 4.0F / 3.0F;
        } else {
            return u <= 11.0F ? u + 1.0F : 12.0F + (u - 11.0F) * 4.0F / 3.0F;
        }
    }

    public int partIndex(int vertex) {
        for (int p = 0; p < 6; p++) {
            if (vertex < this.starts[p + 1]) {
                return p;
            }
        }

        throw new IllegalArgumentException("Vertex outside rig");
    }

    public void sample(float[][] palette) {
        for (int limb = 0; limb < 4; limb++) {
            float[] upper = palette[this.upperBones[limb]];
            float[] lower = palette[this.lowerBones[limb]];

            for (int axis = 0; axis < 3; axis++) {
                float dot = 0.0F;

                for (int r = 0; r < 3; r++) {
                    dot += upper[r * 4 + axis] * lower[r * 4 + axis];
                }

                float divisor = Math.max(0.25F, 1.0F + dot);

                for (int r = 0; r < 3; r++) {
                    this.shellAxes[limb][r * 3 + axis] = (upper[r * 4 + axis] + lower[r * 4 + axis]) / divisor;
                }
            }
        }

        for (int v = 0; v < this.mesh.vertexCount(); v++) {
            SkinnedMesh.Vertex binding = this.mesh.vertices()[v];
            float x = this.rest[v * 3];
            float y = this.rest[v * 3 + 1];
            float z = this.rest[v * 3 + 2];
            float dx = this.shellOffsets[v * 3];
            float dy = this.shellOffsets[v * 3 + 1];
            float dz = this.shellOffsets[v * 3 + 2];

            for (int a = 0; a < 3; a++) {
                float value = 0.0F;
                int k = a * 4;

                for (int j = 0; j < binding.bones().length; j++) {
                    float[] m = palette[binding.bones()[j]];
                    value += (m[k] * x + m[k + 1] * y + m[k + 2] * z + m[k + 3]) * binding.weights()[j];
                    if (!this.jointShell[v]) {
                        value += (m[k] * dx + m[k + 1] * dy + m[k + 2] * dz) * binding.weights()[j];
                    }
                }

                if (this.jointShell[v]) {
                    float[] axes = this.shellAxes[this.partIndex(v) - 2];
                    int row = a * 3;
                    value += axes[row] * dx + axes[row + 1] * dy + axes[row + 2] * dz;
                }

                this.positions[v * 3 + a] = value;
            }
        }

        if (this.mapping != null) {
            this.mapping.sample(palette);
        }

        if (this.torso != null) {
            this.torso.sample(palette);
        }

        Arrays.fill(this.faceNormals, 0.0F);

        for (SkinnedMesh.Triangle t : this.mesh.triangles()) {
            int a = t.v()[0] * 3;
            int b = t.v()[1] * 3;
            int c = t.v()[2] * 3;
            int f = t.face() * 3;
            float ux = this.positions[b] - this.positions[a];
            float uy = this.positions[b + 1] - this.positions[a + 1];
            float uz = this.positions[b + 2] - this.positions[a + 2];
            float vx = this.positions[c] - this.positions[a];
            float vy = this.positions[c + 1] - this.positions[a + 1];
            float vz = this.positions[c + 2] - this.positions[a + 2];
            this.faceNormals[f] = this.faceNormals[f] + (uy * vz - uz * vy);
            this.faceNormals[f + 1] = this.faceNormals[f + 1] + (uz * vx - ux * vz);
            this.faceNormals[f + 2] = this.faceNormals[f + 2] + (ux * vy - uy * vx);
        }

        for (int f = 0; f < this.faceNormals.length; f += 3) {
            float x = this.faceNormals[f];
            float y = this.faceNormals[f + 1];
            float z = this.faceNormals[f + 2];
            float length = (float)Math.sqrt(x * x + y * y + z * z);
            if (length > 1.0E-10F) {
                this.faceNormals[f] = this.faceNormals[f] / length;
                this.faceNormals[f + 1] = this.faceNormals[f + 1] / length;
                this.faceNormals[f + 2] = this.faceNormals[f + 2] / length;
            }
        }
    }
}
