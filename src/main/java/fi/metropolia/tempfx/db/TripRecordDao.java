package fi.metropolia.tempfx.db;

import fi.metropolia.tempfx.model.TripRecord;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/**
 * Create, read and delete operations on the child table {@code trip_record}.
 *
 * <p>Reads join the parent table so the user interface can show the units
 * belonging to each trip.
 */
public class TripRecordDao {

    private static final String SELECT_WITH_UNITS = """
            SELECT t.id, t.label, t.distance, t.time_hours, t.speed, t.unit_system_id,
                   u.code AS unit_code, u.distance_unit, u.speed_unit, t.created_at
            FROM trip_record t
            JOIN unit_system u ON u.id = t.unit_system_id
            """;

    private final Connection connection;

    public TripRecordDao(Connection connection) {
        this.connection = connection;
    }

    /**
     * Inserts a trip and fills in the generated primary key.
     *
     * @param record the trip to store
     * @return the generated id
     * @throws SQLException when the insert fails
     */
    public int insert(TripRecord record) throws SQLException {
        String sql = "INSERT INTO trip_record (label, distance, time_hours, speed, unit_system_id) "
                + "VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement statement =
                     connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, record.getLabel());
            statement.setDouble(2, record.getDistance());
            statement.setDouble(3, record.getTimeHours());
            statement.setDouble(4, record.getSpeed());
            statement.setInt(5, record.getUnitSystemId());
            statement.executeUpdate();

            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    record.setId(keys.getInt(1));
                }
            }
        }
        return record.getId();
    }

    /**
     * @return every stored trip, newest first
     * @throws SQLException when the query fails
     */
    public List<TripRecord> findAll() throws SQLException {
        List<TripRecord> records = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet results = statement.executeQuery(SELECT_WITH_UNITS + " ORDER BY t.id DESC")) {
            while (results.next()) {
                records.add(map(results));
            }
        }
        return records;
    }

    /**
     * Deletes one trip.
     *
     * @param id the primary key
     * @return {@code true} when a row was removed
     * @throws SQLException when the delete fails
     */
    public boolean deleteById(int id) throws SQLException {
        try (PreparedStatement statement =
                     connection.prepareStatement("DELETE FROM trip_record WHERE id = ?")) {
            statement.setInt(1, id);
            return statement.executeUpdate() > 0;
        }
    }

    /**
     * @return how many trips are stored
     * @throws SQLException when the query fails
     */
    public int count() throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet results = statement.executeQuery("SELECT COUNT(*) FROM trip_record")) {
            return results.next() ? results.getInt(1) : 0;
        }
    }

    /**
     * @return the sum of every stored distance, regardless of unit system
     * @throws SQLException when the query fails
     */
    public double totalDistance() throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet results =
                     statement.executeQuery("SELECT COALESCE(SUM(distance), 0) FROM trip_record")) {
            return results.next() ? results.getDouble(1) : 0.0;
        }
    }

    private TripRecord map(ResultSet results) throws SQLException {
        Timestamp createdAt = results.getTimestamp("created_at");
        return new TripRecord(
                results.getInt("id"),
                results.getString("label"),
                results.getDouble("distance"),
                results.getDouble("time_hours"),
                results.getDouble("speed"),
                results.getInt("unit_system_id"),
                results.getString("unit_code"),
                results.getString("distance_unit"),
                results.getString("speed_unit"),
                createdAt == null ? null : createdAt.toLocalDateTime());
    }
}
