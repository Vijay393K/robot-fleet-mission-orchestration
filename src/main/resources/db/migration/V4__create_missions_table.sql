CREATE TABLE missions (

    id UUID PRIMARY KEY,

    mission_type VARCHAR(30) NOT NULL
        CHECK (mission_type IN (
            'PICKUP',
            'DELIVERY',
            'INSPECTION',
            'RETURN_TO_BASE'
        )),

    priority VARCHAR(20) NOT NULL
        CHECK (priority IN (
            'LOW',
            'MEDIUM',
            'HIGH',
            'CRITICAL'
        )),

    required_robot_type VARCHAR(20) NOT NULL
        CHECK (required_robot_type IN (
            'PICKER',
            'CARRIER',
            'INSPECTION'
        )),

    pickup_x DOUBLE PRECISION NOT NULL,
    pickup_y DOUBLE PRECISION NOT NULL,

    drop_x DOUBLE PRECISION NOT NULL,
    drop_y DOUBLE PRECISION NOT NULL,

    status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN (
            'PENDING',
            'ASSIGNED',
            'IN_PROGRESS',
            'COMPLETED',
            'FAILED',
            'CANCELLED'
        )),

    assigned_robot_id UUID,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_mission_robot
        FOREIGN KEY (assigned_robot_id)
        REFERENCES robots(id)
);

CREATE INDEX idx_missions_status
    ON missions(status);

CREATE INDEX idx_missions_priority
    ON missions(priority);

CREATE INDEX idx_missions_assigned_robot
    ON missions(assigned_robot_id);

CREATE INDEX idx_missions_created_at
    ON missions(created_at DESC);