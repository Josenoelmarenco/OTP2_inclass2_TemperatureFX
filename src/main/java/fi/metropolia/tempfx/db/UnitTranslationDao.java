package fi.metropolia.tempfx.db;

import fi.metropolia.tempfx.model.LocalizedUnit;
import fi.metropolia.tempfx.model.SupportedLanguage;
import fi.metropolia.tempfx.model.UnitCategory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Reads unit names and descriptions out of the database in a chosen language.
 *
 * <p>This is the data-access half of the <strong>translation table</strong>
 * approach: {@code measurement_unit} holds one language-neutral row per unit
 * and {@code unit_translation} holds one row per unit and language.
 *
 * <p>Every query joins the translation table <em>twice</em>: once for the
 * requested language and once for English. {@code COALESCE} then picks the
 * requested translation when it exists, the English one when it does not, and
 * the unit key itself if even English is missing, so a name is never null;
 * so a half-translated database still produces a complete, readable drop-down
 * instead of empty rows. The query also reports whether the fallback was used,
 * which makes the behaviour testable and visible in the user interface.
 */
public class UnitTranslationDao {

    /** The language every other language falls back to. */
    public static final String DEFAULT_LANGUAGE = "en";

    /**
     * One row per unit, with the requested translation if present and the
     * English one otherwise.
     *
     * <p>{@code used_fallback} is 1 exactly when the requested language had no
     * row in {@code unit_translation} for that unit.
     */
    private static final String SELECT_LOCALIZED = """
            SELECT m.id              AS id,
                   m.unit_key        AS unit_key,
                   m.category        AS category,
                   m.symbol          AS symbol,
                   COALESCE(requested.name, fallback.name, m.unit_key)      AS name,
                   COALESCE(requested.description, fallback.description, '') AS description,
                   CASE WHEN requested.name IS NULL THEN 1 ELSE 0 END    AS used_fallback
              FROM measurement_unit m
              LEFT JOIN unit_translation requested
                     ON requested.unit_id = m.id AND requested.lang = ?
              LEFT JOIN unit_translation fallback
                     ON fallback.unit_id = m.id AND fallback.lang = ?
            """;

    private final Connection connection;

    /**
     * @param connection an open connection, kept by reference and not closed
     *                   by this class
     */
    public UnitTranslationDao(Connection connection) {
        this.connection = connection;
    }

    /**
     * Returns the units of one category, named in the requested language.
     *
     * @param category the category to list
     * @param language ISO 639-1 code such as {@code ja}; an unknown or
     *                 {@code null} value simply means everything falls back
     *                 to English
     * @return the units, ordered by their primary key
     * @throws SQLException when the query fails
     */
    public List<LocalizedUnit> findByCategory(UnitCategory category, String language)
            throws SQLException {
        String sql = SELECT_LOCALIZED + " WHERE m.category = ? ORDER BY m.id";
        List<LocalizedUnit> units = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, normalize(language));
            statement.setString(2, DEFAULT_LANGUAGE);
            statement.setString(3, category.columnValue());
            try (ResultSet results = statement.executeQuery()) {
                while (results.next()) {
                    units.add(map(results));
                }
            }
        }
        return units;
    }

    /**
     * Returns every unit, named in the requested language.
     *
     * @param language ISO 639-1 code
     * @return all units, ordered by category and then by primary key
     * @throws SQLException when the query fails
     */
    public List<LocalizedUnit> findAll(String language) throws SQLException {
        String sql = SELECT_LOCALIZED + " ORDER BY m.category, m.id";
        List<LocalizedUnit> units = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, normalize(language));
            statement.setString(2, DEFAULT_LANGUAGE);
            try (ResultSet results = statement.executeQuery()) {
                while (results.next()) {
                    units.add(map(results));
                }
            }
        }
        return units;
    }

    /**
     * Looks up a single unit by its stable key.
     *
     * @param unitKey  for example {@code celsius}
     * @param language ISO 639-1 code
     * @return the localized unit, or empty when the key is unknown
     * @throws SQLException when the query fails
     */
    public Optional<LocalizedUnit> findByUnitKey(String unitKey, String language)
            throws SQLException {
        String sql = SELECT_LOCALIZED + " WHERE m.unit_key = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, normalize(language));
            statement.setString(2, DEFAULT_LANGUAGE);
            statement.setString(3, unitKey);
            try (ResultSet results = statement.executeQuery()) {
                return results.next() ? Optional.of(map(results)) : Optional.empty();
            }
        }
    }

    /**
     * Reads the languages the database holds translations for.
     *
     * @return the languages, ordered by primary key
     * @throws SQLException when the query fails
     */
    public List<SupportedLanguage> languages() throws SQLException {
        String sql = "SELECT id, code, native_name, rtl FROM language ORDER BY id";
        List<SupportedLanguage> languages = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet results = statement.executeQuery(sql)) {
            while (results.next()) {
                languages.add(new SupportedLanguage(
                        results.getInt("id"),
                        results.getString("code"),
                        results.getString("native_name"),
                        results.getBoolean("rtl")));
            }
        }
        return languages;
    }

    /**
     * Counts how many translations exist for one language.
     *
     * @param language ISO 639-1 code
     * @return the number of rows in {@code unit_translation} for that language
     * @throws SQLException when the query fails
     */
    public int countTranslations(String language) throws SQLException {
        String sql = "SELECT COUNT(*) FROM unit_translation WHERE lang = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, normalize(language));
            try (ResultSet results = statement.executeQuery()) {
                return results.next() ? results.getInt(1) : 0;
            }
        }
    }

    /**
     * Turns a locale or language tag into the code stored in the database.
     *
     * <p>The application works with locales such as {@code ja_JP}, while the
     * {@code lang} column holds plain {@code ja}, so the tag is cut down to its
     * language part.
     *
     * @param language a language tag, a locale string, or {@code null}
     * @return the two-letter code, or the default language when nothing usable
     *         was given
     */
    public static String normalize(String language) {
        if (language == null || language.isBlank()) {
            return DEFAULT_LANGUAGE;
        }
        String head = language.trim().replace('-', '_').split("_")[0];
        return head.toLowerCase(Locale.ROOT);
    }

    /**
     * @param locale the active locale
     * @return the code stored in the {@code lang} column
     */
    public static String normalize(Locale locale) {
        return locale == null ? DEFAULT_LANGUAGE : normalize(locale.getLanguage());
    }

    private LocalizedUnit map(ResultSet results) throws SQLException {
        return new LocalizedUnit(
                results.getInt("id"),
                results.getString("unit_key"),
                results.getString("category"),
                results.getString("symbol"),
                results.getString("name"),
                results.getString("description"),
                results.getInt("used_fallback") == 1);
    }
}
