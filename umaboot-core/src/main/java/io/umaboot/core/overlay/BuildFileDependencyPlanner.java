package io.umaboot.core.overlay;

import io.umaboot.core.generator.GeneratedUnit;
import io.umaboot.core.generator.GeneratorContext;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Inspects an existing project's build file and prepares the dependency patch
 * required by overlay-generated source files.
 */
public final class BuildFileDependencyPlanner {

    private static final String MAPSTRUCT_VERSION = "1.6.3";
    private static final String LOMBOK_MAPSTRUCT_BINDING_VERSION = "0.2.0";
    private static final String TESTCONTAINERS_VERSION = "1.20.4";
    private static final String JJWT_VERSION = "0.11.5";

    private static final Pattern GRADLE_DEPENDENCY = Pattern.compile(
            "(?m)^\\s*([A-Za-z][A-Za-z0-9_]*)\\s*\\(?\\s*(?:platform\\()?\\s*[\"']([^\"']+)[\"']");
    private static final Pattern GRADLE_DEPENDENCIES_BLOCK = Pattern.compile("(?m)^\\s*dependencies\\s*\\{");

    public Plan plan(Path outputRoot, GeneratorContext ctx) {
        Objects.requireNonNull(outputRoot, "outputRoot");
        Objects.requireNonNull(ctx, "ctx");

        Optional<BuildFile> detected = detectBuildFile(outputRoot, ctx);
        if (detected.isEmpty()) {
            return Plan.unavailable(outputRoot, "No pom.xml, build.gradle.kts, or build.gradle found.");
        }

        BuildFile buildFile = detected.get();
        String content = read(buildFile.path());
        List<RequiredDependency> required = requiredDependencies(ctx);
        List<ExistingDependency> existing = buildFile.type() == BuildFileType.MAVEN
                ? parseMaven(content)
                : parseGradle(content);
        List<Finding> findings = required.stream()
                .map(dependency -> inspect(dependency, existing))
                .toList();

        List<RequiredDependency> missing = findings.stream()
                .filter(finding -> finding.status() != Status.PRESENT)
                .map(Finding::dependency)
                .toList();
        String patched = buildFile.type() == BuildFileType.MAVEN
                ? patchMaven(content, missing)
                : patchGradle(content, buildFile.type(), missing);
        List<GeneratedUnit> patchUnits = patched.equals(content)
                ? List.of()
                : List.of(new GeneratedUnit(buildFile.relativePath(), patched));

        return new Plan(buildFile.path(), buildFile.relativePath(), buildFile.type(),
                true, "", required, findings, patchUnits);
    }

    private static Optional<BuildFile> detectBuildFile(Path outputRoot, GeneratorContext ctx) {
        List<String> candidates = ctx.isGradle()
                ? List.of("build.gradle.kts", "build.gradle", "pom.xml")
                : List.of("pom.xml", "build.gradle.kts", "build.gradle");
        for (String candidate : candidates) {
            Path path = outputRoot.resolve(candidate);
            if (Files.exists(path)) {
                return Optional.of(new BuildFile(path, candidate, type(candidate)));
            }
        }
        return Optional.empty();
    }

    private static BuildFileType type(String relativePath) {
        if (relativePath.endsWith(".kts")) {
            return BuildFileType.GRADLE_KOTLIN;
        }
        if (relativePath.endsWith(".gradle")) {
            return BuildFileType.GRADLE_GROOVY;
        }
        return BuildFileType.MAVEN;
    }

