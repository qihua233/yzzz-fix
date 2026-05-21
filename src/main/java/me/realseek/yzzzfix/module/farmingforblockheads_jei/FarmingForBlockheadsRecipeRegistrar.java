package me.realseek.yzzzfix.module.farmingforblockheads_jei;

import me.realseek.yzzzfix.YzzzFix;
import mezz.jei.api.runtime.IJeiRuntime;
import net.blay09.mods.farmingforblockheads.api.IMarketEntry;
import net.blay09.mods.farmingforblockheads.compat.jei.MarketCategory;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 动态 JEI 配方注册器，负责将农贸市场条目注入 JEI。
 * <p>提供两个入口：
 * <ul>
 *     <li>{@link #tryRegister()} —— 从缓存中读取条目并注册（用于事件重试）。</li>
 *     <li>{@link #tryRegister(Collection)} —— 直接使用指定条目集合注册（网络包到达时调用）。</li>
 * </ul>
 * 注册状态在玩家登出时重置。</p>
 */
public final class FarmingForBlockheadsRecipeRegistrar {

    private static volatile boolean registered;
    private static final Set<String> addedKeys = new HashSet<>();

    private FarmingForBlockheadsRecipeRegistrar() {}

    public static synchronized void tryRegister() {
        tryRegister(MarketEntryCache.getEntries());
    }

    public static synchronized void tryRegister(Collection<IMarketEntry> entries) {
        if (registered) return;

        IJeiRuntime runtime = FarmingForBlockheadsJeiRuntimeHolder.getRuntime();
        if (runtime == null) return;

        if (entries == null || entries.isEmpty()) return;

        for (IMarketEntry entry : entries) {
            if (entry == null) continue;
            String key = entry.getCostItem().getItem() + "|" + entry.getOutputItem().getItem();
            addedKeys.add(key);
        }

        runtime.getRecipeManager().addRecipes(MarketCategory.TYPE, List.copyOf(entries));
        registered = true;
        YzzzFix.LOGGER.info("Registered {} Farming for Blockheads market recipes in JEI.", addedKeys.size());
    }

    public static void reset() {
        registered = false;
        addedKeys.clear();
        MarketEntryCache.reset();
    }
}