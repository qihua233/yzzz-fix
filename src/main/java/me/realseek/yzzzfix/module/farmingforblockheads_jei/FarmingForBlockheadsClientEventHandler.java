package me.realseek.yzzzfix.module.farmingforblockheads_jei;

import me.realseek.yzzzfix.YzzzFix;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RecipesUpdatedEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * 客户端事件处理器，监听配方更新和玩家登出。
 */
@OnlyIn(Dist.CLIENT)
public final class FarmingForBlockheadsClientEventHandler {

    private FarmingForBlockheadsClientEventHandler() {}

    @SubscribeEvent
    public static void onRecipesUpdated(RecipesUpdatedEvent event) {
        YzzzFix.LOGGER.debug("Recipes updated on client, retrying Farming for Blockheads JEI registration.");
        FarmingForBlockheadsRecipeRegistrar.tryRegister();
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        FarmingForBlockheadsRecipeRegistrar.reset();
        YzzzFix.LOGGER.debug("Reset Farming for Blockheads JEI registration state on logout.");
    }
}