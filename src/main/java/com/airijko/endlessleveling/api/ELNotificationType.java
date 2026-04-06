package com.airijko.endlessleveling.api;

/**
 * Granular notification types that can be suppressed at runtime via
 * {@link EndlessLevelingAPI#suppressNotification(ELNotificationType)}.
 *
 * Suppressing a type prevents EL from sending that notification to players.
 * External mods can then send their own replacement messages if desired.
 */
public enum ELNotificationType {
    /** Level-up title splash ("You are now level X"). */
    LEVEL_UP_TITLE,

    /** Level-up skill-point notification popup ("You gained X skill points! Use /lvl to spend them."). */
    LEVEL_UP_SKILL_POINTS,

    /** XP gain notification popup ("Gained X XP!"). */
    XP_GAIN,

    /** Login reminder about unspent skill points (notification + chat). */
    UNSPENT_SKILL_POINTS,

    /** "You have augments available to choose from" chat message. */
    AUGMENT_AVAILABILITY,

    /** Augment proc/triggered chat messages. */
    AUGMENT_TRIGGERED,

    /** Prestige available title + chat message. */
    PRESTIGE_AVAILABLE,

    /** Passive ability trigger chat messages (Swiftness faded, Adrenaline, Healing Aura, etc.). */
    PASSIVE_TRIGGERED,

    /** Passive level-up chat messages ("Swiftness is now level 3"). */
    PASSIVE_LEVEL_UP,

    /** Passive regen notification popup. */
    PASSIVE_REGEN,

    /** Critical hit notification popup. */
    CRITICAL_HIT,

    /** Luck double-drop chat message. */
    LUCK_DOUBLE_DROP,

    /** Mob augment announcement chat message. */
    MOB_AUGMENT_ANNOUNCE,

    /** Tiered instance join info chat message ("Tier 2 | Mob Lv 12-18 | Boss Lv 20"). */
    DUNGEON_TIER_JOIN
}
