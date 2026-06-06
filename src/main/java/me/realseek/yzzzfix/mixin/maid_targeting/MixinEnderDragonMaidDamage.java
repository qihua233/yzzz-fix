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
 * 重定向回同一 Player 身份门禁，形成死循环。</p>
 *
 * <p>该 Mixin 在 {@code hurt(EnderDragonPart, DamageSource, float)} 入口
 * 通过 {@code cancellable = true} 接管全部处理：追溯投射物所有者链定位
 * 真正攻击者，识别玩家拥有的可驯服生物后直接复制原版伤害计算与结算逻辑，
 * 通过 {@code setHealth} 绕过 Player 身份校验。</p>
 *
 * <p>在伤害结算前调用 {@code setLastHurtByMob(player)} 直接记录玩家为击杀者，
 * 确保末影龙死亡时 {@code getKillCredit()} 返回玩家实体，
 * 龙战管理器（{@code DragonFight}）能正确发放奖励（龙蛋、返回传送门、经验）。</p>
 */
@Mixin(value = EnderDragon.class, remap = false)
public abstract class MixinEnderDragonMaidDamage extends Mob {

    protected MixinEnderDragonMaidDamage(EntityType<? extends Mob> entityType, Level level) {
        super(entityType, level);
    }

    @Unique
    private static Field yzzzfix$sittingDamageField;
    @Unique
    private static boolean yzzzfix$fieldSearched = false;

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

        if (!(attacker instanceof TamableAnimal tamable) || !(tamable.getOwner() instanceof Player player)) {
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

        dragon.setLastHurtByMob(player);
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

    @Unique
    private static void yzzzfix$accumulateSittingDamage(EnderDragon dragon, float damage) {
        try {
            if (!yzzzfix$fieldSearched) {
                try {
                    yzzzfix$sittingDamageField = EnderDragon.class.getDeclaredField("sittingDamageReceived");
                } catch (NoSuchFieldException e) {
                    try {
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
        }
    }

    @Unique
    private static Entity yzzzfix$resolveOwner(Entity entity) {
        Entity current = entity;
        int depth = 0;

        while (current != null && depth < 10) {
            if (current instanceof TamableAnimal tamable && tamable.getOwner() instanceof Player) {
                return current;
            }
            if (current instanceof Projectile projectile) {
                Entity owner = projectile.getOwner();
                if (owner == current) {
                    break;
                }
                current = owner;
            } else {
                break;
            }
            depth++;
        }
        return null;
    }
}
