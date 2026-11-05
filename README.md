## 3. Robot Heartbeat & Failure Detection

### 3.1 Heartbeat API

Robots periodically send heartbeat requests to the backend to indicate that they are connected and operational.

**Endpoint:**

`POST /api/v1/robots/{robotId}/heartbeat`

**Request Body:**

```json
{
  "sequenceNumber": 10
}
```

Each robot maintains a monotonically increasing heartbeat sequence number.

The backend stores the last accepted sequence number in the `last_heartbeat_sequence` column of the `robots` table.

A heartbeat is accepted only when its sequence number is greater than the previously accepted value. Duplicate, outdated, and out-of-order heartbeat requests are rejected with HTTP `409 Conflict`.

For every valid heartbeat, the backend updates the robot's `last_heartbeat` timestamp.

This mechanism ensures that old or repeated heartbeat requests cannot make a disconnected robot appear active.

### 3.2 Background Failure Detection

The `RobotOfflineScheduler` runs periodically every 10 seconds and invokes `RobotFailureRecoveryService` to check robot connectivity.

The failure detection threshold is 30 seconds.

When a robot has not sent a valid heartbeat for more than 30 seconds, the backend:

1. Changes its status to `OFFLINE`.
2. Sets its `safety_hold` flag to `true`.
3. Checks whether it has an assigned or executing mission.
4. Moves the affected mission into `RECOVERY_PENDING`.
5. Records a disconnection event in the `mission_events` table with `SYSTEM` as the actor.
6. Excludes the disconnected robot from future mission assignments.

The scheduler uses database locking during recovery to reduce the risk of concurrent updates to the same robot.

### 3.3 Mission Recovery and Reassignment

An interrupted mission is moved to `RECOVERY_PENDING` rather than being immediately marked as `FAILED` or `COMPLETED`.

This status indicates that the mission requires recovery before it can be assigned again.

The backend does not immediately reassign the mission while the original robot may still be physically executing it.

Reassignment is permitted after the original robot has completed the safety reconciliation process.

Once reconciliation succeeds:

- The previous robot assignment is cleared.
- The robot's current mission reference is cleared.
- The mission moves from `RECOVERY_PENDING` to `PENDING`.
- The robot's safety hold is removed.
- The robot returns to `IDLE`.
- The mission becomes eligible for scheduling and reassignment.

The recovery transition is recorded in the mission event history.

### 3.4 Reconnection and Physical Safety Policy

Reassignment of a mission does not guarantee that the original robot has stopped physically executing its task.

For this reason, the backend implements a safety-hold and operator-reconciliation policy.

**When a disconnected robot reconnects:**

1. It can send valid heartbeat requests using increasing sequence numbers.
2. Its last heartbeat timestamp is updated.
3. It remains `OFFLINE` while `safety_hold` is true.
4. It is excluded from new mission assignments.
5. It cannot automatically resume its previous mission.
6. An operator must physically verify that the robot has stopped and is safe.
7. The operator submits a reconciliation request to release the safety hold.

**Reconciliation API:**

`POST /api/v1/robots/{robotId}/reconcile`

**Request Body:**

```json
{
  "physicallySafe": true,
  "operator": "Operator",
  "reason": "Physical safety verified; robot confirmed stopped"
}
```

The `RobotReconciliationService` verifies that:

- The robot is under safety hold.
- A recent valid heartbeat has been received.
- The operator has confirmed physical safety.
- Any active mission is in `RECOVERY_PENDING` and belongs to that robot.

After successful verification, the backend releases the robot from safety hold and returns the interrupted mission to `PENDING`.

**Hardware safety limitation:** The current implementation records the operator's safety confirmation. It does not send an emergency-stop command to the physical robot or independently verify its physical state. Therefore, the operator must verify that the robot is stopped before submitting the reconciliation request.

This policy prevents a reconnecting robot from automatically resuming an old mission while that mission may already have been assigned to another robot.

### 3.5 Stale Mission Completion Protection

A critical edge case occurs when Robot A loses connectivity for more than 30 seconds, its mission is recovered and reassigned to Robot B, and Robot A later sends a delayed mission-completion request.

Accepting Robot A's old request could incorrectly complete a mission currently assigned to Robot B.

To prevent this, the backend uses an **assignment generation mechanism**.

The `missions` table contains an `assignment_generation` column.

Every time a mission is assigned to a robot, its assignment generation is incremented.

For example:

| Assignment | Robot | Assignment Generation |
|---|---|---:|
| Original assignment | Robot A | 1 |
| Reassigned mission | Robot B | 2 |

The mission completion API requires the robot ID and assignment generation.

**Endpoint:**

`PUT /api/v1/missions/{missionId}/complete`

**Example Request:**

```json
{
  "robotId": "851a88cb-ecf2-49da-b070-98d83e836694",
  "assignmentGeneration": 2,
  "actor": "Operator",
  "reason": "Mission successfully completed"
}
```

Before accepting a completion request, the backend checks:

1. The mission is currently `IN_PROGRESS`.
2. The supplied assignment generation matches the current generation.
3. The supplied robot ID matches the currently assigned robot.
4. The robot's current mission matches the requested mission.
5. The robot status is `BUSY`.
6. The robot is not under safety hold.

If any validation fails, the request is rejected with HTTP `409 Conflict`.

Therefore, even if Robot A reconnects and submits a delayed completion request with generation `1`, the backend rejects it because the mission's current generation is `2`.

Only Robot B, using the current assignment generation, can complete the reassigned mission.

### 3.6 Failure Recovery Test Results

The following end-to-end scenario was tested successfully using two robots.

**Test scenario:**

1. Robot A was assigned a mission with assignment generation `1`.
2. Robot A stopped sending heartbeats.
3. The backend detected the timeout and marked Robot A as `OFFLINE`.
4. The mission moved into `RECOVERY_PENDING`.
5. Robot A was reconciled after physical safety verification.
6. The mission was reassigned to Robot B with assignment generation `2`.
7. Robot B started executing the mission.
8. Robot A submitted a delayed completion request using generation `1`.
9. The backend rejected Robot A's request with HTTP `409 Conflict`.
10. Robot B submitted a completion request using generation `2`.
11. The backend accepted the request and changed the mission status to `COMPLETED`.

**Verified results:**

| Test Case | Result |
|---|---|
| Heartbeat sequence validation | Passed |
| Robot marked OFFLINE after heartbeat timeout | Passed |
| Mission moved to RECOVERY_PENDING | Passed |
| Robot safety reconciliation | Passed |
| Mission reassigned to another robot | Passed |
| Old completion request rejected with HTTP 409 | Passed |
| Current robot completion accepted with HTTP 200 | Passed |

**Overall Test Result: PASSED**

The test demonstrates that delayed completion requests from a previous robot assignment cannot incorrectly complete a mission after reassignment.