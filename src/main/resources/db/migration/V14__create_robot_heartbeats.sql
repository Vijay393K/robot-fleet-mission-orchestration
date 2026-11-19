CREATE TABLE robot_heartbeats (

    id BIGSERIAL PRIMARY KEY,

    robot_id UUID NOT NULL,

    session_id UUID,

    sequence_number BIGINT NOT NULL,

    received_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_robot_heartbeats_robot
        FOREIGN KEY (robot_id)
        REFERENCES robots(id),

    CONSTRAINT uq_robot_heartbeats_robot_sequence
        UNIQUE (robot_id, sequence_number),

    CONSTRAINT ck_robot_heartbeats_sequence_positive
        CHECK (sequence_number > 0)
);

CREATE INDEX idx_robot_heartbeats_robot_received_at
ON robot_heartbeats (robot_id, received_at DESC);