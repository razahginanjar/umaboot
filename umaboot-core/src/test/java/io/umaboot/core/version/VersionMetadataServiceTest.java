package io.umaboot.core.version;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link VersionMetadataService}. Covers each of the three live fetchers
 * (Spring Initializr → Spring Boot 3.x; Maven Central → Spring Boot 2.x; Foojay → Java)
 * plus the multi-section cache and fallback chain.
 */
class VersionMetadataServiceTest {

    // =======================================================================
    // Parser tests — pure functions, no HTTP / no cache
    // =======================================================================

    @Test
    void parsesAndSortsInitializrJson() {
        String json = """
                {
                  "bootVersion": {
                    "default": "3.3.5",
                    "values": [
                      {"id": "3.5.0-SNAPSHOT"},
                      {"id": "3.4.0"},
                      {"id": "3.3.6"},
                      {"id": "3.3.x"},
                      {"id": "3.4.0-M2"},
                      {"id": "3.2.12"}
                    ]
                  }
                }
                """;
        // Pre-releases, snapshots, and the "x" pointers are filtered out.
        // Result is sorted descending.
        assertThat(VersionMetadataService.parseSpringBootVersions(json))
                .containsExactly("3.4.0", "3.3.6", "3.2.12");
    }

    @Test
    void parsesMavenCentralResponse() {
        String json = """
                {
                  "responseHeader": {"status": 0},
                  "response": {
                    "numFound": 4,
                    "docs": [
                      {"g": "org.springframework.boot", "a": "spring-boot", "v": "2.7.18"},
                      {"v": "2.7.16"},
                      {"v": "2.7.0-RC1"},
                      {"v": "2.7.17"}
                    ]
                  }
                }
                """;
        // RC pre-release filtered; result sorted descending.
        assertThat(VersionMetadataService.parseMavenCentralVersions(json))
                .containsExactly("2.7.18", "2.7.17", "2.7.16");
    }

    @Test
    void parsesFoojayMajorVersions() {
        String json = """
                {
                  "result": [
                    {"major_version": 17, "term_of_support": "lts", "maintained": true},
                    {"major_version":  8, "term_of_support": "lts", "maintained": true},
                    {"major_version": 21, "term_of_support": "lts", "maintained": true},
                    {"major_version": 11, "term_of_support": "lts", "maintained": true}
                  ]
                }
                """;
        // Sorted ascending by numeric value.
        assertThat(VersionMetadataService.parseFoojayMajorVersions(json))
                .containsExactly("8", "11", "17", "21");
    }

    @Test
    void emptyOrMalformedJson_returnsEmptyList() {
        assertThat(VersionMetadataService.parseSpringBootVersions("{}")).isEmpty();
        assertThat(VersionMetadataService.parseSpringBootVersions("not-json")).isEmpty();
        assertThat(VersionMetadataService.parseMavenCentralVersions("{}")).isEmpty();
        assertThat(VersionMetadataService.parseMavenCentralVersions("garbage")).isEmpty();
        assertThat(VersionMetadataService.parseFoojayMajorVersions("{}")).isEmpty();
        assertThat(VersionMetadataService.parseFoojayMajorVersions("garbage")).isEmpty();
    }

    // =======================================================================
    // End-to-end — live fetch + cache
    // =======================================================================

    @Test
    void springBoot3_successfulFetch_writesCache_andReusesIt(@TempDir Path tmp) {
        Path cache = tmp.resolve("versions.json");
        StubHttpClient http = new StubHttpClient();
        http.respond(VersionMetadataService.SPRING_INITIALIZR_URL, initializr("3.3.5", "3.3.4"));
        VersionMetadataService svc = new VersionMetadataService(cache, http);

        List<String> first = svc.getSpringBootVersions();
        assertThat(first).containsExactly("3.3.5", "3.3.4");
        assertThat(http.callCount).isEqualTo(1);
        assertThat(Files.exists(cache)).isTrue();

        // Within TTL, second call hits cache only.
        List<String> second = svc.getSpringBootVersions();
        assertThat(second).containsExactly("3.3.5", "3.3.4");
        assertThat(http.callCount).as("Second call must not hit HTTP").isEqualTo(1);
    }

