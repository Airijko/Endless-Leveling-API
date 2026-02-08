package com.airijko.endlessleveling.commands.subcommands;

import com.airijko.endlessleveling.EndlessLeveling;
import com.airijko.endlessleveling.managers.PassiveManager;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandUtil;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.permissions.HytalePermissions;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

/**
 * /skills resetcooldowns - admin utility that clears all passive cooldowns for
 * every cached player.
 */
public class ResetCooldownsCommand extends AbstractPlayerCommand {

    private static final String PERMISSION_NODE = HytalePermissions.fromCommand("endlessleveling.resetcooldowns");

    private final PassiveManager passiveManager;

    public ResetCooldownsCommand() {
        super("resetcooldowns", "Reset all passive cooldown timers for cached players");
        this.passiveManager = EndlessLeveling.getInstance().getPassiveManager();
        this.addAliases("cooldownsreset", "resetpassivecooldowns");
    }

    @Override
    protected void execute(@Nonnull CommandContext commandContext,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef senderRef,
            @Nonnull World world) {
        CommandUtil.requirePermission(commandContext.sender(), PERMISSION_NODE);

        if (passiveManager == null) {
            senderRef.sendMessage(Message.raw("Passive manager is unavailable.").color("#ff6666"));
            return;
        }

        int affected = passiveManager.resetAllPassiveCooldowns();
        senderRef.sendMessage(Message.raw("Reset passive cooldowns for " + affected + " player runtime state(s).")
                .color("#4fd7f7"));
    }
}
