package me.realseek.yzzzfix.mixin.maid_targeting;

import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskBowAttack;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 修正女仆弓攻击对末影龙的瞄准高度，使箭矢命中龙体而非飞越。
 *
 * <p>原始 {@code TaskBowAttack#performRangedAttack} 使用
 * {@code m_20188_()} (即 getEyeY) 作为瞄准 Y 坐标（龙身高 8.0 时 eyeY≈+6.8），
 * 且箭矢设了 {@code setNoGravity(true)} 无下坠弧线，导致瞄准点在龙体顶部，
 * 箭矢直线飞行穿过龙体上方空域。</p>
 *
 * <p>该 Mixin 拦截 {@code LivingEntity#m_20188_()} 调用，
 * 当目标为末影龙时将返回值替换为碰撞箱中心 Y（约 height×0.5），
 * 确保箭矢瞄准龙体中部。</p>
 */
@Mixin(value = TaskBowAttack.class, remap = false)
public abstract class MixinTaskBowAttackAim {

    /**
     * 在弓攻击物弹道计算中重定向 {@code m_20188_()} (getEyeY) 调用，
     * 将末影龙瞄准点从眼睛高度修正为碰撞箱中心。
     */
    @Redirect(
            method = "performRangedAttack", // 可以简化，不需要全描述符，Mixin 会自动匹配唯一方法
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;m_20188_()D"), // 拦截混淆名
            require = 0 // 防御性配置
    )
    private double yzzzfix$redirectAimY(LivingEntity entity) {
        if (entity instanceof EnderDragon) {
            // 将瞄准点下移至碰撞箱中心，防止射过龙头顶
            return entity.getBoundingBox().getCenter().y;
        }
        // 调用真实的父类获取方法
        return entity.getEyeY();
    }
}