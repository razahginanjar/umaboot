package io.umaboot.core.generator.hexagonal;

import io.umaboot.core.generator.ArchitectureGenerator;
import io.umaboot.core.generator.GeneratedUnit;
import io.umaboot.core.generator.GeneratorContext;
import io.umaboot.core.generator.jpa.EntityView;
import io.umaboot.core.generator.openapi.OpenApiEmitter;
import io.umaboot.core.model.SchemaModel;
import io.umaboot.core.model.TableModel;
import io.umaboot.core.template.TemplateEngine;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Hexagonal / Ports &amp; Adapters generator.
 *
 * <p>Per non-junction table, emits the full Hexagonal layer set:</p>
 * <ul>
 *   <li>Domain layer: model, port, exception</li>
 *   <li>Application layer: use case interface, application service</li>
 *   <li>Inbound adapter (web): controller, request/response DTOs, mapper</li>
 *   <li>Outbound adapter (persistence): JPA entity + repository + adapter + mapper
 *       <em>or</em> MyBatis persistence model + mapper interface (+ XML) + adapter + mapper,
 *       depending on {@code persistence}.</li>
 * </ul>
 *
 * <p>v0.5: supports {@code persistence: jpa} (default) and {@code persistence: mybatis}
 * (with {@code mybatis.style: xml | annotation}). jOOQ is post-v0.5.</p>
 */
public final class HexagonalGenerator implements ArchitectureGenerator {

    private final TemplateEngine engine;
    private final GeneratorContext ctx;

    public HexagonalGenerator(TemplateEngine engine, GeneratorContext ctx) {
        this.engine = Objects.requireNonNull(engine, "engine");
        this.ctx = Objects.requireNonNull(ctx, "ctx");
        if (!ctx.isJpa() && !ctx.isMyBatis()) {
            throw new IllegalArgumentException(
                    "Hexagonal supports persistence=jpa or mybatis; got " + ctx.persistence());
        }
    }

    @Override
    public String architecture() {
        return "hexagonal";
    }

