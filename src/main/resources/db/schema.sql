-- Two related tables used to keep track of speed, distance and time.
-- unit_system is the parent table; trip_record references it through a
-- foreign key, so every stored trip belongs to one measurement system.
-- The syntax is valid both for H2 (running in MODE=MySQL) and for MariaDB.

CREATE TABLE IF NOT EXISTS unit_system (
    id            INT AUTO_INCREMENT PRIMARY KEY,
    code          VARCHAR(20) NOT NULL UNIQUE,
    distance_unit VARCHAR(20) NOT NULL,
    speed_unit    VARCHAR(20) NOT NULL
);

CREATE TABLE IF NOT EXISTS trip_record (
    id             INT AUTO_INCREMENT PRIMARY KEY,
    label          VARCHAR(100) NOT NULL,
    distance       DOUBLE       NOT NULL,
    time_hours     DOUBLE       NOT NULL,
    speed          DOUBLE       NOT NULL,
    unit_system_id INT          NOT NULL,
    created_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_trip_unit_system FOREIGN KEY (unit_system_id)
        REFERENCES unit_system (id) ON DELETE CASCADE
);
