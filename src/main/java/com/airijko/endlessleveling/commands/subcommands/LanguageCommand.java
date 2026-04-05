package com.airijko.endlessleveling.commands.subcommands;

import com.airijko.endlessleveling.EndlessLeveling;
import com.airijko.endlessleveling.player.PlayerData;
import com.airijko.endlessleveling.managers.LanguageManager;
import com.airijko.endlessleveling.player.PlayerDataManager;
import com.airijko.endlessleveling.ui.PlayerHud;
import com.airijko.endlessleveling.util.Lang;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Locale;

public class LanguageCommand extends AbstractPlayerCommand {

    private final PlayerDataManager playerDataManager;
    private final LanguageManager languageManager;

    public LanguageCommand() {
        super("lang", "Manage your personal EndlessLeveling language (list, set)");
        this.addAliases("language", "translate", "translation");
        EndlessLeveling plugin = EndlessLeveling.getInstance();
        this.playerDataManager = plugin != null ? plugin.getPlayerDataManager() : null;
        this.languageManager = plugin != null ? plugin.getLanguageManager() : null;
        this.addSubCommand(new ListSubCommand());
        this.addSubCommand(new SetSubCommand());
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void execute(@Nonnull CommandContext commandContext,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef senderRef,
            @Nonnull World world) {
        sendUsage(senderRef);
    }

    private void sendLocaleList(PlayerRef senderRef, PlayerData data) {
        List<String> available = languageManager.getAvailableLocales();
        senderRef.sendMessage(Message.raw(Lang.tr(senderRef.getUuid(),
                "command.language.current",
                "Current language: {0}",
                data.getLanguage())).color("#4fd7f7"));

        senderRef.sendMessage(Message.raw(Lang.tr(senderRef.getUuid(),
                "command.language.available",
                "Available locales: {0}",
                String.join(", ", available))).color("#4fd7f7"));
    }

    private void sendUsage(PlayerRef senderRef) {
        senderRef.sendMessage(Lang.message("command.language.usage").color("#ffcc66"));
    }

    private void applyLocaleChange(PlayerRef senderRef, PlayerData data, String requestedLocale) {
        if (requestedLocale == null || requestedLocale.isBlank()) {
            sendUsage(senderRef);
            return;
        }

        String normalizedRequested = normalizeLocale(requestedLocale);
        if (!languageManager.isLocaleAvailable(normalizedRequested)) {
            List<String> available = languageManager.getAvailableLocales();
            senderRef.sendMessage(Message.raw(Lang.tr(senderRef.getUuid(),
                    "command.language.invalid",
                    "Unknown locale '{0}'. Available locales: {1}",
                    normalizedRequested,
                    String.join(", ", available))).color("#ff6666"));
            return;
        }

        data.setLanguage(normalizedRequested);
        playerDataManager.save(data);
        languageManager.invalidateLocaleCache(normalizedRequested);
        PlayerHud.refreshAll();

        senderRef.sendMessage(Message.raw(Lang.tr(senderRef.getUuid(),
                "command.language.updated",
                "Language set to {0}.",
                data.getLanguage())).color("#6cff78"));
    }

    private boolean ensureManagersReady(PlayerRef senderRef) {
        if (playerDataManager != null && languageManager != null) {
            return true;
        }
        senderRef.sendMessage(Lang.message("command.language.unavailable")
                .color("#ff6666"));
        return false;
    }

    private PlayerData requirePlayerData(PlayerRef senderRef) {
        PlayerData data = playerDataManager.get(senderRef.getUuid());
        if (data != null) {
            return data;
        }
        senderRef.sendMessage(Lang.message("command.language.data_not_loaded")
                .color("#ff6666"));
        return null;
    }

    private final class ListSubCommand extends AbstractPlayerCommand {
        private ListSubCommand() {
            super("list", "List available language locales");
        }

        @Override
        protected boolean canGeneratePermission() {
            return false;
        }

        @Override
        protected void execute(@Nonnull CommandContext commandContext,
                @Nonnull Store<EntityStore> store,
                @Nonnull Ref<EntityStore> ref,
                @Nonnull PlayerRef senderRef,
                @Nonnull World world) {
            if (!ensureManagersReady(senderRef)) {
                return;
            }
            PlayerData data = requirePlayerData(senderRef);
            if (data == null) {
                return;
            }
            sendLocaleList(senderRef, data);
        }
    }

    private final class SetSubCommand extends AbstractPlayerCommand {
        private final RequiredArg<String> localeArg = this.withRequiredArg("locale",
                "Language locale (example: en_US)",
                ArgTypes.STRING);

        private SetSubCommand() {
            super("set", "Set your personal language locale");
        }

        @Override
        protected boolean canGeneratePermission() {
            return false;
        }

        @Override
        protected void execute(@Nonnull CommandContext commandContext,
                @Nonnull Store<EntityStore> store,
                @Nonnull Ref<EntityStore> ref,
                @Nonnull PlayerRef senderRef,
                @Nonnull World world) {
            if (!ensureManagersReady(senderRef)) {
                return;
            }
            PlayerData data = requirePlayerData(senderRef);
            if (data == null) {
                return;
            }
            applyLocaleChange(senderRef, data, localeArg.get(commandContext));
        }
    }

    private String normalizeLocale(String raw) {
        if (raw == null || raw.isBlank()) {
            return "en_US";
        }
        String trimmed = raw.trim().replace('-', '_');
        String[] parts = trimmed.split("_");
        if (parts.length == 1) {
            return parts[0].toLowerCase(java.util.Locale.ROOT);
        }
        return parts[0].toLowerCase(java.util.Locale.ROOT) + "_" + parts[1].toUpperCase(java.util.Locale.ROOT);
    }
}
