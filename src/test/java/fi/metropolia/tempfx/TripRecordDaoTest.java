package fi.metropolia.tempfx;

import fi.metropolia.tempfx.db.Database;
import fi.metropolia.tempfx.db.TripRecordDao;
import fi.metropolia.tempfx.db.UnitSystemDao;
import fi.metropolia.tempfx.model.TripRecord;
import fi.metropolia.tempfx.model.UnitSystem;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("TripRecordDao")
class TripRecordDaoTest {

    private Connection connection;
    private TripRecordDao dao;
    private UnitSystem metric;
    private UnitSystem imperial;

    @BeforeEach
    void setUp() throws SQLException {
        connection = DatabaseTest.openInMemoryDatabase();
        Database.initialize(connection);
        dao = new TripRecordDao(connection);
        UnitSystemDao systems = new UnitSystemDao(connection);
        metric = systems.findByCode("METRIC").orElseThrow();
        imperial = systems.findByCode("IMPERIAL").orElseThrow();
    }

    @AfterEach
    void tearDown() throws SQLException {
        connection.close();
    }

    private TripRecord trip(String label, double distance, double hours, UnitSystem system) {
        double speed = SpeedCalculator.speed(distance, hours);
        return new TripRecord(label, distance, hours, speed, system.id());
    }

    @Test
    @DisplayName("starts empty")
    void startsEmpty() throws SQLException {
        assertEquals(0, dao.count());
        assertEquals(0.0, dao.totalDistance(), 1e-9);
        assertTrue(dao.findAll().isEmpty());
    }

    @Test
    @DisplayName("inserts a trip and gives it a generated id")
    void insertsATrip() throws SQLException {
        TripRecord record = trip("Helsinki - Tampere", 180.0, 2.0, metric);
        int id = dao.insert(record);

        assertTrue(id > 0);
        assertEquals(id, record.getId());
        assertEquals(1, dao.count());
    }

    @Test
    @DisplayName("reads the units back from the related table")
    void joinsTheParentTable() throws SQLException {
        dao.insert(trip("Helsinki - Tampere", 180.0, 2.0, metric));

        TripRecord stored = dao.findAll().get(0);
        assertEquals("Helsinki - Tampere", stored.getLabel());
        assertEquals(180.0, stored.getDistance(), 1e-9);
        assertEquals(2.0, stored.getTimeHours(), 1e-9);
        assertEquals(90.0, stored.getSpeed(), 1e-9);
        assertEquals(metric.id(), stored.getUnitSystemId());
        assertEquals("METRIC", stored.getUnitSystemCode());
        assertEquals("km", stored.getDistanceUnit());
        assertEquals("km/h", stored.getSpeedUnit());
        assertNotNull(stored.getCreatedAt());
        assertTrue(stored.toString().contains("Helsinki"));
    }

    @Test
    @DisplayName("keeps trips of both measurement systems and lists the newest first")
    void listsNewestFirst() throws SQLException {
        dao.insert(trip("First", 10.0, 1.0, metric));
        dao.insert(trip("Second", 60.0, 2.0, imperial));

        List<TripRecord> all = dao.findAll();
        assertEquals(2, all.size());
        assertEquals("Second", all.get(0).getLabel());
        assertEquals("mph", all.get(0).getSpeedUnit());
        assertEquals("First", all.get(1).getLabel());
        assertEquals("km/h", all.get(1).getSpeedUnit());
    }

    @Test
    @DisplayName("adds up every stored distance")
    void sumsDistances() throws SQLException {
        dao.insert(trip("A", 10.5, 1.0, metric));
        dao.insert(trip("B", 20.0, 2.0, metric));
        assertEquals(30.5, dao.totalDistance(), 1e-9);
        assertEquals(2, dao.count());
    }

    @Test
    @DisplayName("deletes a trip by its id")
    void deletesATrip() throws SQLException {
        int id = dao.insert(trip("To be removed", 5.0, 0.5, metric));
        assertTrue(dao.deleteById(id));
        assertEquals(0, dao.count());
        assertFalse(dao.deleteById(id), "deleting the same row twice should report false");
    }

    @Test
    @DisplayName("refuses a trip pointing at a measurement system that does not exist")
    void enforcesTheForeignKey() {
        TripRecord orphan = new TripRecord("Orphan", 10.0, 1.0, 10.0, 9999);
        assertThrows(SQLException.class, () -> dao.insert(orphan));
    }
}
