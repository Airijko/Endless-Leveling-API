package com.airijko.endlessleveling.commands.classes;

import com.airijko.endlessleveling.classes.CharacterClassDefinition;
import com.airijko.endlessleveling.classes.ClassManager;
import com.airijko.endlessleveling.player.PlayerData;

import java.util.Locale;

/**
 * Helpers for verifying that a player's primary class belongs to the Bard
 * progression path (any tier: bard, bard_elite, bard_master, ...).
 */
final class BardClassCheck {

    private BardClassCheck() {
    }

    static boolean isBard(ClassManager classManager, PlayerData data) {
        if (classManager == null || data == null) {
            return false;
        }
        CharacterClassDefinition primary = classManager.getPlayerPrimaryClass(data);
        if (primary == null) {
            return false;
        }
        if (primary.getAscension() != null) {
            String path = primary.getAscension().getPath();
            if (path != null && path.equalsIgnoreCase("bard")) {
                return true;
            }
        }
        String id = primary.getId();
        if (id == null) {
            return false;
        }
        String normalized = id.toLowerCase(Locale.ROOT);
        return normalized.equals("bard") || normalized.startsWith("bard_");
    }
}
