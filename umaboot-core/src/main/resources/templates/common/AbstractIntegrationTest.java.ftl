package ${basePackage};

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

/**
 * Base class for {@code @SpringBootTest} integration tests.
 *
 * <p>Spins up a single shared {@code @Container} for the whole test JVM (the
 * {@code static} field) and binds Spring's {@code spring.datasource.*} properties
 * to it via {@code @DynamicPropertySource}. Subclass this and add {@code @Test}
 * methods — the container starts once and is reused across tests.</p>
 *
 * <p>Requires Docker to be running locally.</p>
 */
@Testcontainers
public abstract class AbstractIntegrationTest {

<#if dbIsMariadb>
    @Container
    @SuppressWarnings("resource")
    static final MariaDBContainer<?> DB = new MariaDBContainer<>("mariadb:11")
            .withDatabaseName("test")
            .withUsername("test")
            .withPassword("test");
<#elseif dbIsMysql>
    @Container
    @SuppressWarnings("resource")
    static final MySQLContainer<?> DB = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("test")
            .withUsername("test")
            .withPassword("test");
<#elseif dbIsSqlserver>
    // SQL Server's Testcontainers image requires explicit license acceptance.
    // The default sa password meets MS's complexity requirement (≥8 chars,
    // upper+lower+digit+symbol). The container connects to the `master` DB
    // by default; create test schemas via setup SQL if needed.
    @Container
    @SuppressWarnings("resource")
    static final MSSQLServerContainer<?> DB = new MSSQLServerContainer<>("mcr.microsoft.com/mssql/server:2022-latest")
            .acceptLicense();
<#else>
    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> DB = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("test")
            .withUsername("test")
            .withPassword("test");
</#if>

    @DynamicPropertySource
    static void registerDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", DB::getJdbcUrl);
        registry.add("spring.datasource.username", DB::getUsername);
        registry.add("spring.datasource.password", DB::getPassword);
    }
}
