package me.realseek.yzzzfix.module.elementalcombat_jade;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ElementalCombatJadeConfig {

    private static final Logger LOGGER = LogManager.getLogger("YzzzFix/ElementalCombatJade");
    private static final Path CONFIG_PATH = Paths.get("config", "yzzz-fix-elementalcombat.yaml");

    private static volatile ElementalCombatJadeConfig instance;

    private String attackLabel = "";
    private String defenseLabel = "";
    private int iconLeftPaddingPx = 2;
    private int minIconTextGapPx = 4;

    private ElementalCombatJadeConfig() {}

    public static ElementalCombatJadeConfig get() {
        if (instance == null) {
            synchronized (ElementalCombatJadeConfig.class) {
                if (instance == null) {
                    instance = loadOrCreate();
                }
            }
        }
        return instance;
    }

    public String getAttackLabel() { return attackLabel; }
    public String getDefenseLabel() { return defenseLabel; }
    public int getIconLeftPaddingPx() { return iconLeftPaddingPx; }
    public int getMinIconTextGapPx() { return minIconTextGapPx; }

    public boolean hasCustomAttackLabel() {
        return attackLabel != null && !attackLabel.isEmpty();
    }

    public boolean hasCustomDefenseLabel() {
        return defenseLabel != null && !defenseLabel.isEmpty();
    }

    private static ElementalCombatJadeConfig loadOrCreate() {
        ElementalCombatJadeConfig config = new ElementalCombatJadeConfig();

        if (Files.exists(CONFIG_PATH)) {
            try {
                Map<String, String> flat = parseYaml(CONFIG_PATH);
                config.attackLabel = flat.getOrDefault("labels.attack", "");
                config.defenseLabel = flat.getOrDefault("labels.defense", "");
                config.iconLeftPaddingPx = parseIntOrDefault(flat.get("spacing.icon_left_padding_px"), 2);
                config.minIconTextGapPx = parseIntOrDefault(flat.get("spacing.min_icon_text_gap_px"), 4);
                LOGGER.info("Loaded config from {}", CONFIG_PATH);
                return config;
            } catch (Exception e) {
                LOGGER.warn("Failed to read {}, regenerating.", CONFIG_PATH, e);
            }
        }

        writeDefault();
        return config;
    }

    private static Map<String, String> parseYaml(Path path) throws IOException {
        Map<String, String> result = new LinkedHashMap<>();
        String currentSection = "";

        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;

                int colonIdx = trimmed.indexOf(':');
                if (colonIdx < 0) continue;

                String key = trimmed.substring(0, colonIdx).trim();
                String value = trimmed.substring(colonIdx + 1).trim();

                if (value.isEmpty()) {
                    // section header
                    currentSection = key + ".";
                } else {
                    // strip quotes
                    if ((value.startsWith("\"") && value.endsWith("\""))
                            || (value.startsWith("'") && value.endsWith("'"))) {
                        value = value.substring(1, value.length() - 1);
                    }
                    String fullKey = line.startsWith(" ") || line.startsWith("\t")
                            ? currentSection + key
                            : key;
                    result.put(fullKey, value);
                }
            }
        }
        return result;
    }

    private static int parseIntOrDefault(String value, int def) {
        if (value == null || value.isEmpty()) return def;
        try { return Integer.parseInt(value); }
        catch (NumberFormatException e) { return def; }
    }

    private static void writeDefault() {
        try {
            Path parent = CONFIG_PATH.getParent();
            if (parent != null) Files.createDirectories(parent);

            try (Writer w = Files.newBufferedWriter(CONFIG_PATH, StandardCharsets.UTF_8)) {
                w.write("# Elemental Combat Jade 修复配置\n");
                w.write("# 由 yzzz-fix 生成，修改后需重启游戏\n");
                w.write("#\n");
                w.write("# labels: 自定义 Jade tooltip 中的标签文本\n");
                w.write("#   留空则使用 lang 文件翻译 (推荐)\n");
                w.write("#   填写则强制使用此处的值，优先级高于 lang\n");
                w.write("#\n");
                w.write("# spacing: 图标与文本的间距参数 (单位: 像素)\n");
                w.write("#   icon_left_padding_px: 图标距标签文字右侧的偏移\n");
                w.write("#   min_icon_text_gap_px: 图标右侧与百分比文本的最小间隙\n");
                w.write("\n");
                w.write("labels:\n");
                w.write("  attack: \"\"\n");
                w.write("  defense: \"\"\n");
                w.write("\n");
                w.write("spacing:\n");
                w.write("  icon_left_padding_px: 2\n");
                w.write("  min_icon_text_gap_px: 4\n");
            }
            LOGGER.info("Generated default config at {}", CONFIG_PATH);
        } catch (IOException e) {
            LOGGER.error("Failed to write config to {}", CONFIG_PATH, e);
        }
    }
}
