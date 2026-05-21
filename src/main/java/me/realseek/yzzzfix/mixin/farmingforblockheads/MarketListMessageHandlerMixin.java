package me.realseek.yzzzfix.mixin.farmingforblockheads;

import com.google.common.collect.ArrayListMultimap;
import me.realseek.yzzzfix.module.farmingforblockheads_jei.FarmingForBlockheadsRecipeRegistrar;
import me.realseek.yzzzfix.module.farmingforblockheads_jei.MarketEntryCache;
import net.blay09.mods.farmingforblockheads.api.IMarketCategory;
import net.blay09.mods.farmingforblockheads.api.IMarketEntry;
import net.blay09.mods.farmingforblockheads.network.MarketListMessage;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collection;

/**
 * 拦截 {@code MarketListMessage.handle}，提取市场条目并写入缓存，
 * 同时触发 JEI 注册。
 */
@Mixin(value = MarketListMessage.class, remap = false)
public abstract class MarketListMessageHandlerMixin {

    @Accessor("entryMap")
    public abstract ArrayListMultimap<IMarketCategory, IMarketEntry> getEntryMap();

    @Inject(method = "handle(Lnet/minecraft/world/entity/player/Player;Lnet/blay09/mods/farmingforblockheads/network/MarketListMessage;)V",
            at = @At("TAIL"), remap = false)
    private static void yzzzfix$registerEntriesToJei(Player player, MarketListMessage message, CallbackInfo ci) {
        Collection<IMarketEntry> entries = ((MarketListMessageHandlerMixin) (Object) message).getEntryMap().values();

        MarketEntryCache.setEntries(entries);

        FarmingForBlockheadsRecipeRegistrar.tryRegister(entries);
    }
}