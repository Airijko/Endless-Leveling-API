package com.airijko.endlessleveling.ui;

import javax.annotation.Nonnull;

import com.airijko.endlessleveling.util.Lang;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * Support page displaying project credit and authenticity notice.
 */
public class SupportUIPage extends InteractiveCustomUIPage<SkillsUIPage.Data> {

    public SupportUIPage(@Nonnull PlayerRef playerRef,
            @Nonnull CustomPageLifetime lifetime) {
        super(playerRef, lifetime, SkillsUIPage.Data.CODEC);
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref,
            @Nonnull UICommandBuilder ui,
            @Nonnull UIEventBuilder events,
            @Nonnull Store<EntityStore> store) {

        ui.append("Pages/SupportPage.ui");
        NavUIHelper.applyNavVersion(ui, playerRef, "support");
        NavUIHelper.bindNavEvents(events);

        ui.set("#SupportTitleLabel.Text",
                Lang.tr(playerRef.getUuid(), "ui.support.page.title", "Support"));
        ui.set("#SupportModName.Text",
                Lang.tr(playerRef.getUuid(), "ui.support.mod_name", "Endless Leveling"));
        ui.set("#SupportDeveloperLabel.Text",
                Lang.tr(playerRef.getUuid(), "ui.support.developer", "Developer: Airijko"));
        ui.set("#SupportNotice1.Text",
                Lang.tr(playerRef.getUuid(), "ui.support.notice1",
                        "Some redistributed versions alter the UI or remove the Endless Leveling name and credit."));
        ui.set("#SupportNotice2.Text",
                Lang.tr(playerRef.getUuid(), "ui.support.notice2",
                        "These versions are not official."));
        ui.set("#SupportNotice3.Text",
                Lang.tr(playerRef.getUuid(), "ui.support.notice3",
                        "If you enjoy the mod, please support the original project and developer."));
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref,
            @Nonnull Store<EntityStore> store,
            @Nonnull SkillsUIPage.Data data) {
        super.handleDataEvent(ref, store, data);

        if (data.action != null && !data.action.isEmpty()) {
            NavUIHelper.handleNavAction(data.action, ref, store, playerRef);
        }
    }
}
