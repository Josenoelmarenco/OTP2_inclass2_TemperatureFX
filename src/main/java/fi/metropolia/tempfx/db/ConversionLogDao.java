package fi.metropolia.tempfx.db;

import fi.metropolia.tempfx.model.ConversionRecord;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/**
 * Stores every conversion the user performs and reads the most recent ones
 * back with localized unit names.
 *
 * <p>The log keeps the language that was active at the time, so the history
 * shows what the user actually saw. The unit names themselves are resolved at
 * read time through the same double join and {@code COALESCE} fallback used by
 * {@link UnitTranslationDao}, which means the history is displayed in the
 * language selected now, while the {@code lang} column still records the
 * language the conversion was made in.
 */
public class ConversionLogDao {

    /** How many rows the user interface shows. */
    public static final int RECENT_LIMIT = 5;

    private static final String INSERT_SQL = """
            INSERT INTO conversion_log
                   (input_value, from_unit_id, to_unit_id, result_value, lang)
            VALUES (?, ?, ?, ?, ?)
            """;

    private static final String SELECT_RECENT = """
            SELECT c.id            AS id,
                   c.input_value   AS input_value,
                   c.result_value  AS result_value,
                   c.lang          AS lang,
                   c.created_at    AS created_at,
                   fu.unit_key     AS from_key,
                   tu.unit_key     AS to_key,
                   COALESCE(ft.name, ffb.name, fu.unit_key) AS from_name,
                   COALESCE(tt.name, tfb.name, tu.unit_key) AS to_name
              FROM conversion_log c
              JOIN measurement_unit fu ON fu.id = c.from_unit_id
              JOIN measurement_unit tu ON tu.id = c.to_unit_id
              LEFT JOIN unit_translation ft  ON ft.unit_id  = fu.id AND ft.lang  = ?
              LEFT JOIN unit_translation ffb ON ffb.unit_id = fu.id AND ffb.lang = ?
              LEFT JOIN unit_translation tt  ON tt.unit_id  = tu.id AND tt.lang  = ?
              LEFT JOIN unit_translation tfb ON tfb.unit_id = tu.id AND tfb.lang = ?
             ORDER BY c.id DESC
             LIMIT ?
            """;

    private final Connection connection;

    public ConversionLogDao(Connection connection) {
        this.connection = connection;
    }

    /**
     * Saves one conversion.
     *
     * @param inputValue  the value the user typed
     * @param fromUnitId  primary key of the source unit
     * @param toUnitId    primary key of the target unit
     * @param resultValue the converted value
     * @param language    the language active at that moment
     * @return the generated primary key, or 0 when the driver did not report one
     * @throws SQLException when the insert fails
     */
    public int insert(double inputValue, int fromUnitId, int toUnitId, double resultValue,
                      String language) throws SQLException {
        try (PreparedStatement statement =
                     connection.prepareStatement(INSERT_SQL, Statement.RETURN_GENERATED_KEYS)) {
            statement.setDouble(1, inputValue);
            statement.setInt(2, fromUnitId);
            statement.setInt(3, toUnitId);
            statement.setDouble(4, resultValue);
            statement.setString(5, UnitTranslationDao.normalize(language));
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                return keys.next() ? keys.getInt(1) : 0;
            }
        }
    }

    /**
     * Reads the most recent conversions, newest first.
     *
     * @param limit    how many rows to return
     * @param language the language the names should be shown in
     * @return the rows, newest first
     * @throws SQLException when the query fails
     */
    public List<ConversionRecord> findRecent(int limit, String language) throws SQLException {
        String lang = UnitTranslationDao.normalize(language);
        List<ConversionRecord> records = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(SELECT_RECENT)) {
            statement.setString(1, lang);
            statement.setString(2, UnitTranslationDao.DEFAULT_LANGUAGE);
            statement.setString(3, lang);
            statement.setString(4, UnitTranslationDao.DEFAULT_LANGUAGE);
            statement.setInt(5, Math.max(0, limit));
            try (ResultSet results = statement.executeQuery()) {
                while (results.next()) {
                    records.add(map(results));
                }
            }
        }
        return records;
    }

    /**
     * Reads the last {@value #RECENT_LIMIT} conversions, which is what the
     * assignment asks the GUI to show.
     *
     * @param language the language the names should be shown in
     * @return at most five rows, newest first
     * @throws SQLException when the query fails
     */
    public List<ConversionRecord> findRecent(String language) throws SQLException {
        return findRecent(RECENT_LIMIT, language);
    }

    /**
     * @return how many conversions have been stored in total
     * @throws SQLException when the query fails
     */
    public int count() throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet results = statement.executeQuery("SELECT COUNT(*) FROM conversion_log")) {
            return results.next() ? results.getInt(1) : 0;
        }
    }

    private ConversionRecord map(ResultSet results) throws SQLException {
        Timestamp createdAt = results.getTimestamp("created_at");
        return new ConversionRecord(
                results.getInt("id"),
                results.getDouble("input_value"),
                results.getString("from_key"),
                results.getString("from_name"),
                results.getString("to_key"),
                results.getString("to_name"),
                results.getDouble("result_value"),
                results.getString("lang"),
                createdAt == null ? null : createdAt.toLocalDateTime());
    }
}
