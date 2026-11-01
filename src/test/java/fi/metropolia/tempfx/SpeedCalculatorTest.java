package fi.metropolia.tempfx;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("SpeedCalculator")
class SpeedCalculatorTest {

    private static final double DELTA = 1e-9;

    @ParameterizedTest(name = "{0} over {1} h = {2} per hour")
    @CsvSource({"100.0, 2.0, 50.0", "0.0, 1.0, 0.0", "250.5, 3.0, 83.5", "7.0, 0.5, 14.0"})
    @DisplayName("speed is distance divided by time")
    void calculatesSpeed(double distance, double hours, double expected) {
        assertEquals(expected, SpeedCalculator.speed(distance, hours), DELTA);
    }

    @ParameterizedTest(name = "{0} per hour for {1} h = {2}")
    @CsvSource({"50.0, 2.0, 100.0", "0.0, 3.0, 0.0", "83.5, 3.0, 250.5"})
    @DisplayName("distance is speed multiplied by time")
    void calculatesDistance(double speed, double hours, double expected) {
        assertEquals(expected, SpeedCalculator.distance(speed, hours), DELTA);
    }

    @ParameterizedTest(name = "{0} at {1} per hour takes {2} h")
    @CsvSource({"100.0, 50.0, 2.0", "250.5, 83.5, 3.0", "0.0, 10.0, 0.0"})
    @DisplayName("time is distance divided by speed")
    void calculatesTime(double distance, double speed, double expected) {
        assertEquals(expected, SpeedCalculator.timeHours(distance, speed), DELTA);
    }

    @Test
    @DisplayName("the three formulas agree with each other")
    void formulasAreConsistent() {
        double distance = 180.0;
        double hours = 2.5;
        double speed = SpeedCalculator.speed(distance, hours);
        assertEquals(distance, SpeedCalculator.distance(speed, hours), DELTA);
        assertEquals(hours, SpeedCalculator.timeHours(distance, speed), DELTA);
    }

    @Test
    @DisplayName("refuses a time of zero or less")
    void refusesNonPositiveTime() {
        assertThrows(IllegalArgumentException.class, () -> SpeedCalculator.speed(10.0, 0.0));
        assertThrows(IllegalArgumentException.class, () -> SpeedCalculator.speed(10.0, -1.0));
        assertThrows(IllegalArgumentException.class, () -> SpeedCalculator.distance(10.0, 0.0));
    }

    @Test
    @DisplayName("refuses a negative distance or speed")
    void refusesNegativeValues() {
        assertThrows(IllegalArgumentException.class, () -> SpeedCalculator.speed(-1.0, 1.0));
        assertThrows(IllegalArgumentException.class, () -> SpeedCalculator.distance(-1.0, 1.0));
        assertThrows(IllegalArgumentException.class, () -> SpeedCalculator.timeHours(-1.0, 1.0));
        assertThrows(IllegalArgumentException.class, () -> SpeedCalculator.kilometresToMiles(-1.0));
        assertThrows(IllegalArgumentException.class, () -> SpeedCalculator.milesToKilometres(-1.0));
    }

    @Test
    @DisplayName("refuses a speed of zero when calculating time")
    void refusesZeroSpeedForTime() {
        assertThrows(IllegalArgumentException.class, () -> SpeedCalculator.timeHours(10.0, 0.0));
    }

    @Test
    @DisplayName("converts between kilometres and miles")
    void convertsDistanceUnits() {
        assertEquals(1.0, SpeedCalculator.kilometresToMiles(SpeedCalculator.KILOMETRES_PER_MILE), DELTA);
        assertEquals(SpeedCalculator.KILOMETRES_PER_MILE, SpeedCalculator.milesToKilometres(1.0), DELTA);
        assertEquals(100.0, SpeedCalculator.kilometresToMiles(
                SpeedCalculator.milesToKilometres(100.0)), 1e-9);
        assertEquals(0.0, SpeedCalculator.milesToKilometres(0.0), DELTA);
    }

    @Test
    @DisplayName("is a utility class that cannot be instantiated normally")
    void utilityClassHasPrivateConstructor() throws Exception {
        Constructor<SpeedCalculator> constructor = SpeedCalculator.class.getDeclaredConstructor();
        assertTrue(Modifier.isPrivate(constructor.getModifiers()));
        constructor.setAccessible(true);
        assertNotNull(constructor.newInstance());
    }
}
