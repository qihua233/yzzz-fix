package me.realseek.yzzzfix.mixin.embed;

import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayDeque;

/**
 * 修复 Embeddium/Oculus 渲染管线导致 PoseStack 被意外弹空后，
 * {@code last()} 调用 {@code ArrayDeque.getLast()} 抛出
 * {@code NoSuchElementException} 的崩溃。
 *
 * <p>正常渲染流程中 PoseStack 永远至少含有一个 Pose，空栈属异常状态。
 * 本 Mixin 在 {@code last()} 被调用时检测空栈并补入单位矩阵，
 * 保证渲染循环不中断，副作用极小。</p>
 *
 * <p>内部字段与构造器使用反射访问，避免依赖 refmap。
 * 字段名同时尝试 SRG (f_85834_) 和 MCP (poseStack)，兼容开发与生产环境。</p>
 */
@Mixin(value = PoseStack.class, remap = false)
public abstract class PoseStackMixin {

    @Unique
    private static Field yzzzfix$poseStackField;
    @Unique
    private static Constructor<PoseStack.Pose> yzzzfix$poseCtor;

    @Unique
    private static Field yzzzfix$getPoseStackField() {
        for (String name : new String[]{"f_85834_", "poseStack"}) {
            try {
                Field f = PoseStack.class.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException ignored) {
            }
        }
        return null;
    }

    @Unique
    private static Constructor<PoseStack.Pose> yzzzfix$getPoseCtor() {
        try {
            Constructor<PoseStack.Pose> ctor =
                    PoseStack.Pose.class.getDeclaredConstructor(Matrix4f.class, Matrix3f.class);
            ctor.setAccessible(true);
            return ctor;
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    @Inject(
            method = {
                    "last()Lcom/mojang/blaze3d/vertex/PoseStack$Pose;",
                    "m_85850_()Lcom/mojang/blaze3d/vertex/PoseStack$Pose;"
            },
            at = @At("HEAD")
    )
    private void yzzzfix$ensurePoseNotEmpty(CallbackInfoReturnable<PoseStack.Pose> cir) {
        try {
            if (yzzzfix$poseStackField == null) {
                yzzzfix$poseStackField = yzzzfix$getPoseStackField();
            }
            if (yzzzfix$poseCtor == null) {
                yzzzfix$poseCtor = yzzzfix$getPoseCtor();
            }
            if (yzzzfix$poseStackField != null && yzzzfix$poseCtor != null) {
                ArrayDeque<PoseStack.Pose> deque =
                        (ArrayDeque<PoseStack.Pose>) yzzzfix$poseStackField.get(this);
                if (deque.isEmpty()) {
                    deque.addLast(yzzzfix$poseCtor.newInstance(new Matrix4f(), new Matrix3f()));
                }
            }
        } catch (Exception ignored) {
        }
    }
}
