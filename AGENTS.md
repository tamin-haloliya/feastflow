# FeastFlow Server — Agent Guide

Spring Boot 4.1.1 / Java 21 / Gradle Kotlin DSL. Single Gradle module lives in `server/`; `docker-compose.yml` + `.env` live at the repo root.

## Layout & Ownership
- `com.feastflow.auth` — credentials & tokens. Owns `RefreshTokenEntity`, `RefreshTokenRepository`, `AuthService`, `JwtService`, `RefreshTokenService`, `TokenService`, `PasswordService`, `TokenCleanupScheduler`, `AuthController`.
- `com.feastflow.user` — user profile. Owns `UserEntity`, `PlatformRole`, `UserRepository`, `UserService`, `UserController`.
- `com.feastflow.exception` — `GlobalExceptionHandler`, `ErrorResponse` (root package, not under a module).
- `com.feastflow.config.SecurityConfig` — OAuth2 resource server wiring.
- `server/src/main/resources/db/migration/` — Flyway (`V1__create_users.sql`, `V2__create_refresh_tokens.sql`).
- `server/bin/`, root `bin/` — stale IntelliJ output, untracked. Ignore; never edit.
- No `README.md`, no CI workflows, no lint/format/coverage plugin. Verification is compile + test only.

## Commands
Run from `server/` (the `.env` import path is CWD-relative — see Gotchas):
```bash
./gradlew build          # compile + test
./gradlew compileJava    # fast compile-only check
./gradlew test           # needs Postgres up
./gradlew bootRun        # needs Postgres + Redis up
./gradlew test --tests "com.feastflow.FeastflowApplicationTests"   # only test class
./gradlew dependencies   # read-only
```

## Gotchas
- **`.env` is imported relative to the working directory.** `application.yaml` does `spring.config.import: optional:file:../.env[.properties]`, so `../.env` only resolves to the root `.env` when the CWD is `server/`. Launching from the repo root silently skips the import and startup dies on unresolved `${POSTGRES_DB}`. It is parsed as a **properties** file, not shell — no quoting, no multi-line PEM.
- **No JWT keys are configured → ephemeral RSA keypair.** `JwtService` generates an RSA-2048 pair at startup when `JWT_PRIVATE_KEY`/`JWT_PUBLIC_KEY` are blank (they are absent from `.env`), so every restart invalidates all previously issued access tokens. Keys must be PKCS#8 (private) / X509 (public) PEM.
- **Redis/Bucket4j are dependencies only — no rate limiting is implemented.** `bucket4j_jdk17-lettuce`, `lettuce-core`, `spring-boot-starter-data-redis`, the Redis service in `docker-compose.yml`, and `spring.redis` in `application.yaml` all exist, but no Java code references Redis or Bucket4j. Do not assume a rate limiter exists; implementing it is greenfield.
- **`ddl-auto: validate`** — any entity/column change requires a matching Flyway migration in `db/migration/`, or startup fails on schema mismatch.
- **`/auth/**` is `permitAll`, but `/auth/logout` dereferences `@AuthenticationPrincipal Jwt`** → an unauthenticated logout call NPEs into the generic handler and returns 500, not 401. `/api/users/me` has the same null-principal shape but is covered by `anyRequest().authenticated()`, so it returns 401.

