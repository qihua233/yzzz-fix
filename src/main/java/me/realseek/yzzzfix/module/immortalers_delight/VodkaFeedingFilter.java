package me.realseek.yzzzfix.module.immortalers_delight;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * 针对千古乐事   （Immortalers Delight）清水伏特加的右键喂食过滤器。
 *
 * <p>该类通过监听 Forge 的实体交互事件，拦截玩家使用清水伏特加时的行为。
 * 采用 {@link EventPriority#HIGHEST} 优先级，旨在抢在原模组处理逻辑之前进行干预。
 * 核心规则：禁止将伏特加喂给其他玩家，但允许喂给非玩家实体（如 Boss 或普通怪物）。</p>
 */
@Mod.EventBusSubscriber(modid = "yzzzfix", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class VodkaFeedingFilter {

    /**
     * 拦截玩家对实体右键交互事件。
     *
     * <p>当玩家手持物品并右键点击其他实体时触发此检查。
     * 若匹配到指定物品（清水伏特加）且目标是 {@link Player} 的实例，
     * 则直接取消事件并返回交互失败，从而彻底切断原模组的喂酒逻辑。
     * 对于 Boss 或其他非玩家实体，不作任何拦截，自然放行。</p>
     *
     * @param event 玩家与实体交互事件实例
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onAttemptFeeding(PlayerInteractEvent.EntityInteract event) {
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty()) {
            return;
        }

        // 纯字符串安全匹配，避免强依赖导致编译或运行崩溃
        ResourceLocation itemKey = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (itemKey != null
                && "immortalers_delight".equals(itemKey.getNamespace())
                && "clear_water_vodka".equals(itemKey.getPath())) {

            // 判定交互目标：如果试图喂给其他玩家
            if (event.getTarget() instanceof Player) {
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.FAIL);
            }
        }
    }
}