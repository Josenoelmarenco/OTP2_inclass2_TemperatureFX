package fi.metropolia.tempfx.model;

import java.util.Locale;
import java.util.Optional;

/**
 * The temperature scales the application can convert between.
 *
 * <p>The enum carries the conversion identity only. Since the database
 * localization was added, the <em>name</em> shown in the drop-down comes from
 * {@code unit_translation} through {@code UnitTranslationDao}, not from this
 * class: {@link #getUnitKey()} is the link between the two, matching
 * {@code measurement_unit.unit_key}.
 *
 * <p>The resource-bundle key is kept as a fallback for the case where the
 * database cannot be reached, so the converter still works offline.
 */
public enum TemperatureUnit {

    CELSIUS("celsius", "unit.celsius", "°C"),
    FAHRENHEIT("fahrenheit", "unit.fahrenheit", "°F"),
    KELVIN("kelvin", "unit.kelvin", "K");

    private final String unitKey;
    private final String bundleKey;
    private final String symbol;

    TemperatureUnit(String unitKey, String bundleKey, String symbol) {
        this.unitKey = unitKey;
        this.bundleKey = bundleKey;
        this.symbol = symbol;
    }

    /** @return the key of the matching row in {@code measurement_unit} */
    public String getUnitKey() {
        return unitKey;
    }

    /** @return the key used to look up the translated name in the resource bundles */
    public String getBundleKey() {
        return bundleKey;
    }

    /** @return the symbol shown next to a value, for example {@code °C} */
    public String getSymbol() {
        return symbol;
    }

    /**
     * Finds the scale belonging to a database unit key.
     *
     * @param unitKey a value of {@code measurement_unit.unit_key}
     * @return the matching scale, or empty when the key is not a temperature
     *         unit (for example {@code kilometre})
     */
    public static Optional<TemperatureUnit> fromKey(String unitKey) {
        if (unitKey == null || unitKey.isBlank()) {
            return Optional.empty();
        }
        String wanted = unitKey.trim().toLowerCase(Locale.ROOT);
        for (TemperatureUnit unit : values()) {
            if (unit.unitKey.equals(wanted)) {
                return Optional.of(unit);
            }
        }
        return Optional.empty();
    }
}
