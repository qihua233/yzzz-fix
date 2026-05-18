package me.realseek.yzzzfix.mixin.slashbladeresharped;

import mods.flammpfeil.slashblade.capability.concentrationrank.ConcentrationRank;
import mods.flammpfeil.slashblade.capability.concentrationrank.IConcentrationRank;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 阻止拔刀剑 SSS 评分意外衰减。
 * <p>在 {@code setRawRankPoint} 头部拦截，当当前评分为 SSS (level≥7) 且新分值更低时拒绝写入，
 * 防止任何路径（RankPointHandler、Guard 等）导致的评分下降。</p>
 */
@Mixin(value = ConcentrationRank.class, remap = false)
public abstract class ConcentrationRankMixin {

    @Inject(method = "setRawRankPoint(J)V", at = @At("HEAD"), cancellable = true, remap = false)
    private void yzzzfix$preventSSSDrop(long point, CallbackInfo ci) {
        IConcentrationRank self = (IConcentrationRank) (Object) this;
        long currentRaw = self.getRawRankPoint();
        float currentLevel = currentRaw / (float) ConcentrationRank.UnitCapacity;
        int currentRank = IConcentrationRank.ConcentrationRanks.getRankFromLevel(currentLevel).level;

        if (currentRank >= 7 && point < currentRaw) {
            ci.cancel();
        }
    }
}