    @Test
    void springBoot2_fetchesFromMavenCentral(@TempDir Path tmp) {
        Path cache = tmp.resolve("versions.json");
        StubHttpClient http = new StubHttpClient();
        http.respond(VersionMetadataService.MAVEN_CENTRAL_SB2_URL,
                mavenCentral("2.7.18", "2.7.17"));
        VersionMetadataService svc = new VersionMetadataService(cache, http);

        assertThat(svc.getSpringBoot2Versions()).containsExactly("2.7.18", "2.7.17");
        assertThat(http.callCount).isEqualTo(1);
    }

    @Test
    void java_fetchesFromFoojay(@TempDir Path tmp) {
        Path cache = tmp.resolve("versions.json");
        StubHttpClient http = new StubHttpClient();
        http.respond(VersionMetadataService.FOOJAY_MAJOR_VERSIONS_URL,
                foojay(8, 11, 17, 21));
        VersionMetadataService svc = new VersionMetadataService(cache, http);

        assertThat(svc.getJavaVersions()).containsExactly("8", "11", "17", "21");
        assertThat(http.callCount).isEqualTo(1);
    }

    @Test
    void multipleSectionsCoexistInCache(@TempDir Path tmp) {
        Path cache = tmp.resolve("versions.json");
        StubHttpClient http = new StubHttpClient();
        http.respond(VersionMetadataService.SPRING_INITIALIZR_URL, initializr("3.3.5"));
        http.respond(VersionMetadataService.MAVEN_CENTRAL_SB2_URL, mavenCentral("2.7.18"));
        http.respond(VersionMetadataService.FOOJAY_MAJOR_VERSIONS_URL, foojay(17, 21));
        VersionMetadataService svc = new VersionMetadataService(cache, http);

        svc.getSpringBootVersions();
        svc.getSpringBoot2Versions();
        svc.getJavaVersions();
        assertThat(http.callCount).isEqualTo(3);

        // All three sections fresh now — second pass is fully cached.
        svc.getSpringBootVersions();
        svc.getSpringBoot2Versions();
        svc.getJavaVersions();
        assertThat(http.callCount).as("Cached calls must not hit HTTP").isEqualTo(3);
    }

    @Test
    void networkFailure_fallsBackToStaleCache(@TempDir Path tmp) throws Exception {
        Path cache = tmp.resolve("versions.json");
        // Pre-populate one section in the new cache shape with a stale timestamp.
        Files.writeString(cache, """
                {
                  "springBoot3": {
                    "fetchedAt": 1,
                    "versions": ["3.2.0"]
                  }
                }
                """, StandardCharsets.UTF_8);

        VersionMetadataService svc = new VersionMetadataService(cache, new ThrowingHttpClient());
        assertThat(svc.getSpringBootVersions()).containsExactly("3.2.0");
    }

    @Test
    void networkFailure_andNoCache_returnsHardcodedFallbacks(@TempDir Path tmp) {
        Path cache = tmp.resolve("nonexistent.json");
        VersionMetadataService svc = new VersionMetadataService(cache, new ThrowingHttpClient());

        assertThat(svc.getSpringBootVersions()).isEqualTo(VersionMetadataService.SPRING_BOOT_3_FALLBACK);
        assertThat(svc.getSpringBoot2Versions()).isEqualTo(VersionMetadataService.SPRING_BOOT_2_FALLBACK);
        assertThat(svc.getJavaVersions()).isEqualTo(VersionMetadataService.JAVA_FALLBACK);
    }

    @Test
    void legacyV13CacheShape_isTreatedAsMissing(@TempDir Path tmp) throws Exception {
        Path cache = tmp.resolve("versions.json");
        // The pre-K.1 schema. Should NOT be returned.
        Files.writeString(cache, """
                {
                  "fetchedAt": 1,
                  "springBootVersions": ["legacy-3.0.0"]
                }
                """, StandardCharsets.UTF_8);

        StubHttpClient http = new StubHttpClient();
        http.respond(VersionMetadataService.SPRING_INITIALIZR_URL, initializr("3.3.5"));
        VersionMetadataService svc = new VersionMetadataService(cache, http);

        // Legacy file ignored — fresh fetch happens.
        assertThat(svc.getSpringBootVersions()).containsExactly("3.3.5");
    }

