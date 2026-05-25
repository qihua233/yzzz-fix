package me.realseek.yzzzfix.mixin.goety;

import com.Polarice3.Goety.common.blocks.entities.ModBlockEntity;
import com.Polarice3.Goety.common.blocks.entities.NecroBrazierBlockEntity;
import com.Polarice3.Goety.common.blocks.entities.SoulCandlestickBlockEntity;
import com.Polarice3.Goety.common.blocks.entities.CursedCageBlockEntity;
import com.Polarice3.Goety.common.items.SoulTransferItem;
import com.Polarice3.Goety.common.crafting.BrazierRecipe;
import com.Polarice3.Goety.common.crafting.ModRecipeSerializer;
import com.Polarice3.Goety.init.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
/**
 * 拦截并重写诡厄巫法 (Goety) 亡灵火炉的物品流转与灵魂能量采集逻辑。
 *
 * <p>通过拦截 {@code setItems} 及相关物品处理流程绕过底层容器堆叠限制，实现批量材料的存入与合成。
 * 在 {@code tick} 循环中引入达标熔断判定，并在能量预判阶段读取 {@link SoulTransferItem} 的
 * NBT 标签进行同源方舟去重，解决共享网络下的重复计算与溢出扣费问题。</p>
 *
 * <p>底层扣除逻辑联动 {@link SoulCandlestickBlockEntityMixin} 修正原版边界条件判定漏洞，
 * 避免最后 1 点灵魂能量被异常锁定，确保结算事务的精确性。</p>
 */
@Mixin(value = NecroBrazierBlockEntity.class, remap = false)
public abstract class NecroBrazierBlockEntityMixin extends ModBlockEntity {

    @Unique
    private static final int yzzzfix$SCAN_RADIUS = 8;
    @Unique
    private static final int yzzzfix$MAX_BATCH_SIZE = 64;

    @Shadow public int currentTime;
    @Shadow public BrazierRecipe recipe;
    @Shadow private List<SoulCandlestickBlockEntity> candlestickBlockEntityList;
    @Shadow public abstract Container getContainer();
    @Shadow public abstract NonNullList<ItemStack> getItems();
    @Shadow public abstract boolean isEmpty();
    @Shadow public abstract void removeAllItems();
    @Shadow public abstract BrazierRecipe getRecipe();
    @Shadow public abstract void stopBrazier(boolean finished);

