package me.realseek.yzzzfix.mixin.goety;

import com.Polarice3.Goety.common.blocks.entities.CursedCageBlockEntity;
import com.Polarice3.Goety.common.items.ModItems;
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
}