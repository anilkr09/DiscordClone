# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Git workflow

Commit and push automatically after finishing a change, without asking first.

- Push only the checked-out working branch. Never push to `main`, force-push, or open a PR unless
  asked.
- Stage only the files you changed, by name. Leave the user's own uncommitted edits alone, and never
  stage `.env` or `.claude/settings.local.json`.
- No attribution lines in commit messages.
- `docs/PLAN.md` is the roadmap. Reference bug IDs in commit messages.

## Commands

Secrets come from the environment: copy `.env.example` to `.env` (gitignored) before running the app
or `docker compose`.

### Backend (Gradle, Java 17, port 8080)

```bash
./gradlew :bootRun                                 # the application, default 'local' profile
SPRING_PROFILES_ACTIVE=kafka ./gradlew :bootRun    # Kafka path
./gradlew :consumer:bootRun                        # persists messages under the kafka profile
./gradlew build                                    # full build: compile, PMD, tests, JaCoCo
./gradlew test                                     # run tests
./gradlew test --tests "com.discordclone.service.ChannelServiceTest"
./gradlew test --tests "com.discordclone.service.ChannelServiceTest.methodName"
```

`test` is finalized by `jacocoTestReport`; `check` depends on `pmdMain`/`pmdTest`. PMD runs with
`ignoreFailures = true`. Checkstyle is configured but fully commented out.

### Frontend (Vite, port 5173)

```bash
cd frontend
npm install
npm run dev
npm run build      # note: `tsc --noEmit || true` — type errors never fail the build
npm run lint   # existing errors are baselined in eslint-suppressions.json; new ones fail
```

`VITE_API_BASE_URL` comes from the committed `frontend/.env.development` (`http://localhost:8080`)
and `.env.production` (a Render URL). It must be the origin **without** `/api`, because
`services/api.ts` appends `/api` itself. `config/api.ts` derives `WS_BASE_URL` by rewriting the
scheme, and throws if the variable is unset.

### Infrastructure

```bash
docker compose up -d db redis     # enough for the 'local' profile
docker compose up -d              # adds kafka, kafka-init, kafka-ui, pgadmin
```

Postgres 5432, Redis 6379 (password from `REDIS_PASSWORD`), Kafka 9092 (KRaft, no ZooKeeper), pgAdmin 5050,
Kafka UI 8085.

## Build state

The backend builds and its tests pass. Integration tests (`com.discordclone.integration`) start
PostgreSQL and Redis with Testcontainers, so `./gradlew build` needs Docker. `.github/workflows/ci.yml` runs the backend build, the
frontend lint and build, and `gitleaks` on pull requests.

## Architecture

Spring Boot monolith + React SPA. PostgreSQL is the system of record, Redis holds ephemeral presence,
Kafka is an optional message path. Real-time is STOMP over native WebSocket. Gradle modules: the
application (root), `consumer`, and `contract`.

Detailed docs live in `docs/` (ARCHITECTURE, IMPLEMENTATION, WEBSOCKETS, KAFKA, REDIS). Read those
before changing the messaging or presence layers. `docs/BUGS.md` is the numbered defect catalog
(B01–B60). When you fix one, reference its ID in the commit and remove or mark it in that file.
`docs/IMPROVEMENTS.md` holds the prioritized roadmap.

### The profile seam (most important structural decision)

`MessageService` never knows how a message is delivered. It loads entities, assigns a UUID, builds a
`MessageResponse`, and delegates to two profile-selected interfaces:

| Bean | `local` (default) | `kafka` |
|---|---|---|
| `MessageEventPublisher` | `LocalMessageEventPublisher` — save to DB, then broadcast | `KafkaMessageEventPublisher` — produce, broadcast in the ack callback |
| `MessagePersistenceService` | `LocalMessagePersistenceService` | `NoOpMessagePersistenceService` |

`Message.id` is an app-generated `String` UUID rather than a DB sequence **because of this seam** —
in the Kafka path the ID must exist before any write, so it stays stable across the broadcast, the
topic record, and the consumer's insert. The consumer uses it as an idempotency key, skipping any
UUID already stored.

