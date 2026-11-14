package com.huaxiaosheng.spapacks.client;

import com.huaxiaosheng.spapacks.platform.Vertices;
import com.huaxiaosheng.spapacks.rig.RigSurface;
import com.huaxiaosheng.spapacks.rig.SkinnedMesh;
import com.huaxiaosheng.spapacks.rig.TorsoSurface;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import net.minecraft.client.model.geom.ModelPart;

final class AlexRenderer {
    private AlexRenderer() {
    }

    static void enterModelSpace(PoseStack matrices) {
        matrices.translate(0.0, 1.501, 0.0);
        matrices.scale(1.0666667F, -1.0666667F, -1.0666667F);
    }

    static void draw(SkinnedMesh mesh, RigSurface surface, ModelPart[] parts, PoseStack matrices, VertexConsumer vertices, int light, int overlay, int color) {
        matrices.pushPose();

        try {
            enterModelSpace(matrices);
            Pose entry = matrices.last();
            if (surface.torso != null && parts[1].visible) {
                TorsoSurface torso = surface.torso;

                for (int[] quad : torso.quads) {
                    for (int v : quad) {
                        int k = v * 3;
                        Vertices.put(
                            vertices,
                            entry,
                            torso.positions[k],
                            torso.positions[k + 1],
                            torso.positions[k + 2],
                            color,
                            torso.uv[v][0],
                            torso.uv[v][1],
                            overlay,
                            light,
                            torso.normals[k],
                            torso.normals[k + 1],
                            torso.normals[k + 2]
                        );
                    }
                }
            }

            if (surface.mapping != null) {
                surface.mapping.emit((i, ax, b, c) -> {
                    SkinnedMesh.Triangle tri = mesh.triangles()[i];
                    int part = surface.partIndex(tri.v()[0]);
                    if (parts[part].visible && (part != 1 || surface.torso == null)) {
                        int f = tri.face() * 3;
                        float nxx = surface.faceNormals[f];
                        float nyx = surface.faceNormals[f + 1];
                        float nzx = surface.faceNormals[f + 2];
                        emit(vertices, entry, ax, light, overlay, color, nxx, nyx, nzx);
                        emit(vertices, entry, b, light, overlay, color, nxx, nyx, nzx);
                        emit(vertices, entry, c, light, overlay, color, nxx, nyx, nzx);
                        emit(vertices, entry, c, light, overlay, color, nxx, nyx, nzx);
                    }
                });
            } else {
                float[] p = surface.positions;

                for (int i = 0; i < mesh.triangles().length; i++) {
                    SkinnedMesh.Triangle tri = mesh.triangles()[i];
                    int part = surface.partIndex(tri.v()[0]);
                    if (parts[part].visible && (part != 1 || surface.torso == null)) {
                        int a = tri.v()[0] * 3;
                        int b = tri.v()[1] * 3;
                        int c = tri.v()[2] * 3;
                        float ux = p[b] - p[a];
                        float uy = p[b + 1] - p[a + 1];
                        float uz = p[b + 2] - p[a + 2];
                        float vx = p[c] - p[a];
                        float vy = p[c + 1] - p[a + 1];
                        float vz = p[c + 2] - p[a + 2];
                        float cx = uy * vz - uz * vy;
                        float cy = uz * vx - ux * vz;
                        float cz = ux * vy - uy * vx;
                        if (!((float)Math.sqrt(cx * cx + cy * cy + cz * cz) < 1.0E-10F)) {
                            int face = tri.face() * 3;
                            float nx = surface.faceNormals[face];
                            float ny = surface.faceNormals[face + 1];
                            float nz = surface.faceNormals[face + 2];

                            for (int j = 0; j < 4; j++) {
                                int corner = Math.min(j, 2);
                                int v = tri.v()[corner] * 3;
                                float[] uv = surface.uv[i][corner];
                                Vertices.put(vertices, entry, p[v], p[v + 1], p[v + 2], color, uv[0], uv[1], overlay, light, nx, ny, nz);
                            }
                        }
                    }
                }
            }
        } finally {
            matrices.popPose();
        }
    }

    private static void emit(VertexConsumer vertices, Pose entry, float[] p, int light, int overlay, int color, float nx, float ny, float nz) {
        Vertices.put(vertices, entry, p[0], p[1], p[2], color, p[3], p[4], overlay, light, nx, ny, nz);
    }
}
