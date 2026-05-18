package me.realseek.yzzzfix.mixin.moonstone;

import com.moonstone.moonstonemod.entity.blood;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 修复血球实体碰到佩戴死灵契约玩家时错误的生命值操作。
 *
 * <p>原代码误将 {@code setHealth} 当作回血方法，导致玩家生命值被
 * <b>设置为最大生命值的 15%</b>，而非恢复。
 * 本 Mixin 将其重定向为正确的 {@code heal}，恢复最大生命值的 15%。</p>
 */
@Mixin(value = blood.class, remap = false)
public abstract class BloodEntityMixin {

    @Unique
    private static final float BLOOD_HEAL_RATIO = 0.15f;

    @Redirect(
            method = "m_6123_",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;m_5634_(F)V", ordinal = 0),
            remap = false
    )
    private void yzzzfix$redirectSetHealthToHeal(LivingEntity entity, float health) {
        entity.heal(entity.getMaxHealth() * BLOOD_HEAL_RATIO);
    }
}
