package me.realseek.yzzzfix.mixin.goety;

import com.Polarice3.Goety.api.items.magic.ITotem;
import com.Polarice3.Goety.common.blocks.entities.CursedCageBlockEntity;
import com.Polarice3.Goety.common.items.ModItems;
import com.Polarice3.Goety.utils.SEHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

/**
 * 修复诡厄巫法 (Goety) 诅咒之笼 (Cursed Cage) 的跨维度失效 Bug。
 *
 * <p>原版 {@code getOwner} 方法使用 {@code level.getPlayerByUUID()} 进行查找，
 * 导致当绑定玩家处于其他维度时，无法读取到灵魂能量。
 * 本修复在服务端将查找逻辑提升至全局的 {@code ServerPlayerList}，允许跨维度持续供能。</p>
 */
@Mixin(value = CursedCageBlockEntity.class, remap = false)
public abstract class CursedCageBlockEntityMixin extends BlockEntity {

    // 拿到原版类里的物品对象
    @Shadow public abstract ItemStack getItem();

    public CursedCageBlockEntityMixin(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Inject(method = "getOwner", at = @At("HEAD"), cancellable = true)
    private void yzzzfix$crossDimensionalGetOwner(CallbackInfoReturnable<Player> cir) {
        ItemStack item = this.getItem();

        // 1. 确保物品是连接宝石，且拥有 owner 标签（规范化使用 hasUUID 防崩溃）
        if (item != null && item.getItem() == ModItems.SOUL_TRANSFER.get() && item.hasTag() && item.getTag().hasUUID("owner")) {
            UUID ownerId = item.getTag().getUUID("owner");

            // 2. 核心修复：如果在服务端，直接从 Server 全局名单里提人！
            if (this.level instanceof ServerLevel serverLevel) {
                Player player = serverLevel.getServer().getPlayerList().getPlayer(ownerId);
                // 无论是否找到（有可能玩家下线了就是 null），都直接返回，接管原版逻辑
                cir.setReturnValue(player);
            }

            // 如果是客户端（极少调用，一般用于渲染），不拦截，放行交给原版的同维度查找即可
        }
    }

    /**
     * 将 {@code decreaseSouls} 中无折扣的 {@code SEHelper.decreaseSESouls} 调用
     * 替换为带 ISoulDiscount 折扣的 {@code SEHelper.decreaseSouls}。
     *
     * <p>原版火炉始终扣全量灵魂，无视玩家盔甲的减免属性。
     * 此重定向让火炉与法术共用同一套折扣逻辑——灵魂减免堆满 100% 即为免费合成。</p>
     */
    @Redirect(
            method = "decreaseSouls",
            at = @At(value = "INVOKE", target = "Lcom/Polarice3/Goety/utils/SEHelper;decreaseSESouls(Lnet/minecraft/world/entity/player/Player;I)Z")
    )
    private boolean yzzzfix$applySoulDiscount(Player player, int amount) {
        SEHelper.decreaseSouls(player, amount);
        return true;
    }

    /**
     * 修复 {@code getSouls()} 在灵魂能量系统激活时只返回玩家 SE 而忽略图腾 NBT 的问题。
     *
     * <p>当玩家激活灵魂方舟后，原版 {@code getSouls()} 会直接返回
     * {@code SEHelper.getSESouls(player)}，完全绕开图腾 NBT。
     * 若玩家 SE 为 0（刚激活或已耗尽），即使图腾 NBT 存储了大量灵魂，
     * 蜡烛的 {@code drainSouls} 检查也会失败，导致亡灵火炉永远无法完成合成。</p>
     */
    @Inject(method = "getSouls", at = @At("RETURN"), cancellable = true)
    private void yzzzfix$fixGetSouls(CallbackInfoReturnable<Integer> cir) {
        if (cir.getReturnValue() > 0) return;

        ItemStack item = this.getItem();
        if (item != null && item.getItem() instanceof ITotem && item.hasTag()) {
            int totemSouls = item.getTag().getInt("Souls");
            if (totemSouls > 0) {
                cir.setReturnValue(totemSouls);
            }
        }
    }
}
