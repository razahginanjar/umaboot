package io.umaboot.core.generator.mvc;

import io.umaboot.core.generator.ArchitectureGenerator;
import io.umaboot.core.generator.GeneratedUnit;
import io.umaboot.core.generator.GeneratorContext;
import io.umaboot.core.generator.jpa.EntityView;
import io.umaboot.core.generator.migration.FlywayMigrationRenderer;
import io.umaboot.core.generator.openapi.OpenApiEmitter;
import io.umaboot.core.generator.persistence.PersistenceProvider;
import io.umaboot.core.generator.persistence.PersistenceProviders;
import io.umaboot.core.model.SchemaModel;
import io.umaboot.core.model.TableModel;
import io.umaboot.core.template.TemplateEngine;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * MVC / Layered architecture generator.
 *
 * <p>For each non-junction table, produces controller / service / DTOs /
 * mapper / exception. Persistence-specific files (entity + repository or mapper)
 * come from a delegated {@link PersistenceProvider}.</p>
 *
 * <p>Project-wide files: {@code Application.java}, {@code GlobalExceptionHandler.java},
 * {@code application.yml}, {@code pom.xml}, optional {@code openapi.yaml}.</p>
 */
public final class MvcGenerator implements ArchitectureGenerator {

    private final TemplateEngine engine;
    private final GeneratorContext ctx;
    private final PersistenceProvider persistence;

    public MvcGenerator(TemplateEngine engine, GeneratorContext ctx) {
        this.engine = Objects.requireNonNull(engine, "engine");
        this.ctx = Objects.requireNonNull(ctx, "ctx");
        this.persistence = PersistenceProviders.forContext(ctx, engine);
    }

    @Override
    public String architecture() {
        return "mvc";
    }

