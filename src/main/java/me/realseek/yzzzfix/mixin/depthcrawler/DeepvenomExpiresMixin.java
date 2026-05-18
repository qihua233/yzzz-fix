package me.realseek.yzzzfix.mixin.depthcrawler;

import net.mcreator.depthcrawler.procedures.DeepvenomEffectExpiresProcedure;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 修复 Deepvenom 药水效果过期时的游戏模式恢复逻辑。
 *
 * <p>原逻辑仅检查玩家是否为冒险模式，若是则切回生存。
 * 如果玩家原本就是冒险模式（服务器设置或地图强制），会被错误地改为生存。
 * 本 Mixin 依赖 {@code DeepvenomStartedMixin} 记录的原始游戏模式，
 * 仅恢复那些因 Deepvenom 效果被强制改为冒险模式的玩家，其他情况不干预。</p>
 */
@Mixin(value = DeepvenomEffectExpiresProcedure.class, remap = false)
public abstract class DeepvenomExpiresMixin {

    @Inject(method = "execute", at = @At("TAIL"), remap = false)
    private static void yzzzfix$restoreOriginalGameMode(Entity entity, CallbackInfo ci) {
        if (entity instanceof ServerPlayer player) {
            if (player.gameMode.getGameModeForPlayer() == GameType.ADVENTURE) {
                String original = player.getPersistentData().getString("yzzzfix_deepvenom_original_mode");
                if (!original.isEmpty()) {
                    GameType originalMode = GameType.byName(original);
                    if (originalMode != null && originalMode != GameType.ADVENTURE) {
                        player.setGameMode(originalMode);
                    }
                }
            }
            player.getPersistentData().remove("yzzzfix_deepvenom_original_mode");
        }
    }
}