package com.airijko.endlessleveling.augments;

import com.airijko.endlessleveling.enums.PassiveCategory;
import com.airijko.endlessleveling.enums.PassiveTier;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;

public final class AugmentParser {

    private AugmentParser() {
    }

    @SuppressWarnings("unchecked")
    public static AugmentDefinition parse(Path file, Yaml yaml) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            Map<String, Object> root = yaml.load(in);
            if (root == null) {
                root = Collections.emptyMap();
            }
            String id = stringVal(root.get("id"), stripExtension(file.getFileName().toString()));
            String name = stringVal(root.get("name"), id);
            String description = stringVal(root.get("description"), "");
            PassiveTier tier = PassiveTier.fromConfig(root.get("tier"), PassiveTier.COMMON);
            PassiveCategory category = PassiveCategory.fromConfig(root.get("category"), null);
            boolean stackable = booleanVal(root.get("stackable"), false);
            Object passivesNode = root.getOrDefault("passives", Collections.emptyMap());
            Map<String, Object> passives = passivesNode instanceof Map<?, ?> m
                    ? (Map<String, Object>) m
                    : Collections.emptyMap();
            return new AugmentDefinition(id, name, tier, category, stackable, description, passives);
        }
    }

    private static String stripExtension(String filename) {
        int idx = filename.lastIndexOf('.');
        return idx > 0 ? filename.substring(0, idx) : filename;
    }

    private static String stringVal(Object raw, String fallback) {
        if (raw instanceof String str && !str.isBlank()) {
            return str.trim();
        }
        return fallback;
    }

    private static boolean booleanVal(Object raw, boolean fallback) {
        if (raw instanceof Boolean bool) {
            return bool;
        }
        if (raw instanceof String str && !str.isBlank()) {
            return Boolean.parseBoolean(str.trim());
        }
        if (raw instanceof Number number) {
            return number.intValue() != 0;
        }
        return fallback;
    }
}
