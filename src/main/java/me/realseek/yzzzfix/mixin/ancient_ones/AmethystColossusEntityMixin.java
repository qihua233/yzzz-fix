package me.realseek.yzzzfix.mixin.ancient_ones;

import net.mcreator.ancientones.entity.AmethystColossusEntity;
import net.mcreator.ancientones.procedures.AmethystColossusOnEntityTickUpdateProcedure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 修复 Ancient Ones 的紫晶巨石像实体在 tick 更新时因 MCreator 生成代码
 * 反复调用 getTarget() 导致的 NullPointerException。
 *
 * <p>包裹过程调用，捕获 NPE 并静默处理，
 * 确保实体 tick 不因目标丢失而崩溃。</p>
 */
@Mixin(value = AmethystColossusEntity.class, remap = false)
public class AmethystColossusEntityMixin {

    @Inject(
            method = "m_6075_",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/mcreator/ancientones/procedures/AmethystColossusOnEntityTickUpdateProcedure;execute(Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/world/entity/Entity;)V"
            ),
            cancellable = true
    )
    private void yzzz$catchProcedureNPE(CallbackInfo ci) {
        AmethystColossusEntity self = (AmethystColossusEntity) (Object) this;
        try {
            AmethystColossusOnEntityTickUpdateProcedure.execute(self.level(), self);
        } catch (NullPointerException e) {

        }
        ci.cancel();
    }
}
