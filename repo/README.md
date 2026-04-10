# Retail Campaign Governance & Content Integrity

## Run
```bash
docker compose up --build
```
Open http://localhost:8080
(.env is created automatically from .env.example on first run — no manual setup needed)

## Test
```bash
docker compose -f docker-compose.test.yml run --build test
```

## Stop
```bash
docker compose down
```

## Login
| Role | Username | Password |
|---|---|---|
| Administrator | admin | Admin@Retail2024! |
| Operations | ops | Ops@Retail2024! |
| Reviewer | reviewer | Review@Retail2024! |
| Finance | finance | Finance@Retail2024! |
| Customer Service | cs | CsUser@Retail2024! |

## Recovery
1. Stop containers: `docker compose down`
2. Copy backup from named volume `backup-data` or `/app/backups/`
3. Start fresh MySQL: `docker compose up mysql`
4. Restore: `docker exec -i mysql mysql -uretail_user -pretail_pass retail_campaign < backup.sql`
5. Start app: `docker compose up app`

Estimated RTO: under 4 hours on a single server.

## Local Development (Optional — without Docker)

Docker is the recommended and fully supported path. The steps below are for static
reviewers who want a local fallback.

Prerequisites: JDK 17+, Maven 3.8+ (or use the bundled `./mvnw` wrapper), MySQL 8.0+.

```bash
# Point Spring at a local MySQL instance
export SPRING_DATASOURCE_URL=jdbc:mysql://localhost:3306/retail_campaign?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
export SPRING_DATASOURCE_USERNAME=retail_user
export SPRING_DATASOURCE_PASSWORD=retail_pass

# Run tests (uses Testcontainers — needs Docker available for the MySQL container)
./mvnw test

# Start the app
./mvnw spring-boot:run
```

Note: integration tests still require Docker because they use Testcontainers for a real
MySQL database. For a fully offline static review, the source code under `src/` is
self-contained and the Docker compose path is the supported runtime.
