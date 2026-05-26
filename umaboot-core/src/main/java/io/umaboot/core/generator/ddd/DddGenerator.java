package io.umaboot.core.generator.ddd;

import io.umaboot.core.generator.ArchitectureGenerator;
import io.umaboot.core.generator.GeneratedUnit;
import io.umaboot.core.generator.GeneratorContext;
import io.umaboot.core.generator.jpa.EntityView;
import io.umaboot.core.generator.openapi.OpenApiEmitter;
import io.umaboot.core.model.SchemaModel;
import io.umaboot.core.model.TableModel;
import io.umaboot.core.template.Naming;
import io.umaboot.core.template.TemplateEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * DDD architecture generator.
 *
 * <p>Per aggregate root, emits domain (aggregate, repository port, exception, events),
 * application (service + commands), interfaces (REST controller, DTOs, web mapper),
 * and infrastructure (JPA or MyBatis persistence) layers.</p>
 *
 * <p>v0.5: supports {@code persistence: jpa} and {@code persistence: mybatis}
 * (with {@code mybatis.style: xml | annotation}). jOOQ is post-v0.5.</p>
 */
public final class DddGenerator implements ArchitectureGenerator {

    private static final Logger LOG = LoggerFactory.getLogger(DddGenerator.class);

    private final TemplateEngine engine;
    private final GeneratorContext ctx;

    public DddGenerator(TemplateEngine engine, GeneratorContext ctx) {
        this.engine = Objects.requireNonNull(engine, "engine");
        this.ctx = Objects.requireNonNull(ctx, "ctx");
        if (!ctx.isJpa() && !ctx.isMyBatis()) {
            throw new IllegalArgumentException(
                    "DDD supports persistence=jpa or mybatis; got " + ctx.persistence());
        }
    }

    @Override
    public String architecture() {
        return "ddd";
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
            units.add(unit("pom.xml", "ddd/pom.xml.ftl", pm));
            units.add(unit(resources + "/application.yml", "ddd/application.yml.ftl", pm));
            units.add(unit(javaSrc + "/Application.java", "ddd/Application.java.ftl", pm));
            units.add(unit(javaSrc + "/interfaces/rest/GlobalExceptionHandler.java",
                    "ddd/GlobalExceptionHandler.java.ftl", pm));
            if (ctx.isExceptionEnvelope()) {
                Map<String, Object> envelopeModel = new java.util.LinkedHashMap<>(pm);
                envelopeModel.put("apiErrorPackage", ctx.basePackage() + ".interfaces.rest");
                units.add(unit(javaSrc + "/interfaces/rest/ApiError.java",
                        "common/ApiError.java.ftl", envelopeModel));
            }
        }
        // DomainEvent is a small marker interface — safe to write in overlay too,
        // as long as the user doesn't already have one at the same path. Skipped
        // in overlay to be safe; the project's own DomainEvent (if any) is preferred.
        if (!ctx.overlay()) {
            units.add(unit(javaSrc + "/domain/shared/DomainEvent.java",
                    "ddd/DomainEvent.java.ftl", pm));
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
            if (!ctx.ddd().isAggregateRoot(table.name())) {
                LOG.debug("Skipping non-aggregate-root table: {}", table.name());
                continue;
            }

            Map<String, Object> m = aggregateModel(table);
            String entityName = (String) m.get("entityName");
            String aggregatePackage = (String) m.get("aggregatePackage");

            // Domain
            units.add(unit(javaSrc + "/domain/" + aggregatePackage + "/" + entityName + ".java",
                    "ddd/AggregateRoot.java.ftl", m));
            units.add(unit(javaSrc + "/domain/" + aggregatePackage + "/" + entityName + "Repository.java",
                    "ddd/DomainRepository.java.ftl", m));
            units.add(unit(javaSrc + "/domain/" + aggregatePackage + "/" + entityName + "NotFoundException.java",
                    "ddd/DomainException.java.ftl", m));
            units.add(unit(javaSrc + "/domain/" + aggregatePackage + "/event/" + entityName + "CreatedEvent.java",
                    "ddd/CreatedEvent.java.ftl", m));
            units.add(unit(javaSrc + "/domain/" + aggregatePackage + "/event/" + entityName + "UpdatedEvent.java",
                    "ddd/UpdatedEvent.java.ftl", m));

            // Application
            units.add(unit(javaSrc + "/application/" + aggregatePackage + "/" + entityName + "ApplicationService.java",
                    "ddd/ApplicationService.java.ftl", m));
            units.add(unit(javaSrc + "/application/" + aggregatePackage + "/command/Create" + entityName + "Command.java",
                    "ddd/CreateCommand.java.ftl", m));
            units.add(unit(javaSrc + "/application/" + aggregatePackage + "/command/Update" + entityName + "Command.java",
                    "ddd/UpdateCommand.java.ftl", m));

            // Interfaces (REST) — same for both persistence backends
            units.add(unit(javaSrc + "/interfaces/rest/" + entityName + "Controller.java",
                    "ddd/Controller.java.ftl", m));
            units.add(unit(javaSrc + "/interfaces/rest/dto/Create" + entityName + "Request.java",
                    "ddd/CreateRequest.java.ftl", m));
            units.add(unit(javaSrc + "/interfaces/rest/dto/Update" + entityName + "Request.java",
                    "ddd/UpdateRequest.java.ftl", m));
            units.add(unit(javaSrc + "/interfaces/rest/dto/" + entityName + "Response.java",
                    "ddd/Response.java.ftl", m));
            units.add(unit(javaSrc + "/interfaces/rest/mapper/" + entityName + "WebMapper.java",
                    "ddd/WebMapper.java.ftl", m));

            // Infrastructure — branches on persistence
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
        units.add(unit(javaSrc + "/infrastructure/persistence/" + entityName + "JpaEntity.java",
                "ddd/JpaEntity.java.ftl", m));
        units.add(unit(javaSrc + "/infrastructure/persistence/" + entityName + "JpaRepository.java",
                "ddd/JpaRepository.java.ftl", m));
        units.add(unit(javaSrc + "/infrastructure/persistence/" + entityName + "RepositoryImpl.java",
                "ddd/RepositoryImpl.java.ftl", m));
        units.add(unit(javaSrc + "/infrastructure/persistence/" + entityName + "JpaMapper.java",
                "ddd/JpaMapper.java.ftl", m));
    }

