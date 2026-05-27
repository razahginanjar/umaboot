package io.umaboot.core.introspection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Ensures JDBC driver classes are loaded so {@link java.sql.DriverManager} can
 * find them via its registry.
 *
 * <p>Background: {@code DriverManager} discovers drivers via {@link java.util.ServiceLoader},
 * which uses the <em>thread-context</em> classloader. Inside an IntelliJ plugin, the
 * thread-context classloader is typically the platform's, not the plugin's, so the
 * bundled driver JARs (PostgreSQL, MySQL) aren't visible to {@code ServiceLoader} —
 * even though they're on the plugin classpath. The result is the infamous
 * "No suitable driver found for jdbc:..." error, despite the driver being present.</p>
 *
 * <p>The fix is to force the driver class to load via {@link Class#forName(String)},
 * which uses the calling code's classloader — that's the plugin classloader for us,
 * which DOES see the bundled JARs. The driver's static initializer then calls
 * {@code DriverManager.registerDriver(this)}, making it discoverable for any
 * subsequent {@code DriverManager.getConnection(...)} call on any thread.</p>
 */
public final class JdbcDrivers {

    private static final Logger LOG = LoggerFactory.getLogger(JdbcDrivers.class);

    private static volatile boolean registered = false;

    private JdbcDrivers() {}

    /**
     * Idempotent. Loads PostgreSQL and MySQL driver classes if available on the
     * classpath. Silent if a driver is missing — only fails later if the user
     * actually tries to use that database.
     */
    public static void registerAll() {
        if (registered) return;
        synchronized (JdbcDrivers.class) {
            if (registered) return;
            registerSilently("org.postgresql.Driver");
            registerSilently("com.mysql.cj.jdbc.Driver");
            registerSilently("org.mariadb.jdbc.Driver");
            registerSilently("com.microsoft.sqlserver.jdbc.SQLServerDriver");
            registerSilently("org.sqlite.JDBC");
            registered = true;
        }
    }

    private static void registerSilently(String driverClass) {
        try {
            Class.forName(driverClass);
            LOG.debug("Registered JDBC driver: {}", driverClass);
        } catch (ClassNotFoundException ex) {
            LOG.debug("JDBC driver not on classpath: {}", driverClass);
        }
    }
}
