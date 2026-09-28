# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

### Backend (Gradle, Java 17, port 8080)

```bash
./gradlew bootRun                                  # run with default 'local' profile
SPRING_PROFILES_ACTIVE=kafka ./gradlew bootRun     # run with Kafka producer path
./gradlew build                                    # full build (currently FAILS — see Build state)
./gradlew clean bootJar -x test -x pmdMain -x pmdTest   # the build command that actually works
./gradlew test                                     # run tests (currently FAILS to compile)
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
npm run lint
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

Postgres 5432, Redis 6379 (password `redis123`), Kafka 9092 (KRaft, no ZooKeeper), pgAdmin 5050,
Kafka UI 8085.

## Build state

The repo does not currently build. Two independent blockers:

1. **`spring-boot-starter-data-redis` is missing from `build.gradle`** while `RedisConfig`,
   `RedisKeyExpirationListenerConfig`, and `UserStatusServiceImpl` import
   `org.springframework.data.redis.*`. It was removed in `cd97e97`, before the presence rewrite
   brought Redis code back.
2. **Two test classes are stale.** `UserStatusServiceTest` calls `updateUserStatus(...)`, removed in
   the Redis presence rewrite. `MessageServiceTest` declares 4 `@Mock`s but `MessageService` now
   takes 6 constructor args — `MessageEventPublisher` and `MessagePersistenceService` inject as
   `null`.

This is why `build_jar_command.txt` uses `-x test -x pmdMain -x pmdTest`. Prefer fixing the cause
over propagating the workaround.

## Architecture

Spring Boot monolith + React SPA. PostgreSQL is the system of record, Redis holds ephemeral presence,
Kafka is an optional message path. Real-time is STOMP over native WebSocket.

Detailed docs live in `docs/` (ARCHITECTURE, IMPLEMENTATION, WEBSOCKETS, KAFKA, REDIS). Read those
before changing the messaging or presence layers. `docs/BUGS.md` is the numbered defect catalog
(B01–B50). When you fix one, reference its ID in the commit and remove or mark it in that file.
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
topic record, and any future consumer-side insert.

> **The `kafka` profile loses data.** There is no `@KafkaListener` anywhere, and persistence is a
> no-op under that profile, so messages are broadcast live and never stored. Do not use it for
> anything real until a consumer exists.

`app.kafka.enabled` exists in both profile properties files but is read by nothing — profile
selection alone drives wiring. `KafkaProducerConfig` defines an explicit `ProducerFactory`, which
suppresses Boot's auto-config and therefore **silently ignores every `spring.kafka.producer.*`
property**; bootstrap servers are hardcoded to `localhost:9092`.

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

`SUBSCRIBE` validates only the destination *prefix*. **There is no per-channel authorization** — the
membership check is commented out at `WebSocketAuthInterceptor.java:119-128`, so any authenticated
user can subscribe to any channel ID. `ChannelService.checkUserIsMember` still exists, but it is
wrong for DMs (see below).

`ChatArea.tsx:20` calls `registerGroupMessageSocket` in the **render body**, so every render adds a
STOMP subscription that is never removed. The Redux reducer's de-duplication hides the duplicate
frames. When you touch subscriptions, put them in an effect with an unsubscribe cleanup.

### DMs

DMs are ordinary `Channel` rows with `type = DM` and `dmKey = "dm-{minId}-{maxId}"`, attached to the
**hard-coded server ID 1** (`ChannelService.getOrCreateDmChannel`). Server-membership checks are
therefore meaningless for DMs. `DmChannel`/`DmChannelRepository` exist but are unused. The DM
recipient on send is the client-supplied `MessageRequest.receiver` username, which is not validated.

### Auth and authorization

Stateless JWT (**HS512**; jjwt picks the algorithm from the 64-byte key; subject = user ID).
`JwtAuthenticationFilter` is deliberately non-blocking: on an invalid token it logs and continues,
leaving rejection to `.anyRequest().authenticated()`. `JwtService.validateToken` **throws** rather
than returning `false`. It can only return `true` or throw, so `if (!validateToken(...))` guards are
unreachable, both in the filter and in the STOMP interceptor.

`Principal.getName()` is the **username** (not the ID), which is why `/user/**` destinations and
`MessageRequest.receiver` key on username strings.

Resource authorization is ad hoc, per service method (`ServerService.isUserMember`/`isUserAdmin`).
There is no `@PreAuthorize` anywhere. Several paths have **no** check: message send/read/edit, server
member add/remove, channel subscribe, and presence reads. Do not assume a new endpoint inherits a
check; add it explicitly.

**Never return JPA entities from controllers.** `User.password` has no `@JsonIgnore`, and
`Server.owner` is EAGER, so any `User` or `Server` in a response leaks a BCrypt hash. Several
existing endpoints already do this (`UserController`, `ServerController`, `InviteController`).
`LoginRequest` is `@Data`, and `AuthController` logs it, which writes plaintext passwords to the log.

Frontend token refresh is broken. `services/api.ts` posts to `/api/refresh-token` with no body,
while the backend route is `/api/auth/refresh` with `{refreshToken}`.

## Conventions and gotchas

- **Port is 8080** (`application.properties`), not 8082 as the root README claims.
- **`application1.properties` is dead config** — Spring loads `application.properties` and
  `application-{profile}.properties` only. It contains a `spring.kafka.consumer.group-id` that reads
  as live but is never applied.
- **`spring.cache.type=redis` is inert** — there is no `@EnableCaching` or `@Cacheable` anywhere. All
  Redis access is explicit via `StringRedisTemplate`.
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
- **Inert settings** (look live, do nothing): `spring.websocket.*`, `spring.h2.*` (no H2 dependency),
  `spring.cache.*`, `app.kafka.enabled`, `spring.kafka.producer.*`.
- **Entities use Lombok `@Data`**, which generates `equals`/`hashCode` over lazy associations. Avoid
  adding entities to `HashSet`s.
- **`dto/` and `payload/` both exist** and both contain a `UserDTO`. `MessageService` uses the
  `payload` one.
- **Schema is `ddl-auto=update`** — no Flyway/Liquibase, no migrations.
- `presence:last_seen:{id}` is written but never read and has no TTL. The variable named `lastSeen`
  in `resolveStatus` actually reads the *heartbeat* key.
- `DataInitializer` saves `testuser` with a **plaintext** password by bypassing
  `UserService.createUser` (where BCrypt is applied), so that seeded account can never log in. To
  test locally, register a user through `/api/auth/register`. The seeder creates "Default Server",
  which must be ID 1 for DMs to work, and it creates no `Member` rows.
- **Dead frontend files:** `components/chat/ChatArea copy.tsx`, `services/StatusProvider.tsx`,
  `src/test_command`. Nothing imports them, so ignore them when tracing behaviour.
- **`/user/queue/errors` has no frontend subscriber.** Server-side error pushes (Kafka send failure,
  STOMP JWT errors) are invisible to users.
- **Committed build junk:** `build/libs/*.jar` (stale, 50 MB), root `node_modules/`, `.DS_Store`,
  `data/*.db` (H2), and two unused compose files. `.gitignore` lists `build/`, `.DS_Store`, and
  `node_modules/`, but these files are already tracked, and ignore rules do not untrack files
  (`git rm --cached` does).

## Branches

`main` and `origin/feature/redis` are **tree-identical** — `main` is the merge commit of PR #6.
`origin/feature/kafka` (merged as PR #5) is an ancestor of `main` and represents the pre-Redis state.
Presence there was connection-scoped and DB-backed. Kafka code is identical on all three branches;
only presence and the frontend WebSocket plumbing differ. The `docs` branch adds `docs/` and this
file.

## Committed secrets

`build.gradle:29` contains a SonarCloud token and `application.properties:56` the JWT signing secret.
Both are in git history. If touching either file, do not propagate them — and flag to the user that
the Sonar token needs revoking, not just deleting.
