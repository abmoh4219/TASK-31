# CLAUDE.md — Retail Campaign Governance & Content Integrity
# Task ID: TASK-31
# Read SPEC.md + CLAUDE.md + PLAN.md before every single response.

## Read Order (mandatory, every response)
1. SPEC.md — source of truth
2. CLAUDE.md — this file
3. PLAN.md — current task state

## Project Identity

- Name: Retail Campaign Governance & Content Integrity System
- Task ID: TASK-31
- Type: Full-stack, offline-first, on-premise
- Language: Java 17
- Backend: Spring Boot 3.x + Spring Security + Spring Data JPA
- Frontend: Thymeleaf 3 + Bootstrap 5 + HTMX 1.x + Vanilla JS
- Database: MySQL 8 (Flyway migrations only — never ddl-auto=create in any profile)
- Testing: JUnit 5 + Mockito + Spring Boot Test + Testcontainers (real MySQL)
- Infrastructure: Docker + docker-compose

## QA Evaluation — Both Paths Must Pass

> The QA team evaluates this project in TWO ways simultaneously:
> 1. DOCKER RUNTIME: They run `docker compose up --build` and manually test every feature in the browser
> 2. STATIC CODE AUDIT: They read source code for security correctness, business rule implementation, and code quality
>
> This means:
> - The app must ACTUALLY WORK end-to-end in Docker (every page loads, every feature functions, no broken UI)
> - The app must ALSO be clearly and correctly implemented in source code (readable, explicit, traceable)
> - Beautiful, modern, eye-catching UI is a QA acceptance criterion — not optional
> - Every feature in SPEC.md must be both visible in code AND functional in the running app

## Folder Structure (strict — all code inside repo/)

```
TASK-31/                          ← Claude CLI runs here
├── SPEC.md
├── CLAUDE.md
├── PLAN.md
├── docs/                         ← design.md + api-spec.md (Phase 9)
├── sessions/
├── metadata.json
└── repo/                         ← ALL generated code here
    ├── src/
    │   ├── main/
    │   │   ├── java/com/meridian/retail/
    │   │   │   ├── RetailCampaignApplication.java
    │   │   │   ├── config/           ← SecurityConfig, WebConfig, RateLimitConfig
    │   │   │   ├── controller/       ← one controller per module
    │   │   │   ├── service/          ← business logic, all rules enforced here
    │   │   │   ├── repository/       ← Spring Data JPA repositories
    │   │   │   ├── entity/           ← JPA entities
    │   │   │   ├── dto/              ← request/response DTOs
    │   │   │   ├── security/         ← filters, handlers, CAPTCHA, nonce
    │   │   │   ├── audit/            ← AuditLogService, AuditLog entity
    │   │   │   ├── integrity/        ← dedup, SimHash, fingerprint
    │   │   │   ├── storage/          ← file upload, chunked, watermark
    │   │   │   ├── anomaly/          ← change event recording, spike detection
    │   │   │   └── backup/           ← backup scheduler, restore
    │   │   └── resources/
    │   │       ├── templates/        ← Thymeleaf .html files
    │   │       │   ├── layout/       ← base layout with nav
    │   │       │   ├── auth/         ← login, captcha
    │   │       │   ├── campaign/     ← CRUD forms, receipt preview
    │   │       │   ├── approval/     ← approval queue
    │   │       │   ├── upload/       ← file upload page
    │   │       │   ├── content/      ← duplicate grouping, merge
    │   │       │   ├── analytics/    ← dashboard, export
    │   │       │   ├── audit/        ← audit log viewer
    │   │       │   └── admin/        ← user management
    │   │       ├── static/
    │   │       │   ├── css/          ← custom.css
    │   │       │   ├── js/           ← validation.js, upload.js, charts.js
    │   │       │   └── vendor/       ← Bootstrap 5, HTMX, Chart.js (local copies)
    │   │       ├── db/migration/     ← V1__init.sql, V2__seed.sql, etc.
    │   │       └── application.yml
    │   └── test/
    │       └── java/com/meridian/retail/
    │           ├── service/          ← unit tests (Mockito)
    │           ├── controller/       ← @WebMvcTest slice tests
    │           ├── integration/      ← @SpringBootTest + Testcontainers
    │           └── security/         ← security-focused tests
    ├── Dockerfile
    ├── Dockerfile.test
    ├── docker-compose.yml
    ├── docker-compose.test.yml
    ├── run_tests.sh
    ├── .gitignore
    ├── pom.xml
    └── README.md
```

