package me.realseek.yzzzfix.mixin.minecraft;

import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * 修复 apotheosis_modern_ragnarok 等模组在 GoalSelector 迭代过程中修改 availableGoals 集合
 * 导致 ConcurrentModificationException 崩溃的问题。
 *
 * <p>将构造时的 {@code LinkedHashSet} 替换为 {@code CopyOnWriteArraySet}，
 * 迭代时使用快照，从根本上杜绝并发修改异常。</p>
 */
@Mixin(GoalSelector.class)
public abstract class GoalSelectorSafeMixin {

    @Shadow
    private Set<WrappedGoal> availableGoals;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void yzzz$replaceWithSafeSet(CallbackInfo ci) {
        this.availableGoals = new CopyOnWriteArraySet<>(this.availableGoals);
    }
}
