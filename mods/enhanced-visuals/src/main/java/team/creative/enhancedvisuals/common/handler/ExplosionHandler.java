package team.creative.enhancedvisuals.common.handler;

import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.hurtingprojectile.windcharge.BreezeWindCharge;
import net.minecraft.world.entity.projectile.hurtingprojectile.windcharge.WindCharge;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ServerExplosion;
import net.minecraft.world.level.Explosion.BlockInteraction;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import team.creative.creativecore.common.config.api.CreativeConfig;
import team.creative.creativecore.common.config.premade.IntMinMax;
import team.creative.creativecore.common.config.premade.curve.DecimalCurve;
import team.creative.creativecore.common.config.premade.curve.IntCurve;
import team.creative.enhancedvisuals.api.VisualHandler;
import team.creative.enhancedvisuals.api.type.VisualType;
import team.creative.enhancedvisuals.api.type.VisualTypeBlur;
import team.creative.enhancedvisuals.api.type.VisualTypeParticle;
import team.creative.enhancedvisuals.client.sound.SoundMuteHandler;

public class ExplosionHandler extends VisualHandler {
    @CreativeConfig
    public Identifier beepSound = Identifier.tryBuild("enhancedvisuals", "ringing");
    @CreativeConfig
    public VisualType dust = new VisualTypeParticle("dust");
    @CreativeConfig
    public IntMinMax dustDuration = new IntMinMax(500, 1000);
    @CreativeConfig
    public IntCurve dustAmount = new IntCurve(0, 0, 20, 30);
    @CreativeConfig
    public float maxExplosionVolume = 0.5F;
    @CreativeConfig
    public IntCurve explosionSoundTime = new IntCurve(0, 50, 20, 200);
    @CreativeConfig
    public VisualType blur = new VisualTypeBlur("blur");
    @CreativeConfig
    public DecimalCurve maxBlur = new DecimalCurve(0.0, 50.0, 10.0, 100.0);
    @CreativeConfig
    public IntCurve explosionBlurTime = new IntCurve(0, 10, 20, 20);
    @CreativeConfig
    public boolean ignoreBreeze = true;
    @CreativeConfig
    public boolean ignoreMace = true;
    @CreativeConfig
    public boolean ignoreSelfWindCharge = true;
    private Holder<Enchantment> windBurst;

    private boolean isMace(RegistryAccess access, ItemStack stack) {
        if (this.windBurst == null) {
            this.windBurst = access.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.WIND_BURST);
        }

        return stack.getItem() == Items.MACE && stack.getEnchantments().getLevel(this.windBurst) > 0;
    }

    public void onExploded(Player player, Vec3 pos, float size, BlockInteraction blockInteraction, @Nullable Entity source, @Nullable Class sourceClass) {
        if (!this.ignoreBreeze || sourceClass == null || !BreezeWindCharge.class.isAssignableFrom(sourceClass)) {
            if (!(this.ignoreSelfWindCharge && source instanceof WindCharge w) || w.getOwner() != player) {
                if (!this.ignoreMace
                    || source != null
                    || blockInteraction != BlockInteraction.TRIGGER_BLOCK
                    || !this.isMace(player.registryAccess(), player.getMainHandItem()) && !this.isMace(player.registryAccess(), player.getOffhandItem())) {
                    float f3 = size * 2.0F;
                    double d12 = Math.sqrt(player.distanceToSqr(pos)) / f3;
                    double d14 = ServerExplosion.getSeenPercent(pos, player);
                    double d10 = (1.0 - d12) * d14;
                    float damage = (int)((d10 * d10 + d10) / 2.0 * 7.0 * f3 + 1.0);
                    if (damage > 0.0F) {
                        this.addParticlesFadeOut(this.dust, this, (int)this.dustAmount.valueAt(damage), this.dustDuration, true);
                        DecimalCurve explosionSoundVolume = new DecimalCurve(0.0, this.maxExplosionVolume, this.explosionSoundTime.valueAt(damage), 0.0);
                        DecimalCurve explosionSoundMuteVolume = new DecimalCurve(0.0, 1.0, this.explosionSoundTime.valueAt(damage), 0.0);
                        if (SoundMuteHandler.startMuting(explosionSoundMuteVolume)) {
                            this.playSoundFadeOut(this.beepSound, null, explosionSoundVolume);
                        }

                        this.addVisualFadeOut(
                            this.blur, this, new DecimalCurve(0.0, this.maxBlur.valueAt(damage), (int)this.explosionBlurTime.valueAt(damage), 0.0)
                        );
                    }
                }
            }
        }
    }
}
