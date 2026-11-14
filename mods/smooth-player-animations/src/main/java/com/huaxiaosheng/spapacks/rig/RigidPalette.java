package com.huaxiaosheng.spapacks.rig;

import org.joml.Matrix4f;
import org.joml.Quaternionf;

public final class RigidPalette {
    private static final Matrix4f TO_MODEL = new Matrix4f().translation(0.0F, 1.501F, 0.0F).scale(1.0666667F, -1.0666667F, -1.0666667F);
    private static final Matrix4f FROM_MODEL = new Matrix4f(TO_MODEL).invert();
    private static final int[] MESH_TO_VANILLA = new int[]{1, 0, 2, 3, 4, 5};
    private final int[] partOf;
    private final Matrix4f[] parts = new Matrix4f[6];
    private final Quaternionf turn = new Quaternionf();

    public RigidPalette(SkinnedMesh mesh) {
        int[] starts = mesh.partStarts();
        float[][] share = new float[mesh.boneCount()][6];
        SkinnedMesh.Vertex[] vertices = mesh.vertices();

        for (int part = 0; part < 6; part++) {
            for (int v = starts[part]; v < starts[part + 1]; v++) {
                SkinnedMesh.Vertex binding = vertices[v];

                for (int j = 0; j < binding.bones().length; j++) {
                    float[] var10000 = share[binding.bones()[j]];
                    var10000[MESH_TO_VANILLA[part]] = var10000[MESH_TO_VANILLA[part]] + binding.weights()[j];
                }
            }
        }

        this.partOf = new int[mesh.boneCount()];

        for (int bone = 0; bone < this.partOf.length; bone++) {
            int best = 0;

            for (int part = 1; part < 6; part++) {
                if (share[bone][part] > share[bone][best]) {
                    best = part;
                }
            }

            this.partOf[bone] = best;
        }

        for (int i = 0; i < 6; i++) {
            this.parts[i] = new Matrix4f();
        }
    }

    public void pose(float[][] parts, float[][] rest, float[][] palette, Matrix4f back) {
        for (int i = 0; i < 6; i++) {
            float[] v = parts[i];
            float[] r = rest[i];
            Matrix4f model = this.parts[i]
                .translation(v[3] / 16.0F, v[4] / 16.0F, v[5] / 16.0F)
                .rotate(this.turn.rotationZYX(v[2], v[1], v[0]))
                .scale(v[6], v[7], v[8])
                .translate(-r[0] / 16.0F, -r[1] / 16.0F, -r[2] / 16.0F);
            if (i == 0) {
                back.set(model);
            }

            model.mulLocal(FROM_MODEL).mul(TO_MODEL);
        }

        for (int bone = 0; bone < this.partOf.length; bone++) {
            Palette.fromMatrix(this.parts[this.partOf[bone]], palette[bone]);
        }
    }
}