## Non-Negotiable Rules

1. **Read SPEC.md + CLAUDE.md + PLAN.md first.** Every response, no exceptions.
2. **One task at a time.** Do exactly the PLAN.md task. Nothing more.
3. **Mark [x] then continue.** After completing a task, update PLAN.md and immediately continue to next task within the same phase — never pause between individual tasks.
4. **All code in repo/.** Never create files outside repo/.
5. **App must actually work.** Every page must render correctly in browser. Every form must submit and process. Every feature from SPEC.md must be demonstrable by QA clicking through the running Docker app. Broken pages, 500 errors, or non-functional features are blockers.
6. **Beautiful UI is mandatory.** Modern back-office SaaS aesthetic. Clean typography, consistent spacing, role-specific color coding, intuitive navigation. QA manually inspects the UI — it must be eye-catching and professional.
7. **No ddl-auto=create ever.** Flyway migrations only. Use `spring.jpa.hibernate.ddl-auto=validate`.
8. **Real MySQL in tests.** Testcontainers for all integration tests. Zero H2. Zero mocks for DB layer.
9. **Service layer owns all business rules.** Controllers are thin (parse request, call service, render response). All validation, locking, audit logging, business rule enforcement happens in service classes.
10. **AuditLogService.log() on EVERY critical operation.** Campaign edits, coupon changes, file access, imports, exports, approvals, role changes — all must call audit service with before/after state.
11. **HTMX for real-time validation.** Form validation feedback via HTMX partial responses — gives instant feedback without full page reload.
12. **Pause at phase boundaries only.** Complete all tasks in a phase without stopping. Only pause when entire phase + checkpoint is done.
13. **Fix before proceeding.** If a task causes a compile error or runtime failure, fix it before marking [x].
14. **Zero hardcoded data in templates.** All model attributes come from service layer. Every th:each, th:text bound to real data from DB.
15. **Security must be both correct AND clearly readable.** Security filters, lockout logic, rate limiters — explicit method bodies, comments explaining security intent. QA reads AND runs the code.

## Tech Stack & Dependencies (pom.xml)

```xml
<!-- Spring Boot parent: 3.2.x -->
<!-- Java: 17 -->

<!-- Core Web + Thymeleaf -->
spring-boot-starter-web
spring-boot-starter-thymeleaf
thymeleaf-extras-springsecurity6

<!-- Security -->
spring-boot-starter-security
spring-security-test

<!-- Data -->
spring-boot-starter-data-jpa
mysql-connector-j
flyway-mysql
flyway-core
spring-boot-starter-validation

<!-- Rate Limiting -->
com.github.bucket4j:bucket4j-core:8.10.1

<!-- File Processing -->
org.apache.tika:tika-core:2.9.1            <!-- file type detection by signature -->
org.apache.pdfbox:pdfbox:3.0.1             <!-- PDF watermarking -->
org.imgscalr:imgscalr-lib:4.2             <!-- image watermarking -->

<!-- Fingerprinting -->
com.google.guava:guava:33.0.0-jre          <!-- Murmur3 for SimHash -->
commons-codec:commons-codec:1.16.0         <!-- SHA-256 utilities -->

<!-- Utilities -->
org.projectlombok:lombok
com.fasterxml.jackson.core:jackson-databind
org.apache.commons:commons-io:1.3.2

<!-- Monitoring -->
spring-boot-starter-actuator

<!-- Testing -->
spring-boot-starter-test                    <!-- JUnit 5 + Mockito + MockMvc -->
org.testcontainers:junit-jupiter:1.19.3
org.testcontainers:mysql:1.19.3
```

## UI Design Standards (QA manually reviews the browser)

