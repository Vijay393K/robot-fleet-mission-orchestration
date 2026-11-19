## Local setup

### Prerequisite

Install and start Docker Desktop with Docker Compose available.

### Start the services

Open PowerShell in the project root, where `docker-compose.yml` is located. Create the local environment file if it does not already exist, then build and start the services.

```powershell
if (-not (Test-Path .env)) { Copy-Item .env.example .env }
docker compose up --build -d
docker compose ps
```

Wait until both services and both PostgreSQL containers report `healthy`. Each service applies its own Flyway migrations at startup.

### API documentation

| Service | Swagger UI | OpenAPI JSON |
| --- | --- | --- |
| Fleet Service | [Swagger UI](http://localhost:8081/swagger-ui.html) | [OpenAPI JSON](http://localhost:8081/v3/api-docs) |
| Mission Service | [Swagger UI](http://localhost:8082/swagger-ui.html) | [OpenAPI JSON](http://localhost:8082/v3/api-docs) |

### Stop the services

```powershell
docker compose down
```

### Project documentation

- [High-level architecture diagram](docs/architecture.md)
- [Database ER diagram](docs/er-diagram.md)
- [Database performance results](database-performance/DATABASE_PERFORMANCE.md)
- [Load test data generation script](database-performance/generate-load-data.sql)
- [Design decisions](DESIGN_DECISIONS.md)