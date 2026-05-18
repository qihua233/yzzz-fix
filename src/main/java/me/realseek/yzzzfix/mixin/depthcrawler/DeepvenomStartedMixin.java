package me.realseek.yzzzfix.mixin.depthcrawler;

import net.mcreator.depthcrawler.procedures.DeepvenomEffectStartedappliedProcedure;
import net.mcreator.depthcrawler.DepthcrawlerMod;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 修复 Deepvenom 药水效果开始时的游戏模式切换与延迟恢复任务。
 *
 * <p>原逻辑将生存模式玩家强制改为冒险模式并启动 60 秒延迟恢复任务。
 * 本 Mixin 记录原始游戏模式并取消延迟任务，为后续安全恢复提供依据。</p>
 */
@Mixin(value = DeepvenomEffectStartedappliedProcedure.class, remap = false)
public abstract class DeepvenomStartedMixin {

    @Inject(method = "execute", at = @At("HEAD"), remap = false)
    private static void yzzzfix$recordOriginalGameMode(LevelAccessor world, Entity entity, CallbackInfo ci) {
        if (entity instanceof ServerPlayer player) {
            GameType current = player.gameMode.getGameModeForPlayer();
            if (current != GameType.ADVENTURE) {
                player.getPersistentData().putString("yzzzfix_deepvenom_original_mode", current.getName());
            }
        }
    }

    @Redirect(
            method = "execute",
            at = @At(value = "INVOKE",
                    target = "Lnet/mcreator/depthcrawler/DepthcrawlerMod;queueServerWork(ILjava/lang/Runnable;)V",
                    ordinal = 0),
            remap = false
    )
    private static void yzzzfix$cancelQueuedTask(int time, Runnable task) {
    }
}