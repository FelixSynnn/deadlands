package vectorwing.farmersdelight.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.SingleQuadParticle.Layer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

public class StarParticle extends SingleQuadParticle {
    protected StarParticle(ClientLevel level, double posX, double posY, double posZ, TextureAtlasSprite textureAtlasSprite) {
        super(level, posX, posY, posZ, 0.0, 0.0, 0.0, textureAtlasSprite);
        this.xd *= 0.01F;
        this.yd *= 0.01F;
        this.zd *= 0.01F;
        this.yd += 0.1;
        this.quadSize *= 1.5F;
        this.lifetime = 16;
        this.hasPhysics = false;
    }

    @Override
    protected Layer getLayer() {
        return Layer.OPAQUE;
    }

    @Override
    public float getQuadSize(float scaleFactor) {
        return this.quadSize * Mth.clamp((this.age + scaleFactor) / this.lifetime * 32.0F, 0.0F, 1.0F);
    }

    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        if (this.age++ >= this.lifetime) {
            this.remove();
        } else {
            this.move(this.xd, this.yd, this.zd);
            if (this.y == this.yo) {
                this.xd *= 1.1;
                this.zd *= 1.1;
            }

            this.xd *= 0.86F;
            this.yd *= 0.86F;
            this.zd *= 0.86F;
            if (this.onGround) {
                this.xd *= 0.7F;
                this.zd *= 0.7F;
            }
        }
    }

    public static class Factory implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet spriteSet;

        public Factory(SpriteSet sprite) {
            this.spriteSet = sprite;
        }

        public Particle createParticle(
            SimpleParticleType typeIn, ClientLevel level, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed, RandomSource randomSource
        ) {
            StarParticle particle = new StarParticle(level, x, y + 0.3, z, this.spriteSet.get(randomSource));
            particle.setColor(1.0F, 1.0F, 1.0F);
            return particle;
        }
    }
}
