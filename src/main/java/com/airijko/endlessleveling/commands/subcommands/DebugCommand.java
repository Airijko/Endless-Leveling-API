package com.airijko.endlessleveling.commands.subcommands;

import com.airijko.endlessleveling.EndlessLeveling;
import com.airijko.endlessleveling.leveling.MobLevelingManager;
import com.airijko.endlessleveling.mob.MobLevelingSystem;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;

import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.airijko.endlessleveling.util.OperatorHelper;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;

public class DebugCommand extends AbstractPlayerCommand {

    private final MobLevelingManager mobLevelingManager;
    private final MobLevelingSystem mobLevelingSystem;

    public DebugCommand() {
        super("debug", "Debug tools");
        this.mobLevelingManager = EndlessLeveling.getInstance() != null
                ? EndlessLeveling.getInstance().getMobLevelingManager()
                : null;
        this.mobLevelingSystem = EndlessLeveling.getInstance() != null
                ? EndlessLeveling.getInstance().getMobLevelingSystem()
                : null;
        this.addSubCommand(new StatTestCommand());
        this.addSubCommand(new DistanceCenterSubCommand());
        this.addSubCommand(new MobLevelsSubCommand());
        this.addSubCommand(new HStatSubCommand());
    }

    @Override
    protected void execute(@Nonnull CommandContext commandContext,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef senderRef,
            @Nonnull World world) {
        senderRef.sendMessage(Message.raw("Usage: /el debug stattest | /el debug distancecenter | /el debug moblevels | /el debug hstat")
                .color("#ffcc66"));
    }

    private final class HStatSubCommand extends AbstractPlayerCommand {

        private static final String HSTATS_HEALTHCHECK_URL = "https://api.hstats.dev/api/";

        private HStatSubCommand() {
            super("hstat", "Check HStats API connectivity from this server");
            this.addAliases("hstats", "metricstest", "metricsping");
        }

        @Override
        protected void execute(@Nonnull CommandContext commandContext,
                @Nonnull Store<EntityStore> store,
                @Nonnull Ref<EntityStore> ref,
                @Nonnull PlayerRef senderRef,
                @Nonnull World world) {
            senderRef.sendMessage(Message.raw("Checking HStats connectivity...").color("#9fb6d3"));

            long startedAt = System.nanoTime();
            try {
                URL url = URI.create(HSTATS_HEALTHCHECK_URL).toURL();
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(4000);
                connection.setReadTimeout(4000);
                connection.setUseCaches(false);

                int responseCode = connection.getResponseCode();
                long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000L;
                connection.disconnect();

                senderRef.sendMessage(Message.raw(
                        "HStats reachable: HTTP " + responseCode + " in " + elapsedMs
                                + "ms (endpoint: " + HSTATS_HEALTHCHECK_URL + ")")
                        .color("#6cff78"));
                senderRef.sendMessage(Message.raw(
                        "If startup log also shows 'HStats analytics initialized', metrics should be active.")
                        .color("#4fd7f7"));
            } catch (Exception ex) {
                long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000L;
                senderRef.sendMessage(Message.raw(
                        "HStats connection failed after " + elapsedMs + "ms: " + ex.getClass().getSimpleName()
                                + " - " + sanitize(ex.getMessage()))
                        .color("#ff6666"));
                senderRef.sendMessage(Message.raw(
                        "Check firewall/proxy/DNS and outbound HTTPS to api.hstats.dev:443.")
                        .color("#ff9900"));
            }
        }

        private String sanitize(String value) {
            if (value == null || value.isBlank()) {
                return "No additional error details.";
            }
            String cleaned = value.replace('\n', ' ').replace('\r', ' ').trim();
            return cleaned.length() > 180 ? cleaned.substring(0, 180) + "..." : cleaned;
        }
    }

    private final class DistanceCenterSubCommand extends AbstractPlayerCommand {

        private DistanceCenterSubCommand() {
            super("distancecenter", "Show resolved distance center for this world");
        }

        @Override
        protected void execute(@Nonnull CommandContext commandContext,
                @Nonnull Store<EntityStore> store,
                @Nonnull Ref<EntityStore> ref,
                @Nonnull PlayerRef senderRef,
                @Nonnull World world) {
            if (mobLevelingManager == null) {
                senderRef.sendMessage(Message.raw("MobLevelingManager is unavailable.").color("#ff6666"));
                return;
            }

            String details = mobLevelingManager.describeDistanceCenter(store, world);
            senderRef.sendMessage(Message.raw("Distance center debug: " + details).color("#4fd7f7"));
        }
    }

    private final class MobLevelsSubCommand extends AbstractPlayerCommand {

        private MobLevelsSubCommand() {
            super("moblevels", "Toggle mob leveling on or off in this world");
            this.addAliases("mobcleanup", "clearmobs", "mobclear", "mobtoggle");
        }

        @Override
        protected void execute(@Nonnull CommandContext commandContext,
                @Nonnull Store<EntityStore> store,
                @Nonnull Ref<EntityStore> ref,
                @Nonnull PlayerRef senderRef,
                @Nonnull World world) {
            if (OperatorHelper.denyNonAdmin(senderRef)) return;

            if (mobLevelingSystem == null) {
                senderRef.sendMessage(Message.raw("MobLevelingSystem is unavailable.").color("#ff6666"));
                return;
            }

            boolean nowSuppressed = mobLevelingSystem.debugToggleStore(store);
            senderRef.sendMessage(
                    Message.raw(nowSuppressed
                            ? "Mob leveling is now OFF for this world. Existing mob scaling and nameplates were cleared. Run the command again to turn it back ON."
                            : "Mob leveling is now ON for this world. A full rescale has been requested.")
                            .color("#6cff78"));
        }
    }
}
