package io.umaboot.core.version;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Provides version lists for the Settings UI dropdowns.
 *
 * <p>Three independent online sources, each with its own 24-hour disk cache and
 * curated hardcoded fallback:</p>
 *
 * <ul>
 *   <li><b>Spring Boot 3.x</b> — Spring Initializr's metadata API
 *       (<a href="https://start.spring.io/metadata/client">/metadata/client</a>).</li>
 *   <li><b>Spring Boot 2.x</b> — Maven Central search API. Initializr no longer
 *       surfaces 2.x releases (post-EOL), so we query Maven Central directly for
 *       all published {@code 2.7.*} versions of {@code org.springframework.boot:spring-boot}.</li>
 *   <li><b>Java majors</b> — Foojay DiscoAPI (the same source the Gradle Foojay
 *       resolver uses), filtered to actively-maintained LTS major versions.</li>
 * </ul>
 *
 * <p>Cache file shape ({@code ~/.umaboot/cache/versions.json}):</p>
 * <pre>{@code
 * {
 *   "springBoot3": { "fetchedAt": 1234567, "versions": [...] },
 *   "springBoot2": { "fetchedAt": 1234567, "versions": [...] },
 *   "java":        { "fetchedAt": 1234567, "versions": [...] }
 * }
 * }</pre>
 *
 * <p>Each section refreshes independently. All HTTP work runs synchronously with
 * a short timeout so callers can keep this off the EDT in the IntelliJ plugin.</p>
 */
public final class VersionMetadataService {

    private static final Logger LOG = LoggerFactory.getLogger(VersionMetadataService.class);

    /** Spring Initializr metadata endpoint (Spring Boot 3.x). */
    static final String SPRING_INITIALIZR_URL = "https://start.spring.io/metadata/client";

    /**
     * Maven Central search for all 2.7.* versions of spring-boot. {@code core=gav}
     * returns rows at version-level granularity. Sort by timestamp descending so
     * the newest releases come first; the 2.7 line has ~19 versions total, so
     * {@code rows=30} is a comfortable upper bound.
     */
    static final String MAVEN_CENTRAL_SB2_URL =
            "https://search.maven.org/solrsearch/select"
                    + "?q=g:org.springframework.boot+AND+a:spring-boot+AND+v:2.7.*"
                    + "&core=gav&rows=30&sort=timestamp+desc&wt=json";

    /**
     * Foojay DiscoAPI for actively-maintained LTS major versions. Returns a
     * compact list keyed by {@code major_version} (e.g. 8, 11, 17, 21).
     */
    static final String FOOJAY_MAJOR_VERSIONS_URL =
            "https://api.foojay.io/disco/v3.0/major_versions"
                    + "?ga=true&maintained=true&term_of_support=lts";

    /** How long a cached version list is considered fresh. */
    static final Duration CACHE_TTL = Duration.ofHours(24);

    /** Cache section keys. */
    static final String SECTION_SPRING_BOOT_3 = "springBoot3";
    static final String SECTION_SPRING_BOOT_2 = "springBoot2";
    static final String SECTION_JAVA = "java";

    /**
     * Curated Spring Boot 3.x fallback for offline / fetch-failure cases.
     * Keep recent and compatible with what the generated POM templates expect.
     */
    static final List<String> SPRING_BOOT_3_FALLBACK = List.of(
            "3.4.1",
            "3.4.0",
            "3.3.6",
            "3.3.5",
            "3.2.12"
    );

    /**
     * Curated Spring Boot 2.7.x fallback. Spring Boot 2.7 is EOL in OSS so the
     * list is essentially terminal; this just covers offline / Maven Central
     * outage cases.
     */
    static final List<String> SPRING_BOOT_2_FALLBACK = List.of(
            "2.7.18",
            "2.7.17",
            "2.7.16",
            "2.7.15"
    );

    /** Curated Java LTS fallback. */
    static final List<String> JAVA_FALLBACK = List.of("8", "11", "17", "21");

    private final Path cacheFile;
    private final HttpClient httpClient;

    /** Production constructor: cache lives under {@code ~/.umaboot/cache/versions.json}. */
    public VersionMetadataService() {
        this(defaultCachePath(), HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build());
    }

