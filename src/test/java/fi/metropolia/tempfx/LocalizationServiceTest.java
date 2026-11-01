package fi.metropolia.tempfx;

import fi.metropolia.tempfx.i18n.LanguageOption;
import fi.metropolia.tempfx.i18n.LocalizationService;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("LocalizationService")
class LocalizationServiceTest {

    static List<Locale> supportedLocales() {
        return LocalizationService.supportedLocales();
    }

    @Test
    @DisplayName("offers exactly the five languages required by the assignment")
    void offersFiveLanguages() {
        List<String> tags = LocalizationService.supportedLocales().stream()
                .map(Locale::toLanguageTag)
                .toList();
        assertEquals(List.of("en-US", "fi-FI", "sv-SE", "ja-JP", "ar-SA"), tags);
        assertEquals(5, LocalizationService.options().size());
        assertEquals(Locale.of("en", "US"), LocalizationService.defaultLocale());
    }

    @Test
    @DisplayName("shows every language written in its own words")
    void languageLabelsAreNative() {
        List<String> labels = LocalizationService.options().stream()
                .map(LanguageOption::nativeLabel)
                .toList();
        assertEquals("English", labels.get(0));
        assertEquals("Suomi", labels.get(1));
        assertEquals("Svenska", labels.get(2));
        assertEquals("日本語", labels.get(3));
        assertEquals("العربية", labels.get(4));
    }

    @ParameterizedTest(name = "the bundle for {0} loads")
    @MethodSource("supportedLocales")
    @DisplayName("every bundle loads and has no empty value")
    void everyBundleLoads(Locale locale) {
        ResourceBundle bundle = LocalizationService.bundle(locale);
        assertNotNull(bundle);
        assertFalse(bundle.keySet().isEmpty());
        for (String key : bundle.keySet()) {
            assertFalse(bundle.getString(key).isBlank(), "empty value for " + key + " in " + locale);
        }
    }

    @Test
    @DisplayName("all five bundles contain exactly the same keys")
    void bundlesHaveTheSameKeys() {
        Set<String> english = LocalizationService.bundle(Locale.of("en", "US")).keySet();
        assertTrue(english.size() >= 30, "the English bundle looks incomplete");
        for (Locale locale : LocalizationService.supportedLocales()) {
            assertEquals(english, LocalizationService.bundle(locale).keySet(),
                    "the bundle for " + locale + " does not have the same keys as English");
        }
    }

    @Test
    @DisplayName("loads the Japanese and Arabic UTF-8 texts correctly")
    void loadsNonLatinScripts() {
        ResourceBundle japanese = LocalizationService.bundle(Locale.of("ja", "JP"));
        ResourceBundle arabic = LocalizationService.bundle(Locale.of("ar", "SA"));
        // 言語 and اللغة
        assertEquals("言語", japanese.getString("label.language"));
        assertEquals("اللغة", arabic.getString("label.language"));
    }

    @Test
    @DisplayName("every language has its own title")
    void titlesAreTranslated() {
        long distinct = LocalizationService.supportedLocales().stream()
                .map(locale -> LocalizationService.bundle(locale).getString("app.title"))
                .distinct()
                .count();
        assertEquals(5, distinct);
    }

    @Test
    @DisplayName("marks Arabic as right to left and the others as left to right")
    void detectsRightToLeftLanguages() {
        assertTrue(LocalizationService.isRightToLeft(Locale.of("ar", "SA")));
        assertFalse(LocalizationService.isRightToLeft(Locale.of("en", "US")));
        assertFalse(LocalizationService.isRightToLeft(Locale.of("fi", "FI")));
        assertFalse(LocalizationService.isRightToLeft(Locale.of("sv", "SE")));
        assertFalse(LocalizationService.isRightToLeft(Locale.of("ja", "JP")));
        assertFalse(LocalizationService.isRightToLeft(null));
    }

    @ParameterizedTest(name = "code \"{0}\" resolves to {1}")
    @CsvSource({"en, en", "fi, fi", "sv, sv", "ja, ja", "ar, ar",
                "ar_SA, ar", "ja-JP, ja", "AR, ar", " sv , sv"})
    @DisplayName("resolves locales from language codes")
    void resolvesLanguageCodes(String code, String expected) {
        assertEquals(expected,
                LocalizationService.byLanguageCode(code).orElseThrow().getLanguage());
    }

    @ParameterizedTest(name = "code \"{0}\" is rejected")
    @ValueSource(strings = {"de", "es", "zz", ""})
    @DisplayName("rejects unsupported language codes")
    void rejectsUnknownLanguageCodes(String code) {
        assertTrue(LocalizationService.byLanguageCode(code).isEmpty());
    }

    @Test
    @DisplayName("rejects a null language code and an unknown locale")
    void rejectsNulls() {
        assertTrue(LocalizationService.byLanguageCode(null).isEmpty());
        assertTrue(LocalizationService.optionFor(null).isEmpty());
        assertTrue(LocalizationService.optionFor(Locale.of("de", "DE")).isEmpty());
        assertTrue(LocalizationService.optionFor(Locale.of("ar", "SA")).isPresent());
    }

    @Test
    @DisplayName("formats numbers with the separator of each locale")
    void formatsNumbersPerLocale() {
        assertTrue(LocalizationService.formatNumber(12.5, Locale.of("en", "US")).contains("."));
        assertTrue(LocalizationService.formatNumber(12.5, Locale.of("ja", "JP")).contains("."));
        assertTrue(LocalizationService.formatNumber(12.5, Locale.of("fi", "FI")).contains(","));
        assertTrue(LocalizationService.formatNumber(12.5, Locale.of("sv", "SE")).contains(","));
        assertFalse(LocalizationService.formatNumber(12.5, Locale.of("ar", "SA")).isBlank());
    }

    @Test
    @DisplayName("reads numbers written with a comma or a point")
    void parsesNumbersInBothNotations() {
        Locale finnish = Locale.of("fi", "FI");
        assertEquals(12.5, LocalizationService.parseNumber("12,5", finnish), 1e-9);
        assertEquals(12.5, LocalizationService.parseNumber("12.5", finnish), 1e-9);
        assertEquals(-40.0, LocalizationService.parseNumber(" -40 ", finnish), 1e-9);

        Locale english = Locale.of("en", "US");
        assertEquals(98.6, LocalizationService.parseNumber("98.6", english), 1e-9);
        assertEquals(0.0, LocalizationService.parseNumber("0", english), 1e-9);
    }

    @ParameterizedTest(name = "\"{0}\" is not a number")
    @ValueSource(strings = {"abc", "", "   ", "--5"})
    @DisplayName("refuses text that is not a number")
    void refusesInvalidNumbers(String text) {
        assertThrows(NumberFormatException.class,
                () -> LocalizationService.parseNumber(text, Locale.of("en", "US")));
    }

    @Test
    @DisplayName("refuses a null number")
    void refusesNullNumber() {
        assertThrows(NumberFormatException.class,
                () -> LocalizationService.parseNumber(null, Locale.of("en", "US")));
    }

    @Test
    @DisplayName("fills placeholders in translated messages")
    void fillsPlaceholders() {
        ResourceBundle english = LocalizationService.bundle(Locale.of("en", "US"));
        String stats = LocalizationService.message(english, "label.stats", 3, "120.0");
        assertTrue(stats.contains("3"));
        assertTrue(stats.contains("120.0"));

        // Without arguments the pattern is returned untouched.
        assertEquals(english.getString("status.ready"),
                LocalizationService.message(english, "status.ready"));
    }
}
