package io.umaboot.core.generator;

import io.umaboot.core.config.UmabootConfig;

import java.util.Objects;

/**
 * Shared options passed to all generators in a single run.
 *
 * @param basePackage         e.g. {@code com.example.app}
 * @param projectName         Maven artifactId of the generated project
 * @param projectGroup        Maven groupId of the generated project
 * @param springBootVersion   Spring Boot version to declare in the generated POM
 * @param javaVersion         Target Java version for the generated project (e.g. "17")
 * @param useLombok           if true, generate Lombok-annotated entities/DTOs
 * @param architecture        {@code mvc | hexagonal | ddd}
 * @param persistence         {@code jpa | mybatis | jooq}
 * @param mybatisStyle        when persistence=mybatis: {@code xml | annotation}
 * @param useMapStruct        when persistence=jpa: use MapStruct mappers (compile-time)
 * @param openApiStyle        {@code yaml | annotation | none}
 * @param injectionStyle      bean injection style for services/controllers:
 *                            {@code constructor | lombok | autowired}
 * @param validationStyle     {@code jakarta | none | service}
 * @param dtoStyle            {@code class | record}
 * @param dtoShape            {@code separate | single}
 * @param exceptionStyle      {@code problemdetail | envelope}
 * @param ddd                 DDD-specific options (aggregate-root list, shared kernel package)
 * @param overlay             true when output mode is overlay — generators skip emitting
 *                            project-wide files (pom.xml, Application.java, application.yml,
 *                            GlobalExceptionHandler) so they don't clobber an existing project
 */
