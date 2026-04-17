# fullstack

A full-stack retail campaign governance and content integrity platform. Marketing operations teams use it to create campaigns and coupons, submit them for dual approval, manage uploaded campaign materials, detect near-duplicate content, view analytics, and maintain an immutable audit trail. Administrators manage users, roles, anomaly alerts, and database backups. All business logic enforces role-based access, rate limiting, anti-replay nonce validation, and AES-256-GCM encryption at rest.

## Architecture and Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 17 |
| Backend framework | Spring Boot 3.2 |
| Frontend rendering | Thymeleaf 3 + Bootstrap 5 + HTMX |
| Database | MySQL 8.0 (Flyway migrations only) |
| Security | Spring Security 6 (form login, CSRF, rate limiting, nonce validation, HMAC signing) |
| Testing | JUnit 5 + Mockito + TestRestTemplate + Testcontainers + Jest + Playwright |
| Infrastructure | Docker + Docker Compose |

## Project Structure

```
repo/
├── src/
│   ├── main/
│   │   ├── java/com/meridian/retail/
│   │   │   ├── anomaly/          # Anomaly detection + change event recording
│   │   │   ├── audit/            # Immutable audit log service
│   │   │   ├── backup/           # Scheduled mysqldump + restore
│   │   │   ├── config/           # SecurityConfig, WebConfig
│   │   │   ├── controller/       # 14 HTTP controllers (thin layer)
│   │   │   ├── dto/              # Request/response DTOs
│   │   │   ├── entity/           # 32 JPA entities + enums
│   │   │   ├── integrity/        # SimHash dedup, fingerprinting, merge
│   │   │   ├── repository/       # Spring Data JPA repositories
│   │   │   ├── security/         # Filters, lockout, CAPTCHA, nonce, encryption
│   │   │   └── service/          # All business rules enforced here
│   │   └── resources/
│   │       ├── db/migration/     # V1–V15 Flyway SQL migrations + seed data
│   │       ├── static/js/        # upload.js, nonce-form.js
│   │       └── templates/        # Thymeleaf HTML templates
│   └── test/
│       ├── java/com/meridian/retail/
│       │   ├── unit/             # JUnit 5 + Mockito, no database
│       │   └── api/              # TestRestTemplate against real MySQL (Testcontainers)
│       ├── frontend/unit_tests/  # Jest tests for static JS files
│       └── e2e/                  # Playwright end-to-end tests (headless)
├── Dockerfile                    # Multi-stage production image
├── Dockerfile.test               # Maven test runner image
├── docker-compose.yml
├── pom.xml
└── run_tests.sh
```

## Prerequisites

- Docker
- Docker Compose

No local Java, Maven, Node, or any other tool is required.

## Running the Application

```bash
docker compose up --build
```

```bash
docker-compose up --build
```

Open http://localhost:8080

## Testing

Run the full test suite (backend unit, backend API, frontend JS, and Playwright e2e) with a single command:

```bash
./run_tests.sh
```

The script orchestrates all four suites through Docker. No local tooling needed. Exit code 0 means all suites passed.

## Verification

After starting the app with `docker compose up --build`, confirm it is working:

1. Open http://localhost:8080 — confirm the login page loads with a username and password form.
2. Log in with `admin` / `Admin@Retail2024!` — confirm you reach the Admin Dashboard showing system statistics.
3. Log out, then log in with each other role and confirm the correct landing page:
   - `ops` / `Ops@Retail2024!` → Campaign list
   - `reviewer` / `Review@Retail2024!` → Approval Queue
   - `finance` / `Finance@Retail2024!` → Analytics Dashboard
   - `cs` / `CsUser@Retail2024!` → Coupon Lookup
4. Visit http://localhost:8080/health — confirm the response is `{"status":"UP","service":"retail-campaign"}`.
5. While logged in as `ops`, try to access http://localhost:8080/admin/dashboard — confirm it redirects back to the login page or shows a 403 Forbidden response.

## Seeded Credentials

| Role | Username | Password |
|---|---|---|
| Administrator | admin | Admin@Retail2024! |
| Operations | ops | Ops@Retail2024! |
| Reviewer | reviewer | Review@Retail2024! |
| Finance | finance | Finance@Retail2024! |
| Customer Service | cs | CsUser@Retail2024! |