    /** Test-friendly constructor allowing a custom cache path and HttpClient. */
    public VersionMetadataService(Path cacheFile, HttpClient httpClient) {
        this.cacheFile = cacheFile;
        this.httpClient = httpClient;
    }

    private static Path defaultCachePath() {
        String home = System.getProperty("user.home", ".");
        return Path.of(home, ".umaboot", "cache", "versions.json");
    }

    // ====================================================================
    // Public API
    // ====================================================================

    /**
     * Returns the Spring Boot 3.x versions list, in priority order:
     * <ol>
     *   <li>Fresh disk cache (younger than {@link #CACHE_TTL}).</li>
     *   <li>Live fetch from Spring Initializr (cached on success).</li>
     *   <li>Stale disk cache (older than TTL) if a live fetch failed.</li>
     *   <li>{@link #SPRING_BOOT_3_FALLBACK} as a last resort.</li>
     * </ol>
     * Never throws — always returns a non-empty list.
     */
    public List<String> getSpringBootVersions() {
        return resolve(SECTION_SPRING_BOOT_3, this::fetchFromSpringInitializr, SPRING_BOOT_3_FALLBACK);
    }

    /**
     * Returns the Spring Boot 2.7.x versions list. Same priority order as
     * {@link #getSpringBootVersions()}, fetched from Maven Central instead of
     * Initializr (which no longer lists 2.x).
     */
    public List<String> getSpringBoot2Versions() {
        return resolve(SECTION_SPRING_BOOT_2, this::fetchSpringBoot2FromMavenCentral, SPRING_BOOT_2_FALLBACK);
    }

    /**
     * Returns the Java LTS major versions list. Live-fetched from Foojay's
     * DiscoAPI; falls back to {@link #JAVA_FALLBACK}.
     */
    public List<String> getJavaVersions() {
        return resolve(SECTION_JAVA, this::fetchJavaMajorsFromFoojay, JAVA_FALLBACK);
    }

    /**
     * Returns the Spring Boot versions appropriate for a given target Java version.
     *
     * <ul>
     *   <li>{@code "8"} or {@code "11"} → 2.7.x line via {@link #getSpringBoot2Versions()}.</li>
     *   <li>everything else → 3.x line via {@link #getSpringBootVersions()}.</li>
     * </ul>
     */
    public List<String> getSpringBootVersionsFor(String javaVersion) {
        if ("8".equals(javaVersion) || "11".equals(javaVersion)) {
            return getSpringBoot2Versions();
        }
        return getSpringBootVersions();
    }

    /**
     * Returns the Java versions appropriate for a given Spring Boot version string.
     *
     * <ul>
     *   <li>Major 2 → {@code [8, 11, 17]} (Spring Boot 2.7 supports all three).</li>
     *   <li>Major 3 (or unparseable) → {@code [17, 21]} (Spring Boot 3.x baseline is 17).</li>
     * </ul>
     */
    public List<String> getJavaVersionsFor(String springBootVersion) {
        int major = parseMajor(springBootVersion);
        if (major == 2) {
            return List.of("8", "11", "17");
        }
        return List.of("17", "21");
    }

    // ====================================================================
    // Resolve flow (cache → live → stale cache → fallback)
    // ====================================================================

    /** Functional shape for a live fetcher. May throw any exception. */
    @FunctionalInterface
    private interface Fetcher {
        List<String> fetch() throws Exception;
    }

    private List<String> resolve(String section, Fetcher fetcher, List<String> fallback) {
        Map<String, Cached> cache = readCache();
        Cached cached = cache.get(section);
        if (cached != null && !cached.isStale()) {
            LOG.debug("Using fresh disk cache for [{}]", section);
            return cached.versions;
        }
        try {
            List<String> live = fetcher.fetch();
            if (!live.isEmpty()) {
                writeSection(cache, section, live);
                return live;
            }
        } catch (Exception ex) {
            LOG.info("Live fetch of [{}] failed: {}", section, ex.toString());
        }
        if (cached != null) {
            LOG.debug("Live fetch failed, using stale cache for [{}]", section);
            return cached.versions;
        }
        LOG.debug("Live fetch failed and no cache available for [{}] — using hardcoded fallback", section);
        return fallback;
    }

    // ====================================================================
    // HTTP fetchers
    // ====================================================================

