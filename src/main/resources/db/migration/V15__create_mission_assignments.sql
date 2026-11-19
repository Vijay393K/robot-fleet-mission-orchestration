
CREATE TABLE mission_assignments (
    id UUID PRIMARY KEY,

    mission_id UUID NOT NULL,
    robot_id UUID NOT NULL,

    assignment_generation BIGINT NOT NULL,

    assigned_at TIMESTAMPTZ NOT NULL,
    ended_at TIMESTAMPTZ,

    assigned_by VARCHAR(100) NOT NULL,
    end_reason VARCHAR(255),

    CONSTRAINT fk_mission_assignments_mission
        FOREIGN KEY (mission_id)
        REFERENCES missions(id),

    CONSTRAINT fk_mission_assignments_robot
        FOREIGN KEY (robot_id)
        REFERENCES robots(id),

    CONSTRAINT ck_mission_assignments_generation
        CHECK (assignment_generation >= 0),

    CONSTRAINT ck_mission_assignments_end_state
        CHECK (
            (ended_at IS NULL AND end_reason IS NULL)
            OR
            (ended_at IS NOT NULL AND end_reason IS NOT NULL)
        )
);

CREATE UNIQUE INDEX uq_active_assignment_per_mission
ON mission_assignments(mission_id)
WHERE ended_at IS NULL;

CREATE UNIQUE INDEX uq_active_assignment_per_robot
ON mission_assignments(robot_id)
WHERE ended_at IS NULL;

CREATE INDEX idx_mission_assignments_mission_history
ON mission_assignments(mission_id, assigned_at DESC);

CREATE INDEX idx_mission_assignments_robot_history
ON mission_assignments(robot_id, assigned_at DESC);




INSERT INTO mission_assignments (
    id,
    mission_id,
    robot_id,
    assignment_generation,
    assigned_at,
    ended_at,
    assigned_by,
    end_reason
)
SELECT
    gen_random_uuid(),
    id,
    assigned_robot_id,
    assignment_generation,
    created_at,
    updated_at,
    'LEGACY',
    'LEGACY_COMPLETED_ESTIMATED'
FROM missions
WHERE status = 'COMPLETED'
  AND assigned_robot_id IS NOT NULL;



INSERT INTO mission_assignments (
    id,
    mission_id,
    robot_id,
    assignment_generation,
    assigned_at,
    ended_at,
    assigned_by,
    end_reason
)
SELECT
    gen_random_uuid(),
    id,
    assigned_robot_id,
    assignment_generation,
    created_at,
    NULL,
    'LEGACY',
    NULL
FROM missions
WHERE status = 'RECOVERY_PENDING'
  AND assigned_robot_id IS NOT NULL;
