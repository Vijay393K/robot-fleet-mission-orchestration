# Robot Fleet System Architecture

Docker Compose runs Fleet Service and Mission Service as separate Spring Boot applications. Each service owns its PostgreSQL database. Mission Service calls Fleet Service over REST to check robot availability and manage reservations.

```mermaid
flowchart TD
    Client["API client using Swagger UI or Postman"]

    subgraph Local["Docker Compose"]
        Fleet["Fleet Service · port 8081"]
        Mission["Mission Service · port 8082"]
        FleetDB[("Fleet PostgreSQL")]
        MissionDB[("Mission PostgreSQL")]

        Mission -->|"REST: availability and reservations"| Fleet
        Fleet --> FleetDB
        Mission --> MissionDB
    end

    Client --> Fleet
    Client --> Mission
```

- **Fleet Service** manages robots, telemetry, heartbeats, availability, and reservations.
- **Mission Service** manages mission creation, scheduling, lifecycle, execution events, and assignment history.