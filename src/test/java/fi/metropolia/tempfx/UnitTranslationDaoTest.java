package fi.metropolia.tempfx;

import fi.metropolia.tempfx.db.Database;
import fi.metropolia.tempfx.db.UnitTranslationDao;
import fi.metropolia.tempfx.model.LocalizedUnit;
import fi.metropolia.tempfx.model.SupportedLanguage;
import fi.metropolia.tempfx.model.UnitCategory;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the translation-table approach: the localized unit names and, above
 * all, the fallback to English when a translation is missing.
 */
@DisplayName("UnitTranslationDao")
class UnitTranslationDaoTest {

    /**
     * Translated into English, Finnish and Swedish only by {@code init.sql}.
     * Japanese and Arabic therefore have to fall back.
     */
    private static final String PARTIALLY_TRANSLATED_UNIT = "nautical_mile";

    private Connection connection;
    private UnitTranslationDao dao;

    @BeforeEach
    void setUp() throws SQLException {
        connection = DatabaseTest.openInMemoryDatabase();
        Database.initialize(connection);
        dao = new UnitTranslationDao(connection);
    }

    @AfterEach
    void tearDown() throws SQLException {
        if (connection != null) {
            connection.close();
        }
    }

    @Nested
    @DisplayName("reading localized names")
    class LocalizedNames {

        @Test
        @DisplayName("returns the three temperature units in English")
        void temperatureUnitsInEnglish() throws SQLException {
            List<LocalizedUnit> units = dao.findByCategory(UnitCategory.TEMPERATURE, "en");
            assertEquals(3, units.size());
            assertEquals(List.of("celsius", "fahrenheit", "kelvin"),
                    units.stream().map(LocalizedUnit::unitKey).toList());
            assertEquals(List.of("Celsius", "Fahrenheit", "Kelvin"),
                    units.stream().map(LocalizedUnit::name).toList());
        }

        @ParameterizedTest(name = "temperature units are complete in {0}")
        @ValueSource(strings = {"en", "fi", "sv", "ja", "ar"})
        @DisplayName("every shipped language has a name and a description for every unit")
        void everyLanguageIsComplete(String language) throws SQLException {
            List<LocalizedUnit> units = dao.findByCategory(UnitCategory.TEMPERATURE, language);
            assertEquals(3, units.size());
            for (LocalizedUnit unit : units) {
                assertAll(
                        () -> assertFalse(unit.name().isBlank(),
                                unit.unitKey() + " has no name in " + language),
                        () -> assertFalse(unit.description().isBlank(),
                                unit.unitKey() + " has no description in " + language),
                        () -> assertFalse(unit.usedFallback(),
                                unit.unitKey() + " unexpectedly fell back in " + language));
            }
        }

        @ParameterizedTest(name = "{0} differs from English")
        @ValueSource(strings = {"fi", "sv", "ja", "ar"})
        @DisplayName("the translations are really different texts, not copies")
        void translationsDiffer(String language) throws SQLException {
            String english = nameOf("celsius", "en");
            assertNotEquals(english, nameOf("celsius", language));
        }

        @Test
        @DisplayName("Japanese comes back as real CJK characters")
        void japaneseIsStoredAsUtf8() throws SQLException {
            String name = nameOf("celsius", "ja");
            assertAll(
                    () -> assertFalse(name.contains("?"),
                            "the column is not UTF-8, got: " + name),
                    () -> assertTrue(name.codePoints().anyMatch(cp -> cp > 0x3000),
                            "expected CJK characters, got: " + name));
        }

        @Test
        @DisplayName("Arabic comes back as real Arabic-script characters")
        void arabicIsStoredAsUtf8() throws SQLException {
            String name = nameOf("celsius", "ar");
            assertTrue(name.codePoints().anyMatch(cp -> cp >= 0x0600 && cp <= 0x06FF),
                    "expected Arabic-script characters, got: " + name);
        }

        @Test
        @DisplayName("lists all ten units across the four categories")
        void listsEveryUnit() throws SQLException {
            assertEquals(10, dao.findAll("en").size());
            assertAll(
                    () -> assertEquals(3, dao.findByCategory(UnitCategory.TEMPERATURE, "en").size()),
                    () -> assertEquals(3, dao.findByCategory(UnitCategory.DISTANCE, "en").size()),
                    () -> assertEquals(2, dao.findByCategory(UnitCategory.SPEED, "en").size()),
                    () -> assertEquals(2, dao.findByCategory(UnitCategory.TIME, "en").size()));
        }

        @Test
        @DisplayName("keeps the symbol next to the localized name")
        void keepsTheSymbol() throws SQLException {
            LocalizedUnit celsius = dao.findByUnitKey("celsius", "fi").orElseThrow();
            assertAll(
                    () -> assertEquals("°C", celsius.symbol()),
                    () -> assertTrue(celsius.nameWithSymbol().endsWith("(°C)")));
        }

        @Test
        @DisplayName("an unknown unit key returns empty instead of throwing")
        void unknownUnitKey() throws SQLException {
            assertTrue(dao.findByUnitKey("does_not_exist", "en").isEmpty());
        }
    }

    @Nested
    @DisplayName("fallback to the default language")
    class Fallback {

        @ParameterizedTest(name = "{0} falls back to the English name")
        @ValueSource(strings = {"ja", "ar"})
        @DisplayName("a unit with no translation in the active language shows English")
        void missingTranslationFallsBackToEnglish(String language) throws SQLException {
            LocalizedUnit english =
                    dao.findByUnitKey(PARTIALLY_TRANSLATED_UNIT, "en").orElseThrow();
            LocalizedUnit localized =
                    dao.findByUnitKey(PARTIALLY_TRANSLATED_UNIT, language).orElseThrow();

            assertAll(
                    () -> assertEquals(english.name(), localized.name()),
                    () -> assertEquals(english.description(), localized.description()),
                    () -> assertTrue(localized.usedFallback(),
                            "the fallback must be reported, not silent"));
        }

