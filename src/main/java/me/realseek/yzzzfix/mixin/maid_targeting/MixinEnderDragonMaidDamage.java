package me.realseek.yzzzfix.mixin.maid_targeting;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.boss.EnderDragonPart;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Field;

/**
 * 拦截并重写末影龙受击判定，使女仆及仆从投射物能够对末影龙造成有效伤害。
 *
 * <p>原始 {@code EnderDragon#hurt(EnderDragonPart, DamageSource, float)} 方法
 * 通过 {@code source.getEntity() instanceof Player} 判定伤害来源合法性，
 * 且 {@code EnderDragon#hurt(DamageSource, float)} 被重写为转发到
 * {@code hurt(this.body, source, amount)}，导致在
 * {@code EnderDragonPart#hurt} RETURN 点之外补施加伤害的任何尝试均被
 * 重定向回同一 Player 门禁，形成死循环。</p>
 *
 * <p>该 Mixin 在 {@code hurt(EnderDragonPart, DamageSource, float)} 入口
 * 通过 {@code cancellable = true} 接管全部处理：追溯投射物所有者链定位
 * 真正攻击者，识别玩家拥有的可驯服生物后直接复制原版伤害计算与结算逻辑，
 * 通过 {@code setHealth} 绕过 Player 身份校验。</p>
 */
@Mixin(value = EnderDragon.class, remap = false)
public abstract class MixinEnderDragonMaidDamage extends Mob {

    protected MixinEnderDragonMaidDamage(EntityType<? extends Mob> entityType, Level level) {
        super(entityType, level);
    }

    // 严格遵循规范 3.1：缓存反射字段，避免每 Tick 扫描引发 TPS 灾难
    @Unique
    private static Field yzzzfix$sittingDamageField;
    @Unique
    private static boolean yzzzfix$fieldSearched = false;

    /**
     * 在 {@code hurt(EnderDragonPart, DamageSource, float)} 入口接管
     * 非 Player 来源的伤害裁定。
     */
    @Inject(
            method = {
                    "hurt(Lnet/minecraft/world/entity/boss/EnderDragonPart;Lnet/minecraft/world/damagesource/DamageSource;F)Z",
                    "m_31120_(Lnet/minecraft/world/entity/boss/EnderDragonPart;Lnet/minecraft/world/damagesource/DamageSource;F)Z"
            },
            at = @At("HEAD"),
            cancellable = true
    )
    private void yzzzfix$handleMaidDamage(
            EnderDragonPart part,
            DamageSource source,
            float amount,
            CallbackInfoReturnable<Boolean> cir) {

        Entity attacker = yzzzfix$resolveOwner(source.getEntity());
        if (attacker == null) {
            attacker = yzzzfix$resolveOwner(source.getDirectEntity());
        }

        if (!(attacker instanceof TamableAnimal tamable) || !(tamable.getOwner() instanceof Player)) {
            return;
        }


        EnderDragon dragon = EnderDragon.class.cast(this);

        if (dragon.getPhaseManager().getCurrentPhase().getPhase() == EnderDragonPhase.DYING) {
            cir.setReturnValue(false);
            return;
        }

        float modified = dragon.getPhaseManager().getCurrentPhase().onHurt(source, amount);
        if (part != dragon.head) {
            modified = modified / 4.0F + Math.min(modified, 1.0F);
        }

        if (modified < 0.01F) {
            cir.setReturnValue(false);
            return;
        }

        float healthBefore = dragon.getHealth();
        dragon.setHealth(healthBefore - modified);

        if (dragon.isDeadOrDying() && !dragon.getPhaseManager().getCurrentPhase().isSitting()) {
            dragon.setHealth(1.0F);
            dragon.getPhaseManager().setPhase(EnderDragonPhase.DYING);
        }

        if (dragon.getPhaseManager().getCurrentPhase().isSitting()) {
            float damageDealt = healthBefore - dragon.getHealth();
            if (damageDealt > 0.0F) {
                yzzzfix$accumulateSittingDamage(dragon, damageDealt);
            }
        }

        cir.setReturnValue(true);
    }

    /**
     * 高性能缓存反射：处理无 refmap 环境下的私有字段修改
     */
    @Unique
    private static void yzzzfix$accumulateSittingDamage(EnderDragon dragon, float damage) {
        try {
            if (!yzzzfix$fieldSearched) {
                try {
                    // 尝试获取开发环境 (Mojmap) 字段
                    yzzzfix$sittingDamageField = EnderDragon.class.getDeclaredField("sittingDamageReceived");
                } catch (NoSuchFieldException e) {
                    try {
                        // 尝试获取 1.20.1 生产环境 (SRG) 字段
                        yzzzfix$sittingDamageField = EnderDragon.class.getDeclaredField("f_31102_");
                    } catch (NoSuchFieldException ignored) {}
                }
                if (yzzzfix$sittingDamageField != null) {
                    yzzzfix$sittingDamageField.setAccessible(true);
                }
                yzzzfix$fieldSearched = true;
            }

            if (yzzzfix$sittingDamageField != null) {
                float current = yzzzfix$sittingDamageField.getFloat(dragon);
                float accumulated = current + damage;
                if (accumulated > 0.25F * dragon.getMaxHealth()) {
                    yzzzfix$sittingDamageField.setFloat(dragon, 0.0F);
                    dragon.getPhaseManager().setPhase(EnderDragonPhase.TAKEOFF);
                } else {
                    yzzzfix$sittingDamageField.setFloat(dragon, accumulated);
                }
            }
        } catch (Exception ignored) {
            // 反射彻底失败时静默跳过，符合阻断式防崩溃规范
        }
    }

    /**
     * 追溯投射物或实体的真实所属玩家（若存在）。
     *
     * <p>采用带有最大深度限制的迭代算法，彻底杜绝由于第三方模组（如 Goety）
     * 投射物所有权死循环（Self-referencing 或循环引用）导致的 StackOverflowError。</p>
     */
    @Unique
    private static Entity yzzzfix$resolveOwner(Entity entity) {
        Entity current = entity;
        int depth = 0; // 防护断路器：最大查找深度

        // 最多往上追溯 10 层，超过直接熔断放弃，绝对防崩溃
        while (current != null && depth < 10) {
            // 如果找到了被玩家驯服的生物，直接返回它
            if (current instanceof TamableAnimal tamable && tamable.getOwner() instanceof Player) {
                return current;
            }
            // 如果是投射物，继续往上找主人
            if (current instanceof Projectile projectile) {
                Entity owner = projectile.getOwner();
                // 防御性编程：如果投射物的主人是它自己，立刻打断，防止死循环
                if (owner == current) {
                    break;
                }
                current = owner;
            } else {
                // 既不是宠物也不是投射物，线索中断
                break;
            }

            depth++;
        }
        return null; // 没找到合法的来源
    }
}