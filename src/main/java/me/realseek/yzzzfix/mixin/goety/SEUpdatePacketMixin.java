package me.realseek.yzzzfix.mixin.goety;

import com.Polarice3.Goety.common.capabilities.soulenergy.SEProvider;
import com.Polarice3.Goety.common.capabilities.soulenergy.SEUpdatePacket;
import com.Polarice3.Goety.utils.SEHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
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
 * 修复 SEUpdatePacket 广播导致的 HUD 闪烁。
 *
 * <p>原版 {@code consume} 通过 {@code Goety.PROXY.getPlayer()} 将数据写入本地玩家，
 * 当服务器向所有客户端广播每个玩家的心魂能量包时，非本地玩家的数据会覆盖本地玩家的数据，
 * 导致 HUD 闪烁。</p>
 *
 * <p>本 Mixin 在写入前校验 {@code PlayerUUID} 是否与本地玩家匹配，
 * 不匹配的包直接丢弃，消除跨玩家数据污染。</p>
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
            throw new RuntimeException("[YzzzFix] Failed to reflect SEUpdatePacket fields", e);
        }
    }

    @Inject(method = "consume", at = @At("HEAD"), cancellable = true, remap = false)
    private static void yzzzfix$redirectConsume(
            SEUpdatePacket packet,
            Supplier<NetworkEvent.Context> ctxSupplier,
            CallbackInfo ci
    ) {
        ci.cancel();
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.setPacketHandled(true);

        if (ctx.getDirection() != NetworkDirection.PLAY_TO_CLIENT) {
            return;
        }

        ctx.enqueueWork(() -> {
            try {
                Minecraft mc = Minecraft.getInstance();
                if (mc.player == null) return;

                UUID uuid = (UUID) PLAYER_UUID.get(packet);
                CompoundTag tag = (CompoundTag) TAG.get(packet);

                if (uuid == null || tag == null) return;
                if (!mc.player.getUUID().equals(uuid)) return;

                mc.player.getCapability(SEProvider.CAPABILITY).ifPresent(cap -> {
                    SEHelper.load(tag, cap);
                });
            } catch (Exception ignored) {
            }
        });
    }
}
