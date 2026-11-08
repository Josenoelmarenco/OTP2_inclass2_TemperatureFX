package fi.metropolia.tempfx.db;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Opens the database connection and applies {@code db/init.sql}.
 *
 * <p>The application uses an embedded H2 database running in MySQL
 * compatibility mode, so the very same script also runs on MariaDB. Nothing
 * has to be installed, which is what makes the Docker image and the Kubernetes
 * deployment self-contained. For a real MariaDB server use
 * {@code db/init-mariadb.sql}, which adds the {@code utf8mb4} declarations.
 *
 * <p>The connection settings can be overridden with the environment variables
 * {@code DB_URL}, {@code DB_USER} and {@code DB_PASSWORD}, or with the system
 * properties {@code db.url}, {@code db.user} and {@code db.password}.
 */
public final class Database {

    /** Embedded file database, created next to the application on first run. */
    public static final String DEFAULT_URL = "jdbc:h2:file:./data/tempfx;MODE=MySQL;AUTO_SERVER=TRUE";

    private static final String INIT_RESOURCE = "/db/init.sql";

    /** Matches the target table of an INSERT statement. */
    private static final Pattern INSERT_TARGET =
            Pattern.compile("^INSERT\\s+INTO\\s+([A-Za-z_][A-Za-z0-9_]*)", Pattern.CASE_INSENSITIVE);

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
     * Applies {@code init.sql}: creates the tables and inserts the reference
     * data, including the unit translations in all five languages.
     *
     * <p>Every {@code CREATE TABLE} is written as {@code IF NOT EXISTS}, and the
     * inserts for a table are skipped when that table <em>already held rows
     * before this call started</em>. The method is therefore safe to call on a
     * fresh database and on an existing one, which is what lets the application
     * start against a database created earlier by hand or by another pod.
     *
     * <p>The emptiness of each table is sampled <strong>once, up front</strong>,
     * and deliberately not re-checked between statements. Several inserts write
     * into the same table — {@code measurement_unit} is filled by six
     * statements and {@code unit_translation} by forty-eight — so checking
     * again after the first one would see a non-empty table and silently skip
     * all the rest. Sampling first makes the inserts for one table all-or-nothing.
     *
     * @param connection an open connection
     * @throws SQLException when a statement fails
     */
    public static void initialize(Connection connection) throws SQLException {
        List<String> statements = readInitStatements();

        // 1. Schema first: everything that is not an INSERT, in file order.
        for (String sql : statements) {
            if (insertTargetTable(sql) == null) {
                execute(connection, sql);
            }
        }

        // 2. Sample which tables are already populated, before inserting anything.
        Set<String> targets = new LinkedHashSet<>();
        for (String sql : statements) {
            String table = insertTargetTable(sql);
            if (table != null) {
                targets.add(table);
            }
        }
        Set<String> alreadyPopulated = new HashSet<>();
        for (String table : targets) {
            if (hasRows(connection, table)) {
                alreadyPopulated.add(table);
            }
        }

        // 3. Seed only the tables that were empty.
        for (String sql : statements) {
            String table = insertTargetTable(sql);
            if (table != null && !alreadyPopulated.contains(table)) {
                execute(connection, sql);
            }
        }
    }

    private static void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    /**
     * Reads {@code init.sql} from the classpath and splits it into single
     * statements.
     *
     * <p>The splitter is aware of quoted strings, so a semicolon or a double
     * dash inside a translated description is kept instead of being mistaken
     * for a statement separator or the start of a comment.
     *
     * @return the statements, in file order, with the comments removed
     */
    public static List<String> readInitStatements() {
        return splitStatements(readInitScript());
    }

    /** @return the raw contents of {@code init.sql} */
    public static String readInitScript() {
        StringBuilder script = new StringBuilder();
        try (InputStream input = Database.class.getResourceAsStream(INIT_RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException("Init file not found: " + INIT_RESOURCE);
            }
            try (BufferedReader reader =
                         new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    script.append(line).append('\n');
                }
            }
        } catch (IOException cause) {
            throw new UncheckedIOException(cause);
        }
        return script.toString();
    }

    /**
     * Splits an SQL script into statements, honouring single-quoted literals.
     *
     * @param script the whole script
     * @return the statements, trimmed, without comments or empty entries
     */
    static List<String> splitStatements(String script) {
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean insideLiteral = false;
        int index = 0;

        while (index < script.length()) {
            char character = script.charAt(index);

            if (insideLiteral) {
                current.append(character);
                if (character == '\'') {
                    // Two quotes in a row are an escaped quote, not the end.
                    if (index + 1 < script.length() && script.charAt(index + 1) == '\'') {
                        current.append('\'');
                        index += 2;
                        continue;
                    }
                    insideLiteral = false;
                }
                index++;
                continue;
            }

            if (character == '\'') {
                insideLiteral = true;
                current.append(character);
                index++;
                continue;
            }

            if (character == '-' && index + 1 < script.length() && script.charAt(index + 1) == '-') {
                while (index < script.length() && script.charAt(index) != '\n') {
                    index++;
                }
                continue;
            }

            if (character == ';') {
                addIfNotBlank(statements, current);
                current.setLength(0);
                index++;
                continue;
            }

            current.append(character);
            index++;
        }
        addIfNotBlank(statements, current);
        return statements;
    }

    private static void addIfNotBlank(List<String> statements, StringBuilder candidate) {
        String trimmed = candidate.toString().trim();
        if (!trimmed.isEmpty()) {
            statements.add(trimmed);
        }
    }

    /**
     * @param sql a single statement
     * @return the table an INSERT writes into, or {@code null} when the
     *         statement is not an INSERT
     */
    static String insertTargetTable(String sql) {
        Matcher matcher = INSERT_TARGET.matcher(sql.trim());
        return matcher.find() ? matcher.group(1).toLowerCase(Locale.ROOT) : null;
    }

    /**
     * @param connection an open connection
     * @param table      the table to look at
     * @return {@code true} when the table already holds at least one row
     * @throws SQLException when the query fails
     */
    static boolean hasRows(Connection connection, String table) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet results = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
            return results.next() && results.getInt(1) > 0;
        }
    }
}
