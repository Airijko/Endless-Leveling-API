package com.airijko.endlessleveling.ui;

import javax.annotation.Nonnull;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.nio.file.Files;
import java.nio.file.Path;

public class DungeonsUIPage extends InteractiveCustomUIPage<SkillsUIPage.Data> {

    private static final String OVERVIEW_DESCRIPTION = "Explore every integrated dungeon at a glance. Endgame cards highlight high-pressure encounters, while Major Dungeons cards map the D01 to D03 progression route.";
    private static final String IMAGE_SOURCE_BASE = "Common/UI/Custom/Pages/Dungeons/Cards/Images/";
    private static final String FROZEN_IMAGE_PATH = IMAGE_SOURCE_BASE + "EndgameFrozenPlaceholder.png";
    private static final String SWAMP_IMAGE_PATH = IMAGE_SOURCE_BASE + "EndgameSwampPlaceholder.png";
    private static final String VOID_IMAGE_PATH = IMAGE_SOURCE_BASE + "EndgameVoidPlaceholder.png";
    private static final String AZAROTH_IMAGE_PATH = IMAGE_SOURCE_BASE + "MajorAzarothPlaceholder.png";
    private static final String KATHERINA_IMAGE_PATH = IMAGE_SOURCE_BASE + "MajorKatherinaPlaceholder.png";
    private static final String BARON_IMAGE_PATH = IMAGE_SOURCE_BASE + "MajorBaronPlaceholder.png";

    public DungeonsUIPage(@Nonnull PlayerRef playerRef,
            @Nonnull CustomPageLifetime lifetime) {
        super(playerRef, lifetime, SkillsUIPage.Data.CODEC);
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref,
            @Nonnull UICommandBuilder ui,
            @Nonnull UIEventBuilder events,
            @Nonnull Store<EntityStore> store) {

        ui.append("Pages/Dungeons/DungeonsPage.ui");
        ui.set("#DungeonsOverviewText.Text", OVERVIEW_DESCRIPTION);
        ModularCardUiAppender.appendFolder(ui,
            "#EndgameCards",
            "Common/UI/Custom/Pages/Dungeons/Cards/Endgame",
            "Pages/Dungeons/Cards/Endgame",
            3);
        ModularCardUiAppender.appendFolder(ui,
            "#MajorCards",
            "Common/UI/Custom/Pages/Dungeons/Cards/Major",
            "Pages/Dungeons/Cards/Major",
            3);

        setBannerImage(ui, "#FrozenBannerImage", FROZEN_IMAGE_PATH);
        setBannerImage(ui, "#SwampBannerImage", SWAMP_IMAGE_PATH);
        setBannerImage(ui, "#VoidBannerImage", VOID_IMAGE_PATH);
        setBannerImage(ui, "#AzarothBannerImage", AZAROTH_IMAGE_PATH);
        setBannerImage(ui, "#KatherinaBannerImage", KATHERINA_IMAGE_PATH);
        setBannerImage(ui, "#BaronBannerImage", BARON_IMAGE_PATH);

        NavUIHelper.applyNavVersion(ui, playerRef, "dungeons",
            "Common/UI/Custom/Pages/Dungeons/DungeonsPage.ui",
                "#DungeonsTitle");
        NavUIHelper.bindNavEvents(events);
    }

    private void setBannerImage(@Nonnull UICommandBuilder ui,
            @Nonnull String selector,
            @Nonnull String imagePath) {
        boolean exists = resourceExists(imagePath)
                || (imagePath.startsWith("Common/UI/Custom/")
                        && resourceExists(imagePath.substring("Common/UI/Custom/".length())));
        ui.set(selector + ".Visible", exists);
    }

    private boolean resourceExists(@Nonnull String path) {
        ClassLoader loader = DungeonsUIPage.class.getClassLoader();
        if (loader.getResource(path) != null) {
            return true;
        }
        if (loader.getResource('/' + path) != null) {
            return true;
        }
        try (java.io.InputStream in = loader.getResourceAsStream(path)) {
            if (in != null) {
                return true;
            }
        } catch (java.io.IOException ignored) {
        }

        // Dev/runtime fallback for environments where resources are loaded from filesystem.
        Path rawPath = Path.of(path);
        if (Files.exists(rawPath)) {
            return true;
        }
        Path srcResourcePath = Path.of("src", "main", "resources").resolve(path);
        if (Files.exists(srcResourcePath)) {
            return true;
        }
        if (path.startsWith("Common/UI/Custom/")) {
            Path trimmed = Path.of(path.substring("Common/UI/Custom/".length()));
            if (Files.exists(trimmed)) {
                return true;
            }
            if (Files.exists(Path.of("src", "main", "resources").resolve(trimmed))) {
                return true;
            }
        }
        return false;
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
}