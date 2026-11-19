CREATE TABLE missions (
    id UUID PRIMARY KEY,

    mission_type VARCHAR(30) NOT NULL,
    priority VARCHAR(20) NOT NULL,
    required_robot_type VARCHAR(20) NOT NULL,

    pickup_x DOUBLE PRECISION NOT NULL,
    pickup_y DOUBLE PRECISION NOT NULL,
    drop_x DOUBLE PRECISION NOT NULL,
    drop_y DOUBLE PRECISION NOT NULL,

    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',

    -- Robot records belong to Fleet Service; this is an external reference.
    assigned_robot_id UUID,

    assignment_generation BIGINT NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT ck_missions_type
        CHECK (
            mission_type IN (
                'PICKUP',
                'DELIVERY',
                'INSPECTION',
                'RETURN_TO_BASE'
            )
        ),

    CONSTRAINT ck_missions_priority
        CHECK (
            priority IN ('CRITICAL', 'HIGH', 'MEDIUM', 'LOW')
        ),

    CONSTRAINT ck_missions_required_robot_type
        CHECK (
            required_robot_type IN ('PICKER', 'CARRIER', 'INSPECTION')
        ),

    CONSTRAINT ck_missions_status
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
        ),

    CONSTRAINT ck_missions_coordinates
        CHECK (
            pickup_x BETWEEN -100000.0 AND 100000.0
            AND pickup_y BETWEEN -100000.0 AND 100000.0
            AND drop_x BETWEEN -100000.0 AND 100000.0
            AND drop_y BETWEEN -100000.0 AND 100000.0
        ),

    CONSTRAINT ck_missions_assignment_generation
        CHECK (assignment_generation >= 0),

    CONSTRAINT ck_missions_version
        CHECK (version >= 0),

    CONSTRAINT ck_missions_assignment_state
        CHECK (
            (
                status = 'PENDING'
                AND assigned_robot_id IS NULL
            )
            OR (
                status IN (
                    'ASSIGNED',
                    'IN_PROGRESS',
                    'RECOVERY_PENDING',
                    'COMPLETED'
                )
                AND assigned_robot_id IS NOT NULL
                AND assignment_generation > 0
            )
            OR status IN ('FAILED', 'CANCELLED')
        )
);

-- Recovery retains ownership until the safety workflow releases it.
CREATE UNIQUE INDEX uq_missions_active_robot
    ON missions (assigned_robot_id)
    WHERE status IN (
        'ASSIGNED',
        'IN_PROGRESS',
        'RECOVERY_PENDING'
    );

CREATE INDEX idx_missions_status_created
    ON missions (status, created_at DESC, id);

CREATE INDEX idx_missions_robot_created
    ON missions (assigned_robot_id, created_at DESC, id)
    WHERE assigned_robot_id IS NOT NULL;

-- The scheduler query will use this exact priority ordering.
CREATE INDEX idx_missions_pending_schedule
    ON missions (
        (
            CASE priority
                WHEN 'CRITICAL' THEN 0
                WHEN 'HIGH' THEN 1
                WHEN 'MEDIUM' THEN 2
                WHEN 'LOW' THEN 3
            END
        ),
        created_at,
        id
    )
    WHERE status = 'PENDING';


CREATE TABLE mission_assignments (
    id UUID PRIMARY KEY,
    mission_id UUID NOT NULL,

    -- Both identifiers reference resources owned by Fleet Service.
    robot_id UUID NOT NULL,
    reservation_id UUID NOT NULL,

    assignment_generation BIGINT NOT NULL,
    assigned_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    assigned_by VARCHAR(100) NOT NULL,

    ended_at TIMESTAMPTZ,
    end_reason VARCHAR(255),

    CONSTRAINT fk_mission_assignments_mission
        FOREIGN KEY (mission_id)
        REFERENCES missions (id)
        ON DELETE RESTRICT,

    CONSTRAINT uq_mission_assignments_reservation
        UNIQUE (reservation_id),

    CONSTRAINT uq_mission_assignments_generation
        UNIQUE (mission_id, assignment_generation),

    CONSTRAINT ck_mission_assignments_generation
        CHECK (assignment_generation > 0),

    CONSTRAINT ck_mission_assignments_actor
        CHECK (BTRIM(assigned_by) <> ''),

    CONSTRAINT ck_mission_assignments_end_state
        CHECK (
            (
                ended_at IS NULL
                AND end_reason IS NULL
            )
            OR (
                ended_at IS NOT NULL
                AND end_reason IS NOT NULL
                AND BTRIM(end_reason) <> ''
                AND ended_at >= assigned_at
            )
        )
);

