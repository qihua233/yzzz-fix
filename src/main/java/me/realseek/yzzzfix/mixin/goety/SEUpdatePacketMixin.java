package me.realseek.yzzzfix.mixin.goety;

import com.Polarice3.Goety.common.capabilities.soulenergy.SEProvider;
import com.Polarice3.Goety.common.capabilities.soulenergy.SEUpdatePacket;
import com.Polarice3.Goety.utils.SEHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * 修复 SEUpdatePacket 客户端接收时的 UUID 校验。
 *
 * <p>原版 {@code consume} 始终将灵魂能量数据写入 {@code Goety.PROXY.getPlayer()}
 * （即本地玩家），广播时所有客户端都会把别人的数据覆盖到自己的 capability 上，
 * 造成 HUD 闪烁。本 Mixin 改为按 {@code packet.PlayerUUID} 查找对应的玩家实体再写入。</p>
 */
@Mixin(value = SEUpdatePacket.class, remap = false)
public class SEUpdatePacketMixin {

    @Unique
    private static final Field PLAYER_UUID;
    @Unique
    private static final Field TAG;

    static {
        try {
            PLAYER_UUID = SEUpdatePacket.class.getDeclaredField("PlayerUUID");
            PLAYER_UUID.setAccessible(true);
            TAG = SEUpdatePacket.class.getDeclaredField("tag");
            TAG.setAccessible(true);
        } catch (NoSuchFieldException e) {
            throw new RuntimeException("Failed to reflect SEUpdatePacket fields", e);
        }
    }

    @Inject(method = "consume", at = @At("HEAD"), cancellable = true, remap = false)
    private static void yzzzfix$consumeWithUuidCheck(SEUpdatePacket packet, Supplier<NetworkEvent.Context> ctxSupplier, CallbackInfo ci) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            if (ctx.getDirection() != NetworkDirection.PLAY_TO_CLIENT) return;
            try {
                UUID uuid = (UUID) PLAYER_UUID.get(packet);
                if (uuid == null) return;
                if (Minecraft.getInstance().level == null) return;
                Player player = Minecraft.getInstance().level.getPlayerByUUID(uuid);
                if (player == null) return;
                player.getCapability(SEProvider.CAPABILITY).ifPresent(ise -> {
                    try {
                        SEHelper.load((CompoundTag) TAG.get(packet), ise);
                    } catch (Exception ignored) {}
                });
            } catch (Exception ignored) {}
        });
        ctx.setPacketHandled(true);
        ci.cancel();
    }
}