public record GeneratorContext(
        String basePackage,
        String projectName,
        String projectGroup,
        String springBootVersion,
        String javaVersion,
        boolean useLombok,
        String architecture,
        String persistence,
        String mybatisStyle,
        boolean useMapStruct,
        String openApiStyle,
        String injectionStyle,
        String validationStyle,
        String dtoStyle,
        String dtoShape,
        String exceptionStyle,
        UmabootConfig.AuditOptions audit,
        UmabootConfig.SoftDeleteOptions softDelete,
        UmabootConfig.DockerOptions docker,
        UmabootConfig.CiOptions ci,
        UmabootConfig.LoggingOptions logging,
        UmabootConfig.TestOptions tests,
        String paginationStyle,
        UmabootConfig.SecurityOptions security,
        UmabootConfig.DddOptions ddd,
        boolean overlay,
        String dbDriver) {

    public GeneratorContext {
        Objects.requireNonNull(basePackage, "basePackage");
        Objects.requireNonNull(projectName, "projectName");
        Objects.requireNonNull(projectGroup, "projectGroup");
        Objects.requireNonNull(springBootVersion, "springBootVersion");
        Objects.requireNonNull(javaVersion, "javaVersion");
        architecture = architecture == null ? "mvc" : architecture.toLowerCase();
        persistence = persistence == null ? "jpa" : persistence.toLowerCase();
        mybatisStyle = mybatisStyle == null ? "xml" : mybatisStyle.toLowerCase();
        openApiStyle = openApiStyle == null ? "yaml" : openApiStyle.toLowerCase();
        injectionStyle = injectionStyle == null ? "constructor" : injectionStyle.toLowerCase();
        validationStyle = validationStyle == null ? "jakarta" : validationStyle.toLowerCase();
        dtoStyle = dtoStyle == null ? "class" : dtoStyle.toLowerCase();
        dtoShape = dtoShape == null ? "separate" : dtoShape.toLowerCase();
        exceptionStyle = exceptionStyle == null ? "problemdetail" : exceptionStyle.toLowerCase();
        if (!"constructor".equals(injectionStyle) && !"lombok".equals(injectionStyle) && !"autowired".equals(injectionStyle)) {
            throw new IllegalArgumentException(
                    "injectionStyle must be 'constructor', 'lombok', or 'autowired' (got: " + injectionStyle + ")");
        }
        if ("lombok".equals(injectionStyle) && !useLombok) {
            throw new IllegalArgumentException("injectionStyle=lombok requires useLombok=true");
        }
        ddd = ddd == null ? UmabootConfig.DddOptions.defaults() : ddd;
        audit = audit == null ? UmabootConfig.AuditOptions.defaults() : audit;
        softDelete = softDelete == null ? UmabootConfig.SoftDeleteOptions.defaults() : softDelete;
        docker = docker == null ? UmabootConfig.DockerOptions.defaults() : docker;
        ci = ci == null ? UmabootConfig.CiOptions.defaults() : ci;
        logging = logging == null ? UmabootConfig.LoggingOptions.defaults() : logging;
        tests = tests == null ? UmabootConfig.TestOptions.defaults() : tests;
        paginationStyle = paginationStyle == null ? "offset" : paginationStyle.toLowerCase();
        if (!"offset".equals(paginationStyle) && !"cursor".equals(paginationStyle)) {
            throw new IllegalArgumentException(
                    "paginationStyle must be 'offset' or 'cursor' (got: " + paginationStyle + ")");
        }
        security = security == null ? UmabootConfig.SecurityOptions.defaults() : security;
        dbDriver = dbDriver == null ? "postgres" : dbDriver.toLowerCase();
    }

    /** Defaults: MVC + JPA + Spring Boot 3.3.5 + Java 17 + Lombok, standalone mode. */
    public static GeneratorContext defaults(String basePackage, String projectName) {
        return new GeneratorContext(
                basePackage, projectName, "com.example",
                "3.3.5", "17", true,
                "mvc", "jpa", "xml", false, "yaml", "constructor",
                "jakarta", "class", "separate", "problemdetail",
                UmabootConfig.AuditOptions.defaults(),
                UmabootConfig.SoftDeleteOptions.defaults(),
                UmabootConfig.DockerOptions.defaults(),
                UmabootConfig.CiOptions.defaults(),
                UmabootConfig.LoggingOptions.defaults(),
                UmabootConfig.TestOptions.defaults(),
                "offset",
                UmabootConfig.SecurityOptions.defaults(),
                UmabootConfig.DddOptions.defaults(),
                false,
                "postgres");
    }

    public String basePackagePath() {
        return basePackage.replace('.', '/');
    }

    public boolean isMyBatis() { return "mybatis".equalsIgnoreCase(persistence); }
    public boolean isJpa() { return "jpa".equalsIgnoreCase(persistence); }
    public boolean isJooq() { return "jooq".equalsIgnoreCase(persistence); }

    public boolean isHexagonal() { return "hexagonal".equalsIgnoreCase(architecture); }
    public boolean isDdd() { return "ddd".equalsIgnoreCase(architecture); }
    public boolean isMvc() { return "mvc".equalsIgnoreCase(architecture); }

    public boolean myBatisXml() { return isMyBatis() && "xml".equalsIgnoreCase(mybatisStyle); }
    public boolean myBatisAnnotation() { return isMyBatis() && "annotation".equalsIgnoreCase(mybatisStyle); }

    public boolean isOpenApiYaml() { return "yaml".equalsIgnoreCase(openApiStyle); }
    public boolean isOpenApiAnnotation() { return "annotation".equalsIgnoreCase(openApiStyle); }
    public boolean isOpenApiEnabled() { return !"none".equalsIgnoreCase(openApiStyle); }

    public boolean isInjectionConstructor() { return "constructor".equalsIgnoreCase(injectionStyle); }
    public boolean isInjectionLombok() { return "lombok".equalsIgnoreCase(injectionStyle); }
    public boolean isInjectionAutowired() { return "autowired".equalsIgnoreCase(injectionStyle); }

    public boolean isValidationJakarta() { return "jakarta".equalsIgnoreCase(validationStyle); }
    public boolean isValidationNone() { return "none".equalsIgnoreCase(validationStyle); }
    public boolean isValidationService() { return "service".equalsIgnoreCase(validationStyle); }

    public boolean isDtoClass() { return "class".equalsIgnoreCase(dtoStyle); }
    public boolean isDtoRecord() { return "record".equalsIgnoreCase(dtoStyle); }
    public boolean isDtoSeparate() { return "separate".equalsIgnoreCase(dtoShape); }
    public boolean isDtoSingle() { return "single".equalsIgnoreCase(dtoShape); }

    public boolean isExceptionProblemDetail() { return "problemdetail".equalsIgnoreCase(exceptionStyle); }
    public boolean isExceptionEnvelope() { return "envelope".equalsIgnoreCase(exceptionStyle); }

    public boolean isDbMysql() { return "mysql".equalsIgnoreCase(dbDriver); }
    public boolean isDbPostgres() { return !isDbMysql(); }

    public boolean isPaginationOffset() { return "offset".equalsIgnoreCase(paginationStyle); }
    public boolean isPaginationCursor() { return "cursor".equalsIgnoreCase(paginationStyle); }

    /** Major version of Spring Boot (parsed from {@link #springBootVersion}). */
    public int springBootMajor() {
        if (springBootVersion == null || springBootVersion.isEmpty()) return 3;
        int dot = springBootVersion.indexOf('.');
        String head = dot < 0 ? springBootVersion : springBootVersion.substring(0, dot);
        try { return Integer.parseInt(head); } catch (NumberFormatException ex) { return 3; }
    }
    public boolean isSpringBoot2() { return springBootMajor() == 2; }
    public boolean isSpringBoot3() { return springBootMajor() == 3; }

    public boolean isSecurityNone() { return security != null && security.isNone(); }
    public boolean isSecurityBasic() { return security != null && security.isBasic(); }
    public boolean isSecurityJwt() { return security != null && security.isJwt(); }
    public boolean isSecurityEnabled() { return security != null && security.isEnabled(); }

    /**
     * Returns {@code "jakarta"} for Spring Boot 3.x and {@code "javax"} for
     * Spring Boot 2.x. Used by templates as {@code ${eeNamespace}.persistence.*}
     * etc. so a single template renders correctly for both Jakarta EE and
     * the legacy Java EE namespace.
     */
    public String eeNamespace() {
        return isSpringBoot2() ? "javax" : "jakarta";
    }

    /** Legacy helper for templates that still use the boolean flag. */
    public boolean generateOpenApi() { return isOpenApiEnabled(); }
}
