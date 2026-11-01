package fi.metropolia.tempfx;

import fi.metropolia.tempfx.model.TemperatureUnit;

/**
 * Conversions between Celsius, Fahrenheit and Kelvin.
 *
 * <p>Every method is static and side-effect free, so the whole class can be
 * covered by unit tests without starting the user interface.
 */
public final class TemperatureConverter {

    /** The coldest possible temperature, in degrees Celsius. */
    public static final double ABSOLUTE_ZERO_CELSIUS = -273.15;

    /** Temperatures strictly below this value (in Celsius) are considered extreme. */
    public static final double EXTREME_LOW_CELSIUS = -40.0;

    /** Temperatures strictly above this value (in Celsius) are considered extreme. */
    public static final double EXTREME_HIGH_CELSIUS = 50.0;

    private TemperatureConverter() {
        // Utility class - not meant to be instantiated.
    }

    public static double fahrenheitToCelsius(double fahrenheit) {
        return (fahrenheit - 32) * 5 / 9;
    }

    public static double celsiusToFahrenheit(double celsius) {
        return (celsius * 9 / 5) + 32;
    }

    public static double celsiusToKelvin(double celsius) {
        return celsius - ABSOLUTE_ZERO_CELSIUS;
    }

    public static double kelvinToCelsius(double kelvin) {
        return kelvin + ABSOLUTE_ZERO_CELSIUS;
    }

    public static double fahrenheitToKelvin(double fahrenheit) {
        return celsiusToKelvin(fahrenheitToCelsius(fahrenheit));
    }

    public static double kelvinToFahrenheit(double kelvin) {
        return celsiusToFahrenheit(kelvinToCelsius(kelvin));
    }

    /**
     * Converts a value from any supported scale to Celsius.
     *
     * @param value the temperature to convert
     * @param unit  the scale {@code value} is expressed in
     * @return the same temperature in degrees Celsius
     */
    public static double toCelsius(double value, TemperatureUnit unit) {
        return switch (unit) {
            case CELSIUS -> value;
            case FAHRENHEIT -> fahrenheitToCelsius(value);
            case KELVIN -> kelvinToCelsius(value);
        };
    }

    /**
     * Converts a Celsius value to any supported scale.
     *
     * @param celsius the temperature in degrees Celsius
     * @param unit    the scale to convert to
     * @return the temperature expressed in {@code unit}
     */
    public static double fromCelsius(double celsius, TemperatureUnit unit) {
        return switch (unit) {
            case CELSIUS -> celsius;
            case FAHRENHEIT -> celsiusToFahrenheit(celsius);
            case KELVIN -> celsiusToKelvin(celsius);
        };
    }

    /**
     * Converts a temperature between any two scales.
     *
     * @param value the temperature to convert
     * @param from  the scale of {@code value}
     * @param to    the scale to convert to
     * @return the converted temperature
     * @throws IllegalArgumentException when the value is colder than absolute zero
     */
    public static double convert(double value, TemperatureUnit from, TemperatureUnit to) {
        double celsius = toCelsius(value, from);
        if (celsius < ABSOLUTE_ZERO_CELSIUS - 1e-9) {
            throw new IllegalArgumentException(
                    "Temperature is below absolute zero: " + celsius + " C");
        }
        return fromCelsius(celsius, to);
    }

    /**
     * Tells whether a temperature is outside the normal range.
     *
     * @param celsius temperature in degrees Celsius
     * @return {@code true} when the temperature is below -40 C or above 50 C
     */
    public static boolean isExtremeTemperature(double celsius) {
        return celsius < EXTREME_LOW_CELSIUS || celsius > EXTREME_HIGH_CELSIUS;
    }
}
