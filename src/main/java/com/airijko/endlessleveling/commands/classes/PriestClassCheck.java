package com.airijko.endlessleveling.commands.classes;

import com.airijko.endlessleveling.classes.CharacterClassDefinition;
import com.airijko.endlessleveling.classes.ClassManager;
import com.airijko.endlessleveling.player.PlayerData;

import java.util.Locale;

/**
 * Helpers for verifying that a player's primary class belongs to the Priest
 * progression path (any tier: priest, priest_elite, priest_master, ...).
 */
final class PriestClassCheck {

    private PriestClassCheck() {
    }

    static boolean isPriest(ClassManager classManager, PlayerData data) {
        if (classManager == null || data == null) {
            return false;
        }
        CharacterClassDefinition primary = classManager.getPlayerPrimaryClass(data);
        if (primary == null) {
            return false;
        }
        if (primary.getAscension() != null) {
            String path = primary.getAscension().getPath();
            if (path != null && path.equalsIgnoreCase("priest")) {
                return true;
            }
        }
        String id = primary.getId();
        if (id == null) {
            return false;
        }
        String normalized = id.toLowerCase(Locale.ROOT);
        return normalized.equals("priest") || normalized.startsWith("priest_");
    }
}