    @Override
    public List<GeneratedUnit> generate(SchemaModel schema) {
        Objects.requireNonNull(schema, "schema");
        List<GeneratedUnit> units = new ArrayList<>();

        String javaSrc = "src/main/java/" + ctx.basePackagePath();
        String resources = "src/main/resources";

        Map<String, Object> projectModel = projectModel(schema);
        if (!ctx.overlay()) {
            if (ctx.isGradle()) {
                units.add(unit("build.gradle.kts", "mvc-jpa/build.gradle.kts.ftl", projectModel));
                units.add(unit("settings.gradle.kts", "common/settings.gradle.kts.ftl", projectModel));
            } else {
                units.add(unit("pom.xml", "mvc-jpa/pom.xml.ftl", projectModel));
            }
            units.add(unit(resources + "/" + ctx.applicationConfigFileName(),
                    ctx.isApplicationConfigProperties()
                            ? "mvc-jpa/application.properties.ftl"
                            : "mvc-jpa/application.yml.ftl",
                    projectModel));
            if (ctx.isMigrationFlyway()) {
                units.add(new GeneratedUnit(FlywayMigrationRenderer.PATH,
                        FlywayMigrationRenderer.render(schema, ctx)));
            } else if (ctx.tests().enabled()) {
                units.add(new GeneratedUnit("src/test/resources/schema.sql",
                        FlywayMigrationRenderer.renderTestSchema(schema, ctx)));
            }
            units.add(unit(javaSrc + "/Application.java", "mvc-jpa/Application.java.ftl", projectModel));
            units.add(unit(javaSrc + "/exception/GlobalExceptionHandler.java",
                    "mvc-jpa/GlobalExceptionHandler.java.ftl", projectModel));
            if (ctx.isExceptionEnvelope()) {
                Map<String, Object> envelopeModel = new LinkedHashMap<>(projectModel);
                envelopeModel.put("apiErrorPackage", ctx.basePackage() + ".exception");
                units.add(unit(javaSrc + "/exception/ApiError.java",
                        "common/ApiError.java.ftl", envelopeModel));
            }
        }

        units.addAll(persistence.projectExtras(schema, ctx));

        // PageResponse is referenced by every Controller — emit even in overlay mode.
        units.add(unit(javaSrc + "/common/PageResponse.java",
                "common/PageResponse.java.ftl", projectModel));

        // Phase J — CursorPage emitted when cursor pagination is active.
        if (ctx.isPaginationCursor()) {
            units.add(unit(javaSrc + "/common/CursorPage.java",
                    "common/CursorPage.java.ftl", projectModel));
        }

        // Auditable + AuditorAware are JPA-specific (Spring Data JPA Auditing).
        if (ctx.isJpa() && (boolean) projectModel.get("anyAuditable")) {
            units.add(unit(javaSrc + "/common/Auditable.java",
                    "common/Auditable.java.ftl", projectModel));
            if ((boolean) projectModel.get("anyHasAuditUser")) {
                units.add(unit(javaSrc + "/common/AuditorAwareConfig.java",
                        "common/AuditorAwareConfig.java.ftl", projectModel));
            }
        } else if (!ctx.isJpa() && (boolean) projectModel.get("anyAuditable")) {
            units.add(unit(javaSrc + "/common/AuditProvider.java",
                    "common/AuditProvider.java.ftl", projectModel));
        }

        // Phase H — generated-project tooling. Skipped in overlay (user's project owns these).
        if (!ctx.overlay()) {
            if (ctx.docker().enabled()) {
                units.add(unit("Dockerfile", "common/Dockerfile.ftl", projectModel));
                units.add(unit("docker-compose.yml", "common/docker-compose.yml.ftl", projectModel));
            }
            if (ctx.ci().isGithub()) {
                units.add(unit(".github/workflows/ci.yml", "common/github-ci.yml.ftl", projectModel));
            } else if (ctx.ci().isGitlab()) {
                units.add(unit(".gitlab-ci.yml", "common/gitlab-ci.yml.ftl", projectModel));
            }
            // logback-spring.xml emitted when JSON logging or correlation id is configured;
            // plain console logging without correlation id is Spring Boot's default.
            if (ctx.logging().isJson() || ctx.logging().correlationId()) {
                units.add(unit(resources + "/logback-spring.xml",
                        "common/logback-spring.xml.ftl", projectModel));
            }
            // Phase I — emit AbstractIntegrationTest base once per project.
            if (ctx.tests().enabled()) {
                units.add(unit("src/test/java/" + ctx.basePackagePath() + "/AbstractIntegrationTest.java",
                        "common/AbstractIntegrationTest.java.ftl", projectModel));
            }
            // Phase O — security scaffolding (project-wide; skipped in overlay).
            if (ctx.isSecurityEnabled()) {
                units.add(unit(javaSrc + "/security/SecurityConfig.java",
                        "common/SecurityConfig.java.ftl", projectModel));
                if (ctx.isSecurityJwt()) {
                    units.add(unit(javaSrc + "/security/JwtTokenService.java",
                            "common/JwtTokenService.java.ftl", projectModel));
                    units.add(unit(javaSrc + "/security/JwtAuthenticationFilter.java",
                            "common/JwtAuthenticationFilter.java.ftl", projectModel));
                    units.add(unit(javaSrc + "/security/AuthController.java",
                            "common/AuthController.java.ftl", projectModel));
                    units.add(unit(javaSrc + "/security/LoginRequest.java",
                            "common/LoginRequest.java.ftl", projectModel));
                    units.add(unit(javaSrc + "/security/LoginResponse.java",
                            "common/LoginResponse.java.ftl", projectModel));
                }
            }
        }
        // Correlation id filter is a regular Java source — kept even in overlay so a user
        // adding correlation IDs to an existing project still gets the filter.
        if (ctx.logging().correlationId()) {
            units.add(unit(javaSrc + "/common/CorrelationIdFilter.java",
                    "common/CorrelationIdFilter.java.ftl", projectModel));
        }

        for (TableModel table : schema.tables()) {
            if (table.junction()) continue;
            Map<String, Object> model = EntityView.build(table, ctx);
            String entityName = (String) model.get("entityName");

            // Persistence-specific: entity + repository / mapper / mapper xml
            units.addAll(persistence.generateForTable(table, ctx));

            // Common application layer
            units.add(unit(javaSrc + "/service/" + entityName + "Service.java",
                    "mvc-jpa/Service.java.ftl", model));
            units.add(unit(javaSrc + "/service/impl/" + entityName + "ServiceImpl.java",
                    serviceImplTemplate(), model));
            units.add(unit(javaSrc + "/controller/" + entityName + "Controller.java",
                    "mvc-jpa/Controller.java.ftl", model));
            units.add(unit(javaSrc + "/dto/" + entityName + "RequestDTO.java",
                    "mvc-jpa/RequestDTO.java.ftl", model));
            units.add(unit(javaSrc + "/dto/" + entityName + "ResponseDTO.java",
                    "mvc-jpa/ResponseDTO.java.ftl", model));
            units.add(unit(javaSrc + "/mapper/" + entityName + "DtoMapper.java",
                    mapperTemplate(), model));
            units.add(unit(javaSrc + "/exception/" + entityName + "NotFoundException.java",
                    "mvc-jpa/NotFoundException.java.ftl", model));
            // Phase I — per-entity integration test
            if (ctx.tests().enabled() && !ctx.overlay()) {
                units.add(unit("src/test/java/" + ctx.basePackagePath() + "/" + entityName + "IntegrationTest.java",
                        "common/EntityIntegrationTest.java.ftl", model));
            }
        }

        if (ctx.isOpenApiYaml()) {
            units.add(new GeneratedUnit(
                    "src/main/resources/openapi.yaml",
                    new OpenApiEmitter(ctx).emit(schema)));
        } else if (ctx.isOpenApiAnnotation()) {
            units.add(unit(javaSrc + "/config/OpenApiConfig.java",
                    "common/OpenApiConfig.java.ftl", projectModel));
        }

        return units;
    }

