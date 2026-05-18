package me.realseek.yzzzfix.mixin.goety;

import com.Polarice3.Goety.common.blocks.entities.SoulCandlestickBlockEntity;
import com.Polarice3.Goety.common.blocks.entities.CursedCageBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = SoulCandlestickBlockEntity.class, remap = false)
public class SoulCandlestickBlockEntityMixin {

    /**
     * 修复 Goety 原版 Bug：drainSouls 强行要求 souls > amount 导致最后一点灵魂永久锁死。
     * 拦截 getSouls()，如果是有效灵魂，返回 +1 欺骗原版判断，将 > 转换为 >=。
     */
    @Redirect(
            method = "drainSouls",
            at = @At(value = "INVOKE", target = "Lcom/Polarice3/Goety/common/blocks/entities/CursedCageBlockEntity;getSouls()I")
    )
    private int yzzzfix$fixDrainCondition(CursedCageBlockEntity instance) {
        int souls = instance.getSouls();
        return souls > 0 ? souls + 1 : 0;
    }
}