package me.realseek.yzzzfix.mixin.maid_targeting;

import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.sensor.MaidNearestLivingEntitySensor;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * 拦截并重写女仆最近实体传感器的默认距离排序逻辑。
 *
 * <p>原始 {@code doTick} 方法通过 {@code List.sort(Comparator.comparingDouble(maid::distanceTo))}
 * 将女仆的攻击目标按距离升序排列，导致女仆在多目标混战时优先攻击脚边的低威胁实体
 * 而忽略远处的高血量 Boss。该 Mixin 在 {@code List.sort} 调用点注入自定义比较器，
 * 引入基于 Forge Boss 标签与模组归属的二级制权重系统，实现威胁优先的目标排序。</p>
 *
 * <p>权重定级规则：命中 {@code forge:bosses} 实体类型标签的生物赋予最高权重 100；
 * 目标模组（Bosses of Mass Destruction 等）内未被 Boss 标签覆盖的普通敌对生物赋予中等权重 50；
 * 原版及其他无关模组的普通生物保持权重 0。同权重层级内回退到距离排序，
 * 确保女仆在同等威胁中优先选择近处目标。</p>
 *
 * <p>该权重系统与 {@code maid_fairy_attack_goal.json} 数据标签协同工作——
 * 标签决定女仆是否将某实体视为有效攻击目标，权重决定有效目标之间的锁定优先级。</p>
 */
@Mixin(value = MaidNearestLivingEntitySensor.class, remap = false)
public abstract class MixinMaidNearestLivingEntitySensor {

    @Unique
    private static final TagKey<EntityType<?>> yzzzfix$FORGE_BOSSES =
            TagKey.create(Registries.ENTITY_TYPE, new ResourceLocation("forge", "bosses"));

    @Unique
    private static final TagKey<EntityType<?>> yzzzfix$C_BOSSES =
            TagKey.create(Registries.ENTITY_TYPE, new ResourceLocation("c", "bosses"));

    @Unique
    private static final int yzzzfix$PRIORITY_BOSS = 100;

    @Unique
    private static final int yzzzfix$PRIORITY_ABOVE_BOSS = 120;

    @Unique
    private static final int yzzzfix$PRIORITY_MOD_MOB = 50;

    @Unique
    private static final int yzzzfix$PRIORITY_DEFAULT = 0;

    @Unique
    private static final Set<ResourceLocation> yzzzfix$ABOVE_BOSS_EXCEPTIONS = Set.of(
            // Meet Your Fight — Rosalyne 的 Rose Spirit 仆从
            new ResourceLocation("meetyourfight", "rose_spirit"),
            // Mowzie's Mobs — Umvuthi 召唤的 Umvuthana 仆从
            new ResourceLocation("mowziesmobs", "umvuthana"),
            new ResourceLocation("mowziesmobs", "umvuthana_raptor"),
            new ResourceLocation("mowziesmobs", "umvuthana_crane"),
            new ResourceLocation("mowziesmobs", "umvuthana_follower_raptor"),
            // Goety — Skull Lord 的 Bone Lord 同伴与各类仆从
            new ResourceLocation("goety", "bone_lord"),
            new ResourceLocation("goety", "drowned_servant"),
            new ResourceLocation("goety", "sunken_skeleton_servant"),
            new ResourceLocation("goety", "frozen_zombie_servant"),
            new ResourceLocation("goety", "zombie_servant"),
            new ResourceLocation("goety", "stray_servant"),
            new ResourceLocation("goety", "skeleton_servant"),
            new ResourceLocation("goety", "border_wraith_servant"),
            // Cthulhu Fishing — Obsessed Eye 仆从
            new ResourceLocation("cthulhufishing", "obsessed_eye_mob")
    );

