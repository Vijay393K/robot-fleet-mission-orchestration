CREATE UNIQUE INDEX uq_active_mission_per_robot
ON missions (assigned_robot_id)
WHERE assigned_robot_id IS NOT NULL
  AND status IN ('ASSIGNED', 'IN_PROGRESS');