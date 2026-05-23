package me.realseek.yzzzfix.mixin.maid_targeting;

import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidRangedWalkToTarget;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 修正女仆弓箭模式下盲目贴脸的问题。
 *
 * <p>原始 {@code MaidRangedWalkToTarget#shouldEraseWalkTarget} 仅在目标距离
 * <b>玩家</b>小于 4.0 格时擦除步行目标，未考虑女仆与目标之间的实际距离。
 * 导致女仆一直逼近至近战距离后方由 {@code MaidAttackStrafingTask} 接管，
 * 而其后撤阈值仅 3.0 格，在 3.0–4.0 格形成进退两难区。</p>
 *
 * <p>该 Mixin 在 RETURN 注入女仆自身与目标距离的门槛判定：
 * 当女仆已进入目标 10.0 格内时额外擦除步行目标，
 * 使女仆在弓的有效射程（15 格）中段停止接近并交由侧移循环控制站位。</p>
 */
@Mixin(value = MaidRangedWalkToTarget.class, remap = false)
public abstract class MixinMaidRangedWalkToTarget {

    /**
     * 在原始判定之后追加女仆-目标距离检查，
     * 防止女仆在远程武器模式下无限接近目标。
     */
    @Inject(
        method = "shouldEraseWalkTarget(Lcom/github/tartaricacid/touhoulittlemaid/entity/passive/EntityMaid;Lnet/minecraft/world/entity/LivingEntity;)Z",
        at = @At("RETURN"),
        cancellable = true
    )
    private static void yzzzfix$addApproachThreshold(EntityMaid maid, LivingEntity target, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ()) {
            return;
        }
        if (maid.distanceTo(target) < 10.0F) {
            cir.setReturnValue(true);
        }
    }
}
