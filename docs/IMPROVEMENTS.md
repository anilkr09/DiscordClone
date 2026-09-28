# Improvements

Changes that would make DiscordClone more secure, more correct by construction, easier to scale, and
easier to work on. Defects are catalogued separately in [BUGS.md](BUGS.md). This document covers
what to build or change so that whole classes of those bugs cannot recur. When an improvement
subsumes a bug fix, the bug IDs are listed.

Each item is tagged with a **priority** and a rough **effort**:

| Priority | Meaning | | Effort | Meaning |
|---|---|---|---|---|
| **Now** | Do before any other feature work | | **S** | Under a day |
| **Next** | Do in the following iteration | | **M** | A few days |
| **Later** | Worth doing once the basics are solid | | **L** | A week or more |

## Roadmap at a glance

| Phase | Goal | Items |
|---|---|---|
| **0 — Stop the bleeding** | No exploitable holes; green build | DTO boundary, authorization layer, secret rotation, build and test repair |
| **1 — Correct by construction** | Make the fixed bugs impossible to reintroduce | Authorization tests, generated API client, `Instant` timestamps, error model, migrations |
| **2 — Reliable real-time** | Presence and delivery that survive restarts and reconnects | Presence redesign, WebSocket subscription manager, after-commit events, token lifecycle |
| **3 — Scale and operate** | More than one instance; observable; deployable | Broker relay or Kafka fan-out, Actuator and metrics, CI/CD, container hardening |

---

## Security and authentication

### Enforce a DTO boundary on every controller — Now · M

*Subsumes B01, B02, B05, B36, B39.*

Controllers should never accept or return a JPA entity. Introduce request and response records per
endpoint, and map them explicitly or with MapStruct:

```java
public record UserSummary(Long id, String username) {}
public record UpdateMessageRequest(@NotBlank @Size(max = 4000) String content) {}
```

Then **lock it in with an architecture test** so the mistake cannot come back:

```java
@ArchTest
static final ArchRule controllersDoNotExposeEntities =
    methods().that().areDeclaredInClassesThat().areAnnotatedWith(RestController.class)
        .should().notHaveRawReturnType(annotatedWith(Entity.class));
```

Add `@JsonIgnore` on `User.password` as defence in depth, even once every controller returns DTOs.

### Replace ad hoc checks with a central authorization layer — Now · M

*Subsumes B03, B04, B12, B15, B31.*

Authorization today is an opt-in call inside individual service methods, so every new endpoint
starts out open. Move it into two central places:

- **HTTP:** method security is already enabled (`@EnableMethodSecurity`) but unused. Add a
  `PermissionService` bean and annotate the endpoints:

  ```java
  @PreAuthorize("@perm.isChannelMember(#channelId, principal.id)")
  @GetMapping("/channels/{channelId}")
  ```

- **STOMP:** `spring-security-messaging` is already a dependency. Replace the hand-rolled
  `SUBSCRIBE` and `SEND` checks with Spring Security 6's `@EnableWebSocketSecurity` and an
  `AuthorizationManager<Message<?>>`. That handles destination-level rules declaratively, and a
  custom `AuthorizationManager` can check channel membership for `/topic/channels/{id}/**`. Be aware
  that `@EnableWebSocketSecurity` enforces a CSRF token on `CONNECT` by default. For a token-based
  SPA, supply a no-op `csrfChannelInterceptor` bean deliberately rather than stumbling into it.