    private static List<RequiredDependency> requiredDependencies(GeneratorContext ctx) {
        List<RequiredDependency> required = new ArrayList<>();

        add(required, "org.springframework.boot", "spring-boot-starter-web", null,
                Role.COMPILE, "Generated controllers need Spring Web.");

        if (!ctx.isValidationNone()) {
            add(required, "org.springframework.boot", "spring-boot-starter-validation", null,
                    Role.COMPILE, "Generated request validation annotations need Spring validation.");
        }

        if (ctx.isMigrationFlyway()) {
            add(required, "org.flywaydb", "flyway-core", null,
                    Role.COMPILE, "Flyway migrations are enabled.");
            if (ctx.renderFlywayDatabaseModule()) {
                add(required, "org.flywaydb", ctx.flywayDatabaseModule(), null,
                        Role.RUNTIME, "Flyway needs the database-specific runtime module.");
            }
        }

        if (ctx.logging().isJson()) {
            add(required, "net.logstash.logback", "logstash-logback-encoder",
                    ctx.logstashLogbackEncoderVersion(), Role.COMPILE,
                    "JSON logging uses LogstashEncoder.");
        }

        if (ctx.isSecurityEnabled()) {
            add(required, "org.springframework.boot", "spring-boot-starter-security", null,
                    Role.COMPILE, "Generated security configuration uses Spring Security.");
        }
        if (ctx.isSecurityJwt()) {
            add(required, "io.jsonwebtoken", "jjwt-api", JJWT_VERSION,
                    Role.COMPILE, "JWT security uses JJWT APIs.");
            add(required, "io.jsonwebtoken", "jjwt-impl", JJWT_VERSION,
                    Role.RUNTIME, "JWT security needs the JJWT runtime implementation.");
            add(required, "io.jsonwebtoken", "jjwt-jackson", JJWT_VERSION,
                    Role.RUNTIME, "JWT security needs Jackson JSON support at runtime.");
        }

        if (ctx.isOpenApiAnnotation()) {
            add(required, "org.springdoc", ctx.springdocOpenApiArtifactId(),
                    ctx.springdocOpenApiVersion(), Role.COMPILE,
                    "Generated controllers use springdoc OpenAPI annotations.");
        }

        if (ctx.isJpa()) {
            add(required, "org.springframework.boot", "spring-boot-starter-data-jpa", null,
                    Role.COMPILE, "Generated repositories/entities use Spring Data JPA.");
        } else {
            add(required, "org.springframework.data", "spring-data-commons", null,
                    Role.COMPILE, "Generated PageResponse imports Spring Data Page.");
        }
        if (ctx.isMyBatis()) {
            add(required, "org.mybatis.spring.boot", "mybatis-spring-boot-starter",
                    ctx.isSpringBoot2() ? "2.3.2" : "3.0.4", Role.COMPILE,
                    "Generated persistence code uses MyBatis.");
        }
        if (ctx.isJooq()) {
            add(required, "org.springframework.boot", "spring-boot-starter-jooq", null,
                    Role.COMPILE, "Generated persistence code uses jOOQ.");
        }

        DriverDependency driver = driverDependency(ctx);
        add(required, driver.groupId(), driver.artifactId(), null, Role.RUNTIME,
                "Generated application needs the selected database JDBC driver.");
        if (ctx.isDbSqlite() && ctx.isJpa()) {
            add(required, ctx.sqliteDialectDependencyGroupId(), ctx.sqliteDialectDependencyArtifactId(),
                    ctx.sqliteDialectDependencyVersion(),
                    Role.COMPILE, "SQLite JPA needs the community Hibernate dialects module.");
        }

        if (ctx.useLombok()) {
            add(required, "org.projectlombok", "lombok", ctx.lombokVersion(),
                    Role.COMPILE_ONLY, "Generated code uses Lombok annotations.", true);
            if (ctx.isGradle()) {
                add(required, "org.projectlombok", "lombok", ctx.lombokVersion(),
                        Role.ANNOTATION_PROCESSOR, "Lombok must run as an annotation processor.");
            }
        }

        if (ctx.useMapStruct()) {
            add(required, "org.mapstruct", "mapstruct", MAPSTRUCT_VERSION,
                    Role.COMPILE, "Generated mappers use MapStruct annotations.");
            add(required, "org.mapstruct", "mapstruct-processor", MAPSTRUCT_VERSION,
                    Role.ANNOTATION_PROCESSOR, "MapStruct needs its annotation processor.");
            if (ctx.useLombok()) {
                add(required, "org.projectlombok", "lombok", ctx.lombokVersion(),
                        Role.ANNOTATION_PROCESSOR, "MapStruct + Lombok needs Lombok on the processor path.");
                add(required, "org.projectlombok", "lombok-mapstruct-binding",
                        LOMBOK_MAPSTRUCT_BINDING_VERSION, Role.ANNOTATION_PROCESSOR,
                        "MapStruct + Lombok needs lombok-mapstruct-binding.");
            }
        }

        if (ctx.tests().enabled()) {
            add(required, "org.springframework.boot", "spring-boot-starter-test", null,
                    Role.TEST, "Generated tests use Spring Boot test support.");
            if (!ctx.isDbSqlite()) {
                add(required, "org.testcontainers", "junit-jupiter", TESTCONTAINERS_VERSION,
                        Role.TEST, "Generated integration tests use Testcontainers.");
                add(required, "org.testcontainers", testcontainersModule(ctx), TESTCONTAINERS_VERSION,
                        Role.TEST, "Generated integration tests use a database Testcontainer.");
            }
        }

        return List.copyOf(required);
    }