CREATE UNIQUE INDEX uq_mission_assignments_active_mission
    ON mission_assignments (mission_id)
    WHERE ended_at IS NULL;

CREATE UNIQUE INDEX uq_mission_assignments_active_robot
    ON mission_assignments (robot_id)
    WHERE ended_at IS NULL;

CREATE INDEX idx_mission_assignments_mission_history
    ON mission_assignments (mission_id, assigned_at DESC, id);

CREATE INDEX idx_mission_assignments_robot_history
    ON mission_assignments (robot_id, assigned_at DESC, id);


CREATE TABLE mission_events (
    id UUID PRIMARY KEY,
    mission_id UUID NOT NULL,

    previous_status VARCHAR(30),
    new_status VARCHAR(30) NOT NULL,

    assignment_generation BIGINT NOT NULL DEFAULT 0,
    changed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    actor VARCHAR(100) NOT NULL,
    reason TEXT NOT NULL,
    correlation_id VARCHAR(100),

    CONSTRAINT fk_mission_events_mission
        FOREIGN KEY (mission_id)
        REFERENCES missions (id)
        ON DELETE RESTRICT,

    CONSTRAINT ck_mission_events_previous_status
        CHECK (
            previous_status IS NULL
            OR previous_status IN (
                'PENDING',
                'ASSIGNED',
                'IN_PROGRESS',
                'RECOVERY_PENDING',
                'COMPLETED',
                'FAILED',
                'CANCELLED'
            )
        ),

    CONSTRAINT ck_mission_events_new_status
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
        ),

    CONSTRAINT ck_mission_events_status_change
        CHECK (
            (
                previous_status IS NULL
                AND new_status = 'PENDING'
            )
            OR (
                previous_status IS NOT NULL
                AND previous_status <> new_status
            )
        ),

    CONSTRAINT ck_mission_events_generation
        CHECK (assignment_generation >= 0),

    CONSTRAINT ck_mission_events_actor
        CHECK (BTRIM(actor) <> ''),

    CONSTRAINT ck_mission_events_reason
        CHECK (BTRIM(reason) <> '')
);

CREATE INDEX idx_mission_events_history
    ON mission_events (mission_id, changed_at, id);

-- Protect event history against accidental SQL updates and deletion.
CREATE FUNCTION reject_mission_event_mutation()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'Mission events are append-only'
        USING ERRCODE = '55000';

    RETURN NULL;
END;
$$;

CREATE TRIGGER trg_mission_events_immutable
    BEFORE UPDATE OR DELETE OR TRUNCATE
    ON mission_events
    FOR EACH STATEMENT
    EXECUTE FUNCTION reject_mission_event_mutation();


CREATE TABLE idempotency_records (
    id UUID PRIMARY KEY,

    operation_scope VARCHAR(200) NOT NULL,
    idempotency_key VARCHAR(255) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,

    response_status INTEGER,
    response_body TEXT,
    resource_id UUID,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMPTZ,

    CONSTRAINT uq_idempotency_scope_key
        UNIQUE (operation_scope, idempotency_key),

    CONSTRAINT ck_idempotency_scope
        CHECK (BTRIM(operation_scope) <> ''),

    CONSTRAINT ck_idempotency_key
        CHECK (BTRIM(idempotency_key) <> ''),

    CONSTRAINT ck_idempotency_request_hash
        CHECK (request_hash ~ '^[0-9a-f]{64}$'),

    CONSTRAINT ck_idempotency_response
        CHECK (
            (
                response_status IS NULL
                AND response_body IS NULL
            )
            OR (
                response_status IS NOT NULL
                AND response_body IS NOT NULL
                AND response_status BETWEEN 200 AND 599
            )
        ),

    CONSTRAINT ck_idempotency_expiry
        CHECK (
            expires_at IS NULL
            OR expires_at > created_at
        )
);

CREATE INDEX idx_idempotency_expiry
    ON idempotency_records (expires_at)
    WHERE expires_at IS NOT NULL;