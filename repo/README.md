# Retail Campaign Governance & Content Integrity

## Run
```bash
docker compose up --build
```
Open http://localhost:8080

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
