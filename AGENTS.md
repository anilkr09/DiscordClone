# AGENTS.md — DiscordClone Repository Guide

## Quick Reference

| Area | Command / Fact |
|------|----------------|
| **Backend root** | `./gradlew` (Java 17, Spring Boot 3.2) |
| **Frontend root** | `cd frontend && npm` (Vite + React + TS) |
| **Default backend port** | **8080** (not 8082 — README is wrong) |
| **Default frontend port** | 5173 |
| **DB** | PostgreSQL `discord_clone` on localhost:5432 |
| **Redis** | localhost:6379, password `redis123` |
| **Profiles** | `local` (default, in-memory broker), `kafka` (external consumer) |

---

## Commands That Actually Work

### Backend
```bash
# Run (local profile, needs DB + Redis)
./gradlew bootRun

# Run with Kafka profile (needs message-consumer-service running first)
SPRING_PROFILES_ACTIVE=kafka ./gradlew bootRun

# Build that succeeds (skips broken tests + PMD)
./gradlew clean bootJar -x test -x pmdMain -x pmdTest

# Single test class
./gradlew test --tests "com.discordclone.service.ChannelServiceTest"
```

### Frontend
```bash
cd frontend
npm install
npm run dev
npm run build      # tsc errors are ignored (|| true)
npm run lint
```

### Infrastructure
```bash
# Local profile only
docker compose up -d db redis

# Full stack (includes Kafka, pgAdmin, Kafka UI)
docker compose up -d
```

---

## Critical Architecture Facts

### Profile Seam (Most Important)
`MessageService` delegates delivery to two profile-selected beans:

| Bean | `local` | `kafka` |
|------|---------|---------|
| `MessageEventPublisher` | `LocalMessageEventPublisher` (save → broadcast) | `KafkaMessageEventPublisher` (produce → broadcast in ack) |
| `MessagePersistenceService` | `LocalMessagePersistenceService` | `NoOpMessagePersistenceService` |

- **`Message.id` is a UUID string** (not DB sequence) — required for Kafka idempotency
- **Kafka profile loses data by design** — no `@KafkaListener` here; persistence is no-op. Consumer (`message-consumer-service`) has bugs: drops failed saves after 10 retries (B52), starts at topic end (B54), uses `ddl-auto=update` on copied entities (B55)

### WebSocket Layer
- STOMP endpoint: `/ws` (in-memory simple broker → **single instance only**)
- JWT validated **only on CONNECT** via `WebSocketAuthInterceptor` (token in STOMP header)
- **No per-channel authorization on SUBSCRIBE** — membership check is commented out
- `/ws/**` is `permitAll()` in `SecurityConfig` because browsers can't set HTTP headers on WS upgrade

### DMs Are Not Server Channels
- DM = `Channel` with `type=DM`, `dmKey="dm-{minId}-{maxId}"`, attached to **hardcoded server ID 1**
- Server-membership checks are meaningless for DMs
- `DmChannel`/`DmChannelRepository` exist but are unused
- DM recipient = client-supplied `MessageRequest.receiver` (username, not validated)

### Presence (Redis, TTL-Driven)
- Status is **derived**, not stored. Keys:
  - `presence:heartbeat:{id}` (30s TTL) — liveness, client sends `/app/heartbeat` every 10s
  - `presence:last_activity:{id}` (10min) — engagement, client sends `/app/activity` on input
  - `presence:status:{id}` (60s) — last broadcast, suppresses no-change pushes
  - `presence:custom:{id}` (24h) — manual override, checked **before** liveness (bug: offline user with custom status reappears as DND/IDLE after expiry)
- `/topic/status` = global topic (every user sees every other user's presence)
- **OFFLINE push never fires** — `notify-keyspace-events` unset in docker-compose. Fix: `--notify-keyspace-events Ex`. Pull still works.

---

## Known Broken Things (Don't Waste Time Debugging)

| Issue | Location | Impact |
|-------|----------|--------|
| Missing `spring-boot-starter-data-redis` in `build.gradle` | `build.gradle` | Build fails — `RedisConfig`, `UserStatusServiceImpl` won't compile |
| Stale test: `UserStatusServiceTest` | `src/test/.../UserStatusServiceTest.java` | Calls removed `updateUserStatus()` |
| Stale test: `MessageServiceTest` | `src/test/.../MessageServiceTest.java` | 4 `@Mock`s but constructor now takes 6 args |
| Frontend token refresh broken | `frontend/services/api.ts` | Posts to `/api/refresh-token` (no body); backend expects `/api/auth/refresh` with `{refreshToken}` |
| `/user/queue/errors` has no frontend subscriber | — | Server error pushes (Kafka, JWT) invisible to users |
| `application1.properties` is dead config | root resources | Spring ignores it; contains Kafka `group-id` that reads as live but isn't applied |

---

## Gotchas & Conventions

- **Never return JPA entities from controllers** — `User.password` has no `@JsonIgnore`, `Server.owner` is EAGER → leaks BCrypt hashes. Several endpoints already do this (`UserController`, `ServerController`, `InviteController`).
- **`LoginRequest` is `@Data`** — `AuthController` logs it → plaintext passwords in logs.
- **`Principal.getName()` = username** (not ID) — `/user/**` destinations and `MessageRequest.receiver` key on username.
- **No `@PreAuthorize` anywhere** — resource auth is ad hoc per service method (`ServerService.isUserMember`, etc.). Many paths have **no check**: message send/read/edit, server member add/remove, channel subscribe, presence reads.
- **Entities use Lombok `@Data`** — generates `equals`/`hashCode` over lazy associations. Avoid `HashSet<Entity>`.
- **`dto/` and `payload/` both have `UserDTO`** — `MessageService` uses the `payload` one.
- **Schema is `ddl-auto=update`** — no Flyway/Liquibase.
- **`DataInitializer` seeds `testuser` with plaintext password** — bypasses `UserService.createUser` (BCrypt). That account can never log in. Register via `/api/auth/register` instead.
- **Service layering inconsistent** — some are interfaces + `impl/`, others concrete. `UserService` is concrete `@Service` **and** `UserServiceImpl extends` it as second `@Service` → two beans. Injection works only because param name is `userService` (Spring name matching). Renaming the param will fail with `NoUniqueBeanDefinitionException`.

---

## Docs to Read Before Changing Core Layers

| Layer | Doc |
|-------|-----|
| Messaging / WebSocket | `docs/WEBSOCKETS.md` |
| Kafka path | `docs/KAFKA.md` |
| Redis presence | `docs/REDIS.md` |
| Bug catalog | `docs/BUGS.md` (B01–B60 — reference ID in commit when fixed) |
| Improvement roadmap | `docs/IMPROVEMENTS.md` |

---

## Committed Secrets (Do Not Propagate)

- `build.gradle:29` — SonarCloud token (needs revocation, not just deletion)
- `application.properties:56` — JWT signing secret

---

## Branches

- `main` = `origin/feature/redis` (tree-identical, merge of PR #6)
- `origin/feature/kafka` = ancestor of `main` (PR #5, pre-Redis state)
- `docs` = adds `docs/` and this file