    @Override
    public List<GeneratedUnit> generate(SchemaModel schema) {
        Objects.requireNonNull(schema, "schema");
        List<GeneratedUnit> units = new ArrayList<>();
        String javaSrc = "src/main/java/" + ctx.basePackagePath();
        String resources = "src/main/resources";

        // Project-wide
        Map<String, Object> pm = projectModel(schema);
        if (!ctx.overlay()) {
            units.add(unit("pom.xml", "hexagonal/pom.xml.ftl", pm));
            units.add(unit(resources + "/application.yml", "hexagonal/application.yml.ftl", pm));
            units.add(unit(javaSrc + "/Application.java", "hexagonal/Application.java.ftl", pm));
            units.add(unit(javaSrc + "/adapter/in/web/GlobalExceptionHandler.java",
                    "hexagonal/GlobalExceptionHandler.java.ftl", pm));
            if (ctx.isExceptionEnvelope()) {
                Map<String, Object> envelopeModel = new java.util.LinkedHashMap<>(pm);
                envelopeModel.put("apiErrorPackage", ctx.basePackage() + ".adapter.in.web");
                units.add(unit(javaSrc + "/adapter/in/web/ApiError.java",
                        "common/ApiError.java.ftl", envelopeModel));
            }
        }

        // PageResponse is referenced by every Controller — emit even in overlay mode.
        units.add(unit(javaSrc + "/common/PageResponse.java",
                "common/PageResponse.java.ftl", pm));

        // Auditable + AuditorAware are JPA-specific.
        if (ctx.isJpa() && (boolean) pm.get("anyAuditable")) {
            units.add(unit(javaSrc + "/common/Auditable.java",
                    "common/Auditable.java.ftl", pm));
            if ((boolean) pm.get("anyHasAuditUser")) {
                units.add(unit(javaSrc + "/common/AuditorAwareConfig.java",
                        "common/AuditorAwareConfig.java.ftl", pm));
            }
        }

        // Phase H — generated-project tooling. Skipped in overlay.
        if (!ctx.overlay()) {
            if (ctx.docker().enabled()) {
                units.add(unit("Dockerfile", "common/Dockerfile.ftl", pm));
                units.add(unit("docker-compose.yml", "common/docker-compose.yml.ftl", pm));
            }
            if (ctx.ci().isGithub()) {
                units.add(unit(".github/workflows/ci.yml", "common/github-ci.yml.ftl", pm));
            } else if (ctx.ci().isGitlab()) {
                units.add(unit(".gitlab-ci.yml", "common/gitlab-ci.yml.ftl", pm));
            }
            if (ctx.logging().isJson() || ctx.logging().correlationId()) {
                units.add(unit(resources + "/logback-spring.xml",
                        "common/logback-spring.xml.ftl", pm));
            }
            if (ctx.tests().enabled()) {
                units.add(unit("src/test/java/" + ctx.basePackagePath() + "/AbstractIntegrationTest.java",
                        "common/AbstractIntegrationTest.java.ftl", pm));
            }
            if (ctx.isSecurityEnabled()) {
                units.add(unit(javaSrc + "/security/SecurityConfig.java",
                        "common/SecurityConfig.java.ftl", pm));
                if (ctx.isSecurityJwt()) {
                    units.add(unit(javaSrc + "/security/JwtTokenService.java",
                            "common/JwtTokenService.java.ftl", pm));
                    units.add(unit(javaSrc + "/security/JwtAuthenticationFilter.java",
                            "common/JwtAuthenticationFilter.java.ftl", pm));
                    units.add(unit(javaSrc + "/security/AuthController.java",
                            "common/AuthController.java.ftl", pm));
                    units.add(unit(javaSrc + "/security/LoginRequest.java",
                            "common/LoginRequest.java.ftl", pm));
                    units.add(unit(javaSrc + "/security/LoginResponse.java",
                            "common/LoginResponse.java.ftl", pm));
                }
            }
        }
        if (ctx.logging().correlationId()) {
            units.add(unit(javaSrc + "/common/CorrelationIdFilter.java",
                    "common/CorrelationIdFilter.java.ftl", pm));
        }

        for (TableModel table : schema.tables()) {
            if (table.junction()) continue;
            Map<String, Object> m = EntityView.build(table, ctx);
            String entityName = (String) m.get("entityName");

            // Domain
            units.add(unit(javaSrc + "/domain/model/" + entityName + ".java",
                    "hexagonal/DomainModel.java.ftl", m));
            units.add(unit(javaSrc + "/domain/port/" + entityName + "Repository.java",
                    "hexagonal/DomainPort.java.ftl", m));
            units.add(unit(javaSrc + "/domain/exception/" + entityName + "NotFoundException.java",
                    "hexagonal/DomainException.java.ftl", m));

            // Application
            units.add(unit(javaSrc + "/application/usecase/" + entityName + "UseCase.java",
                    "hexagonal/UseCase.java.ftl", m));
            units.add(unit(javaSrc + "/application/service/" + entityName + "ApplicationService.java",
                    "hexagonal/ApplicationService.java.ftl", m));

            // Inbound adapter (REST) — same for both persistence backends
            units.add(unit(javaSrc + "/adapter/in/web/" + entityName + "Controller.java",
                    "hexagonal/WebController.java.ftl", m));
            units.add(unit(javaSrc + "/adapter/in/web/dto/" + entityName + "Request.java",
                    "hexagonal/WebRequest.java.ftl", m));
            units.add(unit(javaSrc + "/adapter/in/web/dto/" + entityName + "Response.java",
                    "hexagonal/WebResponse.java.ftl", m));
            units.add(unit(javaSrc + "/adapter/in/web/mapper/" + entityName + "WebMapper.java",
                    "hexagonal/WebMapper.java.ftl", m));

            // Outbound adapter — branches on persistence
            if (ctx.isJpa()) {
                emitJpaPersistence(units, javaSrc, entityName, m);
            } else {
                emitMyBatisPersistence(units, javaSrc, entityName, m);
            }

            if (ctx.tests().enabled() && !ctx.overlay()) {
                units.add(unit("src/test/java/" + ctx.basePackagePath() + "/" + entityName + "IntegrationTest.java",
                        "common/EntityIntegrationTest.java.ftl", m));
            }
        }

        if (ctx.isOpenApiYaml()) {
            units.add(new GeneratedUnit(
                    "src/main/resources/openapi.yaml",
                    new OpenApiEmitter(ctx).emit(schema)));
        } else if (ctx.isOpenApiAnnotation()) {
            units.add(unit(javaSrc + "/config/OpenApiConfig.java",
                    "common/OpenApiConfig.java.ftl", pm));
        }

        return units;
    }

