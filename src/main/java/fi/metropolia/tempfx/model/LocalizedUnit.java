package fi.metropolia.tempfx.model;

/**
 * A measurement unit together with its name and description in one language.
 *
 * <p>This is what {@code UnitTranslationDao} returns and what the GUI shows in
 * the unit drop-downs. The text never comes from a {@code .properties} file:
 * it is read from the database in the language the user selected.
 *
 * @param id            primary key of the unit in {@code measurement_unit}
 * @param unitKey       stable identifier, for example {@code celsius}
 * @param category      one of {@link UnitCategory}
 * @param symbol        symbol shown next to a value, for example {@code °C}
 * @param name          localized name
 * @param description   localized short description
 * @param usedFallback  {@code true} when the requested language had no
 *                      translation and the English one was used instead
 */
public record LocalizedUnit(int id, String unitKey, String category, String symbol,
                            String name, String description, boolean usedFallback) {

    /** @return the name followed by its symbol, for example {@code Celsius (°C)} */
    public String nameWithSymbol() {
        return name + " (" + symbol + ")";
    }

    @Override
    public String toString() {
        // The JavaFX ComboBox falls back to toString() when no converter is set.
        return name;
    }
}
