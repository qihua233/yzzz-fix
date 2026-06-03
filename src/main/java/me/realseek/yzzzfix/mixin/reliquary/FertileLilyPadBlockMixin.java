package me.realseek.yzzzfix.mixin.reliquary;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import reliquary.blocks.FertileLilyPadBlock;

@Mixin(FertileLilyPadBlock.class)
public class FertileLilyPadBlockMixin {

    /**
     * 修复 Reliquary 的 FertileLilyPad 催熟逻辑将空气方块误判为作物方块导致的崩溃。
     *
     * <p>原代码仅排除了 {@code Blocks.FIRE}，未排除空气方块，当迭代过程中方块被替换为空气后，
     * 后续读取 {@code age} 属性时抛出 {@code IllegalArgumentException}。
     * 该 Mixin 修正空气检测并在催熟前做二次验证。</p>
     */
    @Overwrite(remap = false)
    private boolean isAllowedCropBlock(Block cropBlock) {
        return !cropBlock.defaultBlockState().isAir() && !(cropBlock instanceof DoublePlantBlock);
    }

    @Inject(method = "tickCropBlock", at = @At("HEAD"), cancellable = true, remap = false)
    private void verifyBeforeTick(ServerLevel world, BlockPos cropPos,
                                  BlockState cropState, Block cropBlock,
                                  double distance, CallbackInfo ci) {
        if (world.getBlockState(cropPos).isAir()) {
            ci.cancel();
        }
    }
}