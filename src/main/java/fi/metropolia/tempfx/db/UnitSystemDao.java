package fi.metropolia.tempfx.db;

import fi.metropolia.tempfx.model.UnitSystem;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Read access to the parent table {@code unit_system}. */
public class UnitSystemDao {

    private final Connection connection;

    public UnitSystemDao(Connection connection) {
        this.connection = connection;
    }

    /**
     * @return every measurement system, ordered by id
     * @throws SQLException when the query fails
     */
    public List<UnitSystem> findAll() throws SQLException {
        String sql = "SELECT id, code, distance_unit, speed_unit FROM unit_system ORDER BY id";
        List<UnitSystem> systems = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet results = statement.executeQuery(sql)) {
            while (results.next()) {
                systems.add(map(results));
            }
        }
        return systems;
    }

    /**
     * @param code the unique code, for example METRIC
     * @return the matching system, or empty when the code is unknown
     * @throws SQLException when the query fails
     */
    public Optional<UnitSystem> findByCode(String code) throws SQLException {
        String sql = "SELECT id, code, distance_unit, speed_unit FROM unit_system WHERE code = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, code);
            try (ResultSet results = statement.executeQuery()) {
                return results.next() ? Optional.of(map(results)) : Optional.empty();
            }
        }
    }

    /**
     * @param id the primary key
     * @return the matching system, or empty when the id is unknown
     * @throws SQLException when the query fails
     */
    public Optional<UnitSystem> findById(int id) throws SQLException {
        String sql = "SELECT id, code, distance_unit, speed_unit FROM unit_system WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, id);
            try (ResultSet results = statement.executeQuery()) {
                return results.next() ? Optional.of(map(results)) : Optional.empty();
            }
        }
    }

    private UnitSystem map(ResultSet results) throws SQLException {
        return new UnitSystem(
                results.getInt("id"),
                results.getString("code"),
                results.getString("distance_unit"),
                results.getString("speed_unit"));
    }
}