    private void emitJpaPersistence(List<GeneratedUnit> units, String javaSrc,
                                    String entityName, Map<String, Object> m) {
        units.add(unit(javaSrc + "/adapter/out/persistence/" + entityName + "JpaEntity.java",
                "hexagonal/JpaEntity.java.ftl", m));
        units.add(unit(javaSrc + "/adapter/out/persistence/" + entityName + "JpaRepository.java",
                "hexagonal/JpaRepository.java.ftl", m));
        units.add(unit(javaSrc + "/adapter/out/persistence/" + entityName + "PersistenceAdapter.java",
                "hexagonal/PersistenceAdapter.java.ftl", m));
        units.add(unit(javaSrc + "/adapter/out/persistence/mapper/" + entityName + "PersistenceMapper.java",
                "hexagonal/PersistenceMapper.java.ftl", m));
    }

    private void emitMyBatisPersistence(List<GeneratedUnit> units, String javaSrc,
                                        String entityName, Map<String, Object> m) {
        units.add(unit(javaSrc + "/adapter/out/persistence/" + entityName + "PersistenceModel.java",
                "hexagonal/PersistenceModel.java.ftl", m));
        if (ctx.myBatisAnnotation()) {
            units.add(unit(javaSrc + "/adapter/out/persistence/" + entityName + "MyBatisMapper.java",
                    "hexagonal/MyBatisMapperAnnotation.java.ftl", m));
        } else {
            units.add(unit(javaSrc + "/adapter/out/persistence/" + entityName + "MyBatisMapper.java",
                    "hexagonal/MyBatisMapperInterface.java.ftl", m));
            units.add(unit("src/main/resources/mapper/" + entityName + "MyBatisMapper.xml",
                    "hexagonal/MyBatisMapperXml.xml.ftl", m));
        }
        units.add(unit(javaSrc + "/adapter/out/persistence/" + entityName + "PersistenceAdapter.java",
                "hexagonal/MyBatisPersistenceAdapter.java.ftl", m));
        units.add(unit(javaSrc + "/adapter/out/persistence/mapper/" + entityName + "PersistenceMapper.java",
                "hexagonal/MyBatisPersistenceMapper.java.ftl", m));
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
        m.put("useLombok", ctx.useLombok());
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
        m.put("dockerEnabled", ctx.docker().enabled());
        m.put("dockerBaseImage", ctx.docker().baseImage());
        m.put("dockerPort", ctx.docker().port());
        m.put("ciStyle", ctx.ci().style());
        m.put("ciGithub", ctx.ci().isGithub());
        m.put("ciGitlab", ctx.ci().isGitlab());
        m.put("loggingStyle", ctx.logging().style());
        m.put("loggingJson", ctx.logging().isJson());
        m.put("loggingPlain", ctx.logging().isPlain());
        m.put("loggingCorrelationId", ctx.logging().correlationId());
        m.put("dbDriver", ctx.dbDriver());
        m.put("dbIsMysql", ctx.isDbMysql());
        m.put("dbIsPostgres", ctx.isDbPostgres());
        m.put("jdbcUrl", ctx.jdbcUrl());
        m.put("jdbcUsername", ctx.jdbcUsername());
        m.put("jdbcPassword", ctx.jdbcPassword());
        m.put("jdbcDriverClass", ctx.jdbcDriverClass());
        m.put("eeNamespace", ctx.eeNamespace());
        m.put("springBoot2", ctx.isSpringBoot2());
        m.put("springBoot3", ctx.isSpringBoot3());
        m.put("springBootMajor", ctx.springBootMajor());
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
        m.put("schemaName", schema.schemaName());
        m.put("isJpa", ctx.isJpa());
        m.put("isMyBatis", ctx.isMyBatis());
        m.put("myBatisXml", ctx.myBatisXml());
        return m;
    }
}
