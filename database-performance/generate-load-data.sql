-- Run against a local benchmark database with the root project's
-- Flyway migrations applied.
-- Generates 100 robots, 100,000 telemetry rows, and 10,000 missions.
-- Fixed IDs and conflict checks prevent duplicate rows on reruns.

BEGIN;

INSERT INTO robots (
    id, name, type, battery_percentage, location_x, location_y,
    status, last_heartbeat, current_session_id
)
SELECT
    md5('robot-fleet-benchmark:robot:' || n)::uuid,
    'Benchmark Robot ' || n,
    (ARRAY['PICKER', 'CARRIER', 'INSPECTION'])[1 + (n - 1) % 3],
    50 + n % 51,
    n % 50,
    n % 40,
    'IDLE',
    CURRENT_TIMESTAMP,
    md5('robot-fleet-benchmark:session:' || n)::uuid
FROM generate_series(1, 100) AS robot(n)
ON CONFLICT (id) DO NOTHING;

INSERT INTO robot_telemetry (
    robot_id, session_id, sequence_number, battery_percentage,
    location_x, location_y, speed, health_status, telemetry_timestamp
)
SELECT
    md5('robot-fleet-benchmark:robot:' || n)::uuid,
    md5('robot-fleet-benchmark:session:' || n)::uuid,
    seq,
    50 + (n + seq) % 51,
    (n + seq) % 100,
    (n * 2 + seq) % 100,
    (seq % 10) / 10.0,
    'HEALTHY',
    CURRENT_TIMESTAMP - (1000 - seq) * INTERVAL '1 second'
FROM generate_series(1, 100) AS robot(n)
CROSS JOIN generate_series(1, 1000) AS sample(seq)
ON CONFLICT (robot_id, session_id, sequence_number) DO NOTHING;

INSERT INTO missions (
    id, mission_type, priority, required_robot_type,
    pickup_x, pickup_y, drop_x, drop_y, status
)
SELECT
    md5('robot-fleet-benchmark:mission:' || n)::uuid,
    (ARRAY['PICKUP', 'DELIVERY', 'INSPECTION', 'RETURN_TO_BASE'])[1 + (n - 1) % 4],
    (ARRAY['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'])[1 + (n - 1) % 4],
    (ARRAY['PICKER', 'CARRIER', 'INSPECTION'])[1 + (n - 1) % 3],
    n % 100,
    (n * 3) % 100,
    (n + 20) % 100,
    (n * 3 + 20) % 100,
    'PENDING'
FROM generate_series(1, 10000) AS mission(n)
ON CONFLICT (id) DO NOTHING;

COMMIT;

ANALYZE robots;
ANALYZE robot_telemetry;
ANALYZE missions;