package com.airijko.endlessleveling.commands.classes;

import com.airijko.endlessleveling.classes.CharacterClassDefinition;
import com.airijko.endlessleveling.classes.ClassManager;
import com.airijko.endlessleveling.ui.ClassPathsUIPage;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.concurrent.CompletableFuture;

public class ClassPathsCommand extends AbstractPlayerCommand {

    private final ClassManager classManager;
    private final OptionalArg<String> classArg = this.withOptionalArg("class", "Class identifier", ArgTypes.STRING);

    public ClassPathsCommand(ClassManager classManager) {
        super("paths", "Open the EndlessLeveling Class Paths page");
        this.classManager = classManager;
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void execute(@Nonnull CommandContext commandContext,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world) {
        Player player = commandContext.senderAs(Player.class);
        if (player == null) {
            return;
        }

        String requestedClassId = null;
        if (classArg.provided(commandContext)) {
            String requestedInput = classArg.get(commandContext);
            if (classManager == null || !classManager.isEnabled()) {
                playerRef.sendMessage(Message.raw("Classes are currently disabled.").color("#ff6666"));
                return;
            }

            CharacterClassDefinition definition = classManager.findClassByUserInput(requestedInput);
            if (definition == null) {
                playerRef.sendMessage(Message.raw("Unknown class: " + requestedInput).color("#ff6666"));
                return;
            }
            requestedClassId = definition.getId();
        }

        String finalRequestedClassId = requestedClassId;
        CompletableFuture.runAsync(() -> player.getPageManager().openCustomPage(ref, store,
                new ClassPathsUIPage(playerRef, CustomPageLifetime.CanDismiss, finalRequestedClassId)), world);
    }
}
