# Robot Fleet Database ER Diagram

## Fleet database

```mermaid
erDiagram
    robots ||..o{ robot_telemetry : records
    robots ||..o{ robot_heartbeats : sends
    robots ||..o{ robot_reservations : has

    robots {
        uuid id PK
        varchar name
        varchar type
        varchar status
        integer battery_percentage
        timestamptz last_heartbeat
        uuid current_mission_id
        boolean safety_hold
    }

    robot_telemetry {
        bigint id PK
        uuid robot_id FK
        uuid session_id
        bigint sequence_number
        timestamptz telemetry_timestamp
    }

    robot_heartbeats {
        bigint id PK
        uuid robot_id FK
        uuid session_id
        bigint sequence_number
        timestamptz received_at
    }

    robot_reservations {
        uuid id PK
        uuid robot_id FK
        uuid mission_id
        varchar status
        timestamptz reserved_at
        timestamptz expires_at
    }
```

`robot_reservations.mission_id` and `robots.current_mission_id` are logical references to Mission Service records. Fleet Service and Mission Service each own a separate PostgreSQL database.

## Mission database

```mermaid
erDiagram
    missions ||..o{ mission_assignments : "has assignment history"
    missions ||..o{ mission_events : "records state changes"
    missions ||..o{ mission_assignment_operations : "tracks assignment operations"

    missions {
        uuid id PK
        varchar mission_type
        varchar priority
        varchar required_robot_type
        varchar status
        uuid assigned_robot_id
        bigint assignment_generation
        timestamptz created_at
    }

    mission_assignments {
        uuid id PK
        uuid mission_id FK
        uuid robot_id
        uuid reservation_id UK
        bigint assignment_generation
        timestamptz assigned_at
        timestamptz ended_at
    }

    mission_events {
        uuid id PK
        uuid mission_id FK
        varchar previous_status
        varchar new_status
        timestamptz changed_at
        varchar actor
        text reason
    }

    mission_assignment_operations {
        uuid id PK
        uuid mission_id FK
        uuid robot_id
        bigint assignment_generation
        varchar status
        uuid reservation_id UK
        timestamptz next_attempt_at
    }

    idempotency_records {
        uuid id PK
        varchar operation_scope
        varchar idempotency_key
        varchar request_hash
        integer response_status
        uuid resource_id
        timestamptz expires_at
    }
```

- `mission_id` references `missions.id` in the assignment, event, and operation tables.
- Robot and reservation IDs are logical references to resources owned by Fleet Service.
- The assignment and operation tables each enforce uniqueness on `(mission_id, assignment_generation)`.
- The idempotency table enforces uniqueness on `(operation_scope, idempotency_key)`.