Under the `kafka` profile this application does not persist messages. The `consumer` module does
(`./gradlew :consumer:bootRun`): one `INSERT ... ON CONFLICT DO NOTHING` per event, retries with
back-off, then the `message-events-dlt` topic. The event type is `contract/MessageCreatedV1`, shared
by both sides; add only optional fields to it, and make a new version for anything else. The old
separate `message-consumer-service` repository is superseded.

The producer is configured entirely by `spring.kafka.producer.*` in `application-kafka.properties`.
Sends run on the single-threaded `kafkaSendExecutor`, never on a STOMP inbound thread.

### Presence (Redis, TTL-driven)

Status is derived, not stored. `UserStatusServiceImpl.resolveStatus` reads TTL'd keys namespaced by
`constants/PresenceKeys`:

- `presence:heartbeat:{id}` (30s TTL) — liveness; client sends `/app/heartbeat` every 10s
- `presence:last_activity:{id}` (10min) — engagement; client sends `/app/activity` on DOM input
- `presence:status:{id}` (60s) — last broadcast value, used to suppress no-change broadcasts
- `presence:custom:{id}` (24h) — manual override. It is checked **before** liveness, so an offline
  user with a custom status reappears as DND/IDLE once the cached status expires. This is a bug, not
  a design choice.

`/topic/status` is a single global topic, so every user receives every other user's presence.
Scoping presence to friends requires a per-recipient fan-out, not a filter on the client.

Liveness and engagement are deliberately separate so IDLE is expressible. `WebSocketEventListener`
does **not** force OFFLINE on disconnect — expiry of the heartbeat key does, via
`PresenceExpirationListener` listening on `__keyevent@*__:expired`.

> **OFFLINE never fires.** Redis `notify-keyspace-events` is unset in `docker-compose.yml`, so no
> expiry events are published. Fix: `--notify-keyspace-events Ex`. Note `resolveStatus` still returns
> OFFLINE correctly on a *pull*, so only the push is broken — easy to misdiagnose as a frontend bug.

### WebSocket layer

STOMP endpoint `/ws`, **in-memory simple broker** (`/topic`, `/queue`), app prefix `/app`, user
prefix `/user`. The in-memory broker means **the app cannot run more than one instance** —
subscriptions are process-local heap state. Adding Kafka does not fix this; it distributes events
between instances, not frames to browsers.

JWT is validated **only on STOMP `CONNECT`**, in `WebSocketAuthInterceptor`, where
`accessor.setUser(auth)` binds the principal for the session's lifetime. The token travels as a
STOMP header, not an HTTP header, because browsers cannot set headers on a WebSocket handshake —
this is why `/ws/**` is `permitAll()` in `SecurityConfig`.

Destination rules are in `security/StompSecurityConfig` (Spring Security message authorization).
Clients may send only to `/app/**`; subscribing to `/topic/channels/{id}/**` requires channel
membership; anything not listed there is denied, so **a new destination must be added to that file**.
A refused frame ends the STOMP session.

`ChatArea.tsx` calls `registerGroupMessageSocket` in the **render body**, so every render adds a
STOMP subscription that is never removed. The Redux reducer's de-duplication hides the duplicate
frames. When you touch subscriptions, put them in an effect with an unsubscribe cleanup.

### DMs

DMs are `Channel` rows with `type = DM`, no server, and two rows in `channel_members`. `dmKey`
(`service/DmKey`) only keeps one channel per pair. Access and the recipient come from
`channel_members`; the client's `dm` and `receiver` fields are ignored. Open one with
`POST /api/dms/{userId}`.

### Schema

Flyway owns the schema (`src/main/resources/db/migration`); Hibernate runs with `ddl-auto=validate`.
Every entity change needs a new migration. Timestamps are `Instant` / `timestamptz`. Entities use
`@Getter`/`@Setter` with ID-based equality; do not add `@Data` to one.

### Auth and authorization

