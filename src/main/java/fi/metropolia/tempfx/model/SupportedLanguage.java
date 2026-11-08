package fi.metropolia.tempfx.model;

/**
 * A row of the {@code language} table.
 *
 * <p>The languages are stored in the database rather than hard-coded, so that
 * {@code unit_translation} can reference them with a foreign key. A translation
 * for a language that does not exist is therefore rejected by the database
 * itself instead of silently ending up as an orphan row.
 *
 * @param id           primary key
 * @param code         ISO 639-1 code, for example {@code ja}
 * @param nativeName   the language written in its own script
 * @param rightToLeft  {@code true} for right-to-left scripts such as Arabic
 */
public record SupportedLanguage(int id, String code, String nativeName, boolean rightToLeft) {

    @Override
    public String toString() {
        return nativeName;
    }
}
