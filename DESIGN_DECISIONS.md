# Design Decisions

## 1. Service boundaries

Fleet Service owns robot registration, robot state, telemetry, heartbeats,
availability, and reservations. Mission Service owns missions, scheduling,
execution events, assignment operations, and assignment history.

Each service owns a PostgreSQL database. Mission Service uses Fleet Service REST
APIs to check availability, reserve a robot, confirm a reservation, and release
it. Each service commits changes within its own database.

## 2. Telemetry ordering and duplicate detection

A telemetry message is identified by `(robot_id, session_id, sequence_number)`.
The database enforces uniqueness on these three columns. A new session lets a
robot restart its sequence counter after a reboot.

For messages arriving in the order `102, 105, 103, 105` in the active session:

1. Sequence 102 is stored and updates the robot's latest state.
2. Sequence 105 is stored and advances the latest state.
3. Sequence 103 is stored as history. The latest state stays at sequence 105.
4. An identical retry of 105 returns the original telemetry ID. Reusing 105
   with a different payload returns HTTP 409.

Telemetry from a previous session can contribute to history. Only a newer
sequence from the active session can update the robot's current state.

## 3. Mission scheduling and concurrency

The scheduler examines pending missions in priority order: CRITICAL, HIGH,
MEDIUM, then LOW. Within a priority, it examines older missions first and
uses the mission ID to break ties.

A candidate must match the required robot type, be IDLE, have at least 25%
battery, have a fresh heartbeat within 30 seconds, have no current mission,
and be clear of safety hold. The scheduler prefers the eligible robot closest
to the pickup point. It compares squared Euclidean distances because they
give the same ordering without a square-root calculation.

Two schedulers can observe the same available robot. The availability check
helps choose a candidate. The reservation determines who can claim it.
Fleet's PostgreSQL partial unique indexes allow at most one active reservation
per robot and one per mission across RESERVED and CONFIRMED reservations.

## 4. Distributed assignment and crash recovery

Mission Service persists an assignment operation before asking Fleet Service
to reserve the robot. It records the reservation ID and uses the confirmed
reservation to finalize the mission assignment.

If Mission Service stops after Fleet reserves the robot, the pending operation
survives the restart. A recovery worker can claim it after the previous worker's
lease expires. Retrying the reservation with the same idempotency key lets Fleet
recognize the original request. An unconfirmed reservation also has an expiry
time, so it cannot hold the robot indefinitely.

If Fleet confirms a reservation but Mission Service can no longer finalize the
assignment, Mission Service records a release operation. The worker retries
that operation until the reservation is released. This handles the interval
between the two services' database commits.

## 5. Robot disconnection and delayed completion

When a valid heartbeat becomes more than 30 seconds old, Fleet's offline check
marks the robot OFFLINE and places it on safety hold. Mission Service moves an
assigned or executing mission to RECOVERY_PENDING when Fleet explicitly reports
its assigned robot OFFLINE. A Fleet connection error alone does not establish
that the robot disconnected.

A fresh heartbeat after reconnection does not clear the hold. An operator must
confirm that the robot is physically safe before it becomes eligible again.
This matters because a disconnected robot may still be executing its old task.

A completion request includes the robot ID and assignment generation. After
reassignment, a delayed completion from the previous robot receives HTTP 409.
The current assignment stays in progress.

## 6. Idempotent API requests

Mission Service stores the operation scope, idempotency key, request hash,
response status, response body, and resource ID in PostgreSQL. A unique
constraint covers `(operation_scope, idempotency_key)`.

For mission creation, the key claim, mission, creation event, and stored
response share a transaction. If two requests use the same key concurrently,
PostgreSQL resolves the competing inserts. Once the first transaction commits,
an identical retry returns the stored result. A retry with a different payload
receives HTTP 409.

## 7. Database performance

The latest-telemetry query uses an index on
`(robot_id, telemetry_timestamp DESC, id DESC)`. For each robot, a lateral
join reads the first matching index entry with `LIMIT 1`. The ID gives a
deterministic result when timestamps are equal.

This index adds storage and work on telemetry inserts. In return, the
latest-per-robot query can avoid scanning the full telemetry history.
The measured query plans are in `database-performance/DATABASE_PERFORMANCE.md`.

## 8. Verification

Fleet unit tests cover duplicate and out-of-order telemetry, heartbeat sequence
validation, and offline safety holds. Mission unit tests cover scheduling,
Fleet unavailability, invalid cancellation, disconnection recovery, and
rejection of delayed completion after reassignment.

The Fleet Service and Mission Service test suites passed, including integration
tests that use PostgreSQL through Testcontainers.

| Service | Tests passed |
| --- | ---: |
| Fleet Service | 9 of 9 |
| Mission Service | 23 of 23 |