package com.airijko.endlessleveling.enums.themes;

import com.airijko.endlessleveling.enums.SkillAttributeType;

import java.util.EnumMap;

/**
 * Shared attribute theme metadata used across profile/race UI sections.
 */
public enum AttributeTheme {
    LIFE_FORCE(SkillAttributeType.LIFE_FORCE,
            "LifeForce",
            "ui.skills.label.life_force",
            "Life Force",
            "ui.skills.description.life_force",
            "Boosts your total health so you can tank more hits.",
            "Potion_Health",
            "#7be0ff",
            "#4fd7f7",
            "#4fd7f7",
            "#8aa1bf"),
    STRENGTH(SkillAttributeType.STRENGTH,
            "Strength",
            "ui.skills.label.strength",
            "Strength",
            "ui.skills.description.strength",
            "Boosts physical damage for melee and ranged weapons.",
            "Weapon_Longsword_Adamantite_Saurian",
            "#ffb347",
            "#ffdf8f",
            "#ffdf8f",
            "#8aa1bf"),
    SORCERY(SkillAttributeType.SORCERY,
            "Sorcery",
            "ui.skills.label.sorcery",
            "Sorcery",
            "ui.skills.description.sorcery",
            "Boosts magic damage for staff weapons.",
            "Weapon_Staff_Mithril",
            "#d7baff",
            "#e8d5ff",
            "#e8d5ff",
            "#8aa1bf"),
    DEFENSE(SkillAttributeType.DEFENSE,
            "Defense",
            "ui.skills.label.defense",
            "Defense",
            "ui.skills.description.defense",
            "Cuts down incoming damage through resistances.",
            "Weapon_Shield_Orbis_Knight",
            "#8be0b2",
            "#6ee7b7",
            "#6ee7b7",
            "#8aa1bf"),
    HASTE(SkillAttributeType.HASTE,
            "Haste",
            "ui.skills.label.haste",
            "Haste",
            "ui.skills.description.haste",
            "Increases movement speed",
            "Spawn_Temple_Helix",
            "#ebe58b",
            "#e7eb8b",
            "#e7eb8b",
            "#8aa1bf"),
    PRECISION(SkillAttributeType.PRECISION,
            "Precision",
            "ui.skills.label.precision",
            "Precision",
            "ui.skills.description.precision",
            "Raises critical hit chance for every attack.",
            "Weapon_Shortbow_Combat",
            "#9ad4ff",
            "#7cb8ff",
            "#7cb8ff",
            "#8aa1bf"),
    FEROCITY(SkillAttributeType.FEROCITY,
            "Ferocity",
            "ui.skills.label.ferocity",
            "Ferocity",
            "ui.skills.description.ferocity",
            "Adds bonus damage to each critical strike.",
            "Weapon_Battleaxe_Mithril",
            "#ff7b7b",
            "#ff5555",
            "#ff5555",
            "#8aa1bf"),
    STAMINA(SkillAttributeType.STAMINA,
            "Stamina",
            "ui.skills.label.stamina",
            "Stamina",
            "ui.skills.description.stamina",
            "Expands stamina for dodges, blocks, and bursts.",
            "Potion_Stamina",
            "#ffc56f",
            "#ffad42",
            "#ffad42",
            "#8aa1bf"),
    FLOW(SkillAttributeType.FLOW,
            "Flow",
            "ui.skills.label.flow",
            "Flow",
            "ui.skills.description.flow",
            "Increases flow (mana) so spells and abilities stay online longer.",
            "Prototype_Tool_Book_Mana",
            "#9be3ff",
            "#7dd6ff",
            "#7dd6ff",
            "#8aa1bf"),
    DISCIPLINE(SkillAttributeType.DISCIPLINE,
            "Discipline",
            "ui.skills.label.discipline",
            "Discipline",
            "ui.skills.description.discipline",
            "Increases XP gain rate from all sources.",
            "Ingredient_Life_Essence",
            "#9ae984",
            "#9ae984",
            "#9ae984",
            "#8aa1bf");

    private static final EnumMap<SkillAttributeType, AttributeTheme> BY_TYPE = new EnumMap<>(SkillAttributeType.class);

    static {
        for (AttributeTheme theme : values()) {
            BY_TYPE.put(theme.type, theme);
        }
    }

    private final SkillAttributeType type;
    private final String uiSuffix;
    private final String labelKey;
    private final String labelFallback;
    private final String labelColor;
    private final String valueColor;
    private final String profileLevelColor;
    private final String descriptionKey;
    private final String descriptionFallback;
    private final String iconItemId;
    private final String raceNoteColor;

    AttributeTheme(SkillAttributeType type,
            String uiSuffix,
            String labelKey,
            String labelFallback,
            String descriptionKey,
            String descriptionFallback,
            String iconItemId,
            String labelColor,
            String valueColor,
            String profileLevelColor,
            String raceNoteColor) {
        this.type = type;
        this.uiSuffix = uiSuffix;
        this.labelKey = labelKey;
        this.labelFallback = labelFallback;
        this.labelColor = labelColor;
        this.valueColor = valueColor;
        this.profileLevelColor = profileLevelColor;
        this.raceNoteColor = raceNoteColor;
        this.descriptionKey = descriptionKey;
        this.descriptionFallback = descriptionFallback;
        this.iconItemId = iconItemId;
    }

    public String descriptionKey() {
        return descriptionKey;
    }

    public String descriptionFallback() {
        return descriptionFallback;
    }

    public String iconItemId() {
        return iconItemId;
    }

    public String uiSuffix() {
        return uiSuffix;
    }

    public String skillsLabelSelector() {
        return "#" + uiSuffix + "Label";
    }

    public String skillsValueSelector() {
        return "#" + uiSuffix + "Value";
    }

    public String skillsLevelPrefixSelector() {
        return "#" + uiSuffix + "LevelPrefix";
    }

    public String skillsLevelSelector() {
        return "#" + uiSuffix + "Level";
    }

    public String skillsDescriptionSelector() {
        return "#" + uiSuffix + "Description";
    }

    public String skillsIconSelector() {
        return "#" + uiSuffix + "Icon";
    }

    public SkillAttributeType type() {
        return type;
    }

    public String labelKey() {
        return labelKey;
    }

    public String labelFallback() {
        return labelFallback;
    }

    public String labelColor() {
        return labelColor;
    }

    public String valueColor() {
        return valueColor;
    }

    public String profileLevelColor() {
        return profileLevelColor;
    }

    public String raceNoteColor() {
        return raceNoteColor;
    }

    public String profileLabelSelector() {
        return "#Attribute" + uiSuffix + "Label";
    }

    public String profileValueSelector() {
        return "#Attribute" + uiSuffix + "Value";
    }

    public String profileLevelSelector() {
        return "#Attribute" + uiSuffix + "Level";
    }

    public String raceLabelSelector() {
        return "#RaceAttribute" + uiSuffix + "Label";
    }

    public String raceValueSelector() {
        return "#RaceAttribute" + uiSuffix + "Value";
    }

    public String raceNoteSelector() {
        return "#RaceAttribute" + uiSuffix + "Note";
    }

    public static AttributeTheme fromType(SkillAttributeType type) {
        return type == null ? null : BY_TYPE.get(type);
    }
}