    private static void add(List<RequiredDependency> dependencies, String groupId, String artifactId,
                            String version, Role role, String reason) {
        add(dependencies, groupId, artifactId, version, role, reason, false);
    }

    private static void add(List<RequiredDependency> dependencies, String groupId, String artifactId,
                            String version, Role role, String reason, boolean optional) {
        if (artifactId != null && !artifactId.isBlank()) {
            dependencies.add(new RequiredDependency(groupId, artifactId, version, role, reason, optional));
        }
    }

    private static DriverDependency driverDependency(GeneratorContext ctx) {
        return new DriverDependency(ctx.jdbcDriverDependencyGroupId(), ctx.jdbcDriverDependencyArtifactId());
    }

    private static String testcontainersModule(GeneratorContext ctx) {
        if (ctx.isDbMariadb()) {
            return "mariadb";
        }
        if (ctx.isDbMysql()) {
            return "mysql";
        }
        if (ctx.isDbSqlserver()) {
            return "mssqlserver";
        }
        return "postgresql";
    }

    private static Finding inspect(RequiredDependency dependency, List<ExistingDependency> existing) {
        List<ExistingDependency> matches = existing.stream()
                .filter(candidate -> dependency.groupId().equals(candidate.groupId())
                        && dependency.artifactId().equals(candidate.artifactId()))
                .toList();
        if (matches.isEmpty()) {
            return new Finding(dependency, Status.MISSING, "missing");
        }
        if (matches.stream().anyMatch(candidate -> accepts(dependency, candidate))) {
            return new Finding(dependency, Status.PRESENT, "present");
        }
        String scopes = matches.stream()
                .map(ExistingDependency::scopeDescription)
                .distinct()
                .sorted()
                .reduce((left, right) -> left + ", " + right)
                .orElse("unknown scope");
        return new Finding(dependency, Status.WRONG_SCOPE,
                "found only in " + scopes + "; needs " + dependency.role().label());
    }

    private static boolean accepts(RequiredDependency required, ExistingDependency existing) {
        if (existing.role() == Role.ANNOTATION_PROCESSOR) {
            return required.role() == Role.ANNOTATION_PROCESSOR;
        }
        String scope = existing.scope();
        return switch (required.role()) {
            case COMPILE -> existing.role() == Role.COMPILE
                    && ("compile".equals(scope) || "implementation".equals(scope)
                    || "api".equals(scope) || scope.isBlank());
            case RUNTIME -> (existing.role() == Role.RUNTIME || existing.role() == Role.COMPILE)
                    && ("runtime".equals(scope) || "runtimeOnly".equals(scope)
                    || "implementation".equals(scope) || "api".equals(scope)
                    || "compile".equals(scope) || scope.isBlank());
            case COMPILE_ONLY -> existing.role() == Role.COMPILE_ONLY
                    || (existing.role() == Role.COMPILE
                    && ("provided".equals(scope) || "compileOnly".equals(scope)
                    || existing.optional() || scope.isBlank()));
            case ANNOTATION_PROCESSOR -> false;
            case TEST -> existing.role() == Role.TEST
                    || (existing.role() == Role.COMPILE && "test".equals(scope));
        };
    }

