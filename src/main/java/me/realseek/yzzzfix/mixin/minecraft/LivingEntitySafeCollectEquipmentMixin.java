package me.realseek.yzzzfix.mixin.minecraft;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**暂未测试实装
 * 彻底移除 LivingEntity.collectEquipmentChanges() 的执行，
 * 避免因其他模组 Mixin 冲突导致的 ConcurrentModificationException 崩溃。
 * <p>该方法仅用于立即同步装备变化，取消后装备状态仍会通过正常网络同步更新。</p>
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntitySafeCollectEquipmentMixin {

    @Inject(method = "collectEquipmentChanges", at = @At("HEAD"), cancellable = true)
    private void yzzzfix$cancelCollectEquipmentChanges(CallbackInfo ci) {
        ci.cancel();
    }
}