package com.huaxiaosheng.spapacks.anim.play;

import com.huaxiaosheng.spapacks.anim.pose.Poses;
import com.huaxiaosheng.spapacks.anim.pose.Skeleton;

abstract class LibraryBackend implements Backend {
    private final float[][] mirrored;
    private final float[][] scratch;

    LibraryBackend(Skeleton skeleton) {
        this.mirrored = skeleton.newPose();
        this.scratch = skeleton.newPose();
    }

    abstract PlayableClip clip(String var1);

    @Override
    public boolean has(String clip) {
        return this.clip(clip) != null;
    }

    @Override
    public double seconds(String clip) {
        PlayableClip c = this.clip(clip);
        return c == null ? 0.0 : c.seconds();
    }

    @Override
    public boolean loops(String clip) {
        PlayableClip c = this.clip(clip);
        return c != null && c.loops();
    }

    @Override
    public boolean holds(String clip) {
        PlayableClip c = this.clip(clip);
        return c != null && c.holds();
    }

    @Override
    public double stride(String clip) {
        PlayableClip c = this.clip(clip);
        return c == null ? 0.0 : c.stride();
    }

    @Override
    public boolean additive(String clip) {
        PlayableClip c = this.clip(clip);
        return c != null && c.additive();
    }

    @Override
    public int keyed(String clip, int bone, boolean mirror) {
        PlayableClip c = this.clip(clip);
        return c == null ? 0 : c.keyed(mirror ? this.skeleton().mirror(bone) : bone);
    }

    void lay(PlayableClip clip, double time, boolean phased, boolean mirror, float[][] out) {
        if (!mirror) {
            clip.lay(time, phased, out);
        } else {
            Poses.mirror(this.skeleton(), out, this.mirrored, this.scratch);
            clip.lay(time, phased, this.mirrored);
            Poses.mirror(this.skeleton(), this.mirrored, out, this.scratch);
        }
    }

    void layNamed(String clip, double time, boolean phased, boolean mirror, float[][] out) {
        PlayableClip c = this.clip(clip);
        if (c != null) {
            this.lay(c, time, phased, mirror, out);
        }
    }
}