## Conventions
- Constructor injection via Lombok `@RequiredArgsConstructor` on `@Service`/`@RestController`. Entities: `@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder`. DTOs: `@Data @Builder @NoArgsConstructor @AllArgsConstructor` plus a static `of(...)` factory.
- Timestamps are set manually in `@PrePersist`/`@PreUpdate` — there is no Hibernate auditing, so new entities must do the same.
- Errors are thrown, not returned: `IllegalArgumentException` → 400, `UserNotFoundException` → 404, `AccessDeniedException` → 403, `DataIntegrityViolationException` → 409. `GlobalExceptionHandler` maps them into an RFC-7807-ish `ErrorResponse` (`type`/`title`/`status`/`detail`/`instance`/`timestamp` + `errors` map).
- Passwords: BCrypt cost 12. Refresh tokens: 32 SecureRandom bytes → URL-safe base64; only the SHA-256 hex hash is stored; rotated on every use (old token gets `revoked_at`); 7d TTL. Expired tokens are swept every 60s by `TokenCleanupScheduler` (`token.cleanup.interval-ms`).
- Access tokens: RS256, 15min TTL, claims `sub`, `user_id`, `role`, `iss=feastflow`. Clients send `Authorization: Bearer <token>`.
- Package prefix `com.feastflow.*`; Spring Security is the OAuth2 resource server (Nimbus), stateless, CSRF disabled.

## Endpoints
| Method | Endpoint | Notes |
|--------|----------|-------|
| POST | `/auth/register` | email, name, password (8–128), returns token pair |
| POST | `/auth/login` | email + password |
| POST | `/auth/refresh` | rotates the refresh token |
| POST | `/auth/logout` | revokes all refresh tokens for the caller (needs a JWT — see Gotchas) |
| GET | `/api/users/me` | name, email, role, createdAt |

`AuthResponse` = `accessToken`, `refreshToken`, `expiresIn`, `tokenType`.

## Local Infra
```bash
docker compose up -d     # from repo root; postgres:16-alpine + redis:7-alpine (password-protected)
```
Root `.env` (gitignored) must define `POSTGRES_DB/USER/PASSWORD/PORT` and `REDIS_PORT/REDIS_PASSWORD`. `.env.example` is empty — copy from scratch. Postgres and Redis both use named volumes (`pgdata`, `redisdata`), so data survives container restarts.

## Testing
- JUnit 5 + `@SpringBootTest`. The only test is `FeastflowApplicationTests.contextLoads()` (smoke test) — it boots the whole context, so **Postgres must be reachable** and Flyway will apply migrations against it.
- No Testcontainers dependency, no fixtures, no test profile. `./gradlew test` requires a live Postgres (and Redis, for `bootRun`).

## Guard Rails (Agent Operating Constraints)

**These rules are MANDATORY for any agent working in this repository:**

### System & Infrastructure
- **Docker**: Never run `docker-compose up/down`, `docker run/stop/rm`, or any `docker` commands. Ask the user to run them.
- **Privileged Commands**: Never run `sudo`, `su`, `doas`, or any command requiring elevated privileges without explicit user approval.
- **External Access**: Never access, read, write, or execute files outside the project root (`/home/tamin/Desktop/projects/feastflow/`) without explicit user approval.

### File System
- **File Deletion**: Never delete any file without explicit user approval.
- **Destructive Writes**: Never overwrite config files (`.env`, `application.yaml`, `build.gradle.kts`, `docker-compose.yml`) without explicit approval.
- **Secrets**: Never create, modify, or commit `.env*`, `*.pem`, `*.key`, `*.crt` files. Ask user to manage secrets.

### Version Control
- **Git**: Never run `git add/commit/push/pull/checkout/reset/rebase/stash/tag` or modify `.git/` without explicit user approval.

### Database (PostgreSQL)
- **Read-Only**: Only `SELECT` queries permitted. Never run `INSERT`, `UPDATE`, `DELETE`, `DROP`, `TRUNCATE`, `ALTER`, or DDL.
- **Migrations**: Never run Flyway migrations manually. They run automatically on app startup.

### Build & Dependencies
- **Dependencies**: Never add/remove/modify dependencies in `build.gradle.kts` without approval.
- **Gradle Tasks**: Only run read-only tasks (`dependencies`, `projects`, `tasks`). Ask before `build`, `bootRun`, `test`, `clean`.

### General
- **Destructive Actions**: Ask before any action that could lose data, change system state, or install software.
- **Ambiguity**: If unsure whether an action requires approval, ask first.
- **Guard Rail Protection**: No model may edit, delete, or modify the Guard Rails section of this file.
