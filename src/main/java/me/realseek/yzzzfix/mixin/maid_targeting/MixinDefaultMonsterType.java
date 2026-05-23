package me.realseek.yzzzfix.mixin.maid_targeting;

import com.github.tartaricacid.touhoulittlemaid.entity.misc.DefaultMonsterType;
import com.github.tartaricacid.touhoulittlemaid.entity.misc.MonsterType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 修正 {@code DefaultMonsterType} 对跨模组 Boss 实体的敌意类型判定。
 *
 * <p>原始方法仅通过 {@code instanceof Enemy} 判定 {@link MonsterType#HOSTILE}，
 * 导致不继承 {@code net.minecraft.world.entity.monster.Monster} 的跨模组 Boss
 *（如 Bosses of Mass Destruction 的四柱）被归类为 {@link MonsterType#NEUTRAL}。
 * NEUTRAL 实体仅在受到伤害（{@code lastHurtByMob}）后触发反击校验，
 * 导致女仆在 Boss 率先出手前完全无视其存在。</p>
 *
 * <p>该 Mixin 在 {@code getMonsterType} 入口处注入三层额外判定：
 * 优先命中 {@code forge:bosses} 或 {@code c:bosses} 标签的实体直接返回
 * {@link MonsterType#HOSTILE}；其次命中 {@code touhou_little_maid:maid_fairy_attack_goal}
 * 标签的非 Boss 实体同样返回 HOSTILE，覆盖不实现 {@code Enemy} 接口的跨模组敌对生物。
 * 未命中任何标签的实体保持原有逻辑链不变。</p>
 */
@Mixin(value = DefaultMonsterType.class, remap = false)
public abstract class MixinDefaultMonsterType {

    @Unique
    private static final TagKey<EntityType<?>> yzzzfix$FORGE_BOSSES =
            TagKey.create(Registries.ENTITY_TYPE, new ResourceLocation("forge", "bosses"));

    @Unique
    private static final TagKey<EntityType<?>> yzzzfix$C_BOSSES =
            TagKey.create(Registries.ENTITY_TYPE, new ResourceLocation("c", "bosses"));

    @Unique
    private static final TagKey<EntityType<?>> yzzzfix$MAID_FAIRY_ATTACK_GOAL =
            TagKey.create(Registries.ENTITY_TYPE, new ResourceLocation("touhou_little_maid", "maid_fairy_attack_goal"));

    /**
     * 在 {@code getMonsterType} 方法入口处注入多层标签判定。
     *
     * <p>当实体已通过原版 {@code instanceof Enemy} 判定时不做干预，
     * 由原始逻辑照常返回 HOSTILE。仅在 {@code Enemy} 判定失败后
     * 依次检查 {@code forge:bosses} / {@code c:bosses} /
     * {@code maid_fairy_attack_goal} 标签，命中则短路返回 HOSTILE，
     * 未命中则交还原版逻辑继续 {@code TamableAnimal} / {@code Npc} 判定。</p>
     */
    @Inject(
            method = "getMonsterType(Lnet/minecraft/world/entity/LivingEntity;)Lcom/github/tartaricacid/touhoulittlemaid/entity/misc/MonsterType;",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void yzzzfix$classifyBossAsHostile(LivingEntity entity, CallbackInfoReturnable<MonsterType> cir) {
        if (entity instanceof Enemy) {
            return;
        }

        EntityType<?> type = entity.getType();
        if (type.is(yzzzfix$FORGE_BOSSES) || type.is(yzzzfix$C_BOSSES)) {
            cir.setReturnValue(MonsterType.HOSTILE);
            return;
        }

        if (type.is(yzzzfix$MAID_FAIRY_ATTACK_GOAL)) {
            cir.setReturnValue(MonsterType.HOSTILE);
        }
    }
}