Model DMs with explicit participants (see [Data model](#data-model-and-persistence)) so the same
`isChannelMember` check works for them too.

### Rotate secrets and move them out of the repository — Now · S

*Subsumes B07.*

- Revoke the SonarCloud token and rotate the JWT secret.
- Read both, plus the database and Redis credentials, from environment variables:
  `app.jwt.secret=${JWT_SECRET}`.
- Add `gitleaks` (or GitHub secret scanning with push protection) to CI so new secrets are blocked
  at push time.
- Rewriting history with `git filter-repo` is optional once the secrets are rotated, because a
  rotated secret is worthless.

### Redesign the token lifecycle — Next · M

*Subsumes B16, B17, B33.*

| Today | Proposed |
|---|---|
| 24 h access token in `localStorage` | 10–15 min access token held **in memory** |
| 7 d refresh token in `localStorage`, never used | Refresh token in an **`HttpOnly`, `Secure`, `SameSite=Strict` cookie**, scoped to `/api/auth/refresh` |
| Access and refresh tokens are structurally identical | `typ` claim; `/refresh` rejects anything but `refresh` |
| No revocation | Refresh tokens stored (hashed) in Redis with rotation and **reuse detection**: a reused token revokes the whole family |
| Socket session outlives the token | Server schedules a disconnect at `exp`; the client re-`CONNECT`s with a fresh token via `beforeConnect` |

Also add login rate limiting (Bucket4j backed by Redis, keyed by IP and username), a minimum
password policy, and generic "invalid credentials" responses.

### Harden the HTTP and WebSocket surface — Next · S

- Restrict `/ws` origins to the CORS allowlist instead of `setAllowedOriginPatterns("*")`.
- Remove `/h2-console/**` from `permitAll` and delete the `frameOptions(sameOrigin)` workaround.
  H2 is not even on the classpath.
- Set STOMP transport limits explicitly: `configureWebSocketTransport` → `setMessageSizeLimit`,
  `setSendBufferSizeLimit`, `setSendTimeLimit`. Delete the inert `spring.websocket.*` properties.
- Add per-user rate limits on `SEND /app/chat.send` (for example, 5 messages per second with a
  burst of 10), again using Redis-backed Bucket4j.
- Stop logging request bodies. Log user IDs, not usernames or emails.

---

## API design

### Publish an OpenAPI spec and generate the frontend client — Next · M

*Subsumes B40 and prevents its recurrence.*

Add `springdoc-openapi`, publish `/v3/api-docs`, and generate TypeScript types and a client with
`openapi-typescript` plus `openapi-fetch` in the frontend build. A route or payload mismatch then
becomes a **compile error** instead of a runtime 404 or 405. This catches the `/refresh-token`,
`/leave`, `/join`, and channel-route mismatches automatically.

### Adopt RFC 7807 problem responses — Next · S

*Subsumes B29, B44.*

Spring 6 has native `ProblemDetail`. Replace the hand-built `ErrorResponse` and the bare
`RuntimeException`s with a small exception hierarchy: `NotFound` → 404, `Forbidden` → 403,
`Conflict` → 409, `Validation` → 400. Map them in one `@RestControllerAdvice`. Never echo
`ex.getMessage()` from an unexpected exception. For STOMP, add a catch-all `@MessageExceptionHandler`
that sends a typed error to `/user/queue/errors`, and have the client subscribe to it.

### Make routes resource-oriented and consistent — Later · M

*Subsumes B39.*

Today `{serverId}` is sometimes a real scope and sometimes ignored. Adopt one shape and version it:

```
/api/v1/servers/{serverId}/channels             GET, POST
/api/v1/channels/{channelId}                    GET, PATCH, DELETE
/api/v1/channels/{channelId}/messages           GET (cursor), POST
/api/v1/messages/{messageId}                    PATCH, DELETE
/api/v1/servers/{serverId}/members              GET
/api/v1/servers/{serverId}/members/@me          DELETE   (leave)
/api/v1/servers/{serverId}/members/{userId}/role PUT
/api/v1/dms                                     POST {recipientId} → channel
```

Merge `UserController` and `UserStatusController` under one clear prefix.

### Validate every input — Next · S

*Subsumes B45.*

Add Bean Validation constraints to every request DTO, including `MessageRequest`
(`@NotBlank @Size(max = 4000) content`, `@NotNull channelId`), and put `@Validated` on the STOMP
controllers. Normalize usernames (trim, and a case policy) at registration. The case-sensitivity
behind B11 disappears once usernames are case-insensitive by construction.

---

## Data model and persistence

### Introduce Flyway and stop using `ddl-auto=update` — Now · M

Generate a baseline migration from the current schema, set `ddl-auto=validate`, and let every later
change be a reviewed migration. This is also where the constraints the code already assumes should
be written down:

```sql
CREATE INDEX idx_messages_channel_ts ON messages (channel_id, timestamp DESC);
CREATE UNIQUE INDEX uq_friendship_pair
  ON friendships (LEAST(sender_id, receiver_id), GREATEST(sender_id, receiver_id));
CREATE INDEX idx_members_user ON server_members (user_id);
ALTER TABLE server_members ADD CONSTRAINT fk_members_server
  FOREIGN KEY (server_id) REFERENCES servers(id) ON DELETE CASCADE;   -- subsumes B23
```

The message history query currently has no supporting index, so it scans the table once the table
is large.

### Use `Instant` for every timestamp — Now · S

*Subsumes B20.*

Replace `LocalDateTime` with `Instant` in `Message`, `Friendship`, `UserStatusEntity`, and `Invite`,
and set `hibernate.jdbc.time_zone=UTC`. Jackson then serializes with a `Z` suffix, and every browser
renders local time correctly.

### Give DMs a real participant model — Next · M

*Subsumes B12, B13, B24.*

Drop the server-1 hack. Either make `Channel.server` genuinely nullable and populate the
already-existing `DmChannel` (`channel_id`, `user1_id`, `user2_id`), or introduce a general
`channel_members` table, which also opens the door to group DMs. Authorization, delivery, and
"who is the recipient" are then all derived from that table on the server. The client supplies
only the channel ID.

### Clean up the entity layer — Next · S

- Replace `@Data` on entities with `@Getter @Setter` and ID-based `equals`/`hashCode`.
- Add `@Version` to `Invite` (or use the atomic `UPDATE … WHERE uses < max_uses`) to fix B37.
- Resolve the N+1 in friend lists with `JOIN FETCH` or `@EntityGraph`.
- Replace offset pagination on history with **cursor pagination** (`?before=<messageId>&limit=50`),
  which stays fast on the new index and does not shift when new messages arrive.
- Consider soft deletes (`deleted_at`) for servers, channels, and messages. Chat products usually
  need "message deleted" tombstones rather than holes.

---

## Messaging pipeline

### Decide whether Kafka earns its place — Next · S (decision)

*Subsumes B10, B19.*

Today Kafka adds a broker, a profile, and a data-loss mode, and it buys nothing. There is no
consumer, and it does not help with multi-instance fan-out while the STOMP broker is in-memory.
Choose one:

- **A. Remove it for now (recommended until scale demands it).** Delete the `kafka` profile and its
  three classes. Keep the `MessageEventPublisher` seam, which is well designed, and implement it with
  after-commit Spring events.
- **B. Finish it properly.** See below.

### If keeping Kafka: complete the pipeline — Later · L

- **Transactional outbox** instead of dual writes: insert the message and an `outbox` row in one DB
  transaction, then relay the outbox to Kafka (a polling relay, or Debezium). This removes the "sent
  to Kafka but not stored" and "stored but not sent" failure modes, and it also fixes B28.
- **Two consumer groups.** A shared `persistence` group performs an idempotent insert
  (`INSERT … ON CONFLICT (id) DO NOTHING`, keyed on the producer's UUID). A **per-instance**
  `fanout-{instanceId}` group broadcasts to that instance's local STOMP sessions. This is what makes
  Kafka solve horizontal scaling.
- **A dedicated event schema** (`MessageCreatedV1`) decoupled from the WebSocket DTO, with type
  headers configured intentionally.
- Delete `KafkaProducerConfig` and configure the producer entirely through
  `spring.kafka.producer.*`, so the tuning in `application-kafka.properties` finally applies. Set
  `max.block.ms` low, and add a `DefaultErrorHandler` with a dead-letter topic.

### Broadcast after commit — Now · S

*Subsumes B28.*

Publish a `MessageCreatedEvent` inside the transaction and broadcast from
`@TransactionalEventListener(phase = AFTER_COMMIT)`. This one change fixes the local-profile race,
and it is the natural hook for the outbox later.

---

## Presence

### Replace keyspace notifications with a heartbeat sweeper — Next · M

*Subsumes B14, B25, B41, B43.*

Keyspace notifications are fire-and-forget. They are lost if no subscriber is connected at that
instant, they must be enabled in Redis configuration, and every instance receives every event.
A sorted-set design avoids all three problems:

```
presence:{userId}   HASH  { hb: <ms>, act: <ms>, custom: <status|null>, status: <last broadcast> }
presence:alive      ZSET  member=userId, score=last heartbeat ms
```

- A heartbeat does `HSET presence:{id} hb now` and `ZADD presence:alive now id`.
- A `@Scheduled` sweeper runs every ~5 s, calling a Lua script that atomically does
  `ZRANGEBYSCORE presence:alive -inf (now-30000)` then `ZREM` on each result. `ZREM` returns 1 on
  exactly one instance, so each offline transition is processed **exactly once cluster-wide**,
  without locks or keyspace events.
- Status resolution checks **liveness first**, then the custom override, then engagement. That
  fixes the DND-while-offline bug by ordering.
- One hash per user replaces five loose keys, including the write-only `last_seen`.
- Rehydrate `custom` from Postgres on the first heartbeat after a Redis restart.

### Scope presence to the people allowed to see it — Next · M

*Subsumes B15.*

Fan out each change to the user's friends, and optionally to co-members of shared servers, via
`/user/queue/presence`, and respect blocks. `getFriendIds` already exists. Cache the friend-ID set in
Redis (`SMEMBERS friends:{id}`), invalidated on friendship changes, so each fan-out does not hit
Postgres.

### Make status logic testable — Next · S

`resolveStatus` calls `System.currentTimeMillis()` directly. Inject a `java.time.Clock` so the state
machine can be unit-tested deterministically at every boundary (29 s, 31 s, 299 s, 301 s).

### Fix the client activity signal — Next · S

*Subsumes B26, B27.*

Send `/app/activity` at most every 15–20 s while there is input, regardless of the current status,
and let the server's 5 s throttle absorb the rest. Handle presence frames in the subscription
callback instead of by reading the last array element.

---

## Real-time transport and scaling

### Move off the in-memory broker — Later · L

The simple broker pins the app to one instance. There are two viable paths:

| Option | How | Trade-off |
|---|---|---|
| **STOMP broker relay** | `enableStompBrokerRelay("/topic", "/queue")` → RabbitMQ with the STOMP plugin | Minimal code change; adds RabbitMQ; the relay handles user destinations across instances |
| **Pub/sub fan-out** | Keep the local broker and bridge instances via Redis pub/sub or Kafka per-instance groups | No new broker if Redis/Kafka are kept; more code to own |

Whichever is chosen, keep sessions stateless so any instance can serve any client. That is already
true for JWT auth.

### Enable STOMP heart-beats and tune the channels — Next · S

*Subsumes B33.*

```java
config.enableSimpleBroker("/topic", "/queue")
      .setTaskScheduler(heartbeatScheduler())
      .setHeartbeatValue(new long[]{10_000, 10_000});
registration.taskExecutor().corePoolSize(8).maxPoolSize(32);   // clientInbound/outbound
```

Also expose `WebSocketMessageBrokerStats` through metrics (see [Observability](#observability)).

### One envelope for every WebSocket event — Next · S

Friend events use `WsEvent{type, payload}`, presence uses a bare map, and messages use a bare DTO.
Use `WsEvent` everywhere, add a `version` field, and generate the TypeScript union type from the
backend enum. Delete the unused constants (`WsDestinations.PRESENCE`/`MESSAGES`, `USER_ONLINE`,
`USER_OFFLINE`), and either emit or remove `CHANNEL_DELETED`, `CHANNEL_UPDATED`, and the friend
cancel events (B49).

---

## Frontend

### A single WebSocket subscription manager — Now · M

*Subsumes B18, B30, B47.*

Centralize every subscription in one module with **reference counting**. `subscribe(dest, handler)`
returns an unsubscribe function, the manager opens the underlying STOMP subscription on the first
subscriber and closes it after the last, and it re-subscribes automatically after a reconnect.
Expose it through a hook that always cleans up:

```ts
useStompSubscription(`/topic/channels/${id}/messages`, onMessage);   // subscribes in an effect
```

This removes `registerGroupMessageSocket`, the unbounded `messageStore` array, and the
last-element-read pattern in one move.

### One source of truth for server state — Next · M

Redux (messages), React Query (servers, channels, friends), and Context (presence) all hold server
data. Keep React Query as the single cache:

- Message history becomes `useInfiniteQuery` with cursor pagination, which also delivers the missing
  scroll-back.
- WebSocket events update the cache with `queryClient.setQueryData`, which is already done for
  friends.
- Optimistic sends use a `tempId`. `replaceOptimisticMessage` already exists in the slice but is
  never used.
- On logout, call `queryClient.clear()` (B34).

Redux can then be removed entirely.

### Token handling and API client — Now · S

*Subsumes B16.*

Adopt the generated client described under [API design](#api-design). Handle refresh in a
**401-response** interceptor with a single in-flight refresh promise, rather than by decoding the JWT
before each request. Use `jwt-decode`, which is already installed, instead of the hand-written
decoder. Keep the access token in memory.

### Surface errors to the user — Next · S

*Subsumes B32.*

`react-hot-toast` is installed. Subscribe to `/user/queue/errors`, show toasts for failed sends and
failed requests, keep unsent text in the input, and add a React error boundary around each route.

### Type safety and hygiene — Next · S

- Remove `|| true` from the build script and enable `"strict": true`. Type errors should fail the
  build.
- Replace `any` in hooks and handlers with generated types.
- Delete dead files: `ChatArea copy.tsx`, `services/StatusProvider.tsx`, `src/test_command`.
- Remove unused dependencies: `socket.io-client`, `sockjs`, `sockjs-client`, `framer-motion`,
  `react-icons`, `react-query` (v3). Either use `jwt-decode` or remove it.
- Remove the 89 `console.log` calls, or gate them behind a `debug` flag. Several log user
  identifiers and message payloads.
- Move hard-coded hex colors (`#313338`, `#5865f2`, …) into the MUI theme palette.
- Rename `CreateChannelModel` and `CreateServerModel` to `…Modal`.

### Performance and UX — Later · M

- Virtualize the message list (`react-virtuoso`) once history pagination exists.
- Validate env at startup (fail fast if `VITE_API_BASE_URL` is missing or ends in `/api`).
- Accessibility: `aria-label` on icon-only buttons (send, invite, add channel), keyboard navigation
  in the server rail and channel list, and visible focus states.

---

## Testing

The six existing unit test classes only cover the service layer, and two of them no longer compile
(B09). A pyramid that would have caught most of [BUGS.md](BUGS.md):

| Layer | Tooling | What it should cover | Would have caught |
|---|---|---|---|
| Unit | JUnit 5, Mockito, injected `Clock` | `resolveStatus` boundaries; publisher selection; DTO mapping | B25, B26, B43 |
| Web slice | `@WebMvcTest` + `spring-security-test` | **An authorization matrix**: every endpoint × {owner, member, stranger, anonymous} | B01–B05, B22, B29 |
| Persistence | `@DataJpaTest` + Testcontainers Postgres | Derived queries, constraints, cascades, migrations | B21, B23 |
| Integration | `@SpringBootTest` + Testcontainers Redis/Kafka | Presence expiry end to end; Kafka round trip | B10, B14 |
| WebSocket | `WebSocketStompClient` against a random port | CONNECT auth, SUBSCRIBE authorization, DM delivery to the right user | B04, B11, B12 |
| Architecture | ArchUnit | No entities in controllers; no `System.out`; layering rules | B01, B02 |
| Frontend unit | Vitest + React Testing Library + MSW | Hooks, reducers, the subscription manager | B18, B27, B30 |
| E2E | Playwright, **two browser contexts** | Register two users, befriend, DM each other, observe presence | B11, B14, B16 |

A two-user Playwright test alone would have caught the most visible defect in the app: DMs not
arriving (B11).

---

## Observability

### Replace the hand-rolled health endpoint with Actuator — Next · S

Add `spring-boot-starter-actuator`. Expose `/actuator/health` with DB, Redis, and Kafka indicators,
plus liveness and readiness groups for the orchestrator. Retire `HomeController`'s `/health` and
the stale `/api` listing.

### Metrics, logs, traces — Later · M

- **Metrics** (Micrometer → Prometheus): active STOMP sessions, inbound/outbound channel queue depth
  (from `WebSocketMessageBrokerStats`), messages sent per second, presence transitions per second,
  Kafka send latency and failures.
- **Logs:** structured JSON, `userId` and `sessionId` in MDC, and INFO by default. Drop
  `show-sql=true`, which duplicates `org.hibernate.SQL=DEBUG`, and the DEBUG levels on
  `DispatcherServlet` and `com.discordclone`. Remove emoji log prefixes and every
  `System.out.println` (for example, in `ServerService.getServerById`).
- **Tracing:** OpenTelemetry via Micrometer Tracing, so an HTTP request, its DB calls, and its
  Kafka send share one trace.

---

## CI and delivery

### A working pipeline — Now · M

*Subsumes B38.*

Run on every pull request, not only `workflow_dispatch`:

1. **Backend:** `./gradlew build` (tests, PMD, JaCoCo) with Gradle caching.
2. **Frontend:** `npm ci && npm run lint && tsc --noEmit && npm run build`.
3. **Security:** CodeQL, dependency review, `gitleaks`, and Trivy on the image.
4. **Image** (on `main` only): `docker/login-action` → `docker/metadata-action` (SHA and semver
   tags) → `docker/build-push-action`. Push the tag that was actually built.

Use current action versions (`actions/checkout@v4`, `actions/setup-java@v4` with `temurin`), store the
Sonar token as a repository secret, and protect `main` so these checks are required.

### Container and compose hardening — Next · S

- **Multi-stage Dockerfile:** build in `gradle:…-jdk17`, run on `eclipse-temurin:17-jre` (not
  `-jdk`) as a non-root user, use Spring Boot layered jars for cache-friendly layers, and add a
  `HEALTHCHECK` on `/actuator/health/liveness`. The current Dockerfile copies whatever is in
  `build/libs`, which today is a stale committed jar.
- **Compose:** add the backend and frontend as services, put `healthcheck`s on every dependency and
  `depends_on: condition: service_healthy`, mount the declared `kafka-data` volume, replace
  `kafka-init`'s `sleep 10` with a health-gated dependency, and load credentials from an untracked
  `.env`. Delete `docker-compose-bkp.yml` and `updated-docker-compose.yml`.

### Dependency management — Next · S

- Enable Dependabot or Renovate for Gradle, npm, Docker, and Actions. GitHub already reports 98
  alerts on `main` (2 critical, 47 high).
- Upgrade Spring Boot from 3.2.2 to a supported 3.x line, and jjwt from 0.11.5 to 0.12.x (the API
  moves to `Jwts.parser().verifyWith(key)`). Move PMD 6.55 to 7.x.
- Remove unused dependencies: the MySQL connector, and the frontend packages listed under
  [Frontend](#frontend).

---

## Configuration

### Typed configuration and honest profiles — Next · S

*Subsumes B50.*

- Bind `app.jwt.*`, presence TTLs, and rate limits with `@ConfigurationProperties` records, validated
  at startup with `@Validated`. Today the presence TTLs (30 s, 60 s, 10 min, 24 h) and thresholds
  (30 s, 300 s, 5 s) are magic numbers scattered through `UserStatusServiceImpl`.
- Add an `application-prod.yml` in which every credential is an environment placeholder.
- Delete settings that do nothing: `application1.properties`, `spring.websocket.*`, `spring.h2.*`,
  `spring.cache.*` (or actually adopt `@Cacheable` for, say, user lookups in the JWT filter), and
  `app.kafka.enabled` (or make it the real switch via `@ConditionalOnProperty`).

---

## Code organization and conventions

- **Package by feature, not by layer.** `auth/`, `messaging/`, `presence/`, `servers/`, `friends/`,
  each with its own controller, service, repository, and DTOs. The current `dto/` vs. `payload/`
  split, with two `UserDTO`s, disappears.
- **One bean per role.** Delete `UserServiceImpl` or make `UserService` an interface (B48).
- **Constructor injection everywhere.** `CustomUserDetailsService` still uses field `@Autowired`.
- **Delete commented-out code.** Large commented blocks in `WebSocketAuthInterceptor`,
  `ChannelController`, and `build.gradle` make it hard to tell intended behavior from abandoned
  behavior. Git keeps the history.
- **Turn the linters on for real.** Re-enable Checkstyle (the config is already committed), set PMD
  `ignoreFailures = false`, and add Spotless for formatting. On the frontend, run ESLint in CI.
- **Clean the repository.** `git rm --cached` the tracked `build/libs/*.jar` (50 MB),
  root `node_modules/`, `.DS_Store`, and `data/*.db`, and extend `.gitignore` with `data/` and
  `*.db`.
- Fix the root `README.md`: the port is 8080, not 8082, and it should point to `docs/`.

---

## Product gaps

These are features the codebase already half-models, and they are natural next steps once the
foundations above are in place:

| Gap | Current state |
|---|---|
| Server member list | `ServerView` renders a hard-coded empty array; `MemberRepository.findMembersByServerId` exists with no endpoint |
| Leave / delete server, delete / rename channel | Hooks exist, no UI, and the client routes are wrong (B40) |
| Edit / delete message | Backend endpoints broken (B05, B21), no UI |
| Message history scroll-back | Only the latest 20 messages are ever loaded |
| Invite landing page | Invite links point to a backend `POST` |
| Unread counts, typing indicators | Not modeled; both fit the existing STOMP layer |
| Avatars | `avatarUrl` is always `""` |
| Attachments | `attachments?: string[]` in the TS type; nothing server-side |
| Voice channels | `ChannelType` has no `VOICE`, though the UI shows a speaker icon for non-text channels |
