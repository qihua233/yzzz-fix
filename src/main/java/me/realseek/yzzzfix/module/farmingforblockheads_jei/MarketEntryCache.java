package me.realseek.yzzzfix.module.farmingforblockheads_jei;

import com.google.common.collect.ArrayListMultimap;
import net.blay09.mods.farmingforblockheads.api.IMarketCategory;
import net.blay09.mods.farmingforblockheads.api.IMarketEntry;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

/**
 * 缓存从网络包中收到的市场条目，解决客户端 MarketRegistry 为空的问题。
 * <p>数据在客户端收到 {@code MarketListMessage} 时写入，
 * 供 JEI 注册器在任何时刻读取。</p>
 */
public final class MarketEntryCache {

    private static final Collection<IMarketEntry> entries = new ArrayList<>();

    private MarketEntryCache() {}

    public static synchronized void setEntries(Collection<IMarketEntry> newEntries) {
        entries.clear();
        if (newEntries != null) {
            entries.addAll(newEntries);
        }
    }

    public static synchronized Collection<IMarketEntry> getEntries() {
        return Collections.unmodifiableCollection(new ArrayList<>(entries));
    }

    public static synchronized void reset() {
        entries.clear();
    }
}