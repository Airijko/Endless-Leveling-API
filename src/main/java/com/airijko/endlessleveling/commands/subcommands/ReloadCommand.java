package com.airijko.endlessleveling.commands.subcommands;

import com.airijko.endlessleveling.EndlessLeveling;
import com.airijko.endlessleveling.augments.AugmentManager;
import com.airijko.endlessleveling.augments.AugmentUnlockManager;
import com.airijko.endlessleveling.classes.ClassWeaponResolver;
import com.airijko.endlessleveling.classes.WeaponConfig;
import com.airijko.endlessleveling.classes.ClassManager;
import com.airijko.endlessleveling.managers.ConfigManager;
import com.airijko.endlessleveling.managers.EventHookManager;
import com.airijko.endlessleveling.leveling.LevelingManager;
import com.airijko.endlessleveling.managers.LoggingManager;
import com.airijko.endlessleveling.managers.LanguageManager;
import com.airijko.endlessleveling.leveling.MobLevelingManager;
import com.airijko.endlessleveling.managers.PluginFilesManager;
import com.airijko.endlessleveling.player.PlayerDataManager;
import com.airijko.endlessleveling.races.RaceManager;
import com.airijko.endlessleveling.player.SkillManager;
import com.airijko.endlessleveling.mob.MobLevelingSystem;
import com.airijko.endlessleveling.ui.PlayerHud;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.airijko.endlessleveling.util.OperatorHelper;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.concurrent.CompletableFuture;

public class ReloadCommand extends AbstractCommand {

    private final ConfigManager configManager;
    private final LanguageManager languageManager;
    private final LevelingManager levelingManager;
    private final MobLevelingManager mobLevelingManager;
    private final RaceManager raceManager;
    private final ClassManager classManager;
    private final PlayerDataManager playerDataManager;
    private final SkillManager skillManager;
    private final AugmentManager augmentManager;
    private final AugmentUnlockManager augmentUnlockManager;
    private final EventHookManager eventHookManager;
    private final MobLevelingSystem mobLevelingSystem;
    private final PluginFilesManager filesManager;

    public ReloadCommand() {
        super("reload", "Reload EndlessLeveling configs, races, and classes");
        EndlessLeveling plugin = EndlessLeveling.getInstance();
        this.configManager = plugin != null ? plugin.getConfigManager() : null;
        this.languageManager = plugin != null ? plugin.getLanguageManager() : null;
        this.levelingManager = plugin != null ? plugin.getLevelingManager() : null;
        this.mobLevelingManager = plugin != null ? plugin.getMobLevelingManager() : null;
        this.raceManager = plugin != null ? plugin.getRaceManager() : null;
        this.classManager = plugin != null ? plugin.getClassManager() : null;
        this.playerDataManager = plugin != null ? plugin.getPlayerDataManager() : null;
        this.skillManager = plugin != null ? plugin.getSkillManager() : null;
        this.augmentManager = plugin != null ? plugin.getAugmentManager() : null;
        this.augmentUnlockManager = plugin != null ? plugin.getAugmentUnlockManager() : null;
        this.eventHookManager = plugin != null ? plugin.getEventHookManager() : null;
        this.mobLevelingSystem = plugin != null ? plugin.getMobLevelingSystem() : null;
        this.filesManager = plugin != null ? plugin.getFilesManager() : null;
    }

    @Nullable
    @Override
    protected CompletableFuture<Void> execute(@Nonnull CommandContext commandContext) {
        if (commandContext.sender() instanceof Player player) {
            PlayerRef senderRef = Universe.get().getPlayer(player.getUuid());
            if (OperatorHelper.denyNonAdmin(senderRef)) return CompletableFuture.completedFuture(null);
        }

        if (configManager != null) {
            configManager.load();
            LoggingManager.configureFromConfig(configManager);
        }

        if (filesManager != null) {
            ClassWeaponResolver.configure(WeaponConfig.load(filesManager.getWeaponsFile()));
        }

        if (languageManager != null) {
            languageManager.reload();
        }

        if (levelingManager != null) {
            levelingManager.reloadConfig();
        }

        if (mobLevelingManager != null) {
            mobLevelingManager.reloadConfig();
        }
        if (mobLevelingSystem != null) {
            mobLevelingSystem.requestFullMobRescale();
        }

        if (skillManager != null) {
            skillManager.reload();
        }

        if (raceManager != null) {
            raceManager.reload();
        }

        if (classManager != null) {
            classManager.reload();
        }

        if (playerDataManager != null) {
            for (var data : playerDataManager.getAllCached()) {
                playerDataManager.save(data);
            }
        }

        if (augmentManager != null) {
            augmentManager.load();
        }

        if (augmentUnlockManager != null) {
            augmentUnlockManager.reload();
            if (playerDataManager != null) {
                for (var data : playerDataManager.getAllCached()) {
                    augmentUnlockManager.ensureUnlocks(data);
                }
            }
        }

        if (eventHookManager != null) {
            eventHookManager.reload();
        }

        commandContext.sendMessage(
                Message.raw("EndlessLeveling reloaded; mob levels and modifiers are being reapplied.")
                        .color("#6cff78"));

        // Refresh HUDs to reflect any mode/range changes.
        PlayerHud.refreshAll();
        return CompletableFuture.completedFuture(null);
    }

}
