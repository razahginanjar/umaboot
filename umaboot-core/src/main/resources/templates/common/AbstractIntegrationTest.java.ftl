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
 * Hibernate uses {@code create-drop} so each test class gets a fresh schema
 * derived from the generated entities.</#if></p>
<#else>
 * <p>Spins up a single shared {@code @Container} for the whole test JVM (the
 * {@code static} field) and binds Spring's {@code spring.datasource.*} properties
 * to it via {@code @DynamicPropertySource}. Subclass this and add {@code @Test}
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
    // SQLite is embedded — each Spring application context gets its own
    // in-memory DB. Hibernate's create-drop builds the schema from the
    // generated entity metamodel on context startup.
    @DynamicPropertySource
    static void registerDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",      () -> "jdbc:sqlite::memory:");
        registry.add("spring.datasource.username", () -> "");
        registry.add("spring.datasource.password", () -> "");
        registry.add("spring.datasource.driver-class-name", () -> "org.sqlite.JDBC");
<#if migrationFlyway>
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
<#else>
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
</#if>
        registry.add("spring.jpa.properties.hibernate.dialect",
                () -> "org.hibernate.community.dialect.SQLiteDialect");
    }
<#elseif dbIsMariadb>
    @Container
    @SuppressWarnings("resource")
    static final MariaDBContainer<?> DB = new MariaDBContainer<>("mariadb:11")
            .withDatabaseName("test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void registerDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", DB::getJdbcUrl);
        registry.add("spring.datasource.username", DB::getUsername);
        registry.add("spring.datasource.password", DB::getPassword);
    }
<#elseif dbIsMysql>
    @Container
    @SuppressWarnings("resource")
    static final MySQLContainer<?> DB = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void registerDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", DB::getJdbcUrl);
        registry.add("spring.datasource.username", DB::getUsername);
        registry.add("spring.datasource.password", DB::getPassword);
    }
<#elseif dbIsSqlserver>
    // SQL Server's Testcontainers image requires explicit license acceptance.
    // The default sa password meets MS's complexity requirement (≥8 chars,
    // upper+lower+digit+symbol). The container connects to the `master` DB
    // by default; create test schemas via setup SQL if needed.
    @Container
    @SuppressWarnings("resource")
    static final MSSQLServerContainer<?> DB = new MSSQLServerContainer<>("mcr.microsoft.com/mssql/server:2022-latest")
            .acceptLicense();

    @DynamicPropertySource
    static void registerDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", DB::getJdbcUrl);
        registry.add("spring.datasource.username", DB::getUsername);
        registry.add("spring.datasource.password", DB::getPassword);
    }
<#else>
    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> DB = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void registerDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", DB::getJdbcUrl);
        registry.add("spring.datasource.username", DB::getUsername);
        registry.add("spring.datasource.password", DB::getPassword);
    }
</#if>
}
