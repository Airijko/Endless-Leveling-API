package com.airijko.endlessleveling.classes;

import com.airijko.endlessleveling.enums.ClassWeaponType;
import com.hypixel.hytale.server.core.inventory.ItemStack;

/**
 * Utility helpers for deriving weapon categories from player equipment.
 */
public final class ClassWeaponResolver {

    private static volatile WeaponConfig weaponConfig = WeaponConfig.empty();

    private ClassWeaponResolver() {
    }

    /** Configure resolver with optional weapons.yml mapping. */
    public static void configure(WeaponConfig config) {
        weaponConfig = config == null ? WeaponConfig.empty() : config;
    }

    public static String resolveCategoryKey(ItemStack stack) {
        if (stack == null || ItemStack.isEmpty(stack)) {
            return WeaponConfig.normalizeCategoryKey(ClassWeaponType.UNARMED.getConfigKey());
        }
        String itemId = stack.getItemId();
        String configured = weaponConfig.resolveCategory(itemId);
        if (configured != null) {
            return configured;
        }
        ClassWeaponType detected = ClassWeaponType.fromItemId(itemId);
        if (detected != null) {
            return WeaponConfig.normalizeCategoryKey(detected.getConfigKey());
        }
        return WeaponConfig.normalizeCategoryKey(ClassWeaponType.UNARMED.getConfigKey());
    }

    public static ClassWeaponType resolve(ItemStack stack) {
        if (stack == null || ItemStack.isEmpty(stack)) {
            return ClassWeaponType.UNARMED;
        }
        String itemId = stack.getItemId();
        String configuredCategory = weaponConfig.resolveCategory(itemId);
        if (configuredCategory != null) {
            ClassWeaponType configuredType = ClassWeaponType.fromConfigKey(configuredCategory);
            if (configuredType != null) {
                return configuredType;
            }
        }
        ClassWeaponType detected = ClassWeaponType.fromItemId(itemId);
        return detected != null ? detected : ClassWeaponType.UNARMED;
    }
}
