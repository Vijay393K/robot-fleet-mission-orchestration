
ALTER TABLE mission_events
DROP CONSTRAINT chk_mission_event_new_status;

ALTER TABLE mission_events
ADD CONSTRAINT chk_mission_event_new_status
CHECK (
    new_status IN (
        'PENDING',
        'ASSIGNED',
        'IN_PROGRESS',
        'RECOVERY_PENDING',
        'COMPLETED',
        'FAILED',
        'CANCELLED'
    )
);



ALTER TABLE mission_events
DROP CONSTRAINT chk_mission_event_previous_status;

ALTER TABLE mission_events
ADD CONSTRAINT chk_mission_event_previous_status
CHECK (
    previous_status IS NULL OR previous_status IN (
        'PENDING',
        'ASSIGNED',
        'IN_PROGRESS',
        'RECOVERY_PENDING',
        'COMPLETED',
        'FAILED',
        'CANCELLED'
    )
);