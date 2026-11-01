package fi.metropolia.tempfx.model;

/**
 * A measurement system stored in the parent table {@code unit_system}.
 *
 * @param id           primary key
 * @param code         unique code, for example METRIC or IMPERIAL
 * @param distanceUnit the distance unit, for example km or mi
 * @param speedUnit    the speed unit, for example km/h or mph
 */
public record UnitSystem(int id, String code, String distanceUnit, String speedUnit) {

    @Override
    public String toString() {
        return code + " (" + distanceUnit + ", " + speedUnit + ")";
    }
}
