package fi.metropolia.tempfx;

import fi.metropolia.tempfx.db.Database;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks that {@code init.sql} is read correctly and really creates the
 * measurement tables, the unit lookup data and the localization structure.
 */
@DisplayName("Database")
class DatabaseTest {

    private Connection connection;

    /**
     * Opens a private in-memory database, so the tests cannot disturb each
     * other or the file database the application uses.
     *
     * @return an open connection
     * @throws SQLException when H2 cannot be started
     */
    static Connection openInMemoryDatabase() throws SQLException {
        return DriverManager.getConnection(
                "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
    }

    @BeforeEach
    void setUp() throws SQLException {
        connection = openInMemoryDatabase();
        Database.initialize(connection);
    }

    @AfterEach
    void tearDown() throws SQLException {
        if (connection != null) {
            connection.close();
        }
    }

    @Nested
    @DisplayName("reading init.sql")
    class ReadingTheScript {

        @Test
        @DisplayName("splits the script into statements and drops the comments")
        void readsInitStatements() {
            List<String> statements = Database.readInitStatements();
            long createTables = statements.stream()
                    .filter(sql -> sql.toUpperCase().startsWith("CREATE TABLE"))
                    .count();
            long translationInserts = statements.stream()
                    .filter(sql -> sql.toUpperCase().startsWith("INSERT INTO UNIT_TRANSLATION"))
                    .count();

            assertAll(
                    () -> assertEquals(6, createTables, "six tables are created"),
                    () -> assertEquals(48, translationInserts,
                            "10 units x 5 languages, minus the two deliberate gaps"),
                    () -> assertTrue(statements.get(0).toUpperCase().contains("UNIT_SYSTEM")),
                    // The comments at the top of the file must not reach the database.
                    () -> assertTrue(statements.stream().noneMatch(sql -> sql.contains("--"))));
        }

        @Test
        @DisplayName("keeps a semicolon and a double dash that sit inside a quoted text")
        void respectsQuotedLiterals() {
            List<String> statements = Database.splitStatements(
                    "INSERT INTO t VALUES ('a; b -- c'); INSERT INTO t VALUES ('d');");
            assertAll(
                    () -> assertEquals(2, statements.size()),
                    () -> assertTrue(statements.get(0).contains("a; b -- c"),
                            "the literal was cut: " + statements.get(0)),
                    () -> assertTrue(statements.get(1).contains("'d'")));
        }

        @Test
        @DisplayName("handles an escaped quote inside a text")
        void respectsEscapedQuotes() {
            List<String> statements =
                    Database.splitStatements("INSERT INTO t VALUES ('it''s fine');");
            assertAll(
                    () -> assertEquals(1, statements.size()),
                    () -> assertTrue(statements.get(0).contains("it''s fine")));
        }

        @Test
        @DisplayName("ignores a comment line completely")
        void dropsCommentLines() {
            List<String> statements = Database.splitStatements(
                    "-- a comment with a ; semicolon\nSELECT 1;");
            assertAll(
                    () -> assertEquals(1, statements.size()),
                    () -> assertEquals("SELECT 1", statements.get(0)));
        }

        @Test
        @DisplayName("recognises the table an INSERT writes into")
        void findsTheInsertTarget() {
            assertAll(
                    () -> assertEquals("language",
                            Database.insertTargetTable("INSERT INTO language (code) VALUES ('en')")),
                    () -> assertEquals("unit_translation",
                            Database.insertTargetTable("insert into unit_translation (a) values (1)")),
                    () -> assertNull(Database.insertTargetTable("CREATE TABLE x (id INT)")),
                    () -> assertNull(Database.insertTargetTable("SELECT 1")));
        }
    }

    @Nested
    @DisplayName("the resulting schema")
    class Schema {

        @ParameterizedTest(name = "{0} exists")
        @ValueSource(strings = {"UNIT_SYSTEM", "TRIP_RECORD", "LANGUAGE",
                "MEASUREMENT_UNIT", "UNIT_TRANSLATION", "CONVERSION_LOG"})
        @DisplayName("creates every table the assignment asks for")
        void createsEveryTable(String table) throws SQLException {
            assertTrue(tableExists(table));
        }

        @Test
        @DisplayName("relates trip_record to unit_system with a foreign key")
        void relatesTripToUnitSystem() throws SQLException {
            assertEquals("UNIT_SYSTEM", referencedTable("TRIP_RECORD"));
        }

        @Test
        @DisplayName("relates unit_translation to measurement_unit and to language")
        void relatesTranslationsToTheirParents() throws SQLException {
            List<String> parents = referencedTables("UNIT_TRANSLATION");
            assertAll(
                    () -> assertTrue(parents.contains("MEASUREMENT_UNIT"),
                            "found: " + parents),
                    () -> assertTrue(parents.contains("LANGUAGE"), "found: " + parents));
        }

        @Test
        @DisplayName("seeds the languages, the units and the translations")
        void seedsTheReferenceData() throws SQLException {
            assertAll(
                    () -> assertEquals(2, count("unit_system")),
                    () -> assertEquals(5, count("language")),
                    () -> assertEquals(10, count("measurement_unit")),
                    () -> assertEquals(48, count("unit_translation")),
                    () -> assertEquals(0, count("conversion_log")));
        }

        @Test
        @DisplayName("running the initialization twice does not duplicate anything")
        void isIdempotent() throws SQLException {
            Database.initialize(connection);
            Database.initialize(connection);
            assertAll(
                    () -> assertEquals(2, count("unit_system")),
                    () -> assertEquals(5, count("language")),
                    () -> assertEquals(10, count("measurement_unit")),
                    () -> assertEquals(48, count("unit_translation")));
        }

        @Test
        @DisplayName("knows whether a table already holds rows")
        void detectsSeededTables() throws SQLException {
            assertAll(
                    () -> assertTrue(Database.hasRows(connection, "language")),
                    () -> assertFalse(Database.hasRows(connection, "conversion_log")));
        }

        @Test
        @DisplayName("links the distance and speed units to a measurement system")
        void linksUnitsToTheirSystem() throws SQLException {
            try (Statement statement = connection.createStatement();
                 ResultSet results = statement.executeQuery(
                         "SELECT COUNT(*) FROM measurement_unit m "
                                 + "JOIN unit_system s ON m.unit_system_id = s.id")) {
                assertTrue(results.next());
                assertEquals(4, results.getInt(1),
                        "kilometre, mile, km/h and mph belong to a system");
            }
        }
    }

    @Test
    @DisplayName("exposes connection settings with sensible defaults")
    void exposesConnectionSettings() {
        Assumptions.assumeTrue(System.getenv("DB_URL") == null,
                "DB_URL is set in this environment");
        assertTrue(Database.DEFAULT_URL.startsWith("jdbc:h2:"));
        assertEquals(Database.DEFAULT_URL, Database.url());

        System.setProperty("db.url", "jdbc:h2:mem:custom");
        try {
            assertEquals("jdbc:h2:mem:custom", Database.url());
        } finally {
            System.clearProperty("db.url");
        }
        assertFalse(Database.user().isBlank());
        assertEquals("", Database.password());
    }

    private boolean tableExists(String name) throws SQLException {
        try (ResultSet tables = connection.getMetaData()
                .getTables(null, null, name, new String[]{"TABLE"})) {
            return tables.next();
        }
    }

    private String referencedTable(String table) throws SQLException {
        List<String> parents = referencedTables(table);
        return parents.isEmpty() ? null : parents.get(0);
    }

    private List<String> referencedTables(String table) throws SQLException {
        List<String> parents = new java.util.ArrayList<>();
        try (ResultSet keys = connection.getMetaData().getImportedKeys(null, null, table)) {
            while (keys.next()) {
                parents.add(keys.getString("PKTABLE_NAME").toUpperCase());
            }
        }
        return parents;
    }

    private int count(String table) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet results = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
            return results.next() ? results.getInt(1) : -1;
        }
    }
}