```
Color Palette:
  Sidebar: #1e293b (dark slate) with #334155 hover
  Primary accent: #3b82f6 (blue)
  Success: #22c55e (green)
  Warning: #f59e0b (amber)
  Danger: #ef4444 (red)
  Background: #f8fafc (light gray)
  Cards: #ffffff with subtle shadow

Typography:
  Font: Inter (loaded from /static/vendor/inter/ — no CDN)
  Headings: font-weight 600
  Body: font-weight 400, line-height 1.6

Layout:
  Fixed sidebar (240px) + main content area
  Sticky top header with breadcrumbs + user info + logout
  Content cards with 24px padding, border-radius 12px
  Consistent 8px grid spacing

Every page must have:
  - Loading spinner while HTMX requests are pending
  - Empty state illustration + message when no data
  - Error state with clear message + retry option
  - Success flash message after form submission
  - "Why blocked" tooltip on every disabled action button

Role color coding:
  admin: purple badge
  reviewer: blue badge
  operations: green badge
  finance: amber badge
  customer_service: gray badge
```

## Security Implementation (explicitly coded — QA reads AND runs)

### SecurityConfig.java
```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
            )
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/login", "/captcha/**", "/actuator/health",
                                 "/static/**", "/vendor/**").permitAll()
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .requestMatchers("/approval/**").hasAnyRole("ADMIN", "REVIEWER")
                .requestMatchers("/analytics/export/**").hasAnyRole("ADMIN", "FINANCE")
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .loginProcessingUrl("/login")
                .successHandler(customSuccessHandler)
                .failureHandler(customFailureHandler)
            )
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/login?logout")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID", "XSRF-TOKEN")
            )
            .sessionManagement(session -> session
                .maximumSessions(1)
                .maxSessionsPreventsLogin(false)
            );
        // Add custom filters in order
        http.addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class);
        http.addFilterBefore(nonceValidationFilter, UsernamePasswordAuthenticationFilter.class);
        http.addFilterBefore(requestSigningFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
```

### AccountLockoutService.java
```java
@Service
public class AccountLockoutService {
    // Account lock: 5 failures within 15 minutes → lock for 15 minutes
    public boolean isAccountLocked(String username) {
        LocalDateTime window = LocalDateTime.now().minusMinutes(15);
        long count = loginAttemptRepo.countByUsernameAndAttemptedAtAfter(username, window);
        return count >= 5;
    }
    // IP block: 20 failures within 60 minutes → block for remainder of hour
    public boolean isIpBlocked(String ipAddress) {
        LocalDateTime window = LocalDateTime.now().minusMinutes(60);
        long count = loginAttemptRepo.countByIpAddressAndAttemptedAtAfter(ipAddress, window);
        return count >= 20;
    }
    public void trackFailedAttempt(String username, String ipAddress) {
        loginAttemptRepo.save(new LoginAttempt(username, ipAddress, LocalDateTime.now()));
    }
    public void resetAttempts(String username) {
        loginAttemptRepo.deleteByUsername(username);
    }
}
```

### RateLimitFilter.java
```java
@Component
public class RateLimitFilter extends OncePerRequestFilter {
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain chain) throws ServletException, IOException {
        String username = getCurrentUsername(request);
        if (username == null) { chain.doFilter(request, response); return; }

        boolean isExport = request.getRequestURI().startsWith("/analytics/export");
        long capacity = isExport ? 10L : 60L;
        String bucketKey = (isExport ? "export:" : "standard:") + username;

        Bucket bucket = buckets.computeIfAbsent(bucketKey, k ->
            Bucket.builder()
                .addLimit(Bandwidth.classic(capacity, Refill.greedy(capacity, Duration.ofMinutes(1))))
                .build()
        );

        if (bucket.tryConsume(1)) {
            chain.doFilter(request, response);
        } else {
            response.setStatus(429);
            response.setHeader("Retry-After", "60");
            response.getWriter().write("{\"error\":\"Rate limit exceeded. Try again in 60 seconds.\"}");
        }
    }
}
```

### AuditLogService.java
```java
@Service
public class AuditLogService {
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(AuditAction action, String entityType, Long entityId,
                    Object before, Object after, String operatorUsername, String ipAddress) {
        AuditLog entry = AuditLog.builder()
            .action(action.name())
            .entityType(entityType)
            .entityId(entityId)
            .operatorUsername(operatorUsername)
            .ipAddress(ipAddress)
            .timestamp(Instant.now())
            .beforeState(objectMapper.writeValueAsString(before))
            .afterState(objectMapper.writeValueAsString(after))
            .build();
        auditLogRepository.save(entry);
        // AuditLogRepository has NO update() or delete() methods — immutable by design
    }
}
```

