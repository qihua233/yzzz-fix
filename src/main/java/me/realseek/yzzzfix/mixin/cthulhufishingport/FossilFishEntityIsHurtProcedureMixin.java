package me.realseek.yzzzfix.mixin.cthulhufishingport;

import net.mcreator.cthulhufishing.procedures.FossilFishEntityIsHurtProcedure;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.function.Predicate;

/**
 * 修复 CthulhuFishing 模组在实体受伤时因并发修改世界实体列表导致的崩溃。
 *
 * <p>原方法 {@code execute} 直接调用 {@code LevelAccessor#getEntitiesOfClass} 查询附近实体。
 * 当生物死亡或世界状态改变时，实体迭代器可能抛出 {@link ConcurrentModificationException}。
 * 本 Mixin 拦截该调用，捕获异常并返回空列表，避免服务器崩溃。</p>
 */
@Mixin(value = FossilFishEntityIsHurtProcedure.class, remap = false)
public abstract class FossilFishEntityIsHurtProcedureMixin {


    @Redirect(
            method = "execute",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/LevelAccessor;getEntitiesOfClass(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;"
            ),
            remap = false
    )
    private static <T extends Entity> List<T> yzzzfix$safeGetEntities(
            LevelAccessor world, Class<T> clazz, AABB aabb, Predicate<? super T> predicate) {
        try {
            return world.getEntitiesOfClass(clazz, aabb, predicate);
        } catch (ConcurrentModificationException e) {

            return Collections.emptyList();
        }
    }
}