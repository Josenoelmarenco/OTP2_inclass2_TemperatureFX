package fi.metropolia.tempfx;

import fi.metropolia.tempfx.db.Database;
import fi.metropolia.tempfx.db.UnitSystemDao;
import fi.metropolia.tempfx.model.UnitSystem;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("UnitSystemDao")
class UnitSystemDaoTest {

    private Connection connection;
    private UnitSystemDao dao;

    @BeforeEach
    void setUp() throws SQLException {
        connection = DatabaseTest.openInMemoryDatabase();
        Database.initialize(connection);
        dao = new UnitSystemDao(connection);
    }

    @AfterEach
    void tearDown() throws SQLException {
        connection.close();
    }

    @Test
    @DisplayName("returns the two seeded measurement systems")
    void findsAll() throws SQLException {
        List<UnitSystem> systems = dao.findAll();
        assertEquals(2, systems.size());
        assertEquals("METRIC", systems.get(0).code());
        assertEquals("IMPERIAL", systems.get(1).code());
    }

    @Test
    @DisplayName("finds the metric system by its code")
    void findsByCode() throws SQLException {
        UnitSystem metric = dao.findByCode("METRIC").orElseThrow();
        assertEquals("km", metric.distanceUnit());
        assertEquals("km/h", metric.speedUnit());
        assertTrue(metric.id() > 0);
        assertTrue(metric.toString().contains("METRIC"));
    }

    @Test
    @DisplayName("finds the imperial system by its code")
    void findsImperialByCode() throws SQLException {
        UnitSystem imperial = dao.findByCode("IMPERIAL").orElseThrow();
        assertEquals("mi", imperial.distanceUnit());
        assertEquals("mph", imperial.speedUnit());
    }

    @Test
    @DisplayName("finds a system by its primary key")
    void findsById() throws SQLException {
        UnitSystem metric = dao.findByCode("METRIC").orElseThrow();
        assertEquals(metric, dao.findById(metric.id()).orElseThrow());
    }

    @Test
    @DisplayName("returns empty for an unknown code or id")
    void returnsEmptyForUnknownValues() throws SQLException {
        assertTrue(dao.findByCode("NAUTICAL").isEmpty());
        assertTrue(dao.findById(9999).isEmpty());
    }
}
