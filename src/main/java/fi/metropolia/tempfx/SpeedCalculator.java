package fi.metropolia.tempfx;

/**
 * Relationships between speed, distance and time.
 *
 * <p>These are the values stored in the database by the application, so the
 * arithmetic lives in its own class and is unit tested independently of both
 * the user interface and the persistence layer.
 */
public final class SpeedCalculator {

    /** One mile expressed in kilometres. */
    public static final double KILOMETRES_PER_MILE = 1.609344;

    private SpeedCalculator() {
        // Utility class - not meant to be instantiated.
    }

    /**
     * Calculates speed as distance divided by time.
     *
     * @param distance  the distance travelled, must not be negative
     * @param timeHours the time taken in hours, must be greater than zero
     * @return the average speed, in distance units per hour
     * @throws IllegalArgumentException when the arguments are out of range
     */
    public static double speed(double distance, double timeHours) {
        requireNonNegativeDistance(distance);
        requirePositiveTime(timeHours);
        return distance / timeHours;
    }

    /**
     * Calculates the distance covered at a constant speed.
     *
     * @param speed     the speed, must not be negative
     * @param timeHours the time in hours, must be greater than zero
     * @return the distance travelled
     * @throws IllegalArgumentException when the arguments are out of range
     */
    public static double distance(double speed, double timeHours) {
        requireNonNegativeSpeed(speed);
        requirePositiveTime(timeHours);
        return speed * timeHours;
    }

    /**
     * Calculates how long a distance takes at a constant speed.
     *
     * @param distance the distance, must not be negative
     * @param speed    the speed, must be greater than zero
     * @return the time in hours
     * @throws IllegalArgumentException when the arguments are out of range
     */
    public static double timeHours(double distance, double speed) {
        requireNonNegativeDistance(distance);
        if (speed <= 0) {
            throw new IllegalArgumentException("Speed must be greater than zero: " + speed);
        }
        return distance / speed;
    }

    /** Converts kilometres to miles. */
    public static double kilometresToMiles(double kilometres) {
        requireNonNegativeDistance(kilometres);
        return kilometres / KILOMETRES_PER_MILE;
    }

    /** Converts miles to kilometres. */
    public static double milesToKilometres(double miles) {
        requireNonNegativeDistance(miles);
        return miles * KILOMETRES_PER_MILE;
    }

    private static void requireNonNegativeDistance(double distance) {
        if (distance < 0) {
            throw new IllegalArgumentException("Distance must not be negative: " + distance);
        }
    }

    private static void requireNonNegativeSpeed(double speed) {
        if (speed < 0) {
            throw new IllegalArgumentException("Speed must not be negative: " + speed);
        }
    }

    private static void requirePositiveTime(double timeHours) {
        if (timeHours <= 0) {
            throw new IllegalArgumentException("Time must be greater than zero: " + timeHours);
        }
    }
}
