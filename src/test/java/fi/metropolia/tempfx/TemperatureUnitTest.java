package fi.metropolia.tempfx;

import fi.metropolia.tempfx.model.TemperatureUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the bridge between the enum used for the arithmetic and the
 * {@code measurement_unit.unit_key} column used for the translations.
 */
@DisplayName("TemperatureUnit")
class TemperatureUnitTest {

    @ParameterizedTest(name = "{0} maps to the unit key {1}")
    @CsvSource({"CELSIUS, celsius", "FAHRENHEIT, fahrenheit", "KELVIN, kelvin"})
    @DisplayName("every scale carries the key of its database row")
    void carriesItsUnitKey(TemperatureUnit unit, String expectedKey) {
        assertEquals(expectedKey, unit.getUnitKey());
    }

    @ParameterizedTest(name = "fromKey(\"{0}\") finds the scale")
    @ValueSource(strings = {"celsius", "CELSIUS", " Celsius ", "fahrenheit", "kelvin"})
    @DisplayName("a key read from the database is resolved back, ignoring case and spaces")
    void resolvesKeysFromTheDatabase(String key) {
        assertTrue(TemperatureUnit.fromKey(key).isPresent(), "not resolved: " + key);
    }

    @Test
    @DisplayName("the round trip key to scale to key is stable")
    void roundTripIsStable() {
        for (TemperatureUnit unit : TemperatureUnit.values()) {
            assertEquals(unit, TemperatureUnit.fromKey(unit.getUnitKey()).orElseThrow());
        }
    }

    @ParameterizedTest(name = "fromKey(\"{0}\") is empty")
    @ValueSource(strings = {"kilometre", "mile_per_hour", "nautical_mile", "nonsense"})
    @DisplayName("a unit that is not a temperature scale is rejected")
    void rejectsOtherCategories(String key) {
        assertTrue(TemperatureUnit.fromKey(key).isEmpty(), "wrongly accepted: " + key);
    }

    @Test
    @DisplayName("a null or blank key is empty instead of throwing")
    void rejectsMissingKeys() {
        assertAll(
                () -> assertTrue(TemperatureUnit.fromKey(null).isEmpty()),
                () -> assertTrue(TemperatureUnit.fromKey("").isEmpty()),
                () -> assertTrue(TemperatureUnit.fromKey("   ").isEmpty()));
    }

    @Test
    @DisplayName("keeps a symbol and a resource-bundle fallback key for every scale")
    void keepsSymbolAndBundleKey() {
        for (TemperatureUnit unit : TemperatureUnit.values()) {
            assertAll(
                    () -> assertFalse(unit.getSymbol().isBlank()),
                    () -> assertTrue(unit.getBundleKey().startsWith("unit."),
                            unit + " has an unexpected bundle key: " + unit.getBundleKey()));
        }
    }
}
