package me.realseek.yzzzfix.mixin.ak;

import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.common.util.NonNullSupplier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/**
 * 修复 AlchemyKingdom 的 InventoryScreenMixin 中调用 {@code orElseGet(null)} 导致的空指针异常。
 *
 * <p>当 capability 不存在时，{@code orElseGet} 尝试调用 null 的 supplier，
 * 触发 {@code NullPointerException}。该 Mixin 在 supplier 为 null 时安全回退。</p>
 */
@Mixin(value = LazyOptional.class, remap = false)
public abstract class LazyOptionalNullFix<T> {

    @Shadow
    public abstract Optional<T> resolve();

    @Inject(method = "orElseGet", at = @At("HEAD"), cancellable = true, remap = false)
    private void ak$fixNullSupplier(NonNullSupplier<? extends T> other, CallbackInfoReturnable<T> cir) {
        if (other == null) {
            cir.setReturnValue(resolve().orElse(null));
        }
    }
}
