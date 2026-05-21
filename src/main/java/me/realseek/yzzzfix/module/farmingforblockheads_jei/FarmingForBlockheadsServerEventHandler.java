package me.realseek.yzzzfix.module.farmingforblockheads_jei;

import com.mojang.logging.LogUtils;
import net.blay09.mods.balm.api.Balm;
import net.blay09.mods.farmingforblockheads.network.MarketListMessage;
import net.blay09.mods.farmingforblockheads.registry.MarketRegistry;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * 服务端事件处理器，在玩家登录时主动推送市场列表。
 */
public final class FarmingForBlockheadsServerEventHandler {

    private FarmingForBlockheadsServerEventHandler() {}

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity().level().isClientSide) return;
        if (event.getEntity() instanceof ServerPlayer player) {
            try {
                var grouped = MarketRegistry.getGroupedEntries();
                if (grouped != null && !grouped.isEmpty()) {
                    Balm.getNetworking().sendTo(player, new MarketListMessage(grouped));
                    LogUtils.getLogger().info("YzzzFix: Sent initial market list to {}", player.getName().getString());
                } else {
                    LogUtils.getLogger().warn("YzzzFix: MarketRegistry grouped entries empty on login");
                }
            } catch (Exception e) {
                LogUtils.getLogger().error("YzzzFix: Failed to send initial market list on login", e);
            }
        }
    }
}