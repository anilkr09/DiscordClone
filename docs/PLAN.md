# Implementation Plan

This plan orders the work in [IMPROVEMENTS.md](IMPROVEMENTS.md) into milestones, and gives each
defect in [BUGS.md](BUGS.md) (B01–B60) and each new finding in
[SECURITY.md §15](SECURITY.md#15-new-findings) (S1–S7) one owning milestone. IMPROVEMENTS.md explains
*why* and *what*. This file says *in which order*, and *when a piece of work counts as done*.

**Decisions this plan assumes:**

- **Kafka stays.** The `kafka` profile is made operable rather than removed. Option A in
  [IMPROVEMENTS.md](IMPROVEMENTS.md#decide-whether-kafka-earns-its-place--next--s-decision).
  Milestones M2 and M7 therefore include work in the
  [`message-consumer-service`](https://github.com/anilkr09/message-consumer-service) repository.
- **The whole roadmap is covered.** M0–M6 are specified in detail. M7–M9 are outlined, and should be
  refined before they start.

## Working rules

- **One branch and one PR per milestone.** Large milestones (M1, M3, M6) may be split into one PR per
  task group, as long as each PR leaves `main` green.
- **Reference bug IDs.** Each commit names the bug IDs it fixes, and marks them fixed in BUGS.md, as
  CLAUDE.md requires. Tick the matching checkbox here in the same PR.
- **Done means green.** A milestone is done only when CI passes and every exit criterion has been
  demonstrated: by an automated test where one is listed, otherwise by a recorded manual check.
- **Fix the cause.** Do not carry a workaround forward. For example, delete the `-x test -x pmdMain`
  flags rather than reusing them.
- **Tests come with the fix.** Every fix in M1 onward lands with the test that would have caught
  it. The table in [IMPROVEMENTS.md § Testing](IMPROVEMENTS.md#testing) says which kind of test.
- **Keep secrets out.** Never copy the committed JWT secret, the Sonar token, or the database and
  Redis passwords into new files, examples, or docs.

## Milestones at a glance

| Milestone | Goal | Effort | Depends on | Owns |
|---|---|---|---|---|
| [M0](#m0--green-build-secrets-out) | The build passes; secrets leave the repository; CI gates PRs | S | — | B07 B08 B09 B38 B50 S6 |
| [M1](#m1--close-the-critical-and-high-security-holes) | No exploitable authorization or data-exposure holes | M | M0 | B01–B06 B11 B36 B39 S1 S2 S3 S5 |
| [M2](#m2--kafka-stop-losing-messages) | The `kafka` profile never silently drops a message | S | M0 | B19 B52 B53 B54 B57 |
| [M3](#m3--data-model-and-migrations) | Schema under migration control; DMs have real participants | M | M1 | B12 B13 B20 B21 B23 B24 B35 B37 B55 |
| [M4](#m4--api-contract-and-error-model) | Typed, validated, generated API with correct status codes | M | M1, M3 | B22 B28 B29 B40 B42 B44 B45 B46 B48 B59 B60 |
| [M5](#m5--sessions-and-tokens) | Sessions that refresh, expire, and revoke correctly | M | M4 | B16 B17 B34 S4 S7 |
| [M6](#m6--reliable-real-time) | Presence and delivery that are correct, scoped, and leak-free | L | M3, M4, M5 | B14 B15 B18 B25 B26 B27 B30 B31 B32 B33 B41 B43 B47 B49 B51 |
| [M7](#m7--complete-the-kafka-pipeline) | The consumer is operated, tested, and contract-bound from here | L | M2, M3 | B10 B56 B58 |
| [M8](#m8--scale-and-operate) | Multi-instance, observable, hardened delivery | L | M6, M7 | — |
| [M9](#m9--product-gaps) | Fill the half-modeled features | ongoing | per item | — |

M2 has no dependency on M1, so it can run in parallel with M1.

```
M0 ─┬─ M1 ── M3 ─┬─ M4 ── M5 ── M6 ─┐
    │            │                  ├─ M8
    └─ M2 ───────┴─ M7 ─────────────┘
```

---

## M0 — Green build, secrets out

**Goal:** `./gradlew build` passes with no exclusions, CI runs on every PR, and no live secret is
read from a tracked file. **Effort:** S. **Owns:** B07, B08, B09, B38, B50, S6. Also makes S2
compile; S2's logic is fixed in M1.

Before any code, the repository owner must do the manual steps in
[Owner-only steps](#owner-only-steps): revoke the SonarCloud token and rotate the JWT secret.

**Status:** done, except that the workflows have not run on GitHub yet (they run on the first pull
request). The build, the secret scan, the compose stack and application startup were verified
locally.

**Build**
- [x] Add `org.springframework.boot:spring-boot-starter-data-redis` to `build.gradle` (B08).
- [x] `UserStatusServiceTest`: rewrite it against the current presence API, replacing the removed
  `updateUserStatus(...)` (B09).
- [x] `MessageServiceTest`: add `@Mock`s for `MessageEventPublisher` and
  `MessagePersistenceService`, so that all 6 constructor arguments are injected (B09).
- [x] Make `security/ChannelAuthorizationService.java` and `MessageController` compile:
  - drop `ChannelType.GROUP_DM`;
  - pass a `String` message ID to `getMessageById`.

  Change only what is needed to compile; M1 fixes the logic (S2).
- [x] Delete `build_jar_command.txt`. The normal command is `./gradlew build`.

**Secrets (B07, S6)**
- [x] In `application.properties`, replace the JWT secret and the database and Redis passwords
  with environment placeholders (`app.jwt.secret=${JWT_SECRET}` and so on). The `local` profile may
  keep obviously fake defaults.
- [x] Remove the Sonar token from `build.gradle:29`, and read it from `SONAR_TOKEN`.
- [x] Add a `.env.example` with placeholder values only. `.env` must be gitignored.

**Repository hygiene (B50)**
- [x] Untrack the build junk with `git rm --cached -r build/ node_modules/ data/` and every
  `.DS_Store`. Add `data/` and `*.db` to `.gitignore`.
- [x] Delete `docker-compose-bkp.yml`, `updated-docker-compose.yml`, and
  `application1.properties`.
- [x] Delete the inert properties: `spring.websocket.*`, `spring.h2.*`, and `spring.cache.*`.
  `spring.kafka.producer.*` and `app.kafka.enabled` are handled in M2.
- [x] Mount the declared `kafka-data` volume, and replace `kafka-init`'s `sleep 10` with a
  health-gated `depends_on`.

**CI (B38)**
- [ ] Add `.github/workflows/ci.yml`, triggered on `pull_request` and on pushes to `main`. It
  needs least-privilege `permissions:`, and has these jobs:
  - backend: `actions/setup-java@v4` (temurin 17), Gradle cache, `./gradlew build`;
  - frontend: `npm ci && npm run lint && npm run build`;
  - secrets: `gitleaks`.
- [ ] Fix `docker-image.yml`:
  - `actions/checkout@v4`;
  - `docker/login-action`, `docker/metadata-action` and `docker/build-push-action`;
  - push the tag that was actually built;
  - run only on `main`.

**Exit criteria**
- `./gradlew build` passes locally and in CI, including tests, PMD and JaCoCo.
- `gitleaks detect --no-git` is clean on the working tree.
- The application starts under the `local` profile with secrets supplied only through the
  environment.

**Notes:** the old secrets remain in git history. Rotating them is what makes that harmless;
rewriting history is optional (see IMPROVEMENTS.md).

---

## M1 — Close the Critical and High security holes

**Goal:** any registered user can reach only their own data and the servers, channels and DMs
they belong to. No credential or hash leaves the server. **Effort:** M. **Owns:** B01, B02, B03, B04,
B05, B06, B11, B36, B39, S1, S2, S3, S5. **Also starts:** B12, with an interim rule; M3 finishes it.

**Status:** done. Deviations from the tasks below: only admins and the owner may add a member (self-joining
needs an invite, because allowing it would let anyone join any server); the authorization matrix
runs against the full application with Testcontainers rather than `@WebMvcTest`; it skips the role
route, which cannot work until B22 is fixed in M4.

**DTO boundary (B01, B02, B05, B36)**
- [x] Add request and response records for every endpoint in `UserController`,
  `ServerController`, `InviteController`, `ChannelController` and `MessageController`. Map them
  explicitly. No controller method accepts or returns an `@Entity`.
- [x] Add `@JsonIgnore` to `User.password` as a backstop.
- [x] Delete `POST /api/users`; registration stays at `/api/auth/register` (B02).
- [x] On message edit, accept an `UpdateMessageRequest(content)`, load the entity, check that the
  caller is the author, and set only `content` (B05).
- [x] Have `ChannelController` return `ChannelDTO` from the single-channel endpoints (B36).
- [x] Add the `com.tngtech.archunit` test dependency, plus a rule that classes annotated
  `@RestController` have no method with an `@Entity` parameter or return type.

**Central authorization: HTTP (B03, B04, B39, S2)**
- [x] Merge `ChannelAuthorizationService` into a single `PermissionService` bean named `perm`.
  It needs `isServerMember`, `isServerAdmin`, `isChannelMember`, `isMessageAuthor` and
  `canManageMembers`. Reuse `ServerService.isUserMember` and `isUserAdmin` rather than
  duplicating their queries.
- [x] Put `@PreAuthorize("@perm…")` on every channel, message, server-member, role and invite
  endpoint. `@EnableMethodSecurity` is already enabled.
- [x] Server membership: adding requires a valid invite, or the caller themselves; removing
  requires the caller themselves, an admin, or the owner (B03).
- [x] Channel routes: verify that `channel.server.id == {serverId}`, and drop `type` from the
  update DTO (B39).
- [x] Throw a 403-mapped exception for authorization failures. The full error model follows in M4.

**Central authorization: STOMP (B04, S1, S2)**
- [x] Use `@EnableWebSocketSecurity` with an `AuthorizationManager<Message<?>>`:
  - `CONNECT` must be authenticated, and keeps the JWT handling already in
    `WebSocketAuthInterceptor`;
  - `SEND` is allowed only to `/app/**` (S1);
  - `SUBSCRIBE` to `/topic/channels/{id}/**` requires `perm.isChannelMember`;
  - `/user/**` is allowed for the session's own user;
  - everything else is denied.
- [x] Provide a no-op `csrfChannelInterceptor` bean, deliberately. The SPA uses bearer tokens, not
  cookies, on `CONNECT`.
- [x] Delete the commented-out membership check and the hand-rolled `SUBSCRIBE` prefix checks.
  Make the interceptor null-safe on the principal.

**DMs, interim rule (B11, B12, S2)**
- [x] Authorize DM channels by participation **before** any server check, parsing the real key
  format `dm-{minId}-{maxId}`. M3 replaces this with a participant table.
- [x] Derive the DM recipient on the server from the channel's other participant. Ignore
  `MessageRequest.receiver`, and reject sends that are blocked either way (B12).
- [x] Frontend: pass `friend.username` (not the capitalized display name) to `MessageInput`
  (B11). This becomes unnecessary once the server derives the recipient, but it fixes live
  delivery right away.

**Logging and exposure (B06, S3, S5)**
- [x] Remove the `LoginRequest` log in `AuthController`. Replace `@Data` on `LoginRequest` with a
  record or `@Getter`, so that no `toString` includes the password (B06).
- [x] Remove the signup `console.log` calls in `AuthProvider.tsx`. Add `esbuild: { drop:
  ['console', 'debugger'] }` to the production config in `vite.config.ts` (S3).
- [x] Bind every published port in `docker-compose.yml` to `127.0.0.1` (S5).

**Exit criteria**
- A `@WebMvcTest` authorization matrix with `spring-security-test` passes. It covers every
  endpoint × {owner, member, stranger, anonymous}, and asserts allow or deny.
- A `WebSocketStompClient` test on a random port shows:
  - a stranger's `SUBSCRIBE` to a channel is rejected;
  - a `SEND` to `/topic/**` is rejected;
  - a DM reaches the correct, mixed-case username.
- The ArchUnit rule passes, and no response body anywhere contains `password`.

---

## M2 — Kafka: stop losing messages

**Goal:** under the `kafka` profile, every produced message is persisted, retried, or
dead-lettered, never dropped, and a broker outage cannot stall WebSocket traffic. **Effort:** S,
across two repositories. **Owns:** B19, B52, B53, B54, B57. See [KAFKA.md](KAFKA.md).

**Status:** done. The consumer was moved into this repository as the `consumer` module (the M7 option),
with the event contract in `contract`, so the fixes below apply there; the separate
`message-consumer-service` repository is superseded and unchanged.

**`message-consumer-service`**
- [x] Register the `DefaultErrorHandler` as a `@Bean`, so that Boot's container factory picks it
  up. Delete the unused factory method (B52).
  - Back-off: exponential, measured in seconds, with a bounded number of attempts.
  - Recoverer: a `DeadLetterPublishingRecoverer` whose `KafkaTemplate` uses Spring Kafka's JSON
    serializer and takes the broker address from properties (B53).
  - Mark not-found failures, for a missing user or channel, as non-retryable.
- [x] Create `message-events-dlt` with 4 partitions, matching `message-events`.
- [x] Set `spring.kafka.consumer.auto-offset-reset=earliest`. Replay is safe because the insert is
  idempotent on the UUID (B54).
- [x] Remove the `application` plugin from `build.gradle`. Confirm that `bootJar` starts (B57).

**This repository**
- [x] Delete `KafkaProducerConfig`, so that Boot's auto-configuration applies the
  `spring.kafka.producer.*` properties. Remove the hard-coded `localhost:9092`.
- [x] Set `spring.kafka.producer.properties.max.block.ms` to about 1500, and run `send` on a
  bounded executor, not on the `clientInboundChannel` worker (B19).
- [x] Delete `app.kafka.enabled` from both profile files. Profile selection stays the only switch.

**Exit criteria**
- Run the consumer against Testcontainers Kafka and PostgreSQL. Stopping the database mid-stream
  causes retries, then dead-lettering. A row count plus a DLT count equals the number of messages
  sent.
- With the broker stopped, heartbeats and other STOMP frames keep flowing, and a send fails within
  about 2 s with an error on `/user/queue/errors`.

---

## M3 — Data model and migrations

**Goal:** the schema is versioned and owned by this application. Timestamps are zone-correct. DMs
have explicit participants, so authorization, delivery and recipient lookup all derive from one
table. **Effort:** M. **Owns:** B12 (final), B13, B20, B21, B23, B24, B35, B37, B55.

**Status:** done. Notes: V3 reads existing local timestamps as UTC. The seeder is now off unless
`app.seed.enabled=true` and `SEED_PASSWORD` are set, because a working seeded account with a known
password would be a backdoor. DMs are opened with `POST /api/dms/{userId}`. The invite landing page
itself is part of M9.

**Migrations (B23, B55)**
- [x] Add Flyway. Generate `V1__baseline.sql` from the current schema, and set
  `spring.jpa.hibernate.ddl-auto=validate`.
- [x] Add `V2__indexes_and_constraints.sql`:
  - `messages (channel_id, timestamp DESC)`;
  - `server_members (user_id)`;
  - a unique friendship pair on `LEAST/GREATEST(sender_id, receiver_id)`;
  - server-member and channel foreign keys to `servers` with `ON DELETE CASCADE` (B23).
- [x] Done in M2: the consumer module uses JDBC and manages no schema.

**Time (B20)**
- [x] Replace `LocalDateTime` with `Instant` in `Message`, `Friendship`, `UserStatusEntity` and
  `Invite`. Migrate the columns to `timestamptz`, and set
  `spring.jpa.properties.hibernate.jdbc.time_zone=UTC`.

**DM participants (B12, B13, B24)**
- [x] Add a `channel_members (channel_id, user_id)` table, and make `Channel.server` nullable for
  `type = DM`. Migrate the existing DM rows from their `dmKey`.
- [x] Rewrite `ChannelService.getOrCreateDmChannel` without server 1. Delete the unused
  `DmChannel` and `DmChannelRepository`.
- [x] Have `PermissionService.isChannelMember` and the DM recipient derivation read
  `channel_members`. Remove the interim `dmKey` parser from M1 (B12, B13).
- [x] Frontend: route DMs without the hard-coded `/channels/1/`, and fix the DM view's loading and
  label states (B24).

**Fixes**
- [x] Fix `DELETE /api/messages/{id}`, so that it can succeed for the author (B21).
- [x] Seeder:
  - create the user through `UserService`, so BCrypt is applied;
  - add the owner's `Member` row;
  - make each seed step independent and idempotent (B35).
- [x] Invites (B37):
  - claim a use with an atomic
    `UPDATE invites SET uses = uses + 1 WHERE code = ? AND uses < max_uses`;
  - return a frontend URL, not the backend `POST` route;
  - reuse an unexpired invite instead of creating one each time the dialog opens.

**Entities**
- [x] Replace `@Data` on entities with `@Getter`/`@Setter`, plus `equals`/`hashCode` based on the
  ID.
- [x] Load friend lists with `JOIN FETCH` or `@EntityGraph` to remove the N+1.
- [x] Use cursor pagination for message history (`?before=<messageId>&limit=50`) on the new index.

**Exit criteria**
- `@DataJpaTest` with Testcontainers PostgreSQL applies all migrations, and checks the
  constraints, the cascades and the derived queries, including the friendship-pair uniqueness and
  server deletion.
- The application starts against an empty database and against a migrated copy of the old schema,
  in both cases under `validate`.
- The M1 authorization matrix still passes, with DM cases now backed by `channel_members`.

---

## M4 — API contract and error model

**Goal:** the API is described by OpenAPI, the frontend client is generated from it, every input is
validated, and every error carries the right status without leaking internals. **Effort:** M.
**Owns:** B22, B28, B29, B40, B42, B44, B45, B46, B48, B59, B60.

**Status:** done, except that only `app.jwt.*` is bound to typed properties here; presence settings are
bound by the M6 redesign and rate limits by M5. The role route is now
`PUT /api/servers/{serverId}/members/{userId}/role`. The generated client is checked by
`OpenApiContractTest` against `frontend/openapi.json`.

**Errors (B29, B44, B60)**
- [x] Add `NotFoundException`, `ForbiddenException`, `ConflictException` and
  `ValidationException`, mapped to 404, 403, 409 and 400 as `ProblemDetail` in one
  `@RestControllerAdvice`. Replace every bare `RuntimeException` in the services.
- [x] The catch-all handler returns a generic 500 and never echoes `ex.getMessage()`.
- [x] Return 404 for unknown URLs: either handle `NoResourceFoundException`, or set
  `spring.web.resources.add-mappings=false` (B60).
- [x] Add a catch-all `@MessageExceptionHandler` that sends a typed error to `/user/queue/errors`.
  Fix the wrong ID in `MessageService`'s not-found message (B44).

**Validation (B45)**
- [x] Add Bean Validation to every request DTO. For example, on `MessageRequest`:
  `@NotBlank @Size(max = 4000)` on `content`, and `@NotNull` on `channelId`.
- [x] Put `@Validated` on the STOMP controllers. Parse the custom-status enum safely, so that a bad
  value is a 400.
- [x] Normalize usernames at registration: trim them, and compare case-insensitively.

**Correctness**
- [x] Broadcast after commit: publish a `MessageCreatedEvent`, and broadcast from
  `@TransactionalEventListener(phase = AFTER_COMMIT)` in the local publisher (B28).
- [x] Fix `PUT /api/servers/{id}/roles/{roleId}`, which declares a `userId` path variable that is
  not in the route (B22).
- [x] Remove the no-op `PUT /api/users/{id}/status`, the stale `GET /api` listing, and the unused
  `Server.type` (B46).
- [x] Keep one `UserService` bean: delete `UserServiceImpl`, keeping its 404 behavior in
  `UserService` (B48).
- [x] Make `AsyncConfig` implement `AsyncConfigurer`, with a bounded `ThreadPoolTaskExecutor` and
  an uncaught-exception handler (B59). Move `persistLastSeen` behind a separate bean, so that
  `@Async` is applied (B42).

**Contract (B40)**
- [x] Add `springdoc-openapi`, and publish `/v3/api-docs`.
- [x] Generate frontend types and a client with `openapi-typescript` and `openapi-fetch` in the
  build. Replace the hand-written `services/*.service.ts` calls, which fixes the `/leave`, `/join`,
  channel and message routes that do not exist.
- [x] Remove `|| true` from the frontend build script, so that type errors fail the build.
- [ ] Bind `app.jwt.*`, the presence TTLs and thresholds, and the rate limits with validated
  `@ConfigurationProperties` records.

**Exit criteria**
- The authorization matrix also asserts status codes: 401 when anonymous, 403 for a stranger, 404
  for a missing resource, and 400 for invalid input.
- The frontend type-checks against the generated client, and CI fails on a route mismatch.
- A test proves that a failed commit broadcasts nothing.

---

## M5 — Sessions and tokens

**Goal:** sessions refresh silently, tokens cannot be misused or replayed, and an open socket
never outlives its authorization. **Effort:** M. **Owns:** B16, B17, B34, S4, S7. **Also does:** the
token half of B33; M6 owns B33 and adds heart-beats.

**Status:** done. Notes: rate limits are a small Redis token bucket rather than Bucket4j; sign-out is
`DELETE /api/auth/refresh`, so the refresh cookie reaches it; an access token issued before
sign-out stays valid for HTTP until it expires (at most 15 minutes); sockets are closed per
instance. A web client on a different site than the API needs `COOKIE_SAME_SITE=None`.

**Server**
- [x] Add a `typ` claim (`access` or `refresh`), plus a `jti`. `/api/auth/refresh` rejects
  anything except `refresh` (B17).
- [x] Rotate refresh tokens. Store them hashed in Redis with a TTL, and on reuse revoke the whole
  token family.
- [x] Access tokens last 15 minutes. The refresh token is set as an `HttpOnly; Secure;
  SameSite=Strict` cookie scoped to `/api/auth/refresh`, with CSRF protection for that one endpoint.
- [x] Make `JwtService.validateToken` return a result instead of throwing, or delete the
  unreachable `if (!validateToken(…))` guards.
- [x] STOMP: store the token's `exp` in the session attributes, reject frames after it, schedule a
  disconnect at `exp`, and close the user's sessions on logout (S4).
- [x] Add Bucket4j rate limits backed by Redis, keyed by IP and username, on login, register,
  refresh, invite join and `chat.send`.
- [x] Set `server.forward-headers-strategy=native`, and confirm that HSTS is sent behind the proxy
  (S7).
- [x] Restrict `/ws` origins to the CORS allowlist for each profile. Remove the `/h2-console/**`
  rules and the `frameOptions(sameOrigin)` workaround.

**Client**
- [x] Keep the access token in memory. Refresh from a 401-response interceptor that shares a single
  in-flight refresh promise. Use `jwt-decode` instead of the hand-written decoder (B16).
- [x] On page load, try a refresh before clearing state. Never delete the refresh token on access
  expiry (B16).
- [x] Use a STOMP `beforeConnect` callback that reads the current token, so that reconnects use a
  fresh one (B33).
- [x] On logout, call `queryClient.clear()`, reset the message store, and remove `currentUser`
  (B34).

**Exit criteria**
- Tests show:
  - an access token is rejected at `/refresh`;
  - a reused refresh token revokes its family;
  - a STOMP session is closed at `exp` and on logout;
  - the rate limits return 429.
- A browser session stays logged in past 15 minutes, across a WebSocket reconnect.
- Logging out and logging in as another user shows none of the previous user's data.

---

## M6 — Reliable real time

**Goal:** presence is correct, timely and scoped to the people allowed to see it. The client holds
exactly one subscription per destination, and failures are visible. **Effort:** L. **Owns:** B14,
B15, B18, B25, B26, B27, B30, B31, B32, B33, B41, B43, B47, B49, B51. See [REDIS.md](REDIS.md) and
[WEBSOCKETS.md](WEBSOCKETS.md).

**Presence redesign (B14, B25, B41, B43)**
- [ ] Replace the loose keys with a `presence:{userId}` hash (`hb`, `act`, `custom`, `status`) and
  a `presence:alive` sorted set, scored by the last heartbeat.
- [ ] Add a `@Scheduled` sweeper, running every about 5 s, that calls a Lua script doing
  `ZRANGEBYSCORE … (now-30000)` and then `ZREM`. Only the instance whose `ZREM` returns 1
  broadcasts OFFLINE, so each transition fires exactly once (B14, B41).
- [ ] Remove `PresenceExpirationListener` and the dependency on keyspace notifications.
- [ ] `resolveStatus` checks **liveness first**, then the custom override, then engagement (B25).
- [ ] Delete `presence:last_seen:*`, the unreachable `seenDiff` branch, and `IdleProvider`'s
  log-only `markIdle`. Rehydrate `custom` from Postgres on the first heartbeat after a Redis
  restart (B43).
- [ ] Inject `java.time.Clock`. Unit-test `resolveStatus` at 29 s, 31 s, 299 s and 301 s.
- [ ] Add the clean-disconnect grace period from
  [REDIS.md §10.14](REDIS.md#1014-finding-14--mark-offline-on-a-clean-disconnect-with-a-grace-period),
  adapted to the sorted-set design (B51).

**Scoping (B15, B31)**
- [ ] Fan each presence change out to the user's friends on `/user/queue/presence`, respecting
  blocks. Cache the friend-ID set in Redis, and invalidate it on friendship changes. Retire the
  global `/topic/status` (B15).
- [ ] `GET /api/users/{id}/status` requires friendship or a shared server (B15).
- [ ] Send channel events on a per-server topic, gated by the M1 `SUBSCRIBE` membership check
  (B31).

**Transport (B33, B49)**
- [ ] Give the simple broker a `TaskScheduler` and 10 s/10 s heart-beats. Size the inbound and
  outbound channel pools explicitly (B33).
- [ ] Set the STOMP transport limits (message size, send buffer, send time).
- [ ] Use one versioned `WsEvent{type, version, payload}` envelope for messages, presence, friends
  and channels. Generate the TS union from the backend enum.
- [ ] Emit `CHANNEL_UPDATED` and `CHANNEL_DELETED`, or remove them. Delete the unused constants
  (B49).

**Frontend (B18, B26, B27, B30, B32, B47)**
- [ ] Add a ref-counted subscription manager with a `useStompSubscription(dest, handler)` hook.
  It opens the subscription on the first subscriber, closes it after the last, re-subscribes after
  a reconnect, and always cleans up in an effect.
- [ ] Remove `registerGroupMessageSocket` from `ChatArea`'s render body, the unbounded
  `messageStore`, and every last-element read (B18, B27, B30, B47).
- [ ] Reset `isConnecting` on errors (B47).
- [ ] De-duplicate `CHANNEL_CREATED` by ID (B30).
- [ ] Send `/app/activity` every 15–20 s while there is input, regardless of the current status
  (B26).
- [ ] Subscribe to `/user/queue/errors`. Show `react-hot-toast` toasts on failed sends and failed
  requests. `sendMessage` returns success, and the input keeps unsent text (B32).
- [ ] Add a React error boundary around each route.
- [ ] Keep server state in React Query only:
  - message history uses `useInfiniteQuery` with the M3 cursor;
  - WebSocket events update it with `setQueryData`;
  - optimistic sends use a `tempId`;
  - then remove Redux.

**Exit criteria**
- A Testcontainers Redis integration test shows OFFLINE pushed exactly once, within 35 s of an
  unclean drop and within 10 s of a clean close. A reload in between does not flicker OFFLINE.
- A stranger receives no presence or channel events for users and servers they are not connected
  to.
- A Vitest test shows the subscription manager holds one subscription per destination across
  re-renders and remounts.
- A Playwright test with **two browser contexts** registers two users, befriends them, sends DMs
  both ways, and observes ONLINE → OFFLINE.

---

## M7 — Complete the Kafka pipeline

**Goal:** the consumer is started, tested, monitored and contract-checked from this repository, and
Kafka is the path to horizontal scale. **Effort:** L. **Owns:** B10, B56, B58. *Outline; refine
before starting.*

- [ ] Bring the consumer in. The preferred option is to move it into this repository as a Gradle
  module, which also aligns it on one Spring Boot major version. At a minimum, add it to
  `docker-compose.yml` with a health check and a documented start order (B10).
- [ ] Consumer operations (B58):
  - Actuator liveness and readiness;
  - a Testcontainers context test in place of the live-services `contextLoads`;
  - `getReferenceById` plus `Persistable` to cut the four queries per message down to two;
  - `JacksonJsonDeserializer`;
  - delete the empty `KafkaErrorHandler` and the dead null branch.
- [ ] Make the event contract a single versioned `MessageCreatedV1` in a shared module, decoupled
  from the WebSocket `MessageResponse`, so that an incompatible change fails the build.
- [ ] Replace the dual write with a transactional outbox: a message row and an `outbox` row in one
  transaction, then a relay to Kafka.
- [ ] Use `INSERT … ON CONFLICT (id) DO NOTHING` for the idempotent insert.
- [ ] Add a per-instance `fanout-{instanceId}` consumer group that broadcasts to the local STOMP
  sessions.
- [ ] Alert on consumer-group lag. The client keeps live-received messages until history confirms
  them (B56).

**Exit criteria:** the consumer persistence suite passes against Testcontainers Kafka and
PostgreSQL. It covers the happy path, a duplicate delivery, a database outage (retried, then
dead-lettered), and a malformed record.

---

## M8 — Scale and operate

**Goal:** the application runs as several instances, behind health checks, with metrics, logs and
traces, from hardened images. **Effort:** L. **Owns:** no bug IDs. It carries the remaining items of
IMPROVEMENTS.md. *Outline; refine before starting.*

- [ ] Replace the in-memory broker:
  - either `enableStompBrokerRelay` to RabbitMQ with the STOMP plugin;
  - or the M7 per-instance Kafka fan-out.

  Keep sessions stateless.
- [ ] Add `spring-boot-starter-actuator` with DB, Redis and Kafka health indicators and
  liveness/readiness groups. Retire `HomeController`'s `/health`.
- [ ] Metrics with Micrometer and Prometheus:
  - STOMP sessions and channel queue depth, from `WebSocketMessageBrokerStats`;
  - messages per second, presence transitions, and Kafka send latency and failures.
- [ ] Logs and tracing:
  - structured JSON logs, with `userId` and `sessionId` in the MDC, at INFO by default;
  - drop `show-sql`, the emoji prefixes and every `System.out.println`;
  - OpenTelemetry through Micrometer Tracing.
- [ ] Docker:
  - a multi-stage Dockerfile: build in `gradle:…-jdk17`, run on `eclipse-temurin:17-jre` as a
    non-root user;
  - layered jars, and a `HEALTHCHECK` on liveness.
- [ ] Compose: run the backend and frontend as services, with `healthcheck` plus
  `depends_on: service_healthy` everywhere. Load credentials from an untracked `.env`.
- [ ] Dependencies:
  - Dependabot or Renovate for Gradle, npm, Docker and Actions;
  - upgrade Spring Boot 3.2.2 to a supported 3.x, jjwt 0.11.5 to 0.12.x
    (`Jwts.parser().verifyWith`), and PMD 6.55 to 7.x;
  - remove the MySQL connector and the unused frontend packages (`socket.io-client`, `sockjs`,
    `sockjs-client`, `framer-motion`, `react-icons`, `react-query` v3);
  - add CodeQL, dependency review and Trivy to CI.
- [ ] Code quality:
  - re-enable Checkstyle, set PMD `ignoreFailures = false`, and add Spotless;
  - TS `"strict": true`, with generated types replacing `any`;
  - delete the dead frontend files (`ChatArea copy.tsx`, `services/StatusProvider.tsx`,
    `src/test_command`), and the `console.log` calls;
  - move hard-coded colors into the MUI theme, and rename the `…Model` components to `…Modal`.
- [ ] Code organization:
  - constructor injection everywhere;
  - delete the commented-out code;
  - fix the root README (port 8080, and a link to `docs/`);
  - optionally, package by feature, which also merges the `dto/` and `payload/` `UserDTO`s.
- [ ] Accessibility and performance:
  - `aria-label` on icon-only buttons, keyboard navigation, and visible focus states;
  - virtualize the message list with `react-virtuoso`;
  - fail fast on a missing or `/api`-suffixed `VITE_API_BASE_URL`.

**Exit criteria:** two backend instances behind a load balancer deliver channel messages, DMs and
presence between users connected to different instances. Every service reports healthy in compose.

---

## M9 — Product gaps

Features the codebase already half-models. Each one can start once the milestone it depends on is
done.

| Feature | Current state | Can start after |
|---|---|---|
| Server member list | `ServerView` renders a hard-coded empty array; `MemberRepository.findMembersByServerId` exists without an endpoint | M1 |
| Leave or delete a server; rename or delete a channel | Hooks exist with no UI, and the client routes are wrong | M4 (generated client) |
| Edit or delete a message | Backend endpoints broken, no UI | M3 (B05/B21 fixed in M1/M3) |
| Message history scroll-back | Only the latest 20 messages are loaded | M3 (cursor), M6 (`useInfiniteQuery`) |
| Invite landing page | Invite links point to a backend `POST` | M3 (B37) |
| Unread counts, typing indicators | Not modeled | M6 (envelope and subscription manager) |
| Avatars | `avatarUrl` is always `""` | M4 |
| Attachments | `attachments?: string[]` exists in TS only | M4, plus object storage |
| Voice channels | No `VOICE` in `ChannelType` | M8 |

---

## Coverage

Every defect has exactly one owning milestone. "Also" marks a milestone that does part of the work
ahead of the owner.

| ID | Title | Severity | Owner | Also |
|---|---|---|---|---|
| B01 | Password hashes are returned by the API | Critical | M1 | |
| B02 | Any account can be overwritten via `POST /api/users` | Critical | M1 | |
| B03 | Server membership endpoints have no authorization | Critical | M1 | |
| B04 | Channels have no read or write authorization | Critical | M1 | |
| B05 | Any message can be rewritten | Critical | M1 | |
| B06 | Plaintext passwords are written to the log | Critical | M1 | |
| B07 | Signing secret and SonarCloud token are committed | Critical | M0 | |
| B08 | The backend does not compile | Critical | M0 | |
| B09 | Two test classes do not compile | Critical | M0 | |
| B10 | Kafka-profile persistence depends on an unmanaged external service | Medium | M7 | |
| B11 | DMs are delivered to the wrong username | High | M1 | |
| B12 | DM recipient and channel are client-controlled | High | M3 | M1 (interim rule) |
| B13 | DMs are pinned to server 1 | High | M3 | |
| B14 | Presence never reaches OFFLINE | High | M6 | |
| B15 | Presence is visible to everyone | High | M6 | |
| B16 | Token refresh is broken end to end | High | M5 | |
| B17 | Access tokens are accepted as refresh tokens | High | M5 | |
| B18 | `ChatArea` leaks a subscription on every render | High | M6 | |
| B19 | A Kafka outage stalls all WebSocket traffic | High | M2 | |
| B20 | Message times are wrong outside UTC | Medium | M3 | |
| B21 | `DELETE /api/messages/{id}` can never succeed | Medium | M3 | |
| B22 | `PUT /api/servers/{id}/roles/{roleId}` can never succeed | Medium | M4 | |
| B23 | Servers cannot be deleted | Medium | M3 | |
| B24 | DM view hangs or mislabels | Medium | M3 | |
| B25 | Offline users reappear with their custom status | Medium | M6 | |
| B26 | Active users flicker between ONLINE and IDLE | Medium | M6 | |
| B27 | Batched status updates are dropped | Medium | M6 | |
| B28 | Messages are broadcast before they are committed | Medium | M4 | |
| B29 | Errors map to the wrong HTTP status | Medium | M4 | M1 (403 for authorization) |
| B30 | Channels appear twice after revisiting a server | Medium | M6 | |
| B31 | Channel creation is broadcast to every user | Medium | M6 | |
| B32 | Failed sends are silent | Medium | M6 | |
| B33 | WebSocket reconnect uses the original token | Medium | M6 | M5 (`beforeConnect`) |
| B34 | Logout leaves the previous user's data cached | Medium | M5 | |
| B35 | The seeder creates an unusable account and server | Medium | M3 | |
| B36 | Single-channel endpoints return a lazy entity | Medium | M1 | |
| B37 | Invites are racy, unshareable, and over-created | Medium | M3 | |
| B38 | The CI workflow cannot succeed | Medium | M0 | |
| B39 | Channel routes ignore `{serverId}` and allow type changes | Medium | M1 | |
| B40 | Frontend calls routes that do not exist | Medium | M4 | |
| B41 | OFFLINE is broadcast twice | Low | M6 | |
| B42 | `@Async` is bypassed by self-invocation | Low | M4 | |
| B43 | Dead presence logic | Low | M6 | |
| B44 | STOMP handler errors are swallowed | Low | M4 | |
| B45 | Missing input validation | Low | M4 | |
| B46 | No-op and stale endpoints | Low | M4 | |
| B47 | Latent WebSocket client and interceptor defects | Low | M6 | M1 (null-safe principal) |
| B48 | Two `UserService` beans | Low | M4 | |
| B49 | Event types out of sync | Low | M6 | |
| B50 | Infrastructure drift | Low | M0 | M2 (Kafka properties) |
| B51 | Clean disconnects wait for the heartbeat TTL | Medium | M6 | |
| B52 | The consumer drops messages it fails to save | High | M2 | |
| B53 | The consumer's dead-letter setup would fail if wired | Medium | M2 | |
| B54 | `latest` offset reset loses messages | Medium | M2 | |
| B55 | Two services manage one schema | Medium | M3 | |
| B56 | Messages are visible before they are stored | Medium | M7 | |
| B57 | The consumer's packaged jar likely fails to start | Medium | M2 | |
| B58 | Consumer operational gaps | Low | M7 | |
| B59 | `@Async` starts a new thread for every call | Low | M4 | |
| B60 | Unknown URLs return 500 instead of 404 | Medium | M4 | |
| S1 | Clients can publish directly to broker destinations | High | M1 | |
| S2 | The `docs1` channel authorization does not compile and mis-authorizes DMs | High | M1 | M0 (compile fix) |
| S3 | The frontend logs the sign-up password and tokens | Medium | M1 | |
| S4 | WebSocket sessions outlive their token | Medium | M5 | |
| S5 | Development services are exposed on every network interface | Medium | M1 | |
| S6 | Secrets ship inside every jar and image | High | M0 | |
| S7 | HSTS is probably not sent behind the TLS proxy | Low | M5 | |

## Owner-only steps

The repository owner has to do these; code changes cannot.

| When | Step |
|---|---|
| Before M0 | **Revoke the SonarCloud token** committed at `build.gradle:29`. Deleting it from the file is not enough, because it stays in git history |
| Before M0 | **Rotate the JWT signing secret**, and set it as `JWT_SECRET` wherever the app is deployed (Render). Rotating it logs out every current session |
| M0 | Add `SONAR_TOKEN`, `DOCKERHUB_USERNAME` and `DOCKERHUB_TOKEN` as repository secrets |
| M0 | Protect `main`: require the CI checks and a PR |
| M0 | Enable GitHub secret scanning with push protection |
| M2 | Apply the consumer fixes in the `message-consumer-service` repository, or approve moving it into this repository (M7) |
| M5 | For the deployed frontend, set `ALLOWED_ORIGINS`. If it stays on a different site than the API (Vercel and Render), set `COOKIE_SAME_SITE=None`; browsers that block third-party cookies will then still fail, so prefer serving both from one site or proxying `/api` |
