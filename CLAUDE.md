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
npm run build      # generates the API types, type-checks, then builds
npm run lint   # existing errors are baselined in eslint-suppressions.json; new ones fail
npm test       # unit tests (Vitest)
npm run test:e2e   # two-browser test; needs the backend on :8080 (PW_CHANNEL=chrome for local Chrome)
```

`VITE_API_BASE_URL` comes from the committed `frontend/.env.development` (`http://localhost:8080`)
and `.env.production` (a Render URL). It must be the origin **without** `/api`, because the
generated paths already start with it. `config/api.ts` derives `WS_BASE_URL` by rewriting the
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
`MessageResponse`, and delegates to a profile-selected `MessageEventPublisher`. Both store the message
through `MessagePersistenceService` inside the same transaction.

- `local` (default): `LocalMessageEventPublisher` broadcasts after the commit.
- `kafka`: `kafka/OutboxMessageEventPublisher` also writes an `outbox` row. `OutboxRelay` publishes
  rows in order and deletes them once acknowledged (one instance at a time, via an advisory lock).
  Each instance's `MessageFanOut`, in its own `fanout-{instance-id}` group, delivers to its sockets.
  Events can repeat but are not lost.

`Message.id` is an app-generated `String` UUID, so it is stable across the row, the broadcast and the
topic record.

The `consumer` module (`./gradlew :consumer:bootRun`, or `docker compose --profile kafka up -d`)
also persists events with `INSERT ... ON CONFLICT DO NOTHING`, retrying and then dead-lettering to
`message-events-dlt`; health and metrics are on port 8081. The event type is
`contract/MessageCreatedV1`, shared by both sides: add only optional fields, and make a new version
for anything else. The old `message-consumer-service` repository is superseded.

The producer is configured entirely by `spring.kafka.producer.*` in `application-kafka.properties`.

### Presence (Redis)

Status is derived, never stored as truth: `presence/PresenceState.resolve` checks liveness first,
then the user's chosen status, then recent activity. `presence/PresenceStore` documents the keys.
Departures are found by `PresenceSweeper` (every second) with one atomic script: silence for 30 s, or
8 s after the last socket closes. No Redis keyspace notifications are used. Changes go to the user's
friends on `/user/queue/presence` (`PresenceNotifier`, friend names cached by `FriendDirectory`).
Timings are `app.presence.*`; time comes from an injected `Clock`.

### WebSocket layer

STOMP endpoint `/ws`, **in-memory simple broker** (`/topic`, `/queue`), app prefix `/app`, user
prefix `/user`. The broker is local to each instance, so pushes and sign-outs go
through `cluster/ClusterBus`: they are published to Redis and every instance delivers to its own
sockets. Push through `ChatBroadcaster` or `ClusterBus`, not `SimpMessagingTemplate`, or only one
instance's clients receive it
(the exception is `kafka/MessageFanOut`, which already runs on every instance).

Every pushed frame is a `WsEvent{type, version, payload}`; destinations are listed in
`WsDestinations`, and the client's type names are generated from `WsEventType`.

The access token is checked on STOMP `CONNECT` in `WebSocketAuthInterceptor`, which binds the
principal and refuses frames after the token expires. The token travels as a STOMP header because
browsers cannot set HTTP headers on a WebSocket handshake — hence `/ws/**` is `permitAll()`.

Destination rules are in `security/StompSecurityConfig` (Spring Security message authorization).
Clients may send only to `/app/**`; subscribing to `/topic/channels/{id}/**` requires channel
membership; anything not listed there is denied, so **a new destination must be added to that file**.
A refused frame ends the STOMP session.

On the client, `src/realtime/` owns the one connection. Subscribe with `useStompSubscription`, which
is reference-counted and cleans up; apply events in `realtime/events.ts`. Server state lives only in
React Query (there is no Redux); message history is `features/messages/`.

### Running and observing

`docker compose --profile app up -d --build` runs two backend replicas behind nginx
(`ops/nginx/lb.conf`, API on 8080) and the web client on 3000. Liveness and readiness are `/livez`
and `/readyz` on the main port; Prometheus metrics are on the management port 9091, which stays
private. Logs are JSON (`LOG_FORMAT=` for plain text); traces are exported when `TRACING_EXPORT=true`.

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

JWT (**HS512**; jjwt picks the algorithm from the 64-byte key; subject = user ID). Tokens carry
`typ` (access or refresh) and `sid` (the login). Access tokens last 15 minutes and live only in the
client's memory. The refresh token is an HttpOnly cookie for `/api/auth/refresh`, rotated on every
use and tracked in Redis by `security/LoginSessions`; reusing one revokes the login. Calls that rely
on the cookie need the `X-CSRF-Protection` header. `DELETE /api/auth/refresh` signs out.
`StompSessionRegistry` closes sockets at token expiry and sign-out. `security/RateLimiter` holds the
limits (`app.rate-limit.*`).

`Principal.getName()` is the **username** (not the ID), which is why `/user/**` destinations and
`MessageRequest.receiver` key on username strings.

Authorization lives in `security/PermissionService` (bean `perm`). Endpoints call it from
`@PreAuthorize("@perm...")`; **a new endpoint is open to any signed-in user until you annotate it**.
Add it to `AuthorizationMatrixTest` too.

**Never return or bind JPA entities in controllers.** `ControllerBoundaryTest` (ArchUnit) enforces
this.

On the client, `src/api/session.ts` owns the tokens and `src/api/client.ts` refreshes once on a 401.

## API conventions

- Errors are `ProblemDetail`. Throw a subclass of `ApiException` (`NotFoundException`,
  `ForbiddenException`, `ConflictException`, `ValidationException`); anything else becomes a generic
  500. STOMP handler errors go to `/user/queue/errors`.
- Broadcast through `ChatBroadcast` events or `WebSocketPublisher`, so frames are sent only after the
  transaction commits.
- The frontend client is generated: after changing an endpoint or DTO, run
  `./gradlew test -PupdateOpenApi` and commit `frontend/openapi.json`. `npm run build` regenerates
  `src/api/schema.d.ts` and type-checks against it. Call the API through `src/api/client.ts`.
- Some services are interfaces with `service/impl/` implementations; others are concrete classes.

## Conventions and gotchas

- **Port is 8080** (`application.properties`), not 8082 as the root README claims.
- **No caching layer** — there is no `@EnableCaching` or `@Cacheable` anywhere. All Redis access is
  explicit via `StringRedisTemplate`.
- **`UserController` and `UserStatusController` share `/api/users`.** Spring resolves the overlap by
  preferring literal segments over templates; adding a `GET /{id}/{x}` route to either will break it.
- **`dto/` and `payload/` both exist** and both contain a `UserDTO`. `MessageService` uses the
  `payload` one.
- `DataInitializer` runs only with `app.seed.enabled=true` and `SEED_PASSWORD`. To test locally you
  can also register through `/api/auth/register`.
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