    private static List<ExistingDependency> parseMaven(String content) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setExpandEntityReferences(false);
            Document document = factory.newDocumentBuilder()
                    .parse(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));
            NodeList nodes = document.getElementsByTagName("dependency");
            List<ExistingDependency> dependencies = new ArrayList<>();
            for (int i = 0; i < nodes.getLength(); i++) {
                if (!(nodes.item(i) instanceof Element dependency)) {
                    continue;
                }
                String groupId = childText(dependency, "groupId");
                String artifactId = childText(dependency, "artifactId");
                if (groupId.isBlank() || artifactId.isBlank()) {
                    continue;
                }
                Role role = hasAncestor(dependency, "annotationProcessorPaths")
                        ? Role.ANNOTATION_PROCESSOR
                        : Role.COMPILE;
                String scope = childText(dependency, "scope");
                if (scope.isBlank() && role == Role.COMPILE) {
                    scope = "compile";
                }
                boolean optional = "true".equalsIgnoreCase(childText(dependency, "optional"));
                dependencies.add(new ExistingDependency(groupId, artifactId, scope, role, optional));
            }
            return dependencies;
        } catch (Exception ex) {
            return List.of();
        }
    }

    private static List<ExistingDependency> parseGradle(String content) {
        Matcher matcher = GRADLE_DEPENDENCY.matcher(content);
        List<ExistingDependency> dependencies = new ArrayList<>();
        while (matcher.find()) {
            String configuration = matcher.group(1);
            String coordinate = matcher.group(2);
            Coordinate parsed = Coordinate.parse(coordinate);
            if (parsed == null) {
                continue;
            }
            dependencies.add(new ExistingDependency(parsed.groupId(), parsed.artifactId(),
                    configuration, gradleRole(configuration), false));
        }
        return dependencies;
    }

    private static Role gradleRole(String configuration) {
        String normalized = configuration.toLowerCase(Locale.ROOT);
        if (normalized.contains("annotationprocessor") || "kapt".equals(normalized)) {
            return Role.ANNOTATION_PROCESSOR;
        }
        if (normalized.startsWith("test")) {
            return Role.TEST;
        }
        if (normalized.contains("runtime")) {
            return Role.RUNTIME;
        }
        if (normalized.contains("compileonly") || normalized.contains("provided")) {
            return Role.COMPILE_ONLY;
        }
        return Role.COMPILE;
    }

    private static String patchMaven(String content, List<RequiredDependency> missing) {
        List<RequiredDependency> dependencies = missing.stream()
                .filter(dependency -> dependency.role() != Role.ANNOTATION_PROCESSOR)
                .toList();
        List<RequiredDependency> processors = missing.stream()
                .filter(dependency -> dependency.role() == Role.ANNOTATION_PROCESSOR)
                .toList();

        String patched = insertMavenDependencies(content, dependencies);
        return insertMavenAnnotationProcessors(patched, processors);
    }

    private static String insertMavenDependencies(String content, List<RequiredDependency> dependencies) {
        if (dependencies.isEmpty()) {
            return content;
        }
        String blocks = dependencies.stream()
                .sorted(Comparator.comparing(RequiredDependency::groupId)
                        .thenComparing(RequiredDependency::artifactId)
                        .thenComparing(dependency -> dependency.role().name()))
                .map(BuildFileDependencyPlanner::mavenDependencyBlock)
                .reduce("", String::concat);
        int dependenciesStart = findMainDependenciesStart(content);
        if (dependenciesStart >= 0) {
            int dependenciesEnd = content.indexOf("</dependencies>", dependenciesStart);
            return content.substring(0, dependenciesEnd)
                    + "\n" + blocks
                    + content.substring(dependenciesEnd);
        }
        int projectEnd = content.lastIndexOf("</project>");
        if (projectEnd < 0) {
            return content + "\n<dependencies>\n" + blocks + "</dependencies>\n";
        }
        String section = "\n    <dependencies>\n" + blocks + "    </dependencies>\n";
        return content.substring(0, projectEnd) + section + content.substring(projectEnd);
    }

    private static int findMainDependenciesStart(String content) {
        int from = 0;
        while (true) {
            int index = content.indexOf("<dependencies>", from);
            if (index < 0) {
                return -1;
            }
            if (!inside(content, index, "<dependencyManagement", "</dependencyManagement>")
                    && !inside(content, index, "<plugin", "</plugin>")) {
                return index;
            }
            from = index + "<dependencies>".length();
        }
    }

    private static boolean inside(String content, int index, String open, String close) {
        int lastOpen = content.lastIndexOf(open, index);
        if (lastOpen < 0) {
            return false;
        }
        int lastClose = content.lastIndexOf(close, index);
        return lastClose < lastOpen;
    }

    private static String mavenDependencyBlock(RequiredDependency dependency) {
        StringBuilder block = new StringBuilder();
        block.append("        <dependency>\n");
        block.append("            <groupId>").append(dependency.groupId()).append("</groupId>\n");
        block.append("            <artifactId>").append(dependency.artifactId()).append("</artifactId>\n");
        if (dependency.version() != null && !dependency.version().isBlank()) {
            block.append("            <version>").append(dependency.version()).append("</version>\n");
        }
        if (dependency.role() == Role.RUNTIME) {
            block.append("            <scope>runtime</scope>\n");
        } else if (dependency.role() == Role.TEST) {
            block.append("            <scope>test</scope>\n");
        }
        if (dependency.optional()) {
            block.append("            <optional>true</optional>\n");
        }
        block.append("        </dependency>\n");
        return block.toString();
    }

    private static String insertMavenAnnotationProcessors(String content, List<RequiredDependency> processors) {
        if (processors.isEmpty()) {
            return content;
        }
        String paths = processors.stream()
                .sorted(Comparator.comparing(RequiredDependency::groupId)
                        .thenComparing(RequiredDependency::artifactId))
                .map(BuildFileDependencyPlanner::mavenProcessorPath)
                .reduce("", String::concat);
        PluginBlock compilerPlugin = findPluginBlock(content, "maven-compiler-plugin");
        if (compilerPlugin != null) {
            String plugin = content.substring(compilerPlugin.start(), compilerPlugin.end());
            String patchedPlugin;
            int processorEnd = plugin.indexOf("</annotationProcessorPaths>");
            if (processorEnd >= 0) {
                patchedPlugin = plugin.substring(0, processorEnd)
                        + "\n" + paths
                        + plugin.substring(processorEnd);
            } else {
                int configurationEnd = plugin.indexOf("</configuration>");
                String processorBlock = "                    <annotationProcessorPaths>\n"
                        + paths
                        + "                    </annotationProcessorPaths>\n";
                if (configurationEnd >= 0) {
                    patchedPlugin = plugin.substring(0, configurationEnd)
                            + processorBlock
                            + plugin.substring(configurationEnd);
                } else {
                    int pluginEnd = plugin.lastIndexOf("</plugin>");
                    patchedPlugin = plugin.substring(0, pluginEnd)
                            + "                <configuration>\n"
                            + processorBlock
                            + "                </configuration>\n"
                            + plugin.substring(pluginEnd);
                }
            }
            return content.substring(0, compilerPlugin.start())
                    + patchedPlugin
                    + content.substring(compilerPlugin.end());
        }

        String pluginBlock = "            <plugin>\n"
                + "                <groupId>org.apache.maven.plugins</groupId>\n"
                + "                <artifactId>maven-compiler-plugin</artifactId>\n"
                + "                <configuration>\n"
                + "                    <annotationProcessorPaths>\n"
                + paths
                + "                    </annotationProcessorPaths>\n"
                + "                </configuration>\n"
                + "            </plugin>\n";
        return insertMavenCompilerPlugin(content, pluginBlock);
    }

    private static String mavenProcessorPath(RequiredDependency dependency) {
        StringBuilder path = new StringBuilder();
        path.append("                        <path>\n");
        path.append("                            <groupId>").append(dependency.groupId()).append("</groupId>\n");
        path.append("                            <artifactId>").append(dependency.artifactId()).append("</artifactId>\n");
        if (dependency.version() != null && !dependency.version().isBlank()) {
            path.append("                            <version>").append(dependency.version()).append("</version>\n");
        }
        path.append("                        </path>\n");
        return path.toString();
    }

    private static PluginBlock findPluginBlock(String content, String artifactId) {
        int artifact = content.indexOf("<artifactId>" + artifactId + "</artifactId>");
        if (artifact < 0) {
            return null;
        }
        int start = content.lastIndexOf("<plugin>", artifact);
        int end = content.indexOf("</plugin>", artifact);
        if (start < 0 || end < 0) {
            return null;
        }
        return new PluginBlock(start, end + "</plugin>".length());
    }

    private static String insertMavenCompilerPlugin(String content, String pluginBlock) {
        int pluginsStart = content.indexOf("<plugins>");
        if (pluginsStart >= 0 && !inside(content, pluginsStart, "<pluginManagement", "</pluginManagement>")) {
            int pluginsEnd = content.indexOf("</plugins>", pluginsStart);
            return content.substring(0, pluginsEnd)
                    + "\n" + pluginBlock
                    + content.substring(pluginsEnd);
        }
        int buildStart = content.indexOf("<build>");
        if (buildStart >= 0) {
            int buildEnd = content.indexOf("</build>", buildStart);
            String plugins = "        <plugins>\n" + pluginBlock + "        </plugins>\n";
            return content.substring(0, buildEnd) + plugins + content.substring(buildEnd);
        }
        int projectEnd = content.lastIndexOf("</project>");
        String build = "\n    <build>\n"
                + "        <plugins>\n"
                + pluginBlock
                + "        </plugins>\n"
                + "    </build>\n";
        if (projectEnd < 0) {
            return content + build;
        }
        return content.substring(0, projectEnd) + build + content.substring(projectEnd);
    }

    private static String patchGradle(String content, BuildFileType type, List<RequiredDependency> missing) {
        if (missing.isEmpty()) {
            return content;
        }
        String lines = missing.stream()
                .sorted(Comparator.comparing((RequiredDependency dependency) -> dependency.role().ordinal())
                        .thenComparing(RequiredDependency::groupId)
                        .thenComparing(RequiredDependency::artifactId))
                .map(dependency -> gradleDependencyLine(type, dependency))
                .reduce("", String::concat);

        Matcher matcher = GRADLE_DEPENDENCIES_BLOCK.matcher(content);
        if (matcher.find()) {
            int brace = content.indexOf('{', matcher.start());
            int end = matchingBrace(content, brace);
            if (end >= 0) {
                return content.substring(0, end)
                        + "\n" + lines
                        + content.substring(end);
            }
        }
        return content + "\n\ndependencies {\n" + lines + "}\n";
    }

    private static String gradleDependencyLine(BuildFileType type, RequiredDependency dependency) {
        String configuration = switch (dependency.role()) {
            case COMPILE -> "implementation";
            case RUNTIME -> "runtimeOnly";
            case COMPILE_ONLY -> "compileOnly";
            case ANNOTATION_PROCESSOR -> "annotationProcessor";
            case TEST -> "testImplementation";
        };
        String coordinate = dependency.coordinate();
        if (type == BuildFileType.GRADLE_KOTLIN) {
            return "    " + configuration + "(\"" + coordinate + "\")\n";
        }
        return "    " + configuration + " '" + coordinate + "'\n";
    }

    private static int matchingBrace(String content, int openBrace) {
        int depth = 0;
        for (int i = openBrace; i < content.length(); i++) {
            char c = content.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static String childText(Element element, String name) {
        NodeList nodes = element.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (node instanceof Element child && name.equals(child.getTagName())) {
                return child.getTextContent() == null ? "" : child.getTextContent().trim();
            }
        }
        return "";
    }

    private static boolean hasAncestor(Node node, String name) {
        Node parent = node.getParentNode();
        while (parent != null) {
            if (parent instanceof Element element && name.equals(element.getTagName())) {
                return true;
            }
            parent = parent.getParentNode();
        }
        return false;
    }

    private static String read(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read " + path, e);
        }
    }

    public record Plan(
            Path buildFile,
            String relativePath,
            BuildFileType type,
            boolean available,
            String unavailableReason,
            List<RequiredDependency> required,
            List<Finding> findings,
            List<GeneratedUnit> patchUnits) {

        static Plan unavailable(Path outputRoot, String reason) {
            return new Plan(outputRoot, "", null, false, reason, List.of(), List.of(), List.of());
        }

        public Plan {
            required = required == null ? List.of() : List.copyOf(required);
            findings = findings == null ? List.of() : List.copyOf(findings);
            patchUnits = patchUnits == null ? List.of() : List.copyOf(patchUnits);
        }

        public boolean hasMissingDependencies() {
            return findings.stream().anyMatch(finding -> finding.status() != Status.PRESENT);
        }

        public boolean hasPatch() {
            return !patchUnits.isEmpty();
        }

        public List<Finding> missingFindings() {
            return findings.stream()
                    .filter(finding -> finding.status() != Status.PRESENT)
                    .toList();
        }

        public List<String> messages() {
            if (!available) {
                return List.of(unavailableReason);
            }
            return missingFindings().stream()
                    .map(Finding::messageForUser)
                    .toList();
        }
    }

    public record Finding(RequiredDependency dependency, Status status, String detail) {
        String messageForUser() {
            String prefix = status == Status.WRONG_SCOPE ? "Wrong scope" : "Missing dependency";
            return prefix + ": " + dependency.coordinateWithoutVersion()
                    + " as " + dependency.role().label()
                    + " (" + dependency.reason() + ")"
                    + (status == Status.WRONG_SCOPE ? " - " + detail : "");
        }
    }

    public record RequiredDependency(String groupId, String artifactId, String version,
                                     Role role, String reason, boolean optional) {
        String coordinate() {
            if (version == null || version.isBlank()) {
                return groupId + ":" + artifactId;
            }
            return groupId + ":" + artifactId + ":" + version;
        }

        String coordinateWithoutVersion() {
            return groupId + ":" + artifactId;
        }
    }

    public enum Status {
        PRESENT,
        MISSING,
        WRONG_SCOPE
    }

    public enum Role {
        COMPILE("compile/application dependency"),
        RUNTIME("runtime dependency"),
        COMPILE_ONLY("compile-only/provided dependency"),
        ANNOTATION_PROCESSOR("annotation processor"),
        TEST("test dependency");

        private final String label;

        Role(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public enum BuildFileType {
        MAVEN,
        GRADLE_KOTLIN,
        GRADLE_GROOVY
    }

    private record BuildFile(Path path, String relativePath, BuildFileType type) {}

    private record DriverDependency(String groupId, String artifactId) {}

    private record ExistingDependency(String groupId, String artifactId,
                                      String scope, Role role, boolean optional) {
        ExistingDependency {
            scope = scope == null ? "" : scope;
        }

        String scopeDescription() {
            return role == Role.ANNOTATION_PROCESSOR ? "annotation processor" : scope;
        }
    }

    private record Coordinate(String groupId, String artifactId) {
        static Coordinate parse(String coordinate) {
            String[] parts = coordinate.split(":");
            if (parts.length < 2 || parts[0].isBlank() || parts[1].isBlank()) {
                return null;
            }
            return new Coordinate(parts[0], parts[1]);
        }
    }

    private record PluginBlock(int start, int end) {}
}
