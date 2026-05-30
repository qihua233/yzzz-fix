package me.realseek.yzzzfix.mixin.embed;

import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Constructor;
import java.util.ArrayDeque;

/**
 * 修复 Embeddium/Oculus 渲染管线导致 PoseStack 被意外弹空后，
 * {@code last()} 调用 {@code ArrayDeque.getLast()} 抛出
 * {@code NoSuchElementException} 的崩溃。
 *
 * <p>正常渲染流程中 PoseStack 永远至少含有一个 Pose，空栈属异常状态。
 * 本 Mixin 在 {@code last()} 被调用时检测空栈并补入单位矩阵，
 * 保证渲染循环不中断，副作用极小。</p>
 */
@Mixin(PoseStack.class)
public abstract class PoseStackMixin {

    @Shadow
    @Final
    private ArrayDeque<PoseStack.Pose> poseStack;

    @Unique
    private static final Constructor<PoseStack.Pose> IDENTITY_POSE_CTOR;

    static {
        try {
            IDENTITY_POSE_CTOR = PoseStack.Pose.class.getDeclaredConstructor(Matrix4f.class, Matrix3f.class);
            IDENTITY_POSE_CTOR.setAccessible(true);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException("Failed to access PoseStack$Pose constructor", e);
        }
    }

    @Inject(method = "last()Lcom/mojang/blaze3d/vertex/PoseStack$Pose;", at = @At("HEAD"))
    private void yzzzfix$ensurePoseNotEmpty(CallbackInfoReturnable<PoseStack.Pose> cir) {
        if (this.poseStack.isEmpty()) {
            try {
                this.poseStack.addLast(IDENTITY_POSE_CTOR.newInstance(new Matrix4f(), new Matrix3f()));
            } catch (Exception ignored) {
                // If we can't create a safe pose, let the original crash happen
            }
        }
    }
}
