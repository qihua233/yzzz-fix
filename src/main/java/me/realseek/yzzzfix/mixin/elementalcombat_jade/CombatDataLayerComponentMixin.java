package me.realseek.yzzzfix.mixin.elementalcombat_jade;

import me.realseek.yzzzfix.module.elementalcombat_jade.ElementalCombatJadeConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

/**
 * 修复 Elemental Combat Jade 插件中：
 * 1. Defense 行图标与百分比文本重叠
 * 2. "Attack:" / "Defense:" 标签不支持多语言
 *
 * 优先级: YAML 配置 > lang 翻译 > 原始英文
 */
@Mixin(targets = "Tavi007.ElementalCombat.client.CombatDataLayerComponent", remap = false)
public abstract class CombatDataLayerComponentMixin {

    @Unique
    private static final String YZZZFIX_ATTACK_KEY = "yzzzfix.elementalcombat.tooltip.attack";
    @Unique
    private static final String YZZZFIX_DEFENSE_KEY = "yzzzfix.elementalcombat.tooltip.defense";
    @Unique
    private static final String YZZZFIX_ORIGINAL_ATTACK = "Attack:";
    @Unique
    private static final String YZZZFIX_ORIGINAL_DEFENSE = "Defense:";
    @Unique
    private static final int YZZZFIX_ICON_WIDTH_PX = 8;
    @Unique
    private static final int YZZZFIX_FALLBACK_SPACE_WIDTH_PX = 4;

    // ── getTooltip: 翻译标签 + 扩展间距 ──

    @Inject(method = "getTooltip", at = @At("RETURN"), cancellable = true)
    private void yzzzfix$patchTooltip(CallbackInfoReturnable<List<String>> cir) {
        List<String> original = cir.getReturnValue();
        if (original == null || original.isEmpty()) return;

        String translatedAttack = yzzzfix$getAttackLabel();
        String translatedDefense = yzzzfix$getDefenseLabel();
        int targetSpaces = yzzzfix$targetDefenseSpaces();
        boolean changed = false;
        List<String> patched = new ArrayList<>(original.size());

        for (String line : original) {
            String fixed = line;
            fixed = yzzzfix$replaceLabel(fixed, YZZZFIX_ORIGINAL_ATTACK, translatedAttack);
            if (yzzzfix$containsIgnoringFormatting(fixed, YZZZFIX_ORIGINAL_DEFENSE)) {
                fixed = yzzzfix$replaceLabel(fixed, YZZZFIX_ORIGINAL_DEFENSE, translatedDefense);
                fixed = yzzzfix$expandSpacingAfterLabel(fixed, translatedDefense, targetSpaces);
            }
            if (!fixed.equals(line)) changed = true;
            patched.add(fixed);
        }

        if (changed) cir.setReturnValue(patched);
    }

    // ── getTextureData: 修正图标 X 坐标 ──

    @ModifyConstant(method = "getTextureData", constant = @Constant(stringValue = "Attack:"), require = 0)
    private String yzzzfix$translateAttackInTextureData(String original) {
        return yzzzfix$getAttackLabel();
    }

    @ModifyConstant(method = "getTextureData", constant = @Constant(stringValue = "Defense:"), require = 0)
    private String yzzzfix$translateDefenseInTextureData(String original) {
        return yzzzfix$getDefenseLabel();
    }

    // ── getWidth: 修正宽度计算 ──

    @ModifyConstant(method = "getWidth", constant = @Constant(stringValue = "Defense: -999%"), require = 0)
    private String yzzzfix$translateDefenseInGetWidth(String original) {
        return yzzzfix$getDefenseLabel() + " -999%";
    }

    @ModifyConstant(method = "getWidth", constant = @Constant(stringValue = "Attack:"), require = 0)
    private String yzzzfix$translateAttackInGetWidth(String original) {
        return yzzzfix$getAttackLabel();
    }

    // ── 工具方法 ──

    @Unique
    private static String yzzzfix$getAttackLabel() {
        ElementalCombatJadeConfig cfg = ElementalCombatJadeConfig.get();
        if (cfg.hasCustomAttackLabel()) return cfg.getAttackLabel();
        try {
            String translated = I18n.get(YZZZFIX_ATTACK_KEY);
            if (!translated.equals(YZZZFIX_ATTACK_KEY)) return translated;
        } catch (Throwable ignored) {}
        return YZZZFIX_ORIGINAL_ATTACK;
    }

    @Unique
    private static String yzzzfix$getDefenseLabel() {
        ElementalCombatJadeConfig cfg = ElementalCombatJadeConfig.get();
        if (cfg.hasCustomDefenseLabel()) return cfg.getDefenseLabel();
        try {
            String translated = I18n.get(YZZZFIX_DEFENSE_KEY);
            if (!translated.equals(YZZZFIX_DEFENSE_KEY)) return translated;
        } catch (Throwable ignored) {}
        return YZZZFIX_ORIGINAL_DEFENSE;
    }

    @Unique
    private static boolean yzzzfix$containsIgnoringFormatting(String text, String target) {
        if (text == null) return false;
        int i = 0;
        while (i < text.length()) {
            if (text.charAt(i) == '§' && i + 1 < text.length()) { i += 2; continue; }
            if (text.regionMatches(i, target, 0, target.length())) return true;
            i++;
        }
        return false;
    }

    @Unique
    private static String yzzzfix$replaceLabel(String text, String oldLabel, String newLabel) {
        if (text == null || oldLabel.equals(newLabel)) return text;
        int i = 0;
        while (i < text.length()) {
            if (text.charAt(i) == '§' && i + 1 < text.length()) { i += 2; continue; }
            if (text.regionMatches(i, oldLabel, 0, oldLabel.length())) {
                return text.substring(0, i) + newLabel + text.substring(i + oldLabel.length());
            }
            i++;
        }
        return text;
    }

    @Unique
    private static String yzzzfix$expandSpacingAfterLabel(String text, String label, int targetSpaces) {
        if (text == null) return text;
        int i = 0;
        while (i < text.length()) {
            if (text.charAt(i) == '§' && i + 1 < text.length()) { i += 2; continue; }
            if (text.regionMatches(i, label, 0, label.length())) {
                int spacesStart = i + label.length();
                int spacesEnd = spacesStart;
                while (spacesEnd < text.length() && text.charAt(spacesEnd) == ' ') spacesEnd++;
                int existingSpaces = spacesEnd - spacesStart;
                if (existingSpaces > 0 && existingSpaces < targetSpaces) {
                    return text.substring(0, spacesStart) + " ".repeat(targetSpaces) + text.substring(spacesEnd);
                }
                return text;
            }
            i++;
        }
        return text;
    }

    @Unique
    private static int yzzzfix$targetDefenseSpaces() {
        ElementalCombatJadeConfig cfg = ElementalCombatJadeConfig.get();
        int spaceWidth = yzzzfix$getSpaceWidthPx();
        int requiredPx = cfg.getIconLeftPaddingPx() + YZZZFIX_ICON_WIDTH_PX + cfg.getMinIconTextGapPx();
        return (requiredPx + spaceWidth - 1) / spaceWidth;
    }

    @Unique
    private static int yzzzfix$getSpaceWidthPx() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.font != null) return Math.max(1, mc.font.width(" "));
        } catch (Throwable ignored) {}
        return YZZZFIX_FALLBACK_SPACE_WIDTH_PX;
    }
}
