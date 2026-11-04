ALTER TABLE missions
DROP CONSTRAINT missions_status_check;

ALTER TABLE missions
ADD CONSTRAINT missions_status_check
CHECK (
    status IN (
        'PENDING',
        'ASSIGNED',
        'IN_PROGRESS',
        'RECOVERY_PENDING',
        'COMPLETED',
        'FAILED',
        'CANCELLED'
    )
);