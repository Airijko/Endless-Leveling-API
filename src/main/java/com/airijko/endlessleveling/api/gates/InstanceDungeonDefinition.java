package com.airijko.endlessleveling.api.gates;

public record InstanceDungeonDefinition(
        String dungeonId,
        String routingTemplateName,
        String basePortalBlockId,
        String worldNameToken,
        String displayName,
        String legacyTemplateName,
        String spawnSuffix) {
}
