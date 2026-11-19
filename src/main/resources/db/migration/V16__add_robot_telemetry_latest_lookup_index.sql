CREATE INDEX IF NOT EXISTS idx_robot_telemetry_latest
ON robot_telemetry (
    robot_id,
    telemetry_timestamp DESC,
    id DESC
);