    /**
     * 拦截 {@code List.sort} 调用，注入基于 Boss 标签与模组归属的自定义威胁权重排序逻辑。
     *
     * <p>通过 {@code @Redirect} 捕获封闭方法 {@code doTick} 的局部变量
     * {@code ServerLevel world} 与 {@code EntityMaid maid}，
     * 供排序回调中计算实体与女仆之间的距离。</p>
     */
    @Redirect(
            method = "doTick(Lnet/minecraft/server/level/ServerLevel;Lcom/github/tartaricacid/touhoulittlemaid/entity/passive/EntityMaid;)V",
            at = @At(value = "INVOKE", target = "Ljava/util/List;sort(Ljava/util/Comparator;)V"),
            remap = false
    )
    private void yzzzfix$injectCustomTargetPriority(List<LivingEntity> list, Comparator<? super LivingEntity> originalComparator, ServerLevel world, EntityMaid maid) {
        list.sort((a, b) -> {
            int weightA = yzzzfix$calculateThreatWeight(a);
            int weightB = yzzzfix$calculateThreatWeight(b);
            if (weightA != weightB) {
                return Integer.compare(weightB, weightA);
            }
            return Double.compare(maid.distanceToSqr(a), maid.distanceToSqr(b));
        });
    }

    /**
     * 计算目标实体的威胁权重。
     *
     * <p>基于 Forge 社区标准 {@code forge:bosses} 实体类型标签判定 Boss 身份，
     * 任何标注了该标签的实体均获得最高威胁权重 100，不受模组来源限制。
     * 未命中 Boss 标签但来自目标模组的实体获得中等权重 50，
     * 其余实体归零。该权重与 {@code maid_fairy_attack_goal} 数据标签协同——
     * 权重决定同标签有效目标之间的锁定优先级。</p>
     *
     * @param target 待评估的目标实体
     * @return 威胁权重（0–100），数值越高越优先被锁定
     */
    @Unique
    private static int yzzzfix$calculateThreatWeight(LivingEntity target) {
        if (target == null) {
            return yzzzfix$PRIORITY_DEFAULT;
        }

        if (yzzzfix$isAboveBossException(target)) {
            return yzzzfix$PRIORITY_ABOVE_BOSS;
        }

        if (target.getType().is(yzzzfix$FORGE_BOSSES) || target.getType().is(yzzzfix$C_BOSSES)) {
            return yzzzfix$PRIORITY_BOSS;
        }

        if (yzzzfix$isTargetMod(target)) {
            return yzzzfix$PRIORITY_MOD_MOB;
        }

        return yzzzfix$PRIORITY_DEFAULT;
    }

    /**
     * 判定目标实体是否命中高于 Boss 的排序特判。
     *
     * <p>该机制为会召唤仆从的 Boss 专设：仆从实体列入
     * {@code yzzzfix$ABOVE_BOSS_EXCEPTIONS} 获得权重 120（高于 Boss 的 100），
     * 确保女仆优先清理仆从再集火 Boss 本体。覆盖：</p>
     * <ul>
     * <li>Meet Your Fight — Rosalyne 的 Rose Spirit</li>
     * <li>Mowzie's Mobs — Umvuthi 的 Umvuthana 系列仆从</li>
     * <li>Goety — Skull Lord 的 Bone Lord 及各类 Servant 仆从</li>
     * <li>Cthulhu Fishing — Obsessed Eye 的仆从</li>
     * </ul>
     *
     * @param target 待判定的目标实体
     * @return 仅当实体命中 {@code yzzzfix$ABOVE_BOSS_EXCEPTIONS} 时返回 {@code true}
     */
    @Unique
    private static boolean yzzzfix$isAboveBossException(LivingEntity target) {
        ResourceLocation key = net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(target.getType());
        return key != null && yzzzfix$ABOVE_BOSS_EXCEPTIONS.contains(key);
    }

    /**
     * 判定目标实体是否来自需要优先处理的跨模组目标。
     *
     * <p>通过实体类型注册表键中的命名空间匹配已配置的目标模组。
     * 该方法仅影响未被 {@code forge:bosses} 标签覆盖的非 Boss 生物，
     * 使其仍获得高于原版普通怪物的排序权重。
     * 扩展新模组时需同步更新该方法与 {@code maid_fairy_attack_goal.json} 标签。</p>
     *
     * @param target 待判定的目标实体
     * @return 若实体来自已配置的目标模组则返回 {@code true}
     */
    @Unique
    private static boolean yzzzfix$isTargetMod(LivingEntity target) {
        net.minecraft.resources.ResourceLocation key = net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(target.getType());
        if (key == null) {
            return false;
        }
        String modId = key.getNamespace();
        return "bosses_of_mass_destruction".equals(modId)
                || "meetyourfight".equals(modId)
                || "goety".equals(modId)
                || "cataclysm".equals(modId)
                || "mowziesmobs".equals(modId)
                || "cthulhufishing".equals(modId);
    }
}
