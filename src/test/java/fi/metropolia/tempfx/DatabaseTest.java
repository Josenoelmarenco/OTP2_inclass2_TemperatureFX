package fi.metropolia.tempfx;

import fi.metropolia.tempfx.db.Database;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Checks that the schema really creates the two related tables. */
@DisplayName("Database")
class DatabaseTest {

    private Connection connection;

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

    @Test
    @DisplayName("reads the schema file and splits it into statements")
    void readsSchemaStatements() {
        List<String> statements = Database.readSchemaStatements();
        assertEquals(2, statements.size(), "the schema should create two tables");
        assertTrue(statements.get(0).toUpperCase().contains("UNIT_SYSTEM"));
        assertTrue(statements.get(1).toUpperCase().contains("TRIP_RECORD"));
        // The comments at the top of the file must not reach the database.
        assertFalse(statements.get(0).contains("--"));
    }

    @Test
    @DisplayName("creates both tables")
    void createsBothTables() throws SQLException {
        assertTrue(tableExists("UNIT_SYSTEM"));
        assertTrue(tableExists("TRIP_RECORD"));
    }

    @Test
    @DisplayName("relates trip_record to unit_system with a foreign key")
    void createsTheForeignKey() throws SQLException {
        try (ResultSet keys = connection.getMetaData()
                .getImportedKeys(null, null, "TRIP_RECORD")) {
            assertTrue(keys.next(), "trip_record should have a foreign key");
            assertEquals("UNIT_SYSTEM", keys.getString("PKTABLE_NAME").toUpperCase());
        }
    }

    @Test
    @DisplayName("inserts the two measurement systems only once")
    void seedsUnitSystems() throws SQLException {
        assertEquals(2, countUnitSystems());
        // Running the initialization again must not duplicate the rows.
        Database.initialize(connection);
        assertEquals(2, countUnitSystems());
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

    private int countUnitSystems() throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet results = statement.executeQuery("SELECT COUNT(*) FROM unit_system")) {
            return results.next() ? results.getInt(1) : -1;
        }
    }
}
