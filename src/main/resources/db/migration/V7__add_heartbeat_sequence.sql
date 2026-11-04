ALTER TABLE robots
ADD COLUMN last_heartbeat_sequence BIGINT NOT NULL DEFAULT 0;