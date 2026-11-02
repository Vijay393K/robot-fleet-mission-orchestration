ALTER TABLE robots
ADD COLUMN IF NOT EXISTS current_session_id UUID;

ALTER TABLE robot_telemetry
ADD COLUMN IF NOT EXISTS session_id UUID;

UPDATE robots
SET current_session_id = gen_random_uuid()
WHERE current_session_id IS NULL;

UPDATE robot_telemetry AS t
SET session_id = r.current_session_id
FROM robots AS r
WHERE t.robot_id = r.id
  AND t.session_id IS NULL;

UPDATE robot_telemetry
SET session_id = gen_random_uuid()
WHERE session_id IS NULL;

ALTER TABLE robot_telemetry
ALTER COLUMN session_id SET NOT NULL;

ALTER TABLE robot_telemetry
DROP CONSTRAINT IF EXISTS uq_robot_telemetry_sequence;

ALTER TABLE robot_telemetry
DROP CONSTRAINT IF EXISTS uq_robot_telemetry_session_sequence;

ALTER TABLE robot_telemetry
ADD CONSTRAINT uq_robot_telemetry_session_sequence
UNIQUE (robot_id, session_id, sequence_number);