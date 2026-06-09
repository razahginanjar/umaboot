package ${basePackage};

<#if dbIsSqlite>
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
<#else>
<#if dbIsMariadb>
import org.testcontainers.containers.MariaDBContainer;
<#elseif dbIsMysql>
import org.testcontainers.containers.MySQLContainer;
<#elseif dbIsSqlserver>
import org.testcontainers.containers.MSSQLServerContainer;
<#else>
import org.testcontainers.containers.PostgreSQLContainer;
</#if>
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
</#if>

/**
 * Base class for {@code @SpringBootTest} integration tests.
 *
<#if dbIsSqlite>
 * <p>SQLite runs in-process — no Docker, no Testcontainers. We point Spring at
 * {@code jdbc:sqlite::memory:} via {@code @DynamicPropertySource}.<#if migrationFlyway>
 * Flyway applies the generated migration before Hibernate validates the schema.</#if><#if !migrationFlyway>
 * Spring SQL init applies the generated test schema before JPA validates it.</#if></p>
<#else>
 * <p>Spins up a single shared {@code @Container} for the whole test JVM (the
 * {@code static} field) and binds Spring's {@code spring.datasource.*} properties
 * to it via {@code @DynamicPropertySource}.<#if !migrationFlyway> The container
 * loads {@code schema.sql} before Spring starts.</#if> Subclass this and add {@code @Test}
 * methods — the container starts once and is reused across tests.</p>
 *
 * <p>Requires Docker to be running locally.</p>
</#if>
 */
<#if !dbIsSqlite>
@Testcontainers
</#if>
public abstract class AbstractIntegrationTest {

<#if dbIsSqlite>
    // SQLite is embedded; no Testcontainer is needed.
    @DynamicPropertySource
    static void registerDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",      () -> "jdbc:sqlite::memory:");
        registry.add("spring.datasource.username", () -> "");
        registry.add("spring.datasource.password", () -> "");
        registry.add("spring.datasource.driver-class-name", () -> "org.sqlite.JDBC");
        registerSchemaMode(registry);
        registry.add("spring.jpa.properties.hibernate.dialect",
                () -> "org.hibernate.community.dialect.SQLiteDialect");
    }
<#elseif dbIsMariadb>
    @Container
    @SuppressWarnings("resource")
    static final MariaDBContainer<?> DB = new MariaDBContainer<>("mariadb:11")
            .withDatabaseName("test")
            .withUsername("test")
            .withPassword("test")<#if !migrationFlyway>
            .withInitScript("schema.sql")</#if>;

    @DynamicPropertySource
    static void registerDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", DB::getJdbcUrl);
        registry.add("spring.datasource.username", DB::getUsername);
        registry.add("spring.datasource.password", DB::getPassword);
        registerSchemaMode(registry);
    }
<#elseif dbIsMysql>
    @Container
    @SuppressWarnings("resource")
    static final MySQLContainer<?> DB = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("test")
            .withUsername("test")
            .withPassword("test")<#if !migrationFlyway>
            .withInitScript("schema.sql")</#if>;

    @DynamicPropertySource
    static void registerDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", DB::getJdbcUrl);
        registry.add("spring.datasource.username", DB::getUsername);
        registry.add("spring.datasource.password", DB::getPassword);
        registerSchemaMode(registry);
    }
<#elseif dbIsSqlserver>
    // SQL Server's Testcontainers image requires explicit license acceptance.
    // The default sa password meets MS's complexity requirement (≥8 chars,
    // upper+lower+digit+symbol). The container connects to the `master` DB
    // by default; create test schemas via setup SQL if needed.
    @Container
    @SuppressWarnings("resource")
    static final MSSQLServerContainer<?> DB = new MSSQLServerContainer<>("mcr.microsoft.com/mssql/server:2022-latest")
            .acceptLicense()<#if !migrationFlyway>
            .withInitScript("schema.sql")</#if>;

    @DynamicPropertySource
    static void registerDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", DB::getJdbcUrl);
        registry.add("spring.datasource.username", DB::getUsername);
        registry.add("spring.datasource.password", DB::getPassword);
        registerSchemaMode(registry);
    }
<#else>
    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> DB = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("test")
            .withUsername("test")
            .withPassword("test")<#if !migrationFlyway>
            .withInitScript("schema.sql")</#if>;

    @DynamicPropertySource
    static void registerDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", DB::getJdbcUrl);
        registry.add("spring.datasource.username", DB::getUsername);
        registry.add("spring.datasource.password", DB::getPassword);
        registerSchemaMode(registry);
    }
</#if>

    private static void registerSchemaMode(DynamicPropertyRegistry registry) {
<#if isJpa>
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
</#if>
<#if dbIsSqlite && !migrationFlyway>
        registry.add("spring.sql.init.mode", () -> "always");
        registry.add("spring.sql.init.schema-locations", () -> "classpath:schema.sql");
</#if>
    }
}
