package com.airijko.endlessleveling.ui;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.packets.interface_.CustomUICommand;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.ui.Value;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Decorating wrapper around {@link UICommandBuilder} that drops {@code set()}
 * commands targeting selectors that are not present in any of the UI documents
 * appended through the wrapper. This prevents the client-side crash
 * "Selected element in CustomUI command was not found." which can occur when
 * a stale selector reference reaches the player's client.
 *
 * <p>Usage in a {@code build()} method:
 * <pre>
 *   public void build(..., UICommandBuilder rawUi, ...) {
 *       SafeUICommandBuilder ui = new SafeUICommandBuilder(rawUi);
 *       ui.append("Pages/Foo.ui");
 *       ui.set("#FooLabel.Text", "hello"); // safe, validated
 *   }
 * </pre>
 *
 * <p>The wrapper inherits from {@link UICommandBuilder} so it can be passed to
 * helpers (like {@link NavUIHelper}) that expect a {@code UICommandBuilder}
 * parameter; dynamic dispatch ensures the safety overrides are still applied.
 * The wrapper never adds commands to its inherited parent state - every
 * operation is forwarded to the wrapped delegate, and {@link #getCommands()}
 * also reads from the delegate.
 */
public class SafeUICommandBuilder extends UICommandBuilder {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClassFull();

    private static final String CLASSPATH_PREFIX = "Common/UI/Custom/";
    private static final Pattern SELECTOR_PATTERN = Pattern.compile("#([A-Za-z_][A-Za-z0-9_]*)");
    private static final Pattern INCLUDE_PATTERN = Pattern
            .compile("\\$[A-Za-z_][A-Za-z0-9_]*\\s*=\\s*\"([^\"]+)\"");

    /** Sentinel for missing/unreadable resources so the cache can store negatives. */
    private static final String MISSING_RESOURCE = "\u0000__MISSING__\u0000";
    private static final ConcurrentHashMap<String, String> RESOURCE_CACHE = new ConcurrentHashMap<>();
    /** Selectors collected per resource path (recursively, with includes). */
    private static final ConcurrentHashMap<String, Set<String>> RESOURCE_SELECTOR_CACHE = new ConcurrentHashMap<>();

    @Nonnull
    private final UICommandBuilder delegate;
    private final Set<String> knownSelectors = new HashSet<>();
    private final Set<String> warnedMissingSelectors = new HashSet<>();
    private boolean strict;

    public SafeUICommandBuilder(@Nonnull UICommandBuilder delegate) {
        this.delegate = delegate;
        // Default: drop unknown selector writes. Set to false to fall back to
        // permissive (warn but forward) if a regression is observed.
        this.strict = true;
    }

    /**
     * Configure whether unknown selector writes are dropped (strict, default)
     * or merely logged and forwarded (permissive).
     */
    public SafeUICommandBuilder withStrict(boolean strict) {
        this.strict = strict;
        return this;
    }

    /**
     * Manually whitelist selector ids that are known to exist on the client
     * even though no static {@code .ui} document containing them was appended
     * (for example, when an external system has already loaded the page).
     */
    public SafeUICommandBuilder registerKnownSelectors(@Nonnull String... selectorIds) {
        for (String id : selectorIds) {
            if (id == null || id.isEmpty()) continue;
            knownSelectors.add(stripLeadingHash(id));
        }
        return this;
    }

    /**
     * Pre-track selectors from one or more {@code .ui} resources without
     * emitting an {@code Append} command. Use this from refresh ticks or HUD
     * state pushes where the document is already attached on the client and
     * only {@code set()} commands are being issued through a fresh builder.
     */
    public SafeUICommandBuilder trackResources(@Nonnull String... documentPaths) {
        for (String path : documentPaths) {
            trackResource(path);
        }
        return this;
    }

    // ---------------------------------------------------------------------
    // Mutation commands - track resources/inline content for selector lookup
    // ---------------------------------------------------------------------

    @Override
    @Nonnull
    public UICommandBuilder clear(String selector) {
        delegate.clear(selector);
        return this;
    }

    @Override
    @Nonnull
    public UICommandBuilder remove(String selector) {
        delegate.remove(selector);
        return this;
    }

    @Override
    @Nonnull
    public UICommandBuilder append(String documentPath) {
        trackResource(documentPath);
        delegate.append(documentPath);
        return this;
    }

    @Override
    @Nonnull
    public UICommandBuilder append(String selector, String documentPath) {
        trackResource(documentPath);
        delegate.append(selector, documentPath);
        return this;
    }

    @Override
    @Nonnull
    public UICommandBuilder appendInline(String selector, String document) {
        trackInline(document);
        delegate.appendInline(selector, document);
        return this;
    }

    @Override
    @Nonnull
    public UICommandBuilder insertBefore(String selector, String documentPath) {
        trackResource(documentPath);
        delegate.insertBefore(selector, documentPath);
        return this;
    }

    @Override
    @Nonnull
    public UICommandBuilder insertBeforeInline(String selector, String document) {
        trackInline(document);
        delegate.insertBeforeInline(selector, document);
        return this;
    }

    // ---------------------------------------------------------------------
    // set(...) overloads - validate selector and forward (or drop)
    // ---------------------------------------------------------------------

    @Override
    @Nonnull
    public UICommandBuilder setNull(String selector) {
        if (!isSelectorSafe(selector)) return this;
        delegate.setNull(selector);
        return this;
    }

    @Override
    @Nonnull
    public <T> UICommandBuilder set(String selector, @Nonnull Value<T> ref) {
        if (!isSelectorSafe(selector)) return this;
        delegate.set(selector, ref);
        return this;
    }

    @Override
    @Nonnull
    public UICommandBuilder set(String selector, @Nonnull String str) {
        if (!isSelectorSafe(selector)) return this;
        delegate.set(selector, str);
        return this;
    }

    @Override
    @Nonnull
    public UICommandBuilder set(String selector, @Nonnull Message message) {
        if (!isSelectorSafe(selector)) return this;
        delegate.set(selector, message);
        return this;
    }

    @Override
    @Nonnull
    public UICommandBuilder set(String selector, boolean b) {
        if (!isSelectorSafe(selector)) return this;
        delegate.set(selector, b);
        return this;
    }

    @Override
    @Nonnull
    public UICommandBuilder set(String selector, float n) {
        if (!isSelectorSafe(selector)) return this;
        delegate.set(selector, n);
        return this;
    }

    @Override
    @Nonnull
    public UICommandBuilder set(String selector, int n) {
        if (!isSelectorSafe(selector)) return this;
        delegate.set(selector, n);
        return this;
    }

    @Override
    @Nonnull
    public UICommandBuilder set(String selector, double n) {
        if (!isSelectorSafe(selector)) return this;
        delegate.set(selector, n);
        return this;
    }

    @Override
    @Nonnull
    public UICommandBuilder setObject(String selector, @Nonnull Object data) {
        if (!isSelectorSafe(selector)) return this;
        delegate.setObject(selector, data);
        return this;
    }

    @Override
    @Nonnull
    public <T> UICommandBuilder set(String selector, @Nonnull T[] data) {
        if (!isSelectorSafe(selector)) return this;
        delegate.set(selector, data);
        return this;
    }

    @Override
    @Nonnull
    public <T> UICommandBuilder set(String selector, @Nonnull List<T> data) {
        if (!isSelectorSafe(selector)) return this;
        delegate.set(selector, data);
        return this;
    }

    @Override
    @Nonnull
    public CustomUICommand[] getCommands() {
        return delegate.getCommands();
    }

    /** Expose the wrapped builder for the rare case it must be passed to legacy APIs. */
    @Nonnull
    public UICommandBuilder unwrap() {
        return delegate;
    }

    // ---------------------------------------------------------------------
    // Selector validation
    // ---------------------------------------------------------------------

    private boolean isSelectorSafe(String selector) {
        if (selector == null || selector.isEmpty()) {
            return false;
        }
        String selectorId = extractFirstSelectorId(selector);
        if (selectorId == null) {
            // Cannot parse a selector id (e.g. selector targets the root or uses an
            // unrecognised syntax) - allow it to pass through to preserve behaviour.
            return true;
        }
        if (knownSelectors.contains(selectorId)) {
            return true;
        }
        if (warnedMissingSelectors.add(selectorId)) {
            LOGGER.atWarning().log(
                    "SafeUI: %s set for unknown selector '%s' (id=%s) - no appended .ui document declares it",
                    strict ? "dropping" : "forwarding", selector, selectorId);
        }
        // In permissive mode the command still goes through; in strict mode we drop
        // to avoid the "Selected element in CustomUI command was not found" client crash.
        return !strict;
    }

    private static String extractFirstSelectorId(String selector) {
        int hashIdx = selector.indexOf('#');
        if (hashIdx < 0) {
            return null;
        }
        int start = hashIdx + 1;
        int end = start;
        while (end < selector.length()) {
            char c = selector.charAt(end);
            if (Character.isLetterOrDigit(c) || c == '_') {
                end++;
            } else {
                break;
            }
        }
        return end > start ? selector.substring(start, end) : null;
    }

    private static String stripLeadingHash(String value) {
        return value.startsWith("#") ? value.substring(1) : value;
    }

    // ---------------------------------------------------------------------
    // Resource scanning
    // ---------------------------------------------------------------------

    private void trackResource(String documentPath) {
        if (documentPath == null || documentPath.isBlank()) {
            return;
        }
        String classpathPath = toClasspathPath(documentPath);
        knownSelectors.addAll(loadSelectorsFor(classpathPath));
    }

    private void trackInline(String inlineContent) {
        if (inlineContent == null || inlineContent.isBlank()) {
            return;
        }
        Matcher m = SELECTOR_PATTERN.matcher(inlineContent);
        while (m.find()) {
            knownSelectors.add(m.group(1));
        }
    }

    private static String toClasspathPath(String documentPath) {
        if (documentPath.startsWith(CLASSPATH_PREFIX)) {
            return documentPath;
        }
        if (documentPath.startsWith("/")) {
            return documentPath.substring(1);
        }
        return CLASSPATH_PREFIX + documentPath;
    }

    /**
     * Returns the set of selectors declared (recursively, following includes)
     * by the given .ui resource. Cached across pages.
     */
    private static Set<String> loadSelectorsFor(String classpathPath) {
        Set<String> cached = RESOURCE_SELECTOR_CACHE.get(classpathPath);
        if (cached != null) {
            return cached;
        }
        Set<String> collected = new HashSet<>();
        Set<String> visited = new HashSet<>();
        scanRecursively(classpathPath, collected, visited);
        RESOURCE_SELECTOR_CACHE.put(classpathPath, collected);
        return collected;
    }

    private static void scanRecursively(String classpathPath, Set<String> collected, Set<String> visited) {
        if (!visited.add(classpathPath)) {
            return;
        }
        String content = loadResource(classpathPath);
        if (content == null) {
            return;
        }

        Matcher selectorMatcher = SELECTOR_PATTERN.matcher(content);
        while (selectorMatcher.find()) {
            collected.add(selectorMatcher.group(1));
        }

        // Follow $X = "..." declarations so includes (e.g. shared nav panels,
        // common templates) contribute their selectors as well.
        Matcher includeMatcher = INCLUDE_PATTERN.matcher(content);
        List<String> includes = new ArrayList<>();
        while (includeMatcher.find()) {
            includes.add(includeMatcher.group(1));
        }
        for (String relative : includes) {
            String resolved = resolveRelativePath(classpathPath, relative);
            if (resolved != null) {
                scanRecursively(resolved, collected, visited);
            }
        }
    }

    private static String resolveRelativePath(String basePath, String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return null;
        }
        // Absolute classpath path
        if (relativePath.startsWith("/")) {
            return relativePath.substring(1);
        }
        int lastSlash = basePath.lastIndexOf('/');
        String baseDir = lastSlash >= 0 ? basePath.substring(0, lastSlash) : "";
        List<String> segments = new ArrayList<>();
        if (!baseDir.isEmpty()) {
            for (String part : baseDir.split("/")) {
                if (!part.isEmpty()) {
                    segments.add(part);
                }
            }
        }
        for (String part : relativePath.split("/")) {
            if (part.isEmpty() || ".".equals(part)) {
                continue;
            }
            if ("..".equals(part)) {
                if (!segments.isEmpty()) {
                    segments.remove(segments.size() - 1);
                }
            } else {
                segments.add(part);
            }
        }
        return String.join("/", segments);
    }

    private static String loadResource(String classpathPath) {
        String cached = RESOURCE_CACHE.computeIfAbsent(classpathPath, key -> {
            ClassLoader loader = SafeUICommandBuilder.class.getClassLoader();
            try (InputStream in = loader.getResourceAsStream(key)) {
                if (in == null) {
                    return MISSING_RESOURCE;
                }
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                LOGGER.atWarning().log("SafeUI: failed reading resource '%s': %s", key, e.getMessage());
                return MISSING_RESOURCE;
            }
        });
        return MISSING_RESOURCE.equals(cached) ? null : cached;
    }
}
