package fi.metropolia.tempfx.model;

/**
 * A row of the language-neutral base table {@code measurement_unit}.
 *
 * <p>It deliberately carries no translatable text: the name and the
 * description live in {@code unit_translation}, one row per language. That
 * separation is the whole point of the translation-table approach.
 *
 * @param id           primary key
 * @param unitKey      stable identifier used by the code, for example {@code celsius}
 * @param category     one of {@link UnitCategory}
 * @param symbol       symbol shown next to a value, for example {@code °C}
 * @param unitSystemId the measurement system this unit belongs to, or
 *                     {@code null} for units that belong to none
 */
public record MeasurementUnit(int id, String unitKey, String category, String symbol,
                              Integer unitSystemId) {
}
