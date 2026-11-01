package fi.metropolia.tempfx;

import fi.metropolia.tempfx.model.TemperatureUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("TemperatureConverter")
class TemperatureConverterTest {

    private static final double DELTA = 1e-9;

    @Nested
    @DisplayName("pairwise conversions")
    class PairwiseConversions {

        @ParameterizedTest(name = "{0} F = {1} C")
        @CsvSource({"32.0, 0.0", "212.0, 100.0", "-40.0, -40.0", "98.6, 37.0"})
        void fahrenheitToCelsius(double fahrenheit, double celsius) {
            assertEquals(celsius, TemperatureConverter.fahrenheitToCelsius(fahrenheit), DELTA);
        }

        @ParameterizedTest(name = "{0} C = {1} F")
        @CsvSource({"0.0, 32.0", "100.0, 212.0", "-40.0, -40.0", "37.0, 98.6"})
        void celsiusToFahrenheit(double celsius, double fahrenheit) {
            assertEquals(fahrenheit, TemperatureConverter.celsiusToFahrenheit(celsius), DELTA);
        }

        @ParameterizedTest(name = "{0} C = {1} K")
        @CsvSource({"0.0, 273.15", "100.0, 373.15", "-273.15, 0.0", "25.0, 298.15"})
        void celsiusToKelvin(double celsius, double kelvin) {
            assertEquals(kelvin, TemperatureConverter.celsiusToKelvin(celsius), DELTA);
        }

        @ParameterizedTest(name = "{0} K = {1} C")
        @CsvSource({"273.15, 0.0", "373.15, 100.0", "0.0, -273.15"})
        void kelvinToCelsius(double kelvin, double celsius) {
            assertEquals(celsius, TemperatureConverter.kelvinToCelsius(kelvin), DELTA);
        }

        @Test
        void fahrenheitToKelvin() {
            assertEquals(273.15, TemperatureConverter.fahrenheitToKelvin(32.0), DELTA);
        }

        @Test
        void kelvinToFahrenheit() {
            assertEquals(32.0, TemperatureConverter.kelvinToFahrenheit(273.15), DELTA);
        }
    }

    @Nested
    @DisplayName("convert between any two units")
    class Convert {

        @ParameterizedTest(name = "converting to the same unit keeps {0}")
        @EnumSource(TemperatureUnit.class)
        void sameUnitIsIdentity(TemperatureUnit unit) {
            assertEquals(21.0, TemperatureConverter.convert(21.0, unit, unit), DELTA);
        }

        @Test
        void celsiusToFahrenheitThroughConvert() {
            assertEquals(212.0, TemperatureConverter.convert(
                    100.0, TemperatureUnit.CELSIUS, TemperatureUnit.FAHRENHEIT), DELTA);
        }

        @Test
        void fahrenheitToKelvinThroughConvert() {
            assertEquals(273.15, TemperatureConverter.convert(
                    32.0, TemperatureUnit.FAHRENHEIT, TemperatureUnit.KELVIN), DELTA);
        }

        @Test
        void kelvinToCelsiusThroughConvert() {
            assertEquals(0.0, TemperatureConverter.convert(
                    273.15, TemperatureUnit.KELVIN, TemperatureUnit.CELSIUS), DELTA);
        }

        @ParameterizedTest(name = "round trip through {0} keeps the value")
        @EnumSource(TemperatureUnit.class)
        void roundTripKeepsTheValue(TemperatureUnit through) {
            double original = 21.5;
            double there = TemperatureConverter.convert(original, TemperatureUnit.CELSIUS, through);
            double back = TemperatureConverter.convert(there, through, TemperatureUnit.CELSIUS);
            assertEquals(original, back, 1e-9);
        }

        @Test
        @DisplayName("refuses a temperature below absolute zero")
        void refusesBelowAbsoluteZero() {
            assertThrows(IllegalArgumentException.class, () -> TemperatureConverter.convert(
                    -300.0, TemperatureUnit.CELSIUS, TemperatureUnit.KELVIN));
            assertThrows(IllegalArgumentException.class, () -> TemperatureConverter.convert(
                    -1.0, TemperatureUnit.KELVIN, TemperatureUnit.CELSIUS));
        }

        @Test
        @DisplayName("accepts exactly absolute zero")
        void acceptsAbsoluteZero() {
            assertEquals(0.0, TemperatureConverter.convert(
                    TemperatureConverter.ABSOLUTE_ZERO_CELSIUS,
                    TemperatureUnit.CELSIUS, TemperatureUnit.KELVIN), 1e-9);
        }
    }

    @Nested
    @DisplayName("isExtremeTemperature")
    class Extreme {

        @ParameterizedTest(name = "{0} C is extreme")
        @ValueSource(doubles = {-41.0, -40.1, -100.0, 50.1, 51.0, 120.0})
        void detectsExtreme(double celsius) {
            assertTrue(TemperatureConverter.isExtremeTemperature(celsius));
        }

        @ParameterizedTest(name = "{0} C is normal")
        @ValueSource(doubles = {-40.0, -39.9, 0.0, 21.0, 49.9, 50.0})
        void detectsNormal(double celsius) {
            assertFalse(TemperatureConverter.isExtremeTemperature(celsius));
        }
    }

    @Test
    @DisplayName("every unit exposes a translation key and a symbol")
    void unitsHaveKeysAndSymbols() {
        for (TemperatureUnit unit : TemperatureUnit.values()) {
            assertNotNull(unit.getBundleKey());
            assertTrue(unit.getBundleKey().startsWith("unit."));
            assertFalse(unit.getSymbol().isBlank());
        }
        assertEquals(TemperatureUnit.CELSIUS, TemperatureUnit.valueOf("CELSIUS"));
    }

    @Test
    @DisplayName("is a utility class that cannot be instantiated normally")
    void utilityClassHasPrivateConstructor() throws Exception {
        Constructor<TemperatureConverter> constructor =
                TemperatureConverter.class.getDeclaredConstructor();
        assertTrue(Modifier.isPrivate(constructor.getModifiers()));
        constructor.setAccessible(true);
        assertNotNull(constructor.newInstance());
    }
}
