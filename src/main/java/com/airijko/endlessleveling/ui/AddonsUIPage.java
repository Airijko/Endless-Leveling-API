package com.airijko.endlessleveling.ui;

import javax.annotation.Nonnull;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.airijko.endlessleveling.util.Lang;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType.Activating;
import static com.hypixel.hytale.server.core.ui.builder.EventData.of;

public class AddonsUIPage extends InteractiveCustomUIPage<SkillsUIPage.Data> {

    private static final String PATREON_URL = "https://www.patreon.com/cw/airijko";
    private static final String STARKY_MJOLNIR_URL = "https://www.curseforge.com/hytale/mods/starky-mjolnir";
    private static final String FILTER_ALL = "all";
    private static final String FILTER_MOD_PARTNERS = "mod_partners";
    private static final String FILTER_COMMUNITY = "community";
    private static final String FILTER_PARTNER_EXTENSIONS = "partner_extensions";
    private static final Map<String, String> FILTER_BY_PLAYER = new ConcurrentHashMap<>();

    public AddonsUIPage(@Nonnull PlayerRef playerRef,
            @Nonnull CustomPageLifetime lifetime) {
        super(playerRef, lifetime, SkillsUIPage.Data.CODEC);
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref,
            @Nonnull UICommandBuilder rawUi,
            @Nonnull UIEventBuilder events,
            @Nonnull Store<EntityStore> store) {

        SafeUICommandBuilder ui = new SafeUICommandBuilder(rawUi);
        ui.append("Pages/Addons/AddonsPage.ui");
        ModularCardUiAppender.appendFolder(ui,
            "#AddonCards",
            "Common/UI/Custom/Pages/Addons/Cards/Core",
            "Pages/Addons/Cards/Core",
            3);
        ModularCardUiAppender.appendFolder(ui,
            "#ExtensionAddonCards",
            "Common/UI/Custom/Pages/Addons/Cards/Extensions",
            "Pages/Addons/Cards/Extensions",
            3);
        ModularCardUiAppender.appendFolder(ui,
            "#ModPartnerCards",
            "Common/UI/Custom/Pages/Addons/Cards/ModPartners",
            "Pages/Addons/Cards/ModPartners",
            3);
        events.addEventBinding(Activating, "#AddonsPatreonButton", of("Action", "addons:patreon"), false);
        events.addEventBinding(Activating, "#ShowAllAddonsButton", of("Action", "addons:filter:all"), false);
        events.addEventBinding(Activating, "#ShowModPartnersButton", of("Action", "addons:filter:mod_partners"), false);
        events.addEventBinding(Activating, "#ShowCommunityAddonsButton", of("Action", "addons:filter:community"), false);
        events.addEventBinding(Activating, "#ShowPartnerExtensionsButton", of("Action", "addons:filter:partner_extensions"), false);
        events.addEventBinding(Activating, "#StarkysMjolnirCard", of("Action", "addons:partner:starky_mjolnir"), false);

        applySectionFilter(ui, getCurrentFilter());

        NavUIHelper.applyNavVersion(ui, playerRef, "addons",
            "Common/UI/Custom/Pages/Addons/AddonsPage.ui",
                "#AddonsTitle");
        NavUIHelper.bindNavEvents(events);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref,
            @Nonnull Store<EntityStore> store,
            @Nonnull SkillsUIPage.Data data) {
        super.handleDataEvent(ref, store, data);

        if (data.action == null || data.action.isEmpty()) {
            return;
        }

        if ("addons:patreon".equalsIgnoreCase(data.action)) {
            playerRef.sendMessage(Lang.message("ui.addons.patreon.prompt").color("#f6a463"));
            playerRef.sendMessage(Lang.message("ui.addons.patreon.label").link(PATREON_URL).color("#ffd08a"));
            return;
        }

        if ("addons:partner:starky_mjolnir".equalsIgnoreCase(data.action)) {
            playerRef.sendMessage(Lang.message("ui.addons.starky.prompt").color("#89c4ff"));
            playerRef.sendMessage(Lang.message("ui.addons.starky.label").link(STARKY_MJOLNIR_URL).color("#ffd08a"));
            return;
        }

        if (data.action.startsWith("addons:filter:")) {
            String filter = data.action.substring("addons:filter:".length());
            if (!isSupportedFilter(filter)) {
                return;
            }

            FILTER_BY_PLAYER.put(playerRef.getUuid().toString(), filter);
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player == null) {
                return;
            }

            player.getPageManager().openCustomPage(ref, store,
                    new AddonsUIPage(playerRef, CustomPageLifetime.CanDismiss));
            return;
        }

        NavUIHelper.handleNavAction(data.action, ref, store, playerRef);
    }

    private String getCurrentFilter() {
        return FILTER_BY_PLAYER.getOrDefault(playerRef.getUuid().toString(), FILTER_ALL);
    }

    private static boolean isSupportedFilter(String filter) {
        return FILTER_ALL.equals(filter)
                || FILTER_MOD_PARTNERS.equals(filter)
                || FILTER_COMMUNITY.equals(filter)
                || FILTER_PARTNER_EXTENSIONS.equals(filter);
    }

    private static void applySectionFilter(UICommandBuilder ui, String filter) {
        boolean showCore = FILTER_ALL.equals(filter);
        boolean showPartners = FILTER_ALL.equals(filter)
                || FILTER_MOD_PARTNERS.equals(filter)
                || FILTER_PARTNER_EXTENSIONS.equals(filter);
        boolean showModPartners = FILTER_ALL.equals(filter) || FILTER_MOD_PARTNERS.equals(filter);
        boolean showPartnerExtensions = FILTER_ALL.equals(filter) || FILTER_PARTNER_EXTENSIONS.equals(filter);

        ui.set("#CoreAddonsSection.Visible", showCore);
        ui.set("#PartnerAddonsSection.Visible", showPartners);
        ui.set("#CoreToPartnersSpacer.Visible", showCore && showPartners);
        ui.set("#ModPartnersSection.Visible", showModPartners);
        ui.set("#ModPartnersToExtensionsSpacer.Visible", showModPartners && showPartnerExtensions);
        ui.set("#PartnerExtensionsSection.Visible", showPartnerExtensions);

        if (FILTER_MOD_PARTNERS.equals(filter)) {
            ui.set("#AddonSectionStatus.Text", Lang.tr("ui.addons.filter.mod_partners", "Showing: Mod Partners"));
        } else if (FILTER_COMMUNITY.equals(filter)) {
            ui.set("#AddonSectionStatus.Text", Lang.tr("ui.addons.filter.community", "Showing: Community Addons"));
        } else if (FILTER_PARTNER_EXTENSIONS.equals(filter)) {
            ui.set("#AddonSectionStatus.Text", Lang.tr("ui.addons.filter.partner_extensions", "Showing: Partner Extentions"));
        } else {
            ui.set("#AddonSectionStatus.Text", Lang.tr("ui.addons.filter.all", "Showing: Endless Leveling Addons"));
        }
    }
}