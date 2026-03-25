package com.airijko.endlessleveling.classes;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hypixel.hytale.logger.HytaleLogger;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Parsed view of weapons.json that allows explicit weapon ID or keyword routing
 * to a normalized weapon category key.
 */
public final class WeaponConfig {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClassFull();

    private final Map<String, String> exactIdMap;
    private final Map<String, String> keywordTokenMap;
    private final Map<String, String> keywordSubstringMap;

    private WeaponConfig(Map<String, String> exactIdMap,
            Map<String, String> keywordTokenMap,
            Map<String, String> keywordSubstringMap) {
        this.exactIdMap = exactIdMap;
        this.keywordTokenMap = keywordTokenMap;
        this.keywordSubstringMap = keywordSubstringMap;
    }

    public static WeaponConfig empty() {
        return new WeaponConfig(Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap());
    }

    public static WeaponConfig load(File file) {
        if (file == null) {
            return empty();
        }

        if (!file.exists()) {
            LOGGER.atWarning().log("weapons.json not found at %s; falling back to built-in resolver", file);
            return empty();
        }

        String fileName = file.getName().toLowerCase(Locale.ROOT);
        if (!fileName.endsWith(".json")) {
            LOGGER.atWarning().log("Unsupported weapons config extension for %s; expected .json", file);
            return empty();
        }

        return loadJson(file);
    }

    private static WeaponConfig loadJson(File file) {
        try (Reader reader = new FileReader(file)) {
            JsonElement rootElement = JsonParser.parseReader(reader);
            if (!(rootElement instanceof JsonObject rootObject)) {
                return empty();
            }

            JsonObject typesSection = extractTypesSection(rootObject);
            if (typesSection == null || typesSection.isEmpty()) {
                return empty();
            }

            Map<String, String> exactIds = new HashMap<>();
            Map<String, String> keywordTokens = new HashMap<>();
            Map<String, String> keywordSubstrings = new HashMap<>();

            for (Map.Entry<String, JsonElement> entry : typesSection.entrySet()) {
                String weaponCategory = normalizeCategoryKey(entry.getKey());
                if (weaponCategory == null) {
                    continue;
                }
                if (!(entry.getValue() instanceof JsonObject ruleNode) || ruleNode.isEmpty()) {
                    continue;
                }

                Set<String> ids = readStrings(ruleNode.get("ids"));
                for (String id : ids) {
                    String normalized = normalizeIdentifier(id);
                    if (normalized != null) {
                        exactIds.put(normalized, weaponCategory);
                    }
                }

                Set<String> keywords = readStrings(ruleNode.get("keywords"));
                for (String keyword : keywords) {
                    String normalized = normalizeToken(keyword);
                    if (normalized != null) {
                        keywordTokens.put(normalized, weaponCategory);
                        keywordSubstrings.put(normalized, weaponCategory);
                    }
                }
            }
            if (exactIds.isEmpty() && keywordTokens.isEmpty()) {
                return empty();
            }
            LOGGER.atInfo().log("Loaded %d weapon ids and %d keywords from weapons.json", exactIds.size(),
                    keywordTokens.size());
            return new WeaponConfig(exactIds, keywordTokens, keywordSubstrings);
        } catch (IOException | RuntimeException ex) {
            LOGGER.atWarning().log("Failed to load weapons.json: %s", ex.getMessage());
            return empty();
        }
    }

    public String resolveCategory(String itemId) {
        String normalized = normalizeIdentifier(itemId);
        if (normalized == null) {
            return null;
        }
        String byId = exactIdMap.get(normalized);
        if (byId != null) {
            return byId;
        }

        List<String> tokens = tokenize(normalized);
        for (String token : tokens) {
            String byToken = keywordTokenMap.get(token);
            if (byToken != null) {
                return byToken;
            }
        }
        for (Map.Entry<String, String> entry : keywordSubstringMap.entrySet()) {
            if (normalized.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static JsonObject extractTypesSection(JsonObject root) {
        JsonElement direct = root.get("types");
        if (direct instanceof JsonObject jsonObject) {
            return jsonObject;
        }
        return root;
    }

    private static Set<String> readStrings(JsonElement node) {
        Set<String> values = new HashSet<>();
        if (node == null || node.isJsonNull()) {
            return values;
        }
        if (node instanceof JsonArray array) {
            for (JsonElement value : array) {
                if (value == null || value.isJsonNull()) {
                    continue;
                }
                String normalized = normalizeToken(value.getAsString());
                if (normalized != null) {
                    values.add(normalized);
                }
            }
            return values;
        }
        String normalized = normalizeToken(node.getAsString());
        if (normalized != null) {
            values.add(normalized);
        }
        return values;
    }

    public static String normalizeCategoryKey(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.replace('-', '_')
                .replace(' ', '_')
                .toLowerCase(Locale.ROOT);
    }

    private static String normalizeIdentifier(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        int namespaceIndex = trimmed.lastIndexOf(':');
        if (namespaceIndex >= 0 && namespaceIndex < trimmed.length() - 1) {
            trimmed = trimmed.substring(namespaceIndex + 1);
        }
        return trimmed.replace('-', '_')
                .replace('.', '_')
                .replace(' ', '_')
                .toUpperCase(Locale.ROOT);
    }

    private static String normalizeToken(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.toUpperCase(Locale.ROOT);
    }

    private static List<String> tokenize(String normalized) {
        if (normalized == null || normalized.isEmpty()) {
            return Collections.emptyList();
        }
        String[] parts = normalized.split("[^A-Z0-9]+");
        List<String> tokens = new ArrayList<>(parts.length);
        for (String part : parts) {
            if (part == null || part.isBlank()) {
                continue;
            }
            tokens.add(part);
        }
        return tokens;
    }
}
