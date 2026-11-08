package fi.metropolia.tempfx.model;

/**
 * The categories stored in {@code measurement_unit.category}.
 *
 * <p>Keeping them in an enum means the GUI cannot ask the database for a
 * category that does not exist, while the column itself stays a plain
 * {@code VARCHAR} so the schema works unchanged on H2 and MariaDB.
 */
public enum UnitCategory {

    TEMPERATURE,
    DISTANCE,
    SPEED,
    TIME;

    /** @return the value as it is written in the database column */
    public String columnValue() {
        return name();
    }
}