    public NecroBrazierBlockEntityMixin(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Inject(method = "setItems(Lnet/minecraft/core/NonNullList;)V", at = @At("HEAD"), cancellable = true)
    private void yzzzfix$onSetItems(NonNullList<ItemStack> items, CallbackInfo ci) {
        for(int i = 0; i < items.size(); ++i) {
            ItemStack stack = items.get(i);
            this.getContainer().setItem(i, stack.copy());
            this.getContainer().getItem(i).setCount(stack.getCount());
        }
        ci.cancel();
    }

    @Unique
    private Container yzzzfix$getDummyContainer(Container realContainer) {
        SimpleContainer dummy = new SimpleContainer(realContainer.getContainerSize());
        for (int i = 0; i < realContainer.getContainerSize(); i++) {
            ItemStack original = realContainer.getItem(i);
            if (!original.isEmpty()) {
                ItemStack copy = original.copy();
                copy.setCount(1);
                dummy.setItem(i, copy);
            }
        }
        return dummy;
    }

    @Unique
    private int yzzzfix$scanEnvironment() {
        if (this.level == null) return 0;
        int totalUniqueSouls = 0;
        Set<UUID> processedArcas = new HashSet<>();
        BlockPos pos = this.getBlockPos();

        for (int i = -yzzzfix$SCAN_RADIUS; i <= yzzzfix$SCAN_RADIUS; ++i) {
            for (int j = -yzzzfix$SCAN_RADIUS; j <= yzzzfix$SCAN_RADIUS; ++j) {
                for (int k = -yzzzfix$SCAN_RADIUS; k <= yzzzfix$SCAN_RADIUS; ++k) {
                    BlockEntity be = this.level.getBlockEntity(pos.offset(i, j, k));
                    if (be instanceof SoulCandlestickBlockEntity candle) {
                        int souls = candle.getSouls();
                        // 1>1 Bug 已经被新的 Mixin 修复，这里可以直接使用 souls > 0
                        if (souls > 0) {
                            boolean isDuplicate = false;
                            BlockPos cagePos = candle.getBlockPos().below();
                            BlockEntity cageBe = this.level.getBlockEntity(cagePos);

                            if (cageBe instanceof CursedCageBlockEntity cage) {
                                ItemStack cageItem = cage.getItem();
                                if (cageItem != null && cageItem.getItem() instanceof SoulTransferItem) {
                                    if (cageItem.hasTag() && cageItem.getTag().hasUUID("owner")) {
                                        UUID ownerId = cageItem.getTag().getUUID("owner");
                                        if (processedArcas.contains(ownerId)) {
                                            isDuplicate = true;
                                        } else {
                                            processedArcas.add(ownerId);
                                        }
                                    }
                                }
                            }
                            if (!isDuplicate) totalUniqueSouls += souls;
                        }
                    }
                }
            }
        }
        return totalUniqueSouls;
    }

    @Unique
    private int yzzzfix$drainExtraSouls(int amountRequired) {
        if (amountRequired <= 0 || this.level == null) return 0;
        int drained = 0;
        BlockPos pos = this.getBlockPos();

        for (int i = -yzzzfix$SCAN_RADIUS; i <= yzzzfix$SCAN_RADIUS; ++i) {
            for (int j = -yzzzfix$SCAN_RADIUS; j <= yzzzfix$SCAN_RADIUS; ++j) {
                for (int k = -yzzzfix$SCAN_RADIUS; k <= yzzzfix$SCAN_RADIUS; ++k) {
                    BlockEntity be = this.level.getBlockEntity(pos.offset(i, j, k));
                    if (be instanceof SoulCandlestickBlockEntity candlestick) {
                        int souls = candlestick.getSouls();
                        if (souls > 0) {
                            int before = candlestick.getSouls();
                            int toDrain = Math.min(before, amountRequired - drained);
                            for (int c = 0; c < toDrain; c++) {
                                candlestick.drainSouls(1, pos);
                            }
                            int after = candlestick.getSouls();
                            drained += (before - after);
                            if (drained >= amountRequired) return drained;
                        }
                    }
                }
            }
        }
        return drained;
    }

    @Inject(method = "tick()V", at = @At(value = "INVOKE", target = "Ljava/util/List;iterator()Ljava/util/Iterator;", ordinal = 0, remap = false))
    private void yzzzfix$preciseTickDraining(CallbackInfo ci) {
        Level level = this.level;
        BrazierRecipe recipe = this.getRecipe();
        if (level == null || level.isClientSide || recipe == null) {
            this.candlestickBlockEntityList.clear();
            return;
        }

        int cost = recipe.getSoulCost();
        int needed = cost - this.currentTime;

        if (needed > 0) {
            int actuallyGathered = 0;

            // 恢复原版的无差别全吸机制，保证 9 笼特效，但在吸够时精准熔断（break）
            for (SoulCandlestickBlockEntity candle : this.candlestickBlockEntityList) {
                if (actuallyGathered >= needed) {
                    break;
                }

                if (candle.getSouls() > 0) {
                    int before = candle.getSouls();
                    candle.drainSouls(1, this.getBlockPos());
                    int after = candle.getSouls();
                    actuallyGathered += (before - after);
                }
            }
            this.currentTime += actuallyGathered;
        }

        // 清空容器，向原版的老循环移交空迭代器
        this.candlestickBlockEntityList.clear();
    }

    @Inject(method = "removeItem(Lnet/minecraft/world/entity/player/Player;)V", at = @At("HEAD"), cancellable = true)
    private void yzzzfix$onRemoveItem(Player player, CallbackInfo ci) {
        if (this.currentTime > 0) {
            this.markUpdated();
            ci.cancel();
        }
    }

    @Inject(method = "activate(Lnet/minecraft/world/level/Level;)Z", at = @At("HEAD"), cancellable = true)
    private void yzzzfix$activate(Level world, CallbackInfoReturnable<Boolean> cir) {
        if (world == null) return;

        if (this.getRecipe() == null) {
            Container dummy = yzzzfix$getDummyContainer(this.getContainer());
            BrazierRecipe brazierRecipe = world.getRecipeManager()
                    .getAllRecipesFor((RecipeType<BrazierRecipe>) ModRecipeSerializer.BRAZIER_TYPE.get())
                    .stream().filter(r -> r.matches(dummy, world)).findFirst().orElse(null);

            if (brazierRecipe == null) {
                cir.setReturnValue(false);
                return;
            }
            this.recipe = brazierRecipe;
        }
        cir.setReturnValue(true);
    }

    @Inject(method = "updateRecipe(Lnet/minecraft/world/level/Level;)V", at = @At("HEAD"), cancellable = true)
    private void yzzzfix$updateRecipe(Level world, CallbackInfo ci) {
        if (world != null) {
            if (this.getRecipe() != null) {
                Container dummy = yzzzfix$getDummyContainer(this.getContainer());
                if (!this.getRecipe().matches(dummy, world)) {
                    this.stopBrazier(false);
                }
            } else {
                this.stopBrazier(false);
            }
        }
        ci.cancel();
    }

    @Inject(method = "addItem(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;)Z", at = @At("HEAD"), cancellable = true)
    private void yzzzfix$onAddItem(@Nullable Player player, ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (this.currentTime > 0 || stack.isEmpty()) {
            cir.setReturnValue(false);
            return;
        }

        Container container = this.getContainer();
        int containerSize = container.getContainerSize();

        // 收集空格和同种物品格
        int emptySlot = -1;
        int sameItemSlot = -1;
        int spaceInSameSlot = 0;
        int emptyCount = 0;
        for (int i = 0; i < containerSize; i++) {
            ItemStack slotStack = container.getItem(i);
            if (slotStack.isEmpty()) {
                if (emptySlot == -1) emptySlot = i;
                emptyCount++;
            } else if (sameItemSlot == -1 && ItemStack.isSameItemSameTags(slotStack, stack)) {
                int space = stack.getMaxStackSize() - slotStack.getCount();
                if (space > 0) {
                    sameItemSlot = i;
                    spaceInSameSlot = space;
                }
            }
        }

        if (emptySlot == -1 && sameItemSlot == -1) {
            cir.setReturnValue(false);
            return;
        }

        // 候选A：全量放入第一个空格
        // 候选B：堆叠到同种格
        // 候选C：拆分到多个空格（每格 1 个），用于无序放入 + 重复材料场景（如 A+C+B(2)）
        SimpleContainer simEmpty = null;
        SimpleContainer simStack = null;
        SimpleContainer simSplit = null;
        BrazierRecipe recipeEmpty = null;
        BrazierRecipe recipeStack = null;
        BrazierRecipe recipeSplit = null;
        int splitCount = 0;

        if (emptySlot != -1) {
            simEmpty = new SimpleContainer(containerSize);
            for (int i = 0; i < containerSize; i++) {
                if (i == emptySlot) {
                    ItemStack copy = stack.copy();
                    copy.setCount(stack.getCount());
                    simEmpty.setItem(i, copy);
                } else {
                    simEmpty.setItem(i, container.getItem(i).copy());
                }
            }
            if (this.level != null) {
                Container dummy = yzzzfix$getDummyContainer(simEmpty);
                recipeEmpty = this.level.getRecipeManager()
                        .getAllRecipesFor((RecipeType<BrazierRecipe>) ModRecipeSerializer.BRAZIER_TYPE.get())
                        .stream().filter(r -> r.matches(dummy, this.level)).findFirst().orElse(null);
            }

            // 候选C：手中数量 > 1 且空格 >= 2 时，尝试拆分（每格 1 个），解决无序放入 B(2) 导致配方不可见的问题
            if (recipeEmpty == null && stack.getCount() > 1 && emptyCount >= 2) {
                splitCount = Math.min(stack.getCount(), emptyCount);
                simSplit = new SimpleContainer(containerSize);
                for (int i = 0; i < containerSize; i++) {
                    simSplit.setItem(i, container.getItem(i).copy());
                }
                int placed = 0;
                for (int i = 0; i < containerSize && placed < splitCount; i++) {
                    if (container.getItem(i).isEmpty()) {
                        ItemStack copy = stack.copy();
                        copy.setCount(1);
                        simSplit.setItem(i, copy);
                        placed++;
                    }
                }
                if (this.level != null) {
                    Container dummy = yzzzfix$getDummyContainer(simSplit);
                    recipeSplit = this.level.getRecipeManager()
                            .getAllRecipesFor((RecipeType<BrazierRecipe>) ModRecipeSerializer.BRAZIER_TYPE.get())
                            .stream().filter(r -> r.matches(dummy, this.level)).findFirst().orElse(null);
                }
            }
        }

        if (sameItemSlot != -1) {
            simStack = new SimpleContainer(containerSize);
            for (int i = 0; i < containerSize; i++) {
                if (i == sameItemSlot) {
                    ItemStack copy = stack.copy();
                    copy.setCount(container.getItem(i).getCount() + Math.min(spaceInSameSlot, stack.getCount()));
                    simStack.setItem(i, copy);
                } else {
                    simStack.setItem(i, container.getItem(i).copy());
                }
            }
            if (this.level != null) {
                Container dummy = yzzzfix$getDummyContainer(simStack);
                recipeStack = this.level.getRecipeManager()
                        .getAllRecipesFor((RecipeType<BrazierRecipe>) ModRecipeSerializer.BRAZIER_TYPE.get())
                        .stream().filter(r -> r.matches(dummy, this.level)).findFirst().orElse(null);
            }
        }

        int targetSlot;
        int toAdd;
        BrazierRecipe matchedRecipe;
        SimpleContainer simulated;

        // 决策优先级：拆分匹配 > 空格匹配且堆叠不匹配 > 堆叠匹配 > 空格 > 堆叠
        if (recipeSplit != null) {
            // 拆分能匹配配方（典型场景：A+C + B(2) → 拆分 B 到两格匹配 A+B+B+C）
            targetSlot = emptySlot;
            toAdd = 1;
            matchedRecipe = recipeSplit;
            simulated = simSplit;
        } else if (recipeEmpty != null && recipeStack == null) {
            targetSlot = emptySlot;
            toAdd = stack.getCount();
            matchedRecipe = recipeEmpty;
            simulated = simEmpty;
        } else if (recipeStack != null) {
            targetSlot = sameItemSlot;
            toAdd = Math.min(spaceInSameSlot, stack.getCount());
            matchedRecipe = recipeStack;
            simulated = simStack;
        } else if (emptySlot != -1) {
            targetSlot = emptySlot;
            toAdd = stack.getCount();
            matchedRecipe = null;
            simulated = simEmpty;
        } else {
            targetSlot = sameItemSlot;
            toAdd = Math.min(spaceInSameSlot, stack.getCount());
            matchedRecipe = null;
            simulated = simStack;
        }

        // 所有路径都不匹配时，尝试重分配：把已有 count>1 的槽位"借"1个到空格，
        // 解决 A + B(2) + C 场景中 B(2) 占一格导致配方看不到第二个 B 的问题
        int redistSrcSlot = -1;
        int redistDstSlot = -1;
        if (matchedRecipe == null && emptySlot != -1 && emptyCount >= 2) {
            for (int i = 0; i < containerSize && redistSrcSlot == -1; i++) {
                ItemStack slotStack = container.getItem(i);
                if (!slotStack.isEmpty() && slotStack.getCount() > 1) {
                    // 找一个空格放"借"出来的 1 个
                    int dstSlot = -1;
                    for (int j = 0; j < containerSize; j++) {
                        if (j != emptySlot && container.getItem(j).isEmpty()) {
                            dstSlot = j;
                            break;
                        }
                    }
                    if (dstSlot != -1) {
                        // 模拟：从 i 移 1 个到 dstSlot，新物品放入 emptySlot
                        SimpleContainer redistSim = new SimpleContainer(containerSize);
                        for (int j = 0; j < containerSize; j++) {
                            if (j == i) {
                                ItemStack copy = slotStack.copy();
                                copy.setCount(slotStack.getCount() - 1);
                                redistSim.setItem(j, copy);
                            } else if (j == dstSlot) {
                                ItemStack copy = slotStack.copy();
                                copy.setCount(1);
                                redistSim.setItem(j, copy);
                            } else if (j == emptySlot) {
                                ItemStack copy = stack.copy();
                                copy.setCount(stack.getCount());
                                redistSim.setItem(j, copy);
                            } else {
                                redistSim.setItem(j, container.getItem(j).copy());
                            }
                        }
                        if (this.level != null) {
                            Container dummy = yzzzfix$getDummyContainer(redistSim);
                            BrazierRecipe redistRecipe = this.level.getRecipeManager()
                                    .getAllRecipesFor((RecipeType<BrazierRecipe>) ModRecipeSerializer.BRAZIER_TYPE.get())
                                    .stream().filter(r -> r.matches(dummy, this.level)).findFirst().orElse(null);
                            if (redistRecipe != null) {
                                redistSrcSlot = i;
                                redistDstSlot = dstSlot;
                                matchedRecipe = redistRecipe;
                                simulated = redistSim;
                            }
                        }
                    }
                }
            }
        }

        if (toAdd <= 0) {
            cir.setReturnValue(false);
            return;
        }

        // 执行重分配：从 redistSrcSlot 移 1 个到 redistDstSlot
        if (redistSrcSlot != -1) {
            container.getItem(redistSrcSlot).shrink(1);
            ItemStack moved = container.getItem(redistSrcSlot).copy();
            moved.setCount(1);
            container.setItem(redistDstSlot, moved);
            container.getItem(redistDstSlot).setCount(1);
        }

        if (matchedRecipe != null) {
            int availableSouls = yzzzfix$scanEnvironment();
            int cost = matchedRecipe.getSoulCost();

            if (cost > 0) {
                int maxBatchBySouls = availableSouls / cost;

                if (maxBatchBySouls == 0) {
                    if (player != null && this.level != null && !this.level.isClientSide) {
                        player.displayClientMessage(Component.literal("可用灵魂能量不足以合成哪怕 1 个 (需 " + cost + " 能量)").withStyle(ChatFormatting.RED), true);
                    }
                    cir.setReturnValue(false);
                    return;
                }

                int simBatchSize = yzzzfix$MAX_BATCH_SIZE;
                for (int i = 0; i < simulated.getContainerSize(); i++) {
                    if (!simulated.getItem(i).isEmpty()) {
                        simBatchSize = Math.min(simBatchSize, simulated.getItem(i).getCount());
                    }
                }

                if (simBatchSize > maxBatchBySouls) {
                    int currentSlotCount = container.getItem(targetSlot).getCount();
                    int allowedToAdd = maxBatchBySouls - currentSlotCount;
                    if (allowedToAdd <= 0) {
                        if (player != null && this.level != null && !this.level.isClientSide) {
                            player.displayClientMessage(Component.literal("受可用灵魂能量限制，无法放入更多该材料").withStyle(ChatFormatting.RED), true);
                        }
                        cir.setReturnValue(false);
                        return;
                    }
                    toAdd = Math.min(toAdd, allowedToAdd);
                    if (player != null && this.level != null && !this.level.isClientSide) {
                        player.displayClientMessage(Component.literal("受可用灵魂能量限制，自动拦截，仅放入 " + toAdd + " 个 (本次最多合成 " + maxBatchBySouls + " 个)").withStyle(ChatFormatting.YELLOW), true);
                    }
                }
            }
        }

        // 实际写入容器：拆分路径需要写多个槽位
        if (simulated == simSplit && splitCount > 1) {
            int placed = 0;
            for (int i = 0; i < containerSize && placed < splitCount; i++) {
                if (container.getItem(i).isEmpty()) {
                    ItemStack newStack = stack.copy();
                    newStack.setCount(1);
                    container.setItem(i, newStack);
                    container.getItem(i).setCount(1);
                    placed++;
                }
            }
            if (player == null || !player.getAbilities().instabuild) {
                stack.shrink(placed);
            }
        } else {
            ItemStack targetStack = container.getItem(targetSlot);
            if (targetStack.isEmpty()) {
                ItemStack newStack = stack.copy();
                newStack.setCount(toAdd);
                container.setItem(targetSlot, newStack);
                container.getItem(targetSlot).setCount(toAdd);
            } else {
                targetStack.grow(toAdd);
            }
            if (player == null || !player.getAbilities().instabuild) {
                stack.shrink(toAdd);
            }
        }

        if (this.level != null) {
            this.level.playSound(null, this.getBlockPos(), SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 1.0F, 1.0F);
        }
        this.markUpdated();

        cir.setReturnValue(true);
    }

    @Inject(method = "stopBrazier(Z)V", at = @At("HEAD"), cancellable = true)
    private void yzzzfix$onStopBrazier(boolean finished, CallbackInfo ci) {
        Level level = this.level;
        if (level == null || level.isClientSide) return;

        ci.cancel();

        BrazierRecipe currentRecipe = this.getRecipe();
        if (currentRecipe != null) {
            level.playSound(null, this.getBlockPos(), SoundEvents.GENERIC_EXTINGUISH_FIRE, SoundSource.BLOCKS, 1.0F, 1.0F);

            if (finished) {
                Container container = this.getContainer();
                int batchSize = yzzzfix$MAX_BATCH_SIZE;
                boolean hasItems = false;

                for (int i = 0; i < container.getContainerSize(); i++) {
                    ItemStack slotStack = container.getItem(i);
                    if (!slotStack.isEmpty()) {
                        hasItems = true;
                        batchSize = Math.min(batchSize, slotStack.getCount());
                    }
                }
                if (!hasItems) batchSize = 0;

                if (batchSize > 0) {
                    int cost = currentRecipe.getSoulCost();
                    int totalTargetCost = batchSize * cost;
                    int soulsStillNeeded = Math.max(0, totalTargetCost - this.currentTime);
                    int successfullyDrained = 0;

                    if (soulsStillNeeded > 0) {
                        int availableNow = yzzzfix$scanEnvironment();
                        int soulsToDrain = Math.min(availableNow, soulsStillNeeded);
                        if (soulsToDrain > 0) {
                            successfullyDrained = yzzzfix$drainExtraSouls(soulsToDrain);
                        }
                    }

                    int totalPaid = this.currentTime + successfullyDrained;
                    int totalCrafts = cost > 0 ? (totalPaid / cost) : batchSize;
                    totalCrafts = Math.min(totalCrafts, batchSize);

                    if (totalCrafts > 0) {
                        Container dummy = yzzzfix$getDummyContainer(container);
                        ItemStack result = level.getRecipeManager()
                                .getRecipeFor((RecipeType<BrazierRecipe>) ModRecipeSerializer.BRAZIER_TYPE.get(), dummy, level)
                                .map(r -> r.assemble(dummy, level.registryAccess()))
                                .orElse(ItemStack.EMPTY);

                        if (!result.isEmpty()) {
                            result.setCount(result.getCount() * totalCrafts);
                            BlockPos pos = this.getBlockPos();
                            NecroBrazierBlockEntity.dropItemStack(level, pos.getX(), pos.getY() + 1.0, pos.getZ(), result);
                            level.playSound(null, pos, ModSounds.CAST_SPELL.get(), SoundSource.BLOCKS, 2.0F, 0.5F);
                        }
                        yzzzfix$shrinkItems(container, totalCrafts);
                    }

                    if (!this.isEmpty()) {
                        yzzzfix$ejectItemsSafely(level, this.getBlockPos(), this.getItems());
                        this.removeAllItems();
                    }
                }
            } else {
                if (!this.isEmpty()) {
                    yzzzfix$ejectItemsSafely(level, this.getBlockPos(), this.getItems());
                    level.playSound(null, this.getBlockPos(), ModSounds.SPELL_FAIL.get(), SoundSource.BLOCKS, 2.0F, 0.5F);
                }
                this.removeAllItems();
            }
        } else {
            if (!this.isEmpty()) {
                yzzzfix$ejectItemsSafely(level, this.getBlockPos(), this.getItems());
                level.playSound(null, this.getBlockPos(), ModSounds.SPELL_FAIL.get(), SoundSource.BLOCKS, 2.0F, 0.5F);
            }
            this.removeAllItems();
        }

        this.recipe = null;
        this.currentTime = 0;
        this.markUpdated();
    }

    @Unique
    private void yzzzfix$shrinkItems(Container container, int amount) {
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack slotStack = container.getItem(i);
            if (!slotStack.isEmpty()) {
                slotStack.shrink(amount);
                if (slotStack.isEmpty()) {
                    container.setItem(i, ItemStack.EMPTY);
                }
            }
        }
    }

    @Unique
    private void yzzzfix$ejectItemsSafely(Level level, BlockPos pos, NonNullList<ItemStack> items) {
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) {
                NecroBrazierBlockEntity.dropItemStack(level, pos.getX(), pos.getY() + 1.0, pos.getZ(), stack.copy());
            }
        }
    }
}