    private List<String> fetchFromSpringInitializr() throws IOException, InterruptedException {
        HttpResponse<String> resp = httpGet(SPRING_INITIALIZR_URL,
                "application/vnd.initializr.v2.2+json");
        return parseSpringBootVersions(resp.body());
    }

    private List<String> fetchSpringBoot2FromMavenCentral() throws IOException, InterruptedException {
        HttpResponse<String> resp = httpGet(MAVEN_CENTRAL_SB2_URL, "application/json");
        return parseMavenCentralVersions(resp.body());
    }

    private List<String> fetchJavaMajorsFromFoojay() throws IOException, InterruptedException {
        HttpResponse<String> resp = httpGet(FOOJAY_MAJOR_VERSIONS_URL, "application/json");
        return parseFoojayMajorVersions(resp.body());
    }

    private HttpResponse<String> httpGet(String url, String accept) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Accept", accept)
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();
        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() / 100 != 2) {
            throw new IOException(url + " returned HTTP " + resp.statusCode());
        }
        return resp;
    }

    // ====================================================================
    // Parsers
    // ====================================================================

    /**
     * Parses the {@code bootVersion.values[]} array from the Initializr JSON.
     * Filters out generic version pointers like {@code 3.3.x} (not real releases),
     * and excludes pre-releases (containing {@code SNAPSHOT}, {@code -M}, {@code -RC}).
     */
    static List<String> parseSpringBootVersions(String json) {
        try {
            JsonNode root = new ObjectMapper().readTree(json);
            JsonNode values = root.path("bootVersion").path("values");
            if (!values.isArray()) return List.of();
            List<String> versions = new ArrayList<>();
            for (JsonNode node : values) {
                String id = node.path("id").asText();
                if (id.isEmpty()) continue;
                if (isPointerOrPreRelease(id)) continue;
                versions.add(id);
            }
            versions.sort(Comparator.comparing(VersionMetadataService::comparable).reversed());
            return List.copyOf(versions);
        } catch (Exception ex) {
            LOG.warn("Failed to parse Spring Initializr metadata: {}", ex.getMessage());
            return List.of();
        }
    }

    /**
     * Parses Maven Central's solrsearch response: {@code response.docs[].v}.
     * Filters out pre-releases. Already sorted by timestamp desc by the API,
     * but we sort by semantic version desc anyway for determinism.
     */
    static List<String> parseMavenCentralVersions(String json) {
        try {
            JsonNode docs = new ObjectMapper().readTree(json).path("response").path("docs");
            if (!docs.isArray()) return List.of();
            List<String> versions = new ArrayList<>();
            for (JsonNode doc : docs) {
                String v = doc.path("v").asText();
                if (v.isEmpty()) continue;
                if (isPointerOrPreRelease(v)) continue;
                versions.add(v);
            }
            versions.sort(Comparator.comparing(VersionMetadataService::comparable).reversed());
            return List.copyOf(versions);
        } catch (Exception ex) {
            LOG.warn("Failed to parse Maven Central response: {}", ex.getMessage());
            return List.of();
        }
    }

    /**
     * Parses Foojay's {@code result[]} array. Each element has a numeric
     * {@code major_version}; we collect them as strings sorted ascending
     * ({@code "8"}, {@code "11"}, {@code "17"}, {@code "21"}).
     */
    static List<String> parseFoojayMajorVersions(String json) {
        try {
            JsonNode result = new ObjectMapper().readTree(json).path("result");
            if (!result.isArray()) return List.of();
            // Use a TreeMap of int keys for natural numeric order.
            java.util.TreeSet<Integer> majors = new java.util.TreeSet<>();
            for (JsonNode entry : result) {
                int n = entry.path("major_version").asInt(-1);
                if (n > 0) majors.add(n);
            }
            List<String> versions = new ArrayList<>();
            for (int n : majors) versions.add(Integer.toString(n));
            return List.copyOf(versions);
        } catch (Exception ex) {
            LOG.warn("Failed to parse Foojay response: {}", ex.getMessage());
            return List.of();
        }
    }

    private static boolean isPointerOrPreRelease(String version) {
        if (version.endsWith(".x") || version.endsWith(".X")) return true;
        String upper = version.toUpperCase();
        return upper.contains("SNAPSHOT") || upper.contains("-M") || upper.contains("-RC");
    }

    /** Comparable key from a version string like 3.3.5 — pads each part to 4 digits. */
    private static String comparable(String version) {
        String[] parts = version.split("[.\\-]");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) sb.append(String.format("%04d", parseInt(p)));
        return sb.toString();
    }

    private static int parseInt(String s) {
        try { return Integer.parseInt(s); } catch (NumberFormatException ignored) { return 0; }
    }

    private static int parseMajor(String version) {
        if (version == null || version.isEmpty()) return 3;
        int dot = version.indexOf('.');
        String head = dot < 0 ? version : version.substring(0, dot);
        try { return Integer.parseInt(head); } catch (NumberFormatException ex) { return 3; }
    }

    // ====================================================================
    // Disk cache (multi-section)
    // ====================================================================

    /**
     * Reads all cache sections. Returns an empty map when the file is missing
     * or unreadable. Tolerates the legacy v1.3 single-section schema by simply
     * treating it as "no cache" — the next live fetch will rewrite it in the
     * new shape.
     */
    Map<String, Cached> readCache() {
        if (!Files.isRegularFile(cacheFile)) return Map.of();
        try {
            String json = Files.readString(cacheFile, StandardCharsets.UTF_8);
            JsonNode root = new ObjectMapper().readTree(json);
            if (!root.isObject()) return Map.of();
            Map<String, Cached> out = new TreeMap<>();
            // New shape: top-level keys are section names, values are {fetchedAt, versions}.
            // Legacy shape (v1.3) has top-level "fetchedAt" + "springBootVersions" — skip
            // those keys; everything else we read as a section.
            root.fields().forEachRemaining(entry -> {
                String key = entry.getKey();
                if ("fetchedAt".equals(key) || "springBootVersions".equals(key)) return;
                JsonNode node = entry.getValue();
                if (!node.isObject()) return;
                long fetched = node.path("fetchedAt").asLong(0);
                JsonNode versionsNode = node.path("versions");
                if (fetched == 0 || !versionsNode.isArray()) return;
                List<String> versions = new ArrayList<>();
                for (JsonNode v : versionsNode) versions.add(v.asText());
                if (versions.isEmpty()) return;
                out.put(key, new Cached(Instant.ofEpochSecond(fetched), versions));
            });
            return out;
        } catch (IOException | RuntimeException ex) {
            LOG.debug("Failed to read cache {}: {}", cacheFile, ex.getMessage());
            return Map.of();
        }
    }

    /**
     * Writes a single section back, preserving the rest. Done by rebuilding
     * the file contents from the in-memory map plus the new section.
     */
    void writeSection(Map<String, Cached> existing, String section, List<String> versions) {
        try {
            if (cacheFile.getParent() != null) Files.createDirectories(cacheFile.getParent());
            Map<String, Object> root = new LinkedHashMap<>();
            // Carry over other sections.
            for (Map.Entry<String, Cached> e : existing.entrySet()) {
                if (e.getKey().equals(section)) continue;
                Map<String, Object> obj = new LinkedHashMap<>();
                obj.put("fetchedAt", e.getValue().fetchedAt.getEpochSecond());
                obj.put("versions", e.getValue().versions);
                root.put(e.getKey(), obj);
            }
            // New / refreshed section.
            Map<String, Object> obj = new LinkedHashMap<>();
            obj.put("fetchedAt", Instant.now().getEpochSecond());
            obj.put("versions", versions);
            root.put(section, obj);

            String json = new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(root);
            Files.writeString(cacheFile, json, StandardCharsets.UTF_8);
            LOG.debug("Cached {} entries for [{}] to {}", versions.size(), section, cacheFile);
        } catch (IOException ex) {
            LOG.debug("Failed to write cache {}: {}", cacheFile, ex.getMessage());
        }
    }

    /** Internal cache record. */
    static final class Cached {
        final Instant fetchedAt;
        final List<String> versions;

        Cached(Instant fetchedAt, List<String> versions) {
            this.fetchedAt = fetchedAt;
            this.versions = List.copyOf(versions);
        }

        boolean isStale() {
            return Instant.now().isAfter(fetchedAt.plus(CACHE_TTL));
        }
    }
}
