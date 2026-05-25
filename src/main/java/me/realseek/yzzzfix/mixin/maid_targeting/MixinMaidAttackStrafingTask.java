package me.realseek.yzzzfix.mixin.maid_targeting;

import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidAttackStrafingTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 修正 {@code MaidAttackStrafingTask} 的侧移距离阈值，使女仆在弓模式下与目标保持 10–18 格安全距离。
 *
 * <p>原始 lambda 以 {@code maxAttackDistance}（弓=15）的 0.5 倍（7.5 格）为前进阈值、
 * 0.2 倍（3.0 格）为后退阈值，导致女仆在 3–7.5 格区间内振荡，频繁进入目标的近战攻击范围。</p>
 *
 * <p>该 Mixin 拦截 {@code MoveControl#strafe(float, float)} 调用，将前进/后退判定
 * 替换为基于绝对距离的固定门槛：超过 18 格时靠近、少于 10 格时后退、
 * 在 10–18 格之间仅保留侧向环绕运动。</p>
 */
@Mixin(value = MaidAttackStrafingTask.class, remap = false)
public abstract class MixinMaidAttackStrafingTask {

    @Redirect(
            method = "lambda$tick$0",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/ai/control/MoveControl;m_24988_(FF)V"),
            require = 0
    )
    private void yzzzfix$redirectStrafe(
            MoveControl moveControl,
            float forwardBack,
            float leftRight,
            ItemStack stack,
            EntityMaid maid,
            LivingEntity target) {

        double distance = maid.distanceTo(target);

        float adjustedForward;
        if (distance < 10.0F) {
            adjustedForward = -0.5F;
        } else if (distance > 18.0F) {
            adjustedForward = 0.5F;
        } else {
            adjustedForward = 0.0F;
        }

        moveControl.strafe(adjustedForward, leftRight);
    }
}