    private void emitMyBatisPersistence(List<GeneratedUnit> units, String javaSrc,
                                        String entityName, Map<String, Object> m) {
        units.add(unit(javaSrc + "/infrastructure/persistence/" + entityName + "PersistenceModel.java",
                "ddd/PersistenceModel.java.ftl", m));
        if (ctx.myBatisAnnotation()) {
            units.add(unit(javaSrc + "/infrastructure/persistence/" + entityName + "MyBatisMapper.java",
                    "ddd/MyBatisMapperAnnotation.java.ftl", m));
        } else {
            units.add(unit(javaSrc + "/infrastructure/persistence/" + entityName + "MyBatisMapper.java",
                    "ddd/MyBatisMapperInterface.java.ftl", m));
            units.add(unit("src/main/resources/mapper/" + entityName + "MyBatisMapper.xml",
                    "ddd/MyBatisMapperXml.xml.ftl", m));
        }
        units.add(unit(javaSrc + "/infrastructure/persistence/" + entityName + "RepositoryImpl.java",
                "ddd/MyBatisRepositoryImpl.java.ftl", m));
        units.add(unit(javaSrc + "/infrastructure/persistence/" + entityName + "PersistenceMapper.java",
                "ddd/MyBatisPersistenceMapper.java.ftl", m));
    }

    private GeneratedUnit unit(String relativePath, String template, Map<String, Object> model) {
        return new GeneratedUnit(relativePath, engine.render(template, model));
    }

    private Map<String, Object> aggregateModel(TableModel table) {
        Map<String, Object> m = new LinkedHashMap<>(EntityView.build(table, ctx));
        String aggregatePackage = Naming.singularize(table.name()).toLowerCase(Locale.ROOT);
        m.put("aggregatePackage", aggregatePackage);
        return m;
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
