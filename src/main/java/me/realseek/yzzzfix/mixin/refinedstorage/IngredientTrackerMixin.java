package me.realseek.yzzzfix.mixin.refinedstorage;

import com.refinedmods.refinedstorage.api.autocrafting.ICraftingPattern;
import com.refinedmods.refinedstorage.api.autocrafting.ICraftingPatternProvider;
import com.refinedmods.refinedstorage.api.network.grid.GridType;
import com.refinedmods.refinedstorage.api.util.IComparer;
import com.refinedmods.refinedstorage.apiimpl.API;
import com.refinedmods.refinedstorage.container.GridContainerMenu;
import com.refinedmods.refinedstorage.integration.jei.IngredientTracker;
import com.refinedmods.refinedstorage.item.PatternItem;
import com.refinedmods.refinedstorage.util.ItemStackKey;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.*;

/**
 * 移除 JEI 填充时的 NBT 匹配限制。
 *
 * <p>原版 IngredientTracker 使用含 NBT 的 ItemStackKey 作为 Map key，
 * 并通过 IComparer.COMPARE_NBT 比较物品，导致同一物品不同 NBT 无法匹配，
 * JEI 填充失败。</p>
 */
@Mixin(value = IngredientTracker.class, remap = false)
public abstract class IngredientTrackerMixin {

    @Shadow
    private Map<ItemStackKey, Integer> storedItems;
    @Shadow
    private Map<ItemStackKey, Integer> patternItems;
    @Shadow
    private Map<ItemStackKey, UUID> craftableItems;

    @Unique
    private static boolean yzzzfix$matches(ItemStack a, ItemStack b) {
        return API.instance().getComparer().isEqual(a, b, 0);
    }

    @Unique
    private static ItemStackKey yzzzfix$keyOf(ItemStack stack) {
        ItemStack copy = stack.copy();
        copy.setTag(null);
        return new ItemStackKey(copy);
    }

    /**
     * @reason 存储物品时剥离 NBT，使 storedItems/patternItems 的 key 不含 NBT。
     */
    @Overwrite
    public void addStack(ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        if (stack.getItem() instanceof ICraftingPatternProvider) {
            ICraftingPattern pattern = PatternItem.fromCache(Minecraft.getInstance().level, stack);
            if (pattern.isValid()) {
                for (ItemStack outputStack : pattern.getOutputs()) {
                    patternItems.merge(yzzzfix$keyOf(outputStack), 1, Integer::sum);
                }
            }
        } else {
            storedItems.merge(yzzzfix$keyOf(stack), stack.getCount(), Integer::sum);
        }
    }

    /**
     * @reason 遍历 Map 并使用无 NBT 比较替代 ItemStackKey 的 Map.get() 查找。
     */
    @Overwrite
    public ItemStack findBestMatch(GridContainerMenu gridContainer, Player player, List<ItemStack> list) {
        ItemStack resultStack = ItemStack.EMPTY;
        int count = 0;

        for (ItemStack listStack : list) {
            // check crafting matrix
            if (gridContainer.getGrid().getGridType().equals(GridType.CRAFTING)) {
                CraftingContainer craftingMatrix = gridContainer.getGrid().getCraftingMatrix();
                if (craftingMatrix != null) {
                    for (int matrixSlot = 0; matrixSlot < craftingMatrix.getContainerSize(); matrixSlot++) {
                        ItemStack stackInSlot = craftingMatrix.getItem(matrixSlot);
                        if (yzzzfix$matches(listStack, stackInSlot) && stackInSlot.getCount() > count) {
                            count = stackInSlot.getCount();
                            resultStack = stackInSlot;
                        }
                    }
                }
            }

            // check inventory
            for (int inventorySlot = 0; inventorySlot < player.getInventory().getContainerSize(); inventorySlot++) {
                ItemStack stackInSlot = player.getInventory().getItem(inventorySlot);
                if (yzzzfix$matches(listStack, stackInSlot) && stackInSlot.getCount() > count) {
                    count = stackInSlot.getCount();
                    resultStack = stackInSlot;
                }
            }

            // check storage — iterate with NBT-free comparison
            for (var entry : storedItems.entrySet()) {
                if (yzzzfix$matches(listStack, entry.getKey().getStack()) && entry.getValue() > count) {
                    resultStack = listStack;
                    count = entry.getValue();
                }
            }
        }

        // check craftable / pattern items
        if (count == 0) {
            for (ItemStack itemStack : list) {
                boolean found = false;
                for (ItemStackKey key : craftableItems.keySet()) {
                    if (yzzzfix$matches(itemStack, key.getStack())) {
                        resultStack = itemStack;
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    for (ItemStackKey key : patternItems.keySet()) {
                        if (yzzzfix$matches(itemStack, key.getStack())) {
                            resultStack = itemStack;
                            found = true;
                            break;
                        }
                    }
                }
                if (found) {
                    break;
                }
            }
        }

        return resultStack;
    }

    /**
     * @reason 移除 checkStack 中 isEqual 的 NBT 比较标志。
     */
    @Redirect(
            method = "checkStack",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/refinedmods/refinedstorage/api/util/IComparer;isEqual"
                            + "(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;I)Z"
            ),
            remap = false
    )
    private boolean yzzzfix$redirectIsEqual(IComparer comparer, ItemStack a, ItemStack b, int flags) {
        return comparer.isEqual(a, b, flags & ~IComparer.COMPARE_NBT);
    }
}