## Docker Architecture

> IMPORTANT: No .env file. No manual configuration. All values are hardcoded directly
> in docker-compose.yml. QA runs  and nothing else.

```yaml
# docker-compose.yml — all values hardcoded, no ${VAR} references, no .env file needed
services:
  mysql:
    image: mysql:8.0
    environment:
      MYSQL_DATABASE: retail_campaign
      MYSQL_ROOT_PASSWORD: rootpassword
      MYSQL_USER: retail_user
      MYSQL_PASSWORD: retail_pass
    volumes:
      - mysql-data:/var/lib/mysql
    healthcheck:
      test: ["CMD", "mysqladmin", "ping", "-h", "localhost", "-u", "retail_user", "-pretail_pass"]
      interval: 10s
      timeout: 5s
      retries: 10

  app:
    build:
      context: .
      dockerfile: Dockerfile
    ports:
      - "8080:8080"
    depends_on:
      mysql:
        condition: service_healthy
    environment:
      SPRING_DATASOURCE_URL: jdbc:mysql://mysql:3306/retail_campaign?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
      SPRING_DATASOURCE_USERNAME: retail_user
      SPRING_DATASOURCE_PASSWORD: retail_pass
      SPRING_DATASOURCE_DRIVER_CLASS_NAME: com.mysql.cj.jdbc.Driver
      APP_ENCRYPTION_KEY: retail-campaign-aes-key-32chars!!
      APP_SIGNING_SECRET: retail-campaign-hmac-signing-key!!
      SPRING_PROFILES_ACTIVE: docker
    volumes:
      - upload-data:/app/uploads
      - backup-data:/app/backups

volumes:
  mysql-data:
  upload-data:
  backup-data:
```

## run_tests.sh (create exactly this at repo/run_tests.sh)

```bash
#!/bin/sh
set -e

echo "========================================"
echo "  Retail Campaign Test Suite"
echo "========================================"

FAILED=0

echo ""
echo "--- Unit Tests ---"
./mvnw test -Dtest="**/*Test,**/*Tests" \
  -Dspring.profiles.active=test \
  --no-transfer-progress 2>&1 || FAILED=1

echo ""
echo "--- Integration Tests (Real MySQL via Testcontainers) ---"
./mvnw test -Dtest="**/*IntegrationTest,**/*IT" \
  -Dspring.profiles.active=test \
  --no-transfer-progress 2>&1 || FAILED=1

echo ""
echo "========================================"
if [ $FAILED -eq 0 ]; then
  echo "  ALL TESTS PASSED"
else
  echo "  SOME TESTS FAILED"
fi
echo "========================================"

exit $FAILED
```

## .gitignore (repo/.gitignore)

```
target/
*.class
*.jar
*.war
*.log
mysql-data/
uploads/
backups/
.DS_Store
.idea/
*.iml
```

> Note: No .env file exists or is needed. This project has zero manual configuration.
> All values are hardcoded in docker-compose.yml and application.yml.

## README (repo/README.md — minimal, exact format)

```markdown
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
```

## Open Questions & Clarifications

[ ] SimHash: custom implementation using Guava Murmur3 — 64-bit, Hamming distance ≤ 8 = near-duplicate
[ ] CAPTCHA: locally generated BufferedImage (java.awt.Graphics2D) — no external service
[ ] Chunked upload: custom protocol (init/chunk/finalize endpoints) — no Tus dependency
[ ] Watermark: PDF (PDFBox) + images (imgscalr) both — text overlay "INTERNAL - {user} - {date}"
[ ] Masked download: unauthorized role gets watermarked version, not original; binary-only → 403
[ ] Backup: Spring @Scheduled + ProcessBuilder runs mysqldump → gzip → /app/backups/ volume
[ ] Nonce storage: used_nonces table, ±5 min timestamp window, 10-min TTL, hourly cleanup
[ ] Request signing: HMAC-SHA256(method+path+timestamp+body-hash) using hardcoded signing key from application.yml (retail-campaign-hmac-signing-key!!)
[ ] Receipt preview: HTMX partial response renders monospace receipt block in real-time as user types
[ ] Dual approval: service layer enforces approver1 != approver2, throws exception if same user
