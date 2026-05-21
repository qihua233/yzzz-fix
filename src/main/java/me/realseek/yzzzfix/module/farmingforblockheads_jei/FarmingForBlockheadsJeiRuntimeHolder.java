package me.realseek.yzzzfix.module.farmingforblockheads_jei;

import mezz.jei.api.runtime.IJeiRuntime;

/**
 * 持有当前 JEI 运行时实例，供注册器安全访问。
 */
public final class FarmingForBlockheadsJeiRuntimeHolder {

    private static volatile IJeiRuntime runtime;

    private FarmingForBlockheadsJeiRuntimeHolder() {}

    public static void setRuntime(IJeiRuntime runtime) {
        FarmingForBlockheadsJeiRuntimeHolder.runtime = runtime;
    }

    public static IJeiRuntime getRuntime() {
        return runtime;
    }
}