package fi.metropolia.tempfx;

import fi.metropolia.tempfx.db.ConversionLogDao;
import fi.metropolia.tempfx.db.Database;
import fi.metropolia.tempfx.db.UnitTranslationDao;
import fi.metropolia.tempfx.model.ConversionRecord;
import fi.metropolia.tempfx.model.TemperatureUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the conversion history: that every conversion is stored with the
 * language that was active, that only the last five come back, and that the
 * unit names in the history are localized at read time.
 */
@DisplayName("ConversionLogDao")
class ConversionLogDaoTest {

    private Connection connection;
    private ConversionLogDao dao;
    private UnitTranslationDao units;
    private int celsiusId;
    private int fahrenheitId;

    @BeforeEach
    void setUp() throws SQLException {
        connection = DatabaseTest.openInMemoryDatabase();
        Database.initialize(connection);
        dao = new ConversionLogDao(connection);
        units = new UnitTranslationDao(connection);
        celsiusId = idOf(TemperatureUnit.CELSIUS);
        fahrenheitId = idOf(TemperatureUnit.FAHRENHEIT);
    }

    @AfterEach
    void tearDown() throws SQLException {
        if (connection != null) {
            connection.close();
        }
    }

    @Test
    @DisplayName("starts empty")
    void startsEmpty() throws SQLException {
        assertAll(
                () -> assertEquals(0, dao.count()),
                () -> assertTrue(dao.findRecent("en").isEmpty()));
    }

    @Test
    @DisplayName("stores a conversion with its value, units, result and language")
    void storesOneConversion() throws SQLException {
        dao.insert(25.0, celsiusId, fahrenheitId, 77.0, "fi");

        List<ConversionRecord> recent = dao.findRecent("fi");
        assertEquals(1, recent.size());
        ConversionRecord record = recent.get(0);
        assertAll(
                () -> assertEquals(25.0, record.inputValue(), 1e-9),
                () -> assertEquals(77.0, record.resultValue(), 1e-9),
                () -> assertEquals("celsius", record.fromUnitKey()),
                () -> assertEquals("fahrenheit", record.toUnitKey()),
                () -> assertEquals("fi", record.lang()),
                () -> assertNotNull(record.createdAt(), "the timestamp must be stored"),
                () -> assertTrue(record.id() > 0, "the generated key must be returned"));
    }

    @Test
    @DisplayName("returns only the last five conversions, newest first")
    void keepsOnlyTheLastFive() throws SQLException {
        for (int i = 1; i <= 8; i++) {
            dao.insert(i, celsiusId, fahrenheitId, i * 1.8 + 32, "en");
        }
        List<ConversionRecord> recent = dao.findRecent("en");

        assertAll(
                () -> assertEquals(5, recent.size(), "the GUI shows five rows"),
                () -> assertEquals(8, dao.count(), "all of them stay stored"),
                () -> assertEquals(8.0, recent.get(0).inputValue(), 1e-9),
                () -> assertEquals(4.0, recent.get(4).inputValue(), 1e-9));
    }

    @Test
    @DisplayName("an explicit limit is honoured")
    void honoursAnExplicitLimit() throws SQLException {
        for (int i = 0; i < 4; i++) {
            dao.insert(i, celsiusId, fahrenheitId, i, "en");
        }
        assertAll(
                () -> assertEquals(2, dao.findRecent(2, "en").size()),
                () -> assertEquals(4, dao.findRecent(10, "en").size()),
                () -> assertTrue(dao.findRecent(0, "en").isEmpty()),
                () -> assertTrue(dao.findRecent(-3, "en").isEmpty(),
                        "a negative limit must not blow up"));
    }

    @ParameterizedTest(name = "history is shown in {0}")
    @ValueSource(strings = {"en", "fi", "sv", "ja", "ar"})
    @DisplayName("the unit names in the history follow the language asked for now")
    void namesFollowTheRequestedLanguage(String language) throws SQLException {
        dao.insert(0.0, celsiusId, fahrenheitId, 32.0, "en");

        ConversionRecord record = dao.findRecent(language).get(0);
        String expected = units.findByUnitKey("celsius", language).orElseThrow().name();
        assertAll(
                () -> assertEquals(expected, record.fromUnitName()),
                () -> assertFalse(record.toUnitName().isBlank()),
                // The row still remembers the language it was created in.
                () -> assertEquals("en", record.lang()));
    }

    @Test
    @DisplayName("the stored language is kept even when the history is read in another one")
    void keepsTheLanguageItWasCreatedIn() throws SQLException {
        dao.insert(10.0, celsiusId, fahrenheitId, 50.0, "ja");

        ConversionRecord readInArabic = dao.findRecent("ar").get(0);
        assertAll(
                () -> assertEquals("ja", readInArabic.lang()),
                () -> assertNotEquals(
                        units.findByUnitKey("celsius", "ja").orElseThrow().name(),
                        readInArabic.fromUnitName(),
                        "the name itself follows the language being displayed"));
    }

    @Test
    @DisplayName("a locale tag is reduced to the stored language code")
    void normalizesTheLanguageOnInsert() throws SQLException {
        dao.insert(1.0, celsiusId, fahrenheitId, 33.8, "ja_JP");
        assertEquals("ja", dao.findRecent("en").get(0).lang());
    }

    @Test
    @DisplayName("the foreign key rejects a language the database does not know")
    void rejectsAnUnknownLanguage() {
        // normalize() keeps "de", which has no row in the language table.
        assertThrows(SQLException.class,
                () -> dao.insert(1.0, celsiusId, fahrenheitId, 2.0, "de"));
    }

    @Test
    @DisplayName("the foreign key rejects a unit that does not exist")
    void rejectsAnUnknownUnit() {
        assertThrows(SQLException.class,
                () -> dao.insert(1.0, 9999, fahrenheitId, 2.0, "en"));
    }

    @Test
    @DisplayName("the limit the GUI uses is five")
    void exposesTheGuiLimit() {
        assertEquals(5, ConversionLogDao.RECENT_LIMIT);
    }

    private int idOf(TemperatureUnit unit) throws SQLException {
        return units.findByUnitKey(unit.getUnitKey(), "en").orElseThrow().id();
    }
}
