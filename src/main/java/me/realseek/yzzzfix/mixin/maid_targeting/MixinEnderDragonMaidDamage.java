package me.realseek.yzzzfix.mixin.maid_targeting;

import net.minecraft.network.chat.Component;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.boss.EnderDragonPart;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Field;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 使女仆及仆从投射物能够对末影龙造成有效伤害，并正常获得击杀归属与掉落物。
 *
 * <p>核心思路：通过 {@code @Redirect} 拦截原版 hurt 方法内对
 * {@code DamageSource.getEntity()} 的调用，当来源为玩家旗下的可驯服生物时
 * 返回玩家实体。原版 Player 身份门禁自然通过，完整保留原版伤害计算、阶段
 * 切换、死亡结算以及其它模组的注入回调。</p>
 *
 * <p>击杀归属通过反射写入 {@code lastHurtByPlayer} 与 Forge 的
 * {@code unlimitedLastHurtByPlayer}，确保经验值与战利品表正确发放。</p>
 */
@Mixin(value = EnderDragon.class, remap = false)
public abstract class MixinEnderDragonMaidDamage extends Mob {

    @Unique
    private static final Logger yzzzfix$LOG = LogManager.getLogger("YzzzFix:EDragon");

    protected MixinEnderDragonMaidDamage(EntityType<? extends Mob> entityType, Level level) {
        super(entityType, level);
    }

    @Unique
    private static Field yzzzfix$lastHurtByPlayerField;
    @Unique
    private static boolean yzzzfix$lastHurtByPlayerFieldSearched = false;
    @Unique
    private static Field yzzzfix$unlimitedLastHurtByPlayerField;
    @Unique
    private static boolean yzzzfix$unlimitedFieldSearched = false;

    /**
     * 拦截 {@code DamageSource.getEntity()} (m_7640_) 调用，将女仆/仆从替换为玩家，
     * 使原版 {@code instanceof Player} 门禁通过，完整保留原版伤害流程与其它模组的注入。
     */
    @Redirect(
            method = {
                    "hurt(Lnet/minecraft/world/entity/boss/EnderDragonPart;Lnet/minecraft/world/damagesource/DamageSource;F)Z",
                    "hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z"
            },
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/damagesource/DamageSource;getEntity()Lnet/minecraft/world/entity/Entity;")
    )
    private Entity yzzzfix$redirectGetEntity(DamageSource source) {
        Entity original = source.getEntity();
        Entity attacker = yzzzfix$resolveOwner(original);
        if (attacker instanceof TamableAnimal t && t.getOwner() instanceof Player p) {
            yzzzfix$setKillCredit(p);
            return p;
        }
        if (original == null) {
            Entity direct = source.getDirectEntity();
            Entity dAttacker = yzzzfix$resolveOwner(direct);
            if (dAttacker instanceof TamableAnimal t2 && t2.getOwner() instanceof Player p2) {
                yzzzfix$setKillCredit(p2);
                return p2;
            }
        }
        return original;
    }

    /**
     * 原版 hurt 执行完毕后设置击杀归属，确保 Forge 的
     * {@code unlimitedLastHurtByPlayer} 在 tick 捕获前已有值。
     */
    @Inject(
            method = "hurt(Lnet/minecraft/world/entity/boss/EnderDragonPart;Lnet/minecraft/world/damagesource/DamageSource;F)Z",
            at = @At("RETURN")
    )
    private void yzzzfix$afterHurt(EnderDragonPart part, DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue()) return;
        Entity attacker = yzzzfix$resolveOwner(source.getEntity());
        if (attacker == null) {
            attacker = yzzzfix$resolveOwner(source.getDirectEntity());
        }
        if (attacker instanceof TamableAnimal t && t.getOwner() instanceof Player player) {
            EnderDragon dragon = EnderDragon.class.cast(this);
            yzzzfix$setKillCredit(player);
            dragon.setLastHurtByMob(player);

            yzzzfix$LOG.info(" MAID HIT: player=" + player.getName().getString()
                + " amount=" + amount
                + " phase=" + dragon.getPhaseManager().getCurrentPhase().getPhase()
                + " health=" + dragon.getHealth());

            if (dragon.isDeadOrDying()) {
                yzzzfix$LOG.info(" DRAGON DYING via maid! player=" + player.getName().getString());
                player.displayClientMessage(Component.literal("§c[YzzzFix] 末影龙被女仆击杀! 玩家: " + player.getName().getString()), false);
            }
        }
    }

    @Unique
    private void yzzzfix$setKillCredit(Player player) {
        EnderDragon dragon = EnderDragon.class.cast(this);
        // lastHurtByPlayer on LivingEntity
        try {
            if (!yzzzfix$lastHurtByPlayerFieldSearched) {
                try {
                    yzzzfix$lastHurtByPlayerField = LivingEntity.class.getDeclaredField("lastHurtByPlayer");
                } catch (NoSuchFieldException e) {
                    try {
                        yzzzfix$lastHurtByPlayerField = LivingEntity.class.getDeclaredField("f_20888_");
                    } catch (NoSuchFieldException e2) {
                        for (Field f : LivingEntity.class.getDeclaredFields()) {
                            if (f.getType() == Player.class) {
                                yzzzfix$lastHurtByPlayerField = f;
                                break;
                            }
                        }
                    }
                }
                if (yzzzfix$lastHurtByPlayerField != null) {
                    yzzzfix$lastHurtByPlayerField.setAccessible(true);
                }
                yzzzfix$lastHurtByPlayerFieldSearched = true;
            }
            if (yzzzfix$lastHurtByPlayerField != null) {
                yzzzfix$lastHurtByPlayerField.set(dragon, player);
            }
        } catch (Exception ignored) {}

        // unlimitedLastHurtByPlayer on EnderDragon (Forge patch)
        try {
            if (!yzzzfix$unlimitedFieldSearched) {
                for (Field f : EnderDragon.class.getDeclaredFields()) {
                    if (f.getType() == Player.class && f != yzzzfix$lastHurtByPlayerField) {
                        yzzzfix$unlimitedLastHurtByPlayerField = f;
                        break;
                    }
                }
                if (yzzzfix$unlimitedLastHurtByPlayerField != null) {
                    yzzzfix$unlimitedLastHurtByPlayerField.setAccessible(true);
                }
                yzzzfix$unlimitedFieldSearched = true;
            }
            if (yzzzfix$unlimitedLastHurtByPlayerField != null) {
                yzzzfix$unlimitedLastHurtByPlayerField.set(dragon, player);
            }
        } catch (Exception ignored) {}
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
                if (owner == current) break;
                current = owner;
            } else {
                Entity owner = yzzzfix$getOwnerViaReflection(current);
                if (owner != null && owner != current) {
                    current = owner;
                } else {
                    break;
                }
            }
            depth++;
        }
        return null;
    }

    @Unique
    private static Entity yzzzfix$getOwnerViaReflection(Entity entity) {
        try {
            java.lang.reflect.Method getOwner = entity.getClass().getMethod("getOwner");
            Object result = getOwner.invoke(entity);
            if (result instanceof Entity owner && owner != entity) {
                return owner;
            }
        } catch (Exception ignored) {}
        return null;
    }
}