        @ParameterizedTest(name = "{0} uses its own translation")
        @ValueSource(strings = {"en", "fi", "sv"})
        @DisplayName("a language that does have the translation does not fall back")
        void presentTranslationDoesNotFallBack(String language) throws SQLException {
            LocalizedUnit unit =
                    dao.findByUnitKey(PARTIALLY_TRANSLATED_UNIT, language).orElseThrow();
            assertFalse(unit.usedFallback());
        }

        @Test
        @DisplayName("Finnish keeps its own name for the same unit")
        void finnishHasItsOwnName() throws SQLException {
            assertEquals("Meripeninkulma",
                    nameOf(PARTIALLY_TRANSLATED_UNIT, "fi"));
        }

        @Test
        @DisplayName("a language the database does not know falls back entirely")
        void unknownLanguageFallsBackEntirely() throws SQLException {
            List<LocalizedUnit> german = dao.findByCategory(UnitCategory.TEMPERATURE, "de");
            assertEquals(3, german.size());
            for (LocalizedUnit unit : german) {
                assertAll(
                        () -> assertTrue(unit.usedFallback()),
                        () -> assertFalse(unit.name().isBlank()));
            }
            assertEquals(List.of("Celsius", "Fahrenheit", "Kelvin"),
                    german.stream().map(LocalizedUnit::name).toList());
        }

        @Test
        @DisplayName("a unit nobody translated falls back to its key, never to null")
        void untranslatedUnitFallsBackToItsKey() throws SQLException {
            try (Statement statement = connection.createStatement()) {
                statement.execute("INSERT INTO measurement_unit (unit_key, category, symbol) "
                        + "VALUES ('furlong', 'DISTANCE', 'fur')");
            }
            LocalizedUnit furlong = dao.findByUnitKey("furlong", "ja").orElseThrow();
            assertAll(
                    () -> assertEquals("furlong", furlong.name()),
                    () -> assertEquals("", furlong.description()),
                    () -> assertTrue(furlong.usedFallback()));
        }

        @Test
        @DisplayName("a null or blank language is treated as the default language")
        void nullLanguageUsesTheDefault() throws SQLException {
            String english = nameOf("celsius", "en");
            assertAll(
                    () -> assertEquals(english, nameOf("celsius", null)),
                    () -> assertEquals(english, nameOf("celsius", "   ")));
        }
    }

    @Nested
    @DisplayName("language codes")
    class LanguageCodes {

        @ParameterizedTest(name = "normalize(\"{0}\") -> {1}")
        @CsvSource({"ja, ja", "ja_JP, ja", "ja-JP, ja", "JA, ja", "sv_SE, sv", "ar_SA, ar"})
        @DisplayName("a locale tag is cut down to the code stored in the column")
        void normalizesTags(String input, String expected) {
            assertEquals(expected, UnitTranslationDao.normalize(input));
        }

        @Test
        @DisplayName("an empty tag becomes the default language")
        void normalizesEmptyTags() {
            assertAll(
                    () -> assertEquals("en", UnitTranslationDao.normalize((String) null)),
                    () -> assertEquals("en", UnitTranslationDao.normalize("")),
                    () -> assertEquals("en", UnitTranslationDao.normalize("  ")),
                    () -> assertEquals("en", UnitTranslationDao.normalize((Locale) null)));
        }

        @Test
        @DisplayName("a Locale is reduced to its language part")
        void normalizesLocales() {
            assertAll(
                    () -> assertEquals("ja", UnitTranslationDao.normalize(Locale.of("ja", "JP"))),
                    () -> assertEquals("ar", UnitTranslationDao.normalize(Locale.of("ar", "SA"))));
        }

        @Test
        @DisplayName("reads the five languages out of the language table")
        void readsTheLanguageTable() throws SQLException {
            List<SupportedLanguage> languages = dao.languages();
            assertEquals(5, languages.size());
            assertEquals(List.of("en", "fi", "sv", "ja", "ar"),
                    languages.stream().map(SupportedLanguage::code).toList());
            for (SupportedLanguage language : languages) {
                assertFalse(language.nativeName().isBlank());
            }
        }

        @Test
        @DisplayName("Arabic is the only language flagged right-to-left")
        void onlyArabicIsRightToLeft() throws SQLException {
            for (SupportedLanguage language : dao.languages()) {
                assertEquals("ar".equals(language.code()), language.rightToLeft(),
                        "wrong direction flag for " + language.code());
            }
        }

        @Test
        @DisplayName("counts the translations stored per language")
        void countsTranslations() throws SQLException {
            assertAll(
                    () -> assertEquals(10, dao.countTranslations("en")),
                    () -> assertEquals(10, dao.countTranslations("fi")),
                    () -> assertEquals(10, dao.countTranslations("sv")),
                    // Japanese and Arabic are missing nautical_mile on purpose.
                    () -> assertEquals(9, dao.countTranslations("ja")),
                    () -> assertEquals(9, dao.countTranslations("ar")),
                    () -> assertEquals(0, dao.countTranslations("de")));
        }
    }

    private String nameOf(String unitKey, String language) throws SQLException {
        Optional<LocalizedUnit> unit = dao.findByUnitKey(unitKey, language);
        return unit.orElseThrow().name();
    }
}