    // =======================================================================
    // Helpers — canned JSON
    // =======================================================================

    private static String initializr(String... ids) {
        StringBuilder sb = new StringBuilder("""
                {"bootVersion": {"values": [
                """);
        for (int i = 0; i < ids.length; i++) {
            if (i > 0) sb.append(",\n");
            sb.append("  {\"id\": \"").append(ids[i]).append("\"}");
        }
        sb.append("]}}");
        return sb.toString();
    }

    private static String mavenCentral(String... versions) {
        StringBuilder sb = new StringBuilder("""
                {"response": {"docs": [
                """);
        for (int i = 0; i < versions.length; i++) {
            if (i > 0) sb.append(",\n");
            sb.append("  {\"v\": \"").append(versions[i]).append("\"}");
        }
        sb.append("]}}");
        return sb.toString();
    }

    private static String foojay(int... majors) {
        StringBuilder sb = new StringBuilder("{\"result\": [");
        for (int i = 0; i < majors.length; i++) {
            if (i > 0) sb.append(",");
            sb.append("{\"major_version\": ").append(majors[i]).append("}");
        }
        sb.append("]}");
        return sb.toString();
    }

    // =======================================================================
    // HttpClient stubs
    // =======================================================================

    /**
     * URL-aware stub. Match an exact URL to a canned body via {@link #respond}.
     * Counts total calls.
     */
    private static class StubHttpClient extends HttpClient {
        private final Map<String, String> bodies = new HashMap<>();
        int callCount = 0;

        void respond(String url, String body) { bodies.put(url, body); }

        @SuppressWarnings("unchecked")
        @Override
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler)
                throws IOException, InterruptedException {
            callCount++;
            String url = request.uri().toString();
            String body = bodies.get(url);
            if (body == null) throw new IOException("No stubbed response for " + url);
            return (HttpResponse<T>) new CannedResponse(body, request.uri());
        }

        @Override public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest r, HttpResponse.BodyHandler<T> h) { throw new UnsupportedOperationException(); }
        @Override public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest r, HttpResponse.BodyHandler<T> h, HttpResponse.PushPromiseHandler<T> p) { throw new UnsupportedOperationException(); }
        @Override public java.util.Optional<java.net.CookieHandler> cookieHandler() { return java.util.Optional.empty(); }
        @Override public java.util.Optional<Duration> connectTimeout() { return java.util.Optional.empty(); }
        @Override public Redirect followRedirects() { return Redirect.NEVER; }
        @Override public java.util.Optional<java.net.ProxySelector> proxy() { return java.util.Optional.empty(); }
        @Override public javax.net.ssl.SSLContext sslContext() { try { return javax.net.ssl.SSLContext.getDefault(); } catch (Exception e) { throw new RuntimeException(e); } }
        @Override public javax.net.ssl.SSLParameters sslParameters() { return new javax.net.ssl.SSLParameters(); }
        @Override public java.util.Optional<java.net.Authenticator> authenticator() { return java.util.Optional.empty(); }
        @Override public Version version() { return Version.HTTP_1_1; }
        @Override public java.util.Optional<java.util.concurrent.Executor> executor() { return java.util.Optional.empty(); }
    }

    private static class ThrowingHttpClient extends StubHttpClient {
        @Override
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> h) throws IOException {
            throw new IOException("network down");
        }
    }

    private static class CannedResponse implements HttpResponse<String> {
        private final String body;
        private final java.net.URI uri;
        CannedResponse(String body, java.net.URI uri) { this.body = body; this.uri = uri; }
        @Override public int statusCode() { return 200; }
        @Override public HttpRequest request() { return null; }
        @Override public java.util.Optional<HttpResponse<String>> previousResponse() { return java.util.Optional.empty(); }
        @Override public java.net.http.HttpHeaders headers() { return java.net.http.HttpHeaders.of(java.util.Map.of(), (a, b) -> true); }
        @Override public String body() { return body; }
        @Override public java.util.Optional<javax.net.ssl.SSLSession> sslSession() { return java.util.Optional.empty(); }
        @Override public java.net.URI uri() { return uri; }
        @Override public HttpClient.Version version() { return HttpClient.Version.HTTP_1_1; }
    }
}
