package com.airijko.endlessleveling.api.gates;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Describes an addon-registered instance dungeon that Endless Leveling can route into.
 */
public record InstanceDungeonDefinition(
        @Nonnull String dungeonId,
        @Nonnull String routingTemplateName,
        @Nonnull String basePortalBlockId,
        @Nonnull String worldNameToken,
        @Nonnull String displayName,
        @Nullable String legacyTemplateName,
        @Nullable String spawnSuffix) {

    public InstanceDungeonDefinition {
        requireText(dungeonId, "dungeonId");
        requireText(routingTemplateName, "routingTemplateName");
        requireText(basePortalBlockId, "basePortalBlockId");
        requireText(worldNameToken, "worldNameToken");
        requireText(displayName, "displayName");
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}