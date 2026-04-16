package com.airijko.endlessleveling.ui;

import javax.annotation.Nonnull;

import java.util.List;

import com.airijko.endlessleveling.util.Lang;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * Servers page listing the official Endless servers as a carousel.
 * Mirrors the Classes / Races / Dungeons carousel layout.
 */
public class ServersUIPage extends InteractiveCustomUIPage<SkillsUIPage.Data> {

    private static final String CAROUSEL_CARD_TEMPLATE = "Pages/Servers/ServerCarouselCard.ui";

    public ServersUIPage(@Nonnull PlayerRef playerRef,
            @Nonnull CustomPageLifetime lifetime) {
        super(playerRef, lifetime, SkillsUIPage.Data.CODEC);
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref,
            @Nonnull UICommandBuilder rawUi,
            @Nonnull UIEventBuilder events,
            @Nonnull Store<EntityStore> store) {

        SafeUICommandBuilder ui = new SafeUICommandBuilder(rawUi);
        ui.append("Pages/Servers/ServersPage.ui");
        NavUIHelper.applyNavVersion(ui, playerRef, "servers",
                "Common/UI/Custom/Pages/Servers/ServersPage.ui",
                "#ServersTitle");
        NavUIHelper.bindNavEvents(events, "Common/UI/Custom/Pages/Servers/ServersPage.ui");

        ui.set("#CarouselSubheading.Text",
                Lang.tr(playerRef.getUuid(), "ui.servers.carousel.subtitle",
                        "Authorized partner servers running official Endless Leveling builds."));

        List<ServerEntry> servers = getOfficialServers();
        ui.set("#CarouselServerCount.Text",
                Lang.tr(playerRef.getUuid(), "ui.servers.count", "{0} {1}",
                        servers.size(),
                        servers.size() == 1
                                ? Lang.tr(playerRef.getUuid(), "ui.servers.count_word.singular", "server")
                                : Lang.tr(playerRef.getUuid(), "ui.servers.count_word.plural", "servers")));

        ui.clear("#CarouselCards");
        for (int i = 0; i < servers.size(); i++) {
            ServerEntry entry = servers.get(i);
            ui.append("#CarouselCards", CAROUSEL_CARD_TEMPLATE);
            String base = "#CarouselCards[" + i + "]";

            ui.set(base + " #CardTag.Text", entry.tag());
            ui.set(base + " #CardServerName.Text", entry.name());
            ui.set(base + " #CardHostLabel.Text",
                    Lang.tr(playerRef.getUuid(), "ui.servers.card.host_label", "Host"));
            ui.set(base + " #CardHost.Text", entry.host());
            ui.set(base + " #CardRegionLabel.Text",
                    Lang.tr(playerRef.getUuid(), "ui.servers.card.region_label", "Region"));
            ui.set(base + " #CardRegion.Text", entry.region());
            ui.set(base + " #CardLanguageLabel.Text",
                    Lang.tr(playerRef.getUuid(), "ui.servers.card.language_label", "Language"));
            ui.set(base + " #CardLanguage.Text", entry.language());
            ui.set(base + " #CardAboutLabel.Text",
                    Lang.tr(playerRef.getUuid(), "ui.servers.card.about_label", "About"));
            ui.set(base + " #CardDescription.Text", entry.description());
            ui.set(base + " #CardFootnote.Text",
                    Lang.tr(playerRef.getUuid(), "ui.servers.card.footnote",
                            "Verified official build"));
            ui.set(base + " #CardBannerOfficial.Visible", entry.endlessLeveling());
            ui.set(base + " #CardBannerPartner.Visible", !entry.endlessLeveling());
        }
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref,
            @Nonnull Store<EntityStore> store,
            @Nonnull SkillsUIPage.Data data) {
        super.handleDataEvent(ref, store, data);

        if (data.action == null || data.action.isEmpty()) {
            return;
        }

        NavUIHelper.handleNavAction(data.action, ref, store, playerRef);
    }

    @Nonnull
    private List<ServerEntry> getOfficialServers() {
        return List.of(
                new ServerEntry(
                        "endless-leveling",
                        "Endless Leveling",
                        "play.endlessleveling.net:9005",
                        Lang.tr(playerRef.getUuid(), "ui.servers.tag.official", "OFFICIAL SERVER"),
                        Lang.tr(playerRef.getUuid(), "ui.servers.card.el.desc",
                                "Hybrid RPG survival + dungeon crawler. Home of Endless Leveling, run "
                                        + "directly by the mod developer.\n\n"
                                        + "- Every Endless Leveling feature available\n"
                                        + "- All premium addons enabled\n"
                                        + "- Full survival progression + endgame gating\n"
                                        + "- Dungeon crawling w/ scaling mob levels\n"
                                        + "- Baseline mod experience as intended"),
                        "NA",
                        "English",
                        true),
                new ServerEntry(
                        "histatu",
                        "Histatu",
                        "play.histatu.net",
                        Lang.tr(playerRef.getUuid(), "ui.servers.tag.official", "OFFICIAL SERVER"),
                        Lang.tr(playerRef.getUuid(), "ui.servers.card.histatu.desc",
                                "Hytale gaming network relaunched in 2026. See Endless Leveling in its "
                                        + "full glory — more events, custom mods, and content than anywhere else.\n\n"
                                        + "Key features:\n"
                                        + "- Survival + Factions gameplay\n"
                                        + "- 25+ custom enchants with leveling tiers\n"
                                        + "- Major co-op dungeons with unique PvE\n"
                                        + "- 3 custom endgame dimensions w/ tiered bosses\n"
                                        + "- 100+ MMO abilities and leveling systems\n"
                                        + "- Fully player-driven economy (shops + auction)\n"
                                        + "- Bounty + kit-based dueling\n"
                                        + "- No-paywall: grind all ranks in-game\n"
                                        + "- Auto load-balanced NA/EU, 24/7 uptime"),
                        "NA",
                        "English",
                        false));
    }

    private record ServerEntry(
            @Nonnull String id,
            @Nonnull String name,
            @Nonnull String host,
            @Nonnull String tag,
            @Nonnull String description,
            @Nonnull String region,
            @Nonnull String language,
            boolean endlessLeveling) {
    }
}
