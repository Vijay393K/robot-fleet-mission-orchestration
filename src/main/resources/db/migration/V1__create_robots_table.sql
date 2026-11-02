
CREATE TABLE robots (
    id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    type VARCHAR(20) NOT NULL
        CHECK (type IN ('PICKER', 'CARRIER', 'INSPECTION')),
    battery_percentage INTEGER NOT NULL
        CHECK (battery_percentage BETWEEN 0 AND 100),
    location_x DOUBLE PRECISION NOT NULL,
    location_y DOUBLE PRECISION NOT NULL,
    status VARCHAR(20) NOT NULL
        CHECK (status IN ('IDLE', 'BUSY', 'CHARGING', 'OFFLINE', 'ERROR')),
    last_heartbeat TIMESTAMPTZ,
    current_mission_id UUID
);
