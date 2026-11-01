package fi.metropolia.tempfx.model;

import java.time.LocalDateTime;

/**
 * One row of the child table {@code trip_record}: a distance covered in a
 * given time, together with the resulting speed and the measurement system
 * it belongs to.
 */
public class TripRecord {

    private int id;
    private final String label;
    private final double distance;
    private final double timeHours;
    private final double speed;
    private final int unitSystemId;

    // Values joined from unit_system, used only for display.
    private final String unitSystemCode;
    private final String distanceUnit;
    private final String speedUnit;
    private final LocalDateTime createdAt;

    /** Constructor used before inserting a new row. */
    public TripRecord(String label, double distance, double timeHours, double speed, int unitSystemId) {
        this(0, label, distance, timeHours, speed, unitSystemId, null, null, null, null);
    }

    /** Full constructor used when reading a row back from the database. */
    public TripRecord(int id, String label, double distance, double timeHours, double speed,
                      int unitSystemId, String unitSystemCode, String distanceUnit,
                      String speedUnit, LocalDateTime createdAt) {
        this.id = id;
        this.label = label;
        this.distance = distance;
        this.timeHours = timeHours;
        this.speed = speed;
        this.unitSystemId = unitSystemId;
        this.unitSystemCode = unitSystemCode;
        this.distanceUnit = distanceUnit;
        this.speedUnit = speedUnit;
        this.createdAt = createdAt;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getLabel() {
        return label;
    }

    public double getDistance() {
        return distance;
    }

    public double getTimeHours() {
        return timeHours;
    }

    public double getSpeed() {
        return speed;
    }

    public int getUnitSystemId() {
        return unitSystemId;
    }

    public String getUnitSystemCode() {
        return unitSystemCode;
    }

    public String getDistanceUnit() {
        return distanceUnit;
    }

    public String getSpeedUnit() {
        return speedUnit;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    @Override
    public String toString() {
        return label + ": " + distance + " " + distanceUnit + " / " + timeHours + " h";
    }
}
