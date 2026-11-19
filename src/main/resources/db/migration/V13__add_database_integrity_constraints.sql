ALTER TABLE robots
ADD CONSTRAINT ck_robots_battery_range
CHECK (battery_percentage BETWEEN 0 AND 100);

ALTER TABLE robot_telemetry
ADD CONSTRAINT ck_telemetry_battery_range
CHECK (battery_percentage BETWEEN 0 AND 100);

ALTER TABLE robot_telemetry
ADD CONSTRAINT ck_telemetry_speed_non_negative
CHECK (speed >= 0);

ALTER TABLE robot_telemetry
ADD CONSTRAINT ck_telemetry_sequence_positive
CHECK (sequence_number > 0);

ALTER TABLE missions
ADD CONSTRAINT ck_missions_assignment_generation_non_negative
CHECK (assignment_generation >= 0);
