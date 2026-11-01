package fi.metropolia.tempfx.db;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Opens the database connection and creates the two related tables.
 *
 * <p>The application uses an embedded H2 database running in MySQL
 * compatibility mode, so the very same {@code schema.sql} also runs on
 * MariaDB. Nothing has to be installed, which is what makes the Docker image
 * and the Kubernetes deployment self-contained.
 *
 * <p>The connection settings can be overridden with the environment variables
 * {@code DB_URL}, {@code DB_USER} and {@code DB_PASSWORD}, or with the system
 * properties {@code db.url}, {@code db.user} and {@code db.password}, so the
 * same build can be pointed at a MariaDB server instead.
 */
public final class Database {

    /** Embedded file database, created next to the application on first run. */
    public static final String DEFAULT_URL = "jdbc:h2:file:./data/tempfx;MODE=MySQL;AUTO_SERVER=TRUE";

    private static final String SCHEMA_RESOURCE = "/db/schema.sql";

    private Database() {
        // Utility class - not meant to be instantiated.
    }

    /** @return the JDBC URL, from the environment, a system property, or the default */
    public static String url() {
        return setting("DB_URL", "db.url", DEFAULT_URL);
    }

    /** @return the database user ("sa" is the default H2 user) */
    public static String user() {
        return setting("DB_USER", "db.user", "sa");
    }

    /** @return the database password (empty by default) */
    public static String password() {
        return setting("DB_PASSWORD", "db.password", "");
    }

    private static String setting(String environmentName, String propertyName, String fallback) {
        String fromEnvironment = System.getenv(environmentName);
        if (fromEnvironment != null && !fromEnvironment.isBlank()) {
            return fromEnvironment;
        }
        return System.getProperty(propertyName, fallback);
    }

    /**
     * Opens a new connection using the configured settings.
     *
     * @return an open JDBC connection
     * @throws SQLException when the database cannot be reached
     */
    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(url(), user(), password());
    }

    /**
     * Creates the tables if they do not exist yet and inserts the two
     * measurement systems the application offers.
     *
     * @param connection an open connection
     * @throws SQLException when a statement fails
     */
    public static void initialize(Connection connection) throws SQLException {
        for (String sql : readSchemaStatements()) {
            try (Statement statement = connection.createStatement()) {
                statement.execute(sql);
            }
        }
        seedUnitSystems(connection);
    }

    /**
     * Reads {@code schema.sql} from the classpath and splits it into single
     * statements, dropping the SQL comments.
     *
     * @return the statements, in file order
     */
    public static List<String> readSchemaStatements() {
        StringBuilder script = new StringBuilder();
        try (InputStream input = Database.class.getResourceAsStream(SCHEMA_RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException("Schema file not found: " + SCHEMA_RESOURCE);
            }
            try (BufferedReader reader =
                         new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String withoutComment = line.split("--", 2)[0];
                    script.append(withoutComment).append('\n');
                }
            }
        } catch (IOException cause) {
            throw new UncheckedIOException(cause);
        }

        List<String> statements = new ArrayList<>();
        for (String candidate : script.toString().split(";")) {
            String trimmed = candidate.trim();
            if (!trimmed.isEmpty()) {
                statements.add(trimmed);
            }
        }
        return statements;
    }

    /** Inserts METRIC and IMPERIAL the first time the database is created. */
    private static void seedUnitSystems(Connection connection) throws SQLException {
        if (countUnitSystems(connection) > 0) {
            return;
        }
        String sql = "INSERT INTO unit_system (code, distance_unit, speed_unit) VALUES (?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, "METRIC");
            statement.setString(2, "km");
            statement.setString(3, "km/h");
            statement.executeUpdate();

            statement.setString(1, "IMPERIAL");
            statement.setString(2, "mi");
            statement.setString(3, "mph");
            statement.executeUpdate();
        }
    }

    private static int countUnitSystems(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet results = statement.executeQuery("SELECT COUNT(*) FROM unit_system")) {
            return results.next() ? results.getInt(1) : 0;
        }
    }
}