    /**
     * The MyBatis path uses a Mapper interface (not a JpaRepository), so it
     * needs a different ServiceImpl template that calls {@code mapper.insert(...)}
     * style methods. JPA uses a {@link org.springframework.data.jpa.repository.JpaRepository}
     * with Spring Data {@code Pageable}. jOOQ exposes a thin facade with
     * {@code findAll(page, size)} ints, which needs a third variant.
     */
    private String serviceImplTemplate() {
        if (ctx.isMyBatis()) return "mvc-jpa/ServiceImplMyBatis.java.ftl";
        if (ctx.isJooq()) return "mvc-jpa/ServiceImplJooq.java.ftl";
        return "mvc-jpa/ServiceImpl.java.ftl";
    }

    /**
     * Mapper template variant — manual hand-rolled mapper (default), or MapStruct
     * when {@code generation.jpa.useMapStruct=true}.
     */
    private String mapperTemplate() {
        if (ctx.useMapStruct()) return "mvc-jpa/MapStructMapper.java.ftl";
        return "mvc-jpa/Mapper.java.ftl";
    }

    private GeneratedUnit unit(String relativePath, String template, Map<String, Object> model) {
        return new GeneratedUnit(relativePath, engine.render(template, model));
    }

    private Map<String, Object> projectModel(SchemaModel schema) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("basePackage", ctx.basePackage());
        m.put("projectName", ctx.projectName());
        m.put("projectGroup", ctx.projectGroup());
        m.put("springBootVersion", ctx.springBootVersion());
        m.put("javaVersion", ctx.javaVersion());
        m.put("javaMajor", ctx.javaMajor());
        m.put("javaSupportsStringIsBlank", ctx.javaSupportsStringIsBlank());
        m.put("javaSupportsListOf", ctx.javaSupportsListOf());
        m.put("javaSupportsListCopyOf", ctx.javaSupportsListCopyOf());
        m.put("javaSupportsStreamToList", ctx.javaSupportsStreamToList());
        m.put("useLombok", ctx.useLombok());
        m.put("lombokVersion", ctx.lombokVersion());
        m.put("logstashLogbackEncoderVersion", ctx.logstashLogbackEncoderVersion());
        m.put("useMapStruct", ctx.useMapStruct());
        m.put("openApiAnnotation", ctx.isOpenApiAnnotation());
        m.put("injectionStyle", ctx.injectionStyle());
        m.put("injectConstructor", ctx.isInjectionConstructor());
        m.put("injectLombok", ctx.isInjectionLombok());
        m.put("injectAutowired", ctx.isInjectionAutowired());
        m.put("validationStyle", ctx.validationStyle());
        m.put("validationJakarta", ctx.isValidationJakarta());
        m.put("validationNone", ctx.isValidationNone());
        m.put("validationService", ctx.isValidationService());
        m.put("dtoStyle", ctx.dtoStyle());
        m.put("dtoClass", ctx.isDtoClass());
        m.put("dtoRecord", ctx.isDtoRecord());
        m.put("dtoShape", ctx.dtoShape());
        m.put("dtoSeparate", ctx.isDtoSeparate());
        m.put("dtoSingle", ctx.isDtoSingle());
        m.put("exceptionStyle", ctx.exceptionStyle());
        m.put("exceptionEnvelope", ctx.isExceptionEnvelope());
        m.put("exceptionProblemDetail", ctx.isExceptionProblemDetail());
        m.put("anyAuditable", EntityView.anyTableHasAudit(schema, ctx));
        m.put("manualAudit", !ctx.isJpa() && EntityView.anyTableHasAudit(schema, ctx));
        m.put("anyHasAuditUser", EntityView.anyTableHasAuditUser(schema, ctx));
        m.put("anyHasCreatedAt", EntityView.anyTableHasCreatedAt(schema, ctx));
        m.put("anyHasUpdatedAt", EntityView.anyTableHasUpdatedAt(schema, ctx));
        m.put("anyHasCreatedBy", EntityView.anyTableHasCreatedBy(schema, ctx));
        m.put("anyHasUpdatedBy", EntityView.anyTableHasUpdatedBy(schema, ctx));
        m.put("auditCreatedAt", ctx.audit().createdAt());
        m.put("auditUpdatedAt", ctx.audit().updatedAt());
        m.put("auditCreatedBy", ctx.audit().createdBy());
        m.put("auditUpdatedBy", ctx.audit().updatedBy());
        m.put("auditablePackage", ctx.basePackage() + ".common");
        // Phase H — project tooling flags
        m.put("dockerEnabled", ctx.docker().enabled());
        m.put("dockerBaseImage", ctx.docker().baseImage());
        m.put("dockerPort", ctx.docker().port());
        m.put("ciStyle", ctx.ci().style());
        m.put("ciGithub", ctx.ci().isGithub());
        m.put("ciGitlab", ctx.ci().isGitlab());
        m.put("gradleVersion", ctx.gradleVersion());
        m.put("loggingStyle", ctx.logging().style());
        m.put("loggingJson", ctx.logging().isJson());
        m.put("loggingPlain", ctx.logging().isPlain());
        m.put("loggingCorrelationId", ctx.logging().correlationId());
        m.put("dbDriver", ctx.dbDriver());
        m.put("dbIsMysql", ctx.isDbMysql());
        m.put("dbIsMariadb", ctx.isDbMariadb());
        m.put("dbIsSqlserver", ctx.isDbSqlserver());
        m.put("dbIsSqlite", ctx.isDbSqlite());
        m.put("dbIsPostgres", ctx.isDbPostgres());
        m.put("isMaven", ctx.isMaven());
        m.put("isGradle", ctx.isGradle());
        m.put("jdbcUrl", ctx.jdbcUrl());
        m.put("jdbcUsername", ctx.jdbcUsername());
        m.put("jdbcPassword", ctx.jdbcPassword());
        m.put("jdbcDriverClass", ctx.jdbcDriverClass());
        m.put("jdbcDriverDependencyGroupId", ctx.jdbcDriverDependencyGroupId());
        m.put("jdbcDriverDependencyArtifactId", ctx.jdbcDriverDependencyArtifactId());
        m.put("jdbcDriverDependencyCoordinate", ctx.jdbcDriverDependencyCoordinate());
        m.put("sqliteDialectDependencyGroupId", ctx.sqliteDialectDependencyGroupId());
        m.put("sqliteDialectDependencyArtifactId", ctx.sqliteDialectDependencyArtifactId());
        m.put("sqliteDialectDependencyVersion", ctx.sqliteDialectDependencyVersion());
        m.put("sqliteDialectDependencyCoordinate", ctx.sqliteDialectDependencyCoordinate());
        m.put("sqliteHibernateDialectClass", ctx.sqliteHibernateDialectClass());
        m.put("eeNamespace", ctx.eeNamespace());
        m.put("springBoot2", ctx.isSpringBoot2());
        m.put("springBoot3", ctx.isSpringBoot3());
        m.put("springBootMajor", ctx.springBootMajor());
        m.put("springdocOpenApiArtifactId", ctx.springdocOpenApiArtifactId());
        m.put("springdocOpenApiVersion", ctx.springdocOpenApiVersion());
        m.put("securityStyle", ctx.security().style());
        m.put("securityEnabled", ctx.isSecurityEnabled());
        m.put("securityNone", ctx.isSecurityNone());
        m.put("securityBasic", ctx.isSecurityBasic());
        m.put("securityJwt", ctx.isSecurityJwt());
        m.put("securityUsers", ctx.security().users());
        m.put("jwtSecret", ctx.security().jwt().secret() == null ? "change-me" : ctx.security().jwt().secret());
        m.put("jwtExpirationMinutes", ctx.security().jwt().expirationMinutes());
        m.put("jwtHeader", ctx.security().jwt().header());
        m.put("jwtPrefix", ctx.security().jwt().prefix());
        m.put("testsEnabled", ctx.tests().enabled());
        m.put("migrationStyle", ctx.migrations().style());
        m.put("migrationFlyway", ctx.isMigrationFlyway());
        m.put("flywayDatabaseModule", ctx.flywayDatabaseModule());
        m.put("renderFlywayDatabaseModule", ctx.renderFlywayDatabaseModule());
        m.put("jooqVersion", ctx.jooqVersion());
        m.put("jooqGradlePluginVersion", ctx.jooqGradlePluginVersion());
        m.put("jooqCodegenDriverGroupId", ctx.jooqCodegenDriverGroupId());
        m.put("jooqCodegenDriverArtifactId", ctx.jooqCodegenDriverArtifactId());
        m.put("jooqCodegenDriverVersion", ctx.jooqCodegenDriverVersion());
        m.put("jooqCodegenDriverCoordinate", ctx.jooqCodegenDriverCoordinate());
        m.put("paginationStyle", ctx.paginationStyle());
        m.put("paginationCursor", ctx.isPaginationCursor());
        m.put("paginationOffset", ctx.isPaginationOffset());
        m.put("persistence", ctx.persistence());
        m.put("isJpa", ctx.isJpa());
        m.put("isMyBatis", ctx.isMyBatis());
        m.put("isJooq", ctx.isJooq());
        m.put("myBatisXml", ctx.myBatisXml());
        m.put("schemaName", schema.schemaName());
        m.put("tables", schema.tables());
        return m;
    }
}
