package me.realseek.yzzzfix.mixin.mutant_monsters;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 修复 Mutant Monsters 的 HurtByNearestTargetGoal 在 start() 中
 * 对 getLastHurtByMob() 返回的 null 直接调用 distanceToSqr()
 * 导致的 NullPointerException。
 *
 * <p>注入 Mob 的 distanceToSqr(Entity) 入口，当目标为 null 时
 * 返回 Double.MAX_VALUE，使比较逻辑自然跳过 null 目标。</p>
 */
@Mixin(value = Mob.class, remap = false)
public class MobDistanceNullSafeMixin {

    @Inject(
            method = "m_20280_(Lnet/minecraft/world/entity/Entity;)D",
            at = @At("HEAD"),
            cancellable = true
    )
    private void yzzz$nullSafeDistanceToSqr(Entity target, CallbackInfoReturnable<Double> cir) {
        if (target == null) {
            cir.setReturnValue(Double.MAX_VALUE);
        }
    }
}
