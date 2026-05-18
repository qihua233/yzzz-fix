package me.realseek.yzzzfix.mixin.immortalers_delight;

import com.renyigesai.immortalers_delight.block.enchantal_cooler.EnchantalCoolerBlockEntity;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 修复魔能冷却器方块实体在退出存档后物品消失的问题。
 *
 * <p>原代码用于版本迁移的 {@code newVersion} 字段从未写入 NBT，
 * 导致每次加载存档时都会重复执行迁移逻辑，覆盖已有物品栏。</p>
 *
 * <p>本 Mixin 在方块实体加载和保存时持久化该标记。
 * 对于已有存档（标记不存在），直接设置 {@code newVersion = true} 跳过迁移，
 * 确保物品不会丢失。</p>
 */
@Mixin(value = EnchantalCoolerBlockEntity.class, remap = false)
public abstract class EnchantalCoolerBlockEntityMixin {

    @Shadow
    private boolean newVersion;

    @Unique
    private static final String yzzzfix$VERSION_KEY = "yzzzfix_NewVersion";


    @Inject(method = "m_142466_", at = @At("HEAD"), remap = false)
    private void yzzzfix$restoreNewVersion(CompoundTag tag, CallbackInfo ci) {
        if (tag.contains(yzzzfix$VERSION_KEY)) {
            this.newVersion = tag.getBoolean(yzzzfix$VERSION_KEY);
        } else {
            this.newVersion = true;
        }
    }

    @Inject(method = "m_183515_", at = @At("TAIL"), remap = false)
    private void yzzzfix$saveNewVersion(CompoundTag tag, CallbackInfo ci) {
        tag.putBoolean(yzzzfix$VERSION_KEY, this.newVersion);
    }
}