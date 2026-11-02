CREATE TABLE mission_events (
    id UUID PRIMARY KEY,

    mission_id UUID NOT NULL,

    previous_status VARCHAR(30),

    new_status VARCHAR(30) NOT NULL,

    changed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    actor VARCHAR(100),

    reason TEXT,

    CONSTRAINT fk_mission_events_mission
        FOREIGN KEY (mission_id)
        REFERENCES missions(id),

    CONSTRAINT chk_mission_event_new_status
        CHECK (new_status IN (
            'PENDING',
            'ASSIGNED',
            'IN_PROGRESS',
            'COMPLETED',
            'FAILED',
            'CANCELLED'
        )),

    CONSTRAINT chk_mission_event_previous_status
        CHECK (
            previous_status IS NULL OR previous_status IN (
                'PENDING',
                'ASSIGNED',
                'IN_PROGRESS',
                'COMPLETED',
                'FAILED',
                'CANCELLED'
            )
        )
);

CREATE INDEX idx_mission_events_mission_id
ON mission_events(mission_id);

CREATE INDEX idx_mission_events_changed_at
ON mission_events(changed_at);