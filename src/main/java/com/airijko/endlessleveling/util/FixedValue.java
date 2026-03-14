package com.airijko.endlessleveling.util;

/**
 * Centralized fixed values that must stay consistent across chat/UI flows.
 */
public enum FixedValue {
    ROOT_COMMAND("/lvl"),
    CHAT_PREFIX("[EndlessLeveling] ");

    private final String value;

    FixedValue(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
