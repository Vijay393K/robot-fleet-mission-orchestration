
CREATE TABLE robot_telemetry_sessions (
    session_id UUID PRIMARY KEY,
    robot_id UUID NOT NULL
        REFERENCES robots(id),
    started_at TIMESTAMPTZ NOT NULL
        DEFAULT CURRENT_TIMESTAMP,
    ended_at TIMESTAMPTZ,

    CONSTRAINT ck_telemetry_session_dates
        CHECK (
            ended_at IS NULL
            OR ended_at >= started_at
        )
);

CREATE UNIQUE INDEX uq_active_telemetry_session_per_robot
ON robot_telemetry_sessions (robot_id)
WHERE ended_at IS NULL;

CREATE INDEX idx_telemetry_sessions_robot_started
ON robot_telemetry_sessions (
    robot_id,
    started_at DESC
);

INSERT INTO robot_telemetry_sessions (
    session_id,
    robot_id
)
SELECT
    current_session_id,
    id
FROM robots
WHERE current_session_id IS NOT NULL;
