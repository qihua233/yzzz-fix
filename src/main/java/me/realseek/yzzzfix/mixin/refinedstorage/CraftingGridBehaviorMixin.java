package me.realseek.yzzzfix.mixin.refinedstorage;

import com.refinedmods.refinedstorage.api.network.INetwork;
import com.refinedmods.refinedstorage.api.util.Action;
import com.refinedmods.refinedstorage.api.util.IComparer;
import com.refinedmods.refinedstorage.apiimpl.network.grid.CraftingGridBehavior;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 移除 onRecipeTransfer 中的 NBT 匹配限制。
 *
 * <p>原版从 RS 网络提取物品和从玩家背包查找物品时使用 COMPARE_NBT，
 * 导致不同 NBT 的同类物品无法匹配，JEI 填充到合成台失败。</p>
 */
@Mixin(value = CraftingGridBehavior.class, remap = false)
public class CraftingGridBehaviorMixin {

    @Redirect(
            method = "onRecipeTransfer",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/refinedmods/refinedstorage/api/network/INetwork;extractItem"
                            + "(Lnet/minecraft/world/item/ItemStack;IILcom/refinedmods/refinedstorage/api/util/Action;)"
                            + "Lnet/minecraft/world/item/ItemStack;"
            ),
            remap = false
    )
    private ItemStack yzzzfix$redirectExtract(
            INetwork network,
            ItemStack stack, int size, int flags, Action action
    ) {
        return network.extractItem(stack, size, flags & ~IComparer.COMPARE_NBT, action);
    }

    @Redirect(
            method = "onRecipeTransfer",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/refinedmods/refinedstorage/api/util/IComparer;isEqual"
                            + "(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;I)Z"
            ),
            remap = false
    )
    private boolean yzzzfix$redirectIsEqual(
            com.refinedmods.refinedstorage.api.util.IComparer comparer,
            ItemStack a, ItemStack b, int flags
    ) {
        return comparer.isEqual(a, b, flags & ~IComparer.COMPARE_NBT);
    }
}
