package fi.metropolia.tempfx.model;

import java.time.LocalDateTime;

/**
 * One row of {@code conversion_log}, already joined with the localized unit
 * names so the GUI can display it directly.
 *
 * @param id            primary key
 * @param inputValue    the value the user typed
 * @param fromUnitKey   source unit key, for example {@code celsius}
 * @param fromUnitName  source unit name in the language of this row
 * @param toUnitKey     target unit key
 * @param toUnitName    target unit name in the language of this row
 * @param resultValue   the converted value
 * @param lang          the language that was active when the conversion was made
 * @param createdAt     when the conversion was stored
 */
public record ConversionRecord(int id, double inputValue,
                               String fromUnitKey, String fromUnitName,
                               String toUnitKey, String toUnitName,
                               double resultValue, String lang, LocalDateTime createdAt) {
}
