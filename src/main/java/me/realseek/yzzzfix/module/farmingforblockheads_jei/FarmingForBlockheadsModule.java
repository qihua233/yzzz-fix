package me.realseek.yzzzfix.module.farmingforblockheads_jei;

import me.realseek.yzzzfix.YzzzFix;
import me.realseek.yzzzfix.module.ModuleRuntimeHooks;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.DistExecutor;

/**
 * Farming for Blockheads 修复模块的运行时钩子。
 * <p>负责注册客户端事件处理器、服务端事件处理器，
 * 并在 JEI 运行时可用时触发配方注册。</p>
 */
public final class FarmingForBlockheadsModule implements ModuleRuntimeHooks {

    public static final FarmingForBlockheadsModule INSTANCE = new FarmingForBlockheadsModule();

    private FarmingForBlockheadsModule() {}

    @Override
    public void initCommon() {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> MinecraftForge.EVENT_BUS.register(FarmingForBlockheadsClientEventHandler.class));
        MinecraftForge.EVENT_BUS.register(FarmingForBlockheadsServerEventHandler.class);
        YzzzFix.LOGGER.info("Farming for Blockheads JEI module initialized.");
    }

    @Override
    public void onJeiRuntimeAvailable(IJeiRuntime jeiRuntime) {
        FarmingForBlockheadsJeiRuntimeHolder.setRuntime(jeiRuntime);
        FarmingForBlockheadsRecipeRegistrar.tryRegister();
    }

    @Override
    public void onJeiRuntimeUnavailable() {
        FarmingForBlockheadsJeiRuntimeHolder.setRuntime(null);
        FarmingForBlockheadsRecipeRegistrar.reset();
    }
}