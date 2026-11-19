CREATE TABLE mission_assignment_operations (
    id UUID PRIMARY KEY,
    mission_id UUID NOT NULL,
    robot_id UUID NOT NULL,
    assignment_generation BIGINT NOT NULL,

    -- Reuse the operation UUID as Fleet's Idempotency-Key on every reserve retry.
    status VARCHAR(30) NOT NULL DEFAULT 'RESERVE_PENDING',

    reservation_id UUID,
    reservation_expires_at TIMESTAMPTZ,

    requested_by VARCHAR(100) NOT NULL,
    reason TEXT NOT NULL,
    correlation_id VARCHAR(100),

    attempt_count BIGINT NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ,
    last_attempt_at TIMESTAMPTZ,
    last_error_code VARCHAR(100),
    last_error_message VARCHAR(1000),

    -- A new token identifies each worker claim, including reclaimed work.
    lease_token UUID,
    lease_expires_at TIMESTAMPTZ,

    version BIGINT NOT NULL DEFAULT 0,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finished_at TIMESTAMPTZ,

    CONSTRAINT fk_assignment_operations_mission
        FOREIGN KEY (mission_id)
        REFERENCES missions (id)
        ON DELETE RESTRICT,

    CONSTRAINT uq_assignment_operations_generation
        UNIQUE (mission_id, assignment_generation),

    CONSTRAINT uq_assignment_operations_reservation
        UNIQUE (reservation_id),

    CONSTRAINT ck_assignment_operations_generation
        CHECK (assignment_generation > 0),

    CONSTRAINT ck_assignment_operations_status
        CHECK (
            status IN (
                'RESERVE_PENDING',
                'CONFIRM_PENDING',
                'CONFIRMED',
                'RELEASE_PENDING',
                'RELEASED',
                'ABORTED'
            )
        ),

    CONSTRAINT ck_assignment_operations_reservation
        CHECK (
            (
                reservation_id IS NULL
                AND reservation_expires_at IS NULL
            )
            OR (
                reservation_id IS NOT NULL
                AND reservation_expires_at IS NOT NULL
            )
        ),

    CONSTRAINT ck_assignment_operations_required_reservation
        CHECK (
            status NOT IN (
                'CONFIRM_PENDING',
                'CONFIRMED',
                'RELEASED'
            )
            OR reservation_id IS NOT NULL
        ),

    CONSTRAINT ck_assignment_operations_actor
        CHECK (BTRIM(requested_by) <> ''),

    CONSTRAINT ck_assignment_operations_reason
        CHECK (BTRIM(reason) <> ''),

    CONSTRAINT ck_assignment_operations_attempt_count
        CHECK (attempt_count >= 0),

    CONSTRAINT ck_assignment_operations_version
        CHECK (version >= 0),

    CONSTRAINT ck_assignment_operations_retry_schedule
        CHECK (
            (
                status IN (
                    'RESERVE_PENDING',
                    'CONFIRM_PENDING',
                    'RELEASE_PENDING'
                )
                AND next_attempt_at IS NOT NULL
            )
            OR (
                status IN ('CONFIRMED', 'RELEASED', 'ABORTED')
                AND next_attempt_at IS NULL
            )
        ),

    CONSTRAINT ck_assignment_operations_lease
        CHECK (
            (
                lease_token IS NULL
                AND lease_expires_at IS NULL
            )
            OR (
                lease_token IS NOT NULL
                AND lease_expires_at IS NOT NULL
                AND status IN (
                    'RESERVE_PENDING',
                    'CONFIRM_PENDING',
                    'RELEASE_PENDING'
                )
            )
        ),

    CONSTRAINT ck_assignment_operations_finished
        CHECK (
            (
                status IN ('RELEASED', 'ABORTED')
                AND finished_at IS NOT NULL
                AND finished_at >= created_at
            )
            OR (
                status NOT IN ('RELEASED', 'ABORTED')
                AND finished_at IS NULL
            )
        )
);

-- Keep ownership while confirmation or release remains unresolved.
CREATE UNIQUE INDEX uq_assignment_operations_active_mission
    ON mission_assignment_operations (mission_id)
    WHERE status IN (
        'RESERVE_PENDING',
        'CONFIRM_PENDING',
        'CONFIRMED',
        'RELEASE_PENDING'
    );

CREATE UNIQUE INDEX uq_assignment_operations_active_robot
    ON mission_assignment_operations (robot_id)
    WHERE status IN (
        'RESERVE_PENDING',
        'CONFIRM_PENDING',
        'CONFIRMED',
        'RELEASE_PENDING'
    );

CREATE INDEX idx_assignment_operations_due
    ON mission_assignment_operations (next_attempt_at, id)
    WHERE status IN (
        'RESERVE_PENDING',
        'CONFIRM_PENDING',
        'RELEASE_PENDING'
    );

CREATE INDEX idx_assignment_operations_history
    ON mission_assignment_operations (mission_id, created_at DESC, id);