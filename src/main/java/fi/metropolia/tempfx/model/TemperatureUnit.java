package fi.metropolia.tempfx.model;

/** The temperature scales the application can convert between. */
public enum TemperatureUnit {

    CELSIUS("unit.celsius", "°C"),
    FAHRENHEIT("unit.fahrenheit", "°F"),
    KELVIN("unit.kelvin", "K");

    private final String bundleKey;
    private final String symbol;

    TemperatureUnit(String bundleKey, String symbol) {
        this.bundleKey = bundleKey;
        this.symbol = symbol;
    }

    /** @return the key used to look up the translated name in the resource bundles */
    public String getBundleKey() {
        return bundleKey;
    }

    /** @return the symbol shown next to a value, for example {@code °C} */
    public String getSymbol() {
        return symbol;
    }
}
