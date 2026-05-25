package me.realseek.yzzzfix.mixin.goety;

import com.Polarice3.Goety.common.capabilities.soulenergy.SEUpdatePacket;
import com.Polarice3.Goety.common.network.ModNetwork;
import com.Polarice3.Goety.utils.SEHelper;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 修复诡厄巫法 (Goety) 灵魂能量多人联机不同步的盲区 Bug。
 *
 * <p>原版 {@code sendSEUpdatePacket} 仅将数据单播 (sendTo) 给玩家本人，
 * 导致其他玩家无法通过探查类模组或游戏机制读取其真实的灵魂能量。
 * 本修复将其拦截并重定向为全局广播 (sendToALL)。</p>
 *
 * <p>客户端由 {@link SEUpdatePacketMixin} 配合按 packet.PlayerUUID
 * 查找对应玩家实体写入，避免广播时本地 HUD 被他人数据覆盖。</p>
 */
@Mixin(value = SEHelper.class, remap = false)
public class SEHelperMixin {

    @Inject(method = "sendSEUpdatePacket", at = @At("HEAD"), cancellable = true)
    private static void yzzzfix$broadcastSEUpdatePacket(Player player, CallbackInfo ci) {
        if (player != null && !player.level().isClientSide()) {
            ModNetwork.sendToALL(new SEUpdatePacket(player));
        }
        ci.cancel();
    }
}
