package me.realseek.yzzzfix.mixin.embed;

import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayDeque;

/**
 * 修复 Embeddium 渲染管线导致 PoseStack 被意外弹空后崩溃。
 * m_85836_ = popPose() (void)，Embeddium MatrixStackMixin 在 popPose 中
 * 注入了 getLast() 调用，导致空栈时 ArrayDeque.getLast() 抛出 NoSuchElementException。
 */
@Mixin(value = PoseStack.class, remap = false)
public abstract class PoseStackMixin {

    @Unique
    private static Field yzzzfix$poseStackField;
    @Unique
    private static Constructor<PoseStack.Pose> yzzzfix$poseCtor;

    @Unique
    private static Field yzzzfix$getField() {
        for (String name : new String[]{"f_85834_", "poseStack"}) {
            try {
                Field f = PoseStack.class.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException ignored) {}
        }
        return null;
    }

    @Unique
    private static Constructor<PoseStack.Pose> yzzzfix$getCtor() {
        try {
            Constructor<PoseStack.Pose> c = PoseStack.Pose.class.getDeclaredConstructor(Matrix4f.class, Matrix3f.class);
            c.setAccessible(true);
            return c;
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    @Inject(method = "m_85836_", at = @At("HEAD"))
    private void yzzzfix$ensureNotEmpty(CallbackInfo ci) {
        try {
            if (yzzzfix$poseStackField == null) {
                yzzzfix$poseStackField = yzzzfix$getField();
            }
            if (yzzzfix$poseCtor == null) {
                yzzzfix$poseCtor = yzzzfix$getCtor();
            }
            if (yzzzfix$poseStackField != null && yzzzfix$poseCtor != null) {
                ArrayDeque<PoseStack.Pose> deque = (ArrayDeque<PoseStack.Pose>) yzzzfix$poseStackField.get(this);
                if (deque.isEmpty()) {
                    deque.addLast(yzzzfix$poseCtor.newInstance(new Matrix4f(), new Matrix3f()));
                }
            }
        } catch (Exception ignored) {}
    }
}
