package com.airijko.endlessleveling.ui;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public final class ModularCardUiAppender {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClassFull();

    // JAR resources are immutable at runtime; cache the folder listing forever.
    private static final ConcurrentHashMap<String, List<String>> FOLDER_LISTING_CACHE =
            new ConcurrentHashMap<>();

    private ModularCardUiAppender() {
    }

    public static void warmUp(@Nonnull String... classpathFolders) {
        for (String folder : classpathFolders) {
            FOLDER_LISTING_CACHE.computeIfAbsent(folder, ModularCardUiAppender::listUiFileNames);
        }
    }

    static void appendFolder(@Nonnull UICommandBuilder ui,
            @Nonnull String containerSelector,
            @Nonnull String classpathFolder,
            @Nonnull String uiPathPrefix,
            int cardsPerRow) {
        ui.clear(containerSelector);

        List<String> cardNames = FOLDER_LISTING_CACHE.computeIfAbsent(
                classpathFolder, ModularCardUiAppender::listUiFileNames);
        if (cardNames.isEmpty()) {
            return;
        }

        int rowIndex = -1;
        for (int index = 0; index < cardNames.size(); index++) {
            if (index % cardsPerRow == 0) {
                ui.appendInline(containerSelector, "Group { LayoutMode: Left; Anchor: (Bottom: 12); }");
                rowIndex++;
            }

            ui.append(containerSelector + "[" + rowIndex + "]", uiPathPrefix + "/" + cardNames.get(index));
        }
    }

    @Nonnull
    private static List<String> listUiFileNames(@Nonnull String classpathFolder) {
        Set<String> fileNames = new LinkedHashSet<>();
        collectFromFolderUrls(classpathFolder, fileNames);
        collectFromCodeSourceJar(classpathFolder, fileNames);

        return fileNames.stream()
                .sorted(Comparator.naturalOrder())
                .toList();
    }

    private static void collectFromFolderUrls(String classpathFolder, Set<String> fileNames) {
        try {
            Enumeration<URL> urls = ModularCardUiAppender.class.getClassLoader().getResources(classpathFolder);
            while (urls.hasMoreElements()) {
                URL url = urls.nextElement();
                String protocol = url.getProtocol();
                if ("file".equalsIgnoreCase(protocol)) {
                    collectFromFileUrl(url, fileNames);
                } else if ("jar".equalsIgnoreCase(protocol)) {
                    collectFromJarUrl(url, classpathFolder, fileNames);
                }
            }
        } catch (IOException e) {
            LOGGER.atWarning().log("Failed to enumerate card UI resources under %s: %s", classpathFolder,
                    e.getMessage());
        }
    }

    private static void collectFromFileUrl(URL url, Set<String> fileNames) {
        try {
            Path folder = Path.of(url.toURI());
            if (!Files.isDirectory(folder)) {
                return;
            }
            try (var stream = Files.list(folder)) {
                stream.filter(Files::isRegularFile)
                        .map(path -> path.getFileName().toString())
                        .filter(name -> name.endsWith(".ui"))
                        .forEach(fileNames::add);
            }
        } catch (IOException | URISyntaxException e) {
            LOGGER.atWarning().log("Failed to inspect card UI directory %s: %s", url, e.getMessage());
        }
    }

    private static void collectFromJarUrl(URL url, String classpathFolder, Set<String> fileNames) {
        try {
            JarURLConnection connection = (JarURLConnection) url.openConnection();
            // CRITICAL: do NOT remove this call.
            //
            // Without setUseCaches(false), getJarFile() returns the JarFile
            // instance that sun.net.www.protocol.jar.JarFileFactory caches
            // on behalf of the entire JVM URL layer - i.e. the same instance
            // the mod classloader uses for every getResource /
            // getResourceAsStream against this JAR. The try-with-resources
            // below would then close the classloader's shared handle,
            // causing subsequent resource loads across the whole plugin to
            // fail with IOException whose message is just the JAR path
            // ("/home/container/mods/EndlessLeveling-X_Y_Z.jar"). That
            // cascades into empty card enumeration, SafeUI dropping every
            // card-declared selector, and the dungeons page rendering into
            // a broken UI state that can crash the client.
            //
            // Setting useCaches=false causes the JDK to return a fresh,
            // connection-scoped JarFile that this method owns and may close
            // safely via the try-with-resources below.
            connection.setUseCaches(false);
            try (JarFile jarFile = connection.getJarFile()) {
                collectFromJarEntries(jarFile, classpathFolder, fileNames);
            }
        } catch (IOException e) {
            LOGGER.atWarning().log("Failed to inspect card UI jar folder %s: %s", url, e.getMessage());
        }
    }

    private static void collectFromCodeSourceJar(String classpathFolder, Set<String> fileNames) {
        try {
            URL location = ModularCardUiAppender.class.getProtectionDomain().getCodeSource().getLocation();
            if (location == null || !location.getPath().endsWith(".jar")) {
                return;
            }
            try (JarFile jarFile = new JarFile(Path.of(location.toURI()).toFile())) {
                collectFromJarEntries(jarFile, classpathFolder, fileNames);
            }
        } catch (Exception e) {
            LOGGER.atWarning().log("Failed to inspect packaged card UI resources under %s: %s", classpathFolder,
                    e.getMessage());
        }
    }

    private static void collectFromJarEntries(JarFile jarFile, String classpathFolder, Set<String> fileNames) {
        String prefix = classpathFolder.endsWith("/") ? classpathFolder : classpathFolder + "/";
        List<String> matches = new ArrayList<>();

        jarFile.stream()
                .map(JarEntry::getName)
                .filter(name -> name.startsWith(prefix))
                .filter(name -> name.endsWith(".ui"))
                .filter(name -> name.indexOf('/', prefix.length()) < 0)
                .map(name -> name.substring(prefix.length()))
                .forEach(matches::add);

        matches.stream().sorted().forEach(fileNames::add);
    }
}