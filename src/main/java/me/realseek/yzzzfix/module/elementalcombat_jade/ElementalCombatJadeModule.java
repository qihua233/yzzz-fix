package me.realseek.yzzzfix.module.elementalcombat_jade;

import me.realseek.yzzzfix.module.ModuleRuntimeHooks;

public final class ElementalCombatJadeModule implements ModuleRuntimeHooks {

    public static final ElementalCombatJadeModule INSTANCE = new ElementalCombatJadeModule();

    private ElementalCombatJadeModule() {}

    @Override
    public void initClient() {
        ElementalCombatJadeConfig.get();
    }
}