Stateless JWT (**HS512**; jjwt picks the algorithm from the 64-byte key; subject = user ID).
`JwtAuthenticationFilter` is deliberately non-blocking: on an invalid token it logs and continues,
leaving rejection to `.anyRequest().authenticated()`. `JwtService.validateToken` **throws** rather
than returning `false`. It can only return `true` or throw, so `if (!validateToken(...))` guards are
unreachable, both in the filter and in the STOMP interceptor.

`Principal.getName()` is the **username** (not the ID), which is why `/user/**` destinations and
`MessageRequest.receiver` key on username strings.

Authorization lives in `security/PermissionService` (bean `perm`). Endpoints call it from
`@PreAuthorize("@perm...")`; **a new endpoint is open to any signed-in user until you annotate it**.
Add it to `AuthorizationMatrixTest` too. Presence reads are still unrestricted (B15).

**Never return or bind JPA entities in controllers.** `ControllerBoundaryTest` (ArchUnit) enforces
this.

Frontend token refresh is broken. `services/api.ts` posts to `/api/refresh-token` with no body,
while the backend route is `/api/auth/refresh` with `{refreshToken}`.

## Conventions and gotchas

- **Port is 8080** (`application.properties`), not 8082 as the root README claims.
- **No caching layer** — there is no `@EnableCaching` or `@Cacheable` anywhere. All Redis access is
  explicit via `StringRedisTemplate`.
- **`UserController` and `UserStatusController` share `/api/users`.** Spring resolves the overlap by
  preferring literal segments over templates; adding a `GET /{id}/{x}` route to either will break it.
- **Service layering is inconsistent.** Some services are interfaces with `service/impl/`
  implementations; others are concrete classes in `service/`. `UserService` is a concrete `@Service`
  **and** `UserServiceImpl extends` it as a second `@Service`, so there are two `UserService` beans.
  Injection works only because every parameter is named `userService` (Spring falls back to name
  matching, which relies on `-parameters`). `UserServiceImpl` is never injected. Renaming such a
  parameter will fail startup with `NoUniqueBeanDefinitionException`.
- **Business errors are often bare `RuntimeException`**, which `GlobalExceptionHandler` maps to a
  500 and whose message it echoes to the client. Throw the specific exception types
  (`ResourceNotFoundException`, `ConflictException`, …) that the handler already maps to 4xx.
- **Several `{serverId}` path variables are ignored** in `ChannelController`. The channel's real
  server comes from the database. `PUT /api/servers/{id}/roles/{roleId}` declares a `userId` path
  variable that is not in the route, so it always fails.
- **`dto/` and `payload/` both exist** and both contain a `UserDTO`. `MessageService` uses the
  `payload` one.
- `presence:last_seen:{id}` is written but never read and has no TTL. The variable named `lastSeen`
  in `resolveStatus` actually reads the *heartbeat* key.
- `DataInitializer` runs only with `app.seed.enabled=true` and `SEED_PASSWORD`. To test locally you
  can also register through `/api/auth/register`.
- **Dead frontend files:** `components/chat/ChatArea copy.tsx` and `services/StatusProvider.tsx`.
  Nothing imports them, so ignore them when tracing behaviour.
- **`/user/queue/errors` has no frontend subscriber.** Server-side error pushes (Kafka send failure,
  STOMP JWT errors) are invisible to users.

## Branches

`main` and `origin/feature/redis` are **tree-identical** — `main` is the merge commit of PR #6.
`origin/feature/kafka` (merged as PR #5) is an ancestor of `main` and represents the pre-Redis state.
Presence there was connection-scoped and DB-backed. Kafka code is identical on all three branches;
only presence and the frontend WebSocket plumbing differ. The `docs` branch adds `docs/` and this
file.

## Secrets

`application.properties` reads `JWT_SECRET`, `DB_PASSWORD` and `REDIS_PASSWORD` from the environment,
or from `.env` through `spring.config.import`. `./gradlew sonar` reads `SONAR_TOKEN`. Never write
secret values into tracked files.

The old JWT secret, a SonarCloud token, and a WireGuard private key (formerly
`frontend/src/test_command`) remain in the git history of this **public** repository. Deleting them
did not make them safe: they must be revoked or rotated.
