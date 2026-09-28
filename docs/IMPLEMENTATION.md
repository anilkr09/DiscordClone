# Implementation

Package-by-package walkthrough, the full API surface, configuration, local setup, and the defect
list.

## 1. Build and toolchain

| | |
|---|---|
| Language | Java 17 (toolchain-pinned in `build.gradle:16-22`) |
| Framework | Spring Boot 3.2.2 |
| Build | Gradle, `bootJar` → `discord-clone-0.0.1-SNAPSHOT.jar` (plain `jar` disabled) |
| Quality | JaCoCo (XML + HTML), PMD 6.55.0 (`ignoreFailures = true`), SonarQube plugin |
| Frontend | React 18, TypeScript 5.7, Vite 6 |

Checkstyle is configured but entirely commented out (`build.gradle:84-92`, `:101-106`, `:117`), even
though `config/checkstyle/checkstyle.xml` is committed. PMD runs with `ignoreFailures = true`, so
violations never break the build. `check` depends on `pmdMain, pmdTest`.

The build that actually works today is recorded in `build_jar_command.txt`:

```bash
./gradlew clean bootJar -x test -x pmdMain -x pmdTest
```

It skips tests because they do not compile (§7). It will not succeed either until the Redis starter
is restored (§8.1).

The frontend build script is `tsc --noEmit --skipLibCheck || true && vite build`. Because `||` and
`&&` associate left to right, this is `(tsc ... || true) && vite build`: `vite build` always runs,
and **type errors never fail the build**.

### Declared backend dependencies

```groovy
spring-boot-starter-data-jpa, -security, -web, -websocket, -validation
jackson-databind
org.springframework.kafka:spring-kafka
org.postgresql:postgresql:42.7.3
org.springframework.security:spring-security-messaging
io.jsonwebtoken:jjwt-api / -impl / -jackson  (0.11.5)
lombok (compileOnly + annotationProcessor)
com.mysql:mysql-connector-j  (runtimeOnly — unused; PostgreSQL is the datasource)
```

Three problems:

- **`spring-boot-starter-data-redis` is absent** (§8.1).
- The MySQL connector is declared, but nothing uses it.
- There is no H2 dependency, even though H2 settings and H2 database files are present (§2).

### CI

`.github/workflows/docker-image.yml` runs only on `workflow_dispatch`. Its steps are
`./gradlew build`, then `docker build -t discord-backend .`, then
`docker push anil0003/discord-clone:latest`. It cannot succeed as written:

1. `./gradlew build` runs the tests, which do not compile (§7), and the main sources do not compile
   either (§8.1).
2. The image is tagged `discord-backend` but the push targets `anil0003/discord-clone:latest`, a tag
   that was never created.
3. There is no `docker login` step, so the push would be unauthenticated even with the right tag.

It also pins `actions/checkout@v2` and `actions/setup-java@v2` with `distribution: 'openjdk'`, all of
which are deprecated.

## 2. Configuration and profiles

### Files

| File | Loaded? | Contents |
|---|---|---|
| `application.properties` | yes | Datasource, JPA, JWT, Redis, logging, `spring.profiles.active=local` |
| `application-local.properties` | yes, under `local` | `app.kafka.enabled=false` |
| `application-kafka.properties` | yes, under `kafka` | Kafka producer settings (mostly inert — see [KAFKA.md](KAFKA.md#22-application-kafkaproperties-mostly-inert)) |
| `application1.properties` | **no** | Dead file; does not match Spring's naming convention |
| `frontend/.env.development` | Vite, dev | `VITE_API_BASE_URL=http://localhost:8080` |
| `frontend/.env.production` | Vite, build | `VITE_API_BASE_URL=https://discordclone-hd22.onrender.com` |

`application1.properties` is not a profile file. Spring loads `application.properties` and
`application-{profile}.properties` only. The file contains a `spring.kafka.consumer.group-id` that
reads as live consumer configuration but is never applied. It does not exist on `feature/kafka`.

### Profile matrix

| Profile | `MessageEventPublisher` | `MessagePersistenceService` | Messages persisted? |
|---|---|---|---|
| `local` (default) | `LocalMessageEventPublisher` | `LocalMessagePersistenceService` | Yes |
| `kafka` | `KafkaMessageEventPublisher` | `NoOpMessagePersistenceService` | **No** — no consumer exists |

`app.kafka.enabled` is defined in both profile files but **read by nothing**. No
`@ConditionalOnProperty` or `@Value` references it, so profile selection alone drives the wiring.

### Key settings

```properties
server.port=8080                       # README says 8082; HomeController /api says 8082; this wins
spring.jpa.hibernate.ddl-auto=update   # no migration tool
spring.jpa.open-in-view=false          # correct — but see §4 on returning lazy entities
spring.jpa.show-sql=true               # and logging.level.org.hibernate.SQL=DEBUG — SQL logged twice
app.jwt.expiration=86400000            # 24h access token
app.jwt.refresh-expiration=604800000   # 7d refresh token
spring.cache.type=redis                # inert — no @EnableCaching anywhere
```

### Inert settings

These look like configuration but do nothing:

| Setting | Why it is inert |
|---|---|
| `spring.websocket.max-text-message-size`, `...max-binary-message-size` | Not Spring Boot properties. Message size limits require `configureWebSocketTransport`, which `WebSocketConfig` does not override |
| `spring.h2.console.enabled`, `spring.h2.console.path` | No H2 dependency, so the console is never registered. `/h2-console/**` is still `permitAll`, and `frameOptions(sameOrigin)` was added for it |
| `spring.cache.*` | No `@EnableCaching`, no `@Cacheable` |
| `app.kafka.enabled` | Nothing reads it |
| everything in `application1.properties` | File is never loaded |

There is no `application-prod.properties`. Every value, including database credentials, the Redis
password, and the JWT secret, is a literal rather than an `${ENV_VAR}` placeholder. That includes
production: `.env.production` points at a Render deployment, and nothing in the repo shows how the
production datasource is configured.

## 3. Package walkthrough

### `security/`

| Class | Role |
|---|---|
| `SecurityConfig` | Filter chain, CORS, `PasswordEncoder`, `AuthenticationManager` |
| `JwtService` | Issue/validate/parse tokens; `@PostConstruct` builds the HMAC key (HS512 — 64-byte secret) |
| `JwtAuthenticationFilter` | Per-request bearer token → `SecurityContext` |
| `CustomUserDetailsService` | `loadUserByUsername` + `loadUserById` |
| `UserPrincipal` | `UserDetails`; `getName()` returns the **username**; single authority `ROLE_USER` |
| `WebSocketAuthInterceptor` | STOMP frame authentication ([WEBSOCKETS.md](WEBSOCKETS.md#3-authentication)) |
| `CurrentUser` | Meta-annotation over `@AuthenticationPrincipal`; **unused** — controllers use `@AuthenticationPrincipal` or `SecurityContextHolder` directly |

`JwtService.validateToken` **throws** `JwtAuthenticationException` on every failure path rather than
returning `false`, so it can only ever return `true` or throw. Callers written as
`if (!validateToken(t))` therefore never take the false branch: they either proceed or propagate an
exception. `JwtAuthenticationFilter` wraps the call in a try/catch and continues unauthenticated,
which works, but the boolean return type is misleading. The same pattern affects the STOMP
interceptor ([WEBSOCKETS.md](WEBSOCKETS.md#31-connect--the-only-place-the-jwt-is-checked)) and
`/api/auth/refresh` (§8.9).

Controllers get the current user in three ways: `@AuthenticationPrincipal UserPrincipal`
(Channel, Server, Invite), a private `getCurrentUser()` reading `SecurityContextHolder` (Friend,
UserStatus), and `SimpMessageHeaderAccessor.getUser()` (the STOMP controllers).

### `service/`

| Class | Notes |
|---|---|
| `MessageService` | Loads entities, assigns UUID, builds DTO, delegates to publisher. **No membership check** |
| `MessageEventPublisher` + 2 impls | The profile seam ([KAFKA.md](KAFKA.md#1-the-profile-seam)) |
| `MessagePersistenceService` + 2 impls | `Local` saves; `NoOp` returns unsaved |
| `UserStatusService` / `impl` | Redis presence ([REDIS.md](REDIS.md)) |
| `PresenceExpirationListener` | Keyspace-expiry → OFFLINE |
| `FriendshipService` / `impl` | Friend graph + `WsEvent` fan-out to `/user/queue/friends` |
| `ChannelService` | Admin check on create/update/delete; member check on get/list; DM get-or-create (pinned to server 1) |
| `ServerService` | Owner checks on update/delete/role; **no checks** on add/remove member; `isUserMember`/`isUserAdmin` helpers |
| `InviteService` | Member check on create; join validates expiry and `maxUses` |
| `UserService` | **Concrete `@Service`**; BCrypt-encodes on `createUser` |
| `UserServiceImpl` | Second `@Service` that `extends UserService`. **Never injected** — see below |

**Two `UserService` beans.** `UserService` and `UserServiceImpl` are both `@Service`, and both are
assignable to `UserService`. Every consumer (`AuthController`, `UserController`, `ChannelService`,
`ServerService`, `InviteService`) injects a constructor parameter named `userService`. With two
candidates and no `@Primary`, Spring falls back to matching that parameter name against bean names,
so it always picks the base `UserService` bean. This works only because the Spring Boot Gradle
plugin compiles with `-parameters`. The overrides in `UserServiceImpl`, which throw
`ResourceNotFoundException` (404) instead of `RuntimeException` (500), therefore never execute. That
is why "user not found" surfaces as a 500 throughout the API.

**Error semantics.** Many business failures are thrown as bare `RuntimeException`:
`InviteService` ("Invite expired", "Invalid invite code", "already a member"), `ServerService`
("Only the server owner can…", "Invalid role id"), and `UserService.getUserById`.
`GlobalExceptionHandler` maps `RuntimeException` to **500** and echoes `ex.getMessage()` in the body.
So an expired invite, a non-owner trying to delete a server, and a missing user all return 500
Internal Server Error with the internal message. `ChannelService` uses `UnauthorizedException`,
mapped to **401**, for what are really authorization failures and should be **403**. The
`@ControllerAdvice` has a complete ladder of specific handlers (409, 400, 404, 405, 401, 503); the
services just do not throw the exceptions those handlers expect.

### `model/`

Entities use Lombok `@Data` on JPA classes. This generates `equals`/`hashCode` across **all**
fields, including lazy `@ManyToOne` associations. Touching those on a detached entity risks
`LazyInitializationException`, and mutable-field hashing breaks `HashSet` membership when a field
changes. `ServerService` compares owners with `server.getOwner().equals(user)`, which under `@Data`
compares every field of `User`, password hash included. It works because both sides are loaded from
the same row. `Message` additionally carries `@Data` plus redundant `@Setter`/`@Getter`. The
conventional fix is `@Getter/@Setter` with an explicit ID-based `equals`/`hashCode`.

`User` has no relationship mappings and **no `@JsonIgnore` on `password`**. Returning a `User`
anywhere serializes the BCrypt hash (§8.4).

`Server.type` (`ServerType.PUBLIC/PRIVATE`) exists, but `createServer` has the setter commented out,
so every server's `type` is `null`. The column carries no meaning today.

## 4. API surface

"Authz" is the resource-level check the code performs beyond "is authenticated".

### Auth — `/api/auth` (public)

| Method | Path | Body | Returns | Notes |
|---|---|---|---|---|
| POST | `/register` | `RegistrationRequest` (`@Valid`) | `JwtAuthResponse` | Creates the user, then re-authenticates with the raw password |
| POST | `/login` | `LoginRequest` | `JwtAuthResponse` | **Logs the plaintext password** (§8.4) |
| POST | `/refresh` | `RefreshTokenRequest` | `JwtAuthResponse` (no user fields) | Accepts access tokens; guard unreachable (§8.9). The frontend calls a different path (§5) |

### Messages — `/api/messages`

| Method | Path | Authz | Notes |
|---|---|---|---|
| GET | `/channels/{channelId}` | **none** | Page 0, size 20, newest first. The frontend fetches only the first page and has no scroll-back |
| PUT | `/{messageId}` | **none** | Binds a raw `Message` entity; path variable ignored (§8.6) |
| DELETE | `/{messageId}` | **none** | Cannot succeed (§8.8) |
| STOMP | `/app/chat.send` | **none** | `MessageRequest`; DM `receiver` is client-supplied |

History responses (`MessageResp`) include each author's **email address**
(`MessageResp.Author{id, username, email}`), so reading a channel exposes every participant's email.

### Status — `/api/users`

| Method | Path | Authz |
|---|---|---|
| GET | `/{userId}/status` | **none** — any user's presence |
| GET | `/me/status` | self |
| GET | `/friends/status` | self |
| POST | `/status/custom?status=` | self |
| DELETE | `/status/custom` | self |
| DELETE | `/status/reset` | self |

### Users — `/api/users`

| Method | Path | Authz | Notes |
|---|---|---|---|
| GET | `/` | **none** | **Every user, with email and password hash** |
| GET | `/{id}`, `/username/{username}`, `/search?prefix=` | **none** | Raw `User` entities, hash included |
| POST | `/` | authenticated | Binds a raw `User`, **including `id`** (§8.5) |
| PUT | `/{id}/status` | **none** | **No-op**: `UserService.updateUserStatus` has `setStatus` commented out and re-saves the user unchanged |

`UserController` and `UserStatusController` **share the `/api/users` base path**. Spring resolves the
overlaps (`/friends/status` and `/me/status` vs. `/{userId}/status`, `/search` vs. `/{id}`) by
preferring literal segments over templates, so startup succeeds. It is fragile, though. Adding
`GET /{id}/{something}` to either controller would create a genuine ambiguity, and the split means
the `/api/users` surface is described in two files.

### Friends — `/api/friends`

| Method | Path | Notes |
|---|---|---|
| GET | `""`, `/requests`, `/requests/outgoing`, `/check/{friendId}` | Self-scoped |
| POST | `/request` | By **username**; auto-accepts if the target already sent you a pending request; re-sending after REJECTED is allowed |
| PUT | `/accept/{requestId}`, `/reject/{requestId}` | Receiver only |
| DELETE | `/{friendId}` | Must be ACCEPTED |
| POST / DELETE | `/block/{targetId}` | Replaces any existing row with a BLOCKED row owned by the blocker |

Blocking prevents new friend requests between the pair, and nothing else. It does not stop DMs
(`MessageService` never consults friendships) and it does not hide presence (§8.10). There is no
endpoint to cancel an outgoing request, although the frontend has a handler for a
`FRIEND_REQUEST_CANCELLED` event. The list queries load `sender`/`receiver` lazily per row, so
`getFriends` and the request lists issue one query per friendship (N+1).

### Servers — `/api/servers`

| Method | Path | Authz | Notes |
|---|---|---|---|
| POST | `""` | authenticated | Creates server + `OWNER` membership; name must be globally unique |
| GET | `""` | self | Returns raw `Server` entities; the EAGER `owner` includes its password hash |
| GET | `/{serverId}` | **none** | Returns `ServerDTO` (safe) for any server |
| POST | `/{serverId}/members/{userId}` | **none** | **Anyone can add anyone to any server** (§8.3) |
| DELETE | `/{serverId}/members/{userId}` | **none** | **Anyone can remove any non-owner member** (§8.3) |
| PUT | `/{serverId}` | owner | Binds raw `Server`; only name/description copied |
| DELETE | `/{serverId}` | owner | **Will fail** — see below |
| PUT | `/{serverId}/roles/{roleId}` | owner | **Always fails** — see below |

**`PUT /{serverId}/roles/{roleId}` can never succeed.** The handler declares
`@PathVariable Long userId`, but the mapping has no `{userId}` segment
(`ServerController.java:70-76`). Spring throws `MissingPathVariableException` before the method
runs, and the generic handler turns it into a 500. Even if the path were fixed, `roleId` is mapped
to `Role.values()[roleId]`, meaning ADMIN=0, MEMBER=1, OWNER=2. That couples the API to enum
declaration order, and it lets an owner mint additional `OWNER`s, which the delete/update checks
(based on `Server.owner`, not `Member.role`) would then not recognise.

**`DELETE /{serverId}` fails on foreign keys.** `ServerService.deleteServer` calls
`serverRepository.delete(server)` with no cascade. Every server created through the API has at least
one `server_members` row (the owner's), plus usually channels and invites, all with foreign keys to
`servers.id`. Hibernate generates those constraints under `ddl-auto=update`, so the delete violates
them and the `DataIntegrityViolationException` handler returns 409. Only a server with no members,
channels, or invites could be deleted. *Inferred from the schema mapping; not exercised.*

### Channels — `/api/channels/{serverId}`

| Method | Path | Authz | Notes |
|---|---|---|---|
| POST | `""` | admin | Broadcasts `CHANNEL_CREATED` to **every** connected user on `/topic/channels` |
| POST | `/dm/{userId}` | authenticated | Get-or-create DM; returns the channel ID. `{serverId}` is ignored; DMs are attached to server 1 |
| GET | `""` | member | Returns `ChannelDTO` list |
| GET | `/{channelId}` | member | Returns the raw `Channel` entity. `{serverId}` is ignored |
| PUT | `/{channelId}` | admin | Binds a raw `Channel`; copies name, description, **type** (a channel can be flipped to `DM`) |
| DELETE | `/{channelId}` | admin | No cascade to messages; likely FK failure once the channel has messages |

The `{serverId}` path variable is used only by create and list. For `/dm/{userId}`,
`/{channelId}` get/put/delete, it is ignored; the channel's actual server comes from the database.
So `/api/channels/999/5` operates on channel 5 regardless of which server it belongs to.

`GET` and `PUT /{channelId}` return a `Channel` entity whose `server` is a lazy proxy. With
`open-in-view=false` and no `jackson-datatype-hibernate` module, Jackson touches that proxy after the
transaction has closed, which normally fails with `LazyInitializationException` or a "no serializer
for ByteBuddyInterceptor" error. *Likely, not exercised.* `ChannelDTO`, which the list endpoint
uses, avoids this.

### Invites — `/api/invites`

| Method | Path | Authz | Notes |
|---|---|---|---|
| POST | `/create?serverId&maxUses=10&validMinutes=1440` | member | Code = first 8 chars of a UUID; URL built from the request host |
| POST | `/join/{code}` | authenticated | Returns raw `Server` (owner hash included) |

`joinViaInvite` performs a read-check-increment on `uses` with no lock or version column, so
concurrent joins can exceed `maxUses`. `createInvite` is not `@Transactional`. The returned
`inviteUrl` points at the **backend** endpoint (`/api/invites/join/{code}`, a `POST`), so it is not
a link a browser can open.

### Misc

`GET /`, `GET /api`, `GET /health` are all in `HomeController`. `/` and `/health` are in the
`permitAll` list. **`/api` is not**, so it falls through to `.anyRequest().authenticated()` and
requires a token despite sitting alongside two public endpoints. Its content is also stale: it lists
`/api/channels/servers/{serverId}`, `POST /api/messages/channels/{channelId}`, and
`ws://localhost:8082/ws`, none of which match the real routes.

## 5. Frontend structure

```
frontend/src/
├── App.tsx                 Route table + provider composition
├── components/             auth, chat, friends, layout, servers, user
├── providers/
│   ├── AuthProvider        Login state, token + username + id in localStorage
│   ├── WebSocketProvider   STOMP client, subscription registry
│   ├── StatusProvider      Composes the three presence providers
│   ├── PresenceProvider    Self status + 10s heartbeat loop
│   ├── IdleProvider        DOM activity → /app/activity
│   └── FriendStatusProvider Friend status map
├── hooks/                  useServers, useChannels, useFriends, useStatus, useUserStatus
├── store/                  Redux Toolkit (messages slice, de-duplicated, capped per channel)
├── websocket/              message.socket.ts, friends.events.ts
├── services/api.ts         Axios instance + refresh interceptor
└── config/api.ts           API_BASE_URL / WS_BASE_URL
```

Dead files: `components/chat/ChatArea copy.tsx`, `services/StatusProvider.tsx` (a second, unused
`StatusProvider`), and `frontend/src/test_command`. `services/message.service.ts` is partly used:
its history fetch is live, but its edit and delete helpers call backend routes that do not exist.

### API client and token refresh

`services/api.ts` builds `baseURL = API_BASE_URL + '/api'`, so `VITE_API_BASE_URL` must be the
server origin **without** `/api`. The committed `.env.development` does this correctly.

Its request interceptor decodes the JWT, and if it has expired, calls
`POST ${API_BASE_URL}/api/refresh-token` with an **empty body** and `withCredentials: true`. That
endpoint does not exist; the backend route is `/api/auth/refresh`, and it reads the refresh token
from the JSON body, not a cookie. Every refresh therefore fails, the interceptor clears storage, and
it redirects to `/login`. The stored `refreshToken` is never sent anywhere. The practical session
length is the 24-hour access-token TTL. Requests queued behind a failed refresh are never resolved,
but the redirect makes that moot.

### State management

Three systems coexist: **Redux Toolkit** (messages), **React Query** (servers, channels, friends),
and **React Context** (auth, websocket, presence). React Query and Redux overlap in purpose. React
Query is the better fit for server state; the messages slice exists mainly because WebSocket pushes
arrive outside the query lifecycle. `react-query` v3 and `@tanstack/react-query` v5 are **both** in
`package.json`, but only the latter is used, by `lib/reactQuery.ts`.

### Routing

`/channels` is the authenticated shell (`MainLayout`), with `@me` for DMs/friends and `:serverId`
for servers. `ProtectedRoute` gates the subtree. Note that
`<Route path="*" element={<Navigate to="/login" />} />` is nested *inside* `ProtectedRoute`, so
unmatched authenticated routes redirect to login rather than showing a 404.

Tokens are stored in `localStorage`, which is readable by any script on the origin. An XSS bug
therefore becomes full account takeover for the token's 24-hour lifetime.

## 6. Local setup

```bash
# 1. infrastructure
docker compose up -d db redis                      # enough for the local profile
docker compose up -d                               # + kafka, kafka-init, kafka-ui, pgadmin

# 2. backend (http://localhost:8080)
./gradlew bootRun                                  # local profile (default)
SPRING_PROFILES_ACTIVE=kafka ./gradlew bootRun     # kafka profile — see warning below

# 3. frontend (http://localhost:5173)
cd frontend
npm install
npm run dev                                        # uses the committed .env.development
```

`VITE_API_BASE_URL` comes from the committed `frontend/.env.development` (`http://localhost:8080`).
It must not end in `/api`, because `api.ts` appends that itself. If it is unset, `config/api.ts`
throws at module load (`undefined.replace`).

**Fresh-database prerequisites.** On first start, `DataInitializer` creates `testuser`, a
"Default Server" owned by it, and a "Default channel". Everything else depends on that server
existing as **ID 1**, because every DM channel is attached to it (§8.7). Two caveats:

- `testuser`'s password is stored in plaintext, so **`testuser` cannot log in** (§8.11). Register a
  real account through the UI instead.
- No `Member` row is created for server 1, so nobody, including its owner, is a member of the
  server that holds every DM.

> **Before any of this works**, apply the blocking fixes in §8.1 and §8.2. As committed, the backend
> does not compile, and presence never reaches OFFLINE.

> **Do not use the `kafka` profile** until a consumer exists — messages are broadcast but never
> stored ([KAFKA.md](KAFKA.md#4-the-missing-consumer)).

Supporting UIs: pgAdmin <http://localhost:5050> (`admin@dev.com` / `admin`), Kafka UI
<http://localhost:8085>.

## 7. Testing

Six Mockito-based unit test classes exist under `src/test/java/com/discordclone/service/`. They
cover the service layer with 50 `@Test` methods in total:

| Class | `@Test` methods | Lines |
|---|---|---|
| `ServerServiceTest` | 12 | 221 |
| `UserServiceTest` | 9 | 143 |
| `ChannelServiceTest` | 8 | 155 |
| `UserStatusServiceTest` | 8 | 174 |
| `InviteServiceTest` | 7 | 156 |
| `MessageServiceTest` | 6 | 156 |

They are plain `@ExtendWith(MockitoExtension.class)` unit tests. There is no Spring context, no
`@SpringBootTest`, no integration or controller test, and nothing that exercises the WebSocket,
Kafka, or Redis layers. Because there are no controller tests, none of the authorization gaps in §4
is covered.

**Two of these classes are stale and will not compile against `main`:**

1. `UserStatusServiceTest` calls `userStatusService.updateUserStatus(1L, UserStatus.IDLE)`
   (`:70`, `:87`). That method was removed from `UserStatusService` in the presence rewrite, which
   replaced it with `handleHeartbeat`/`handleActivity` and derived-status queries. The test still
   targets the `feature/kafka`-era API, so `src/test` compilation fails on `main`.
2. `MessageServiceTest` declares four `@Mock`s: `MessageRepository`, `UserRepository`,
   `ChannelRepository`, `SimpMessagingTemplate`. `MessageService` now takes **six** constructor
   arguments, having gained `MessagePersistenceService` and `MessageEventPublisher`. With
   `@InjectMocks` against a `@RequiredArgsConstructor` class, the two unmatched parameters are
   injected as `null`. Any test that reaches `eventPublisher.publish(...)` at
   `MessageService.java:71` throws `NullPointerException`.

The tests were written before the Kafka seam and the Redis presence rewrite, and were not updated
alongside either. `./gradlew build` runs `test` (and `check` runs PMD), so the build currently fails
at test compilation, independently of the missing Redis dependency (§8.1).

`frontend/README.md` is the unmodified Vite template README, which the root README nonetheless cites
for "frontend testing instructions". There is no test tooling in `frontend/package.json` (no Vitest,
Jest, or Testing Library).

## 8. Known issues

> [BUGS.md](BUGS.md) is the complete, numbered catalog (B01–B50), with an evidence tag and a fix
> for each item. This section is a narrative summary of the most important ones, and
> [IMPROVEMENTS.md](IMPROVEMENTS.md) covers the structural changes that prevent them.

Ordered by severity. §8.1–8.2 stop the system from building or working. §8.3–8.7 are exploitable by
any registered user.

### 8.1 The backend does not compile on `main`

`build.gradle` declares no Redis dependency, and none of the declared starters brings Spring Data
Redis in transitively. Meanwhile, these files import `org.springframework.data.redis.*`:

- `config/RedisConfig.java` — `StringRedisTemplate`, `RedisConnectionFactory`
- `config/RedisKeyExpirationListenerConfig.java` — `RedisMessageListenerContainer`, `PatternTopic`, `MessageListenerAdapter`
- `service/impl/UserStatusServiceImpl.java` — `StringRedisTemplate`

**Root cause.** The starter was added in `2b8eea7` ("added redis service", 2025-04-23) and removed in
`cd97e97` ("global exception handler update", 2026-02-14). The later `feature/redis` work
reintroduced Redis code without restoring it. Affects `main` and `feature/redis`; `feature/kafka`
has no Redis imports and is unaffected.

```groovy
implementation 'org.springframework.boot:spring-boot-starter-data-redis'
```

*Not verified by compilation.* No JRE is installed in the environment these docs were written in
(`./gradlew` reports "Unable to locate a Java Runtime"). The conclusion comes from comparing the
dependency block against the imports. The only build artifact in the tree is a stale committed jar,
`build/libs/discord-clone-0.0.1-SNAPSHOT.jar` (50 MB, from `1b0f85e`, 2026-02-16). It contains none
of the Kafka publisher, Redis config, or presence-listener classes, so it proves nothing about
today's sources.

A second, independent blocker: two test classes no longer compile (§7). Both must be fixed for
`./gradlew build` to pass.

### 8.2 Presence never reaches OFFLINE

Redis keyspace notifications are disabled by default, and nothing enables them, so
`PresenceExpirationListener` never fires. Fix in `docker-compose.yml`:

```yaml
command: redis-server --requirepass redis123 --notify-keyspace-events Ex
```

Full analysis: [REDIS.md](REDIS.md#keyspace-notifications-are-not-enabled).

### 8.3 Missing authorization on server membership, messages, and subscriptions

Several write and read paths check only that the caller is logged in:

| Action | Endpoint | Effect |
|---|---|---|
| Add any user to any server | `POST /api/servers/{id}/members/{userId}` | Join private servers; add others without consent |
| Remove any non-owner member | `DELETE /api/servers/{id}/members/{userId}` | Kick anyone from any server |
| Post to any channel | `SEND /app/chat.send` | Message servers you are not in |
| Read any channel's history | `GET /api/messages/channels/{id}` | Read servers you are not in |
| Subscribe to any channel | `SUBSCRIBE /topic/channels/{id}/messages` | Live-read servers you are not in |
| Send a "DM" to anyone | `SEND /app/chat.send` with `dm: true` | `receiver` and `channelId` are client-chosen and never cross-checked |

Channel IDs and server IDs are sequential integers. `ServerService.isUserMember`,
`ServerService.isUserAdmin`, and `ChannelService.checkUserIsMember` already exist and are used
correctly elsewhere, so the fix is mostly to call them. DMs need a separate participant check against
`dmKey` (§8.7). See also
[WEBSOCKETS.md](WEBSOCKETS.md#32-subscribe-and-send).

### 8.4 Password hashes and plaintext passwords are exposed

- **Hashes over the API.** `User.password` has no `@JsonIgnore`. `GET /api/users` returns every
  account's email and BCrypt hash to any authenticated caller. `/api/users/{id}`,
  `/username/{u}`, and `/search` do the same per user. Every endpoint that returns a `Server` entity
  (`GET`/`POST /api/servers`, the member endpoints, `POST /api/invites/join/{code}`) embeds the
  EAGER `owner`, hash included. BCrypt is slow to crack, but handing out hashes turns every weak
  password into an offline attack.
- **Plaintext in logs.** `AuthController.java:72` logs `loginRequest` at INFO. `LoginRequest` is
  `@Data`, so its `toString()` includes `password`, and every login writes the user's password to the
  application log. (`RegistrationRequest` has no `@ToString`, so `/register`'s log line prints only an
  object reference.)
- **Emails in history.** `MessageResp.Author` includes `email`, so every channel reader sees every
  author's email.

Fix: add `@JsonIgnore` to `User.password` as a backstop, return DTOs from every controller, drop the
request object from the login log line, and remove `email` from `Author`.

### 8.5 Account overwrite through `POST /api/users`

`UserController.createUser` binds a raw `User` from the request body and passes it to
`UserService.createUser`. That method checks that the *username* and *email* are unused, encodes
the password, and calls `userRepository.save(user)`. It never clears `user.id`. Spring Data's
`save` uses `merge` when the ID is non-null, so a body such as
`{"id": 7, "username": "fresh", "email": "fresh@x", "password": "mine"}` **overwrites user 7's row**:
the username, email, and password all change, and the attacker can now log in as that account, with
its memberships, friendships, and history attached. The endpoint needs any authenticated account,
nothing more. *Follows from `SimpleJpaRepository.save` semantics; not exercised.* Remove the
endpoint (registration already exists at `/api/auth/register`), or bind to a DTO without `id`.

### 8.6 Mass assignment on message edit

`PUT /api/messages/{messageId}` binds a raw `Message` entity from the body and saves it. It ignores
the path variable and performs no ownership check. Any user can rewrite any message, including its
`sender` and `channel`, by sending that message's ID in the body. It also leaves `edited` at whatever
the client sends.

The path variable is declared `Long` while message IDs are UUID strings, so the natural request
`PUT /api/messages/{uuid}` fails type conversion with a 400. The hole is still open, because the
body decides which row is written: `PUT /api/messages/1` with `{"id": "<victim uuid>", ...}` passes
conversion and overwrites the victim's message.

### 8.7 DMs are pinned to server 1

`ChannelService.getOrCreateDmChannel` attaches every DM channel to `serverService.getServerById(1L)`.

- If server 1 does not exist, DM creation fails with 404.
- Server-membership checks are meaningless for DMs. Nobody is a member of server 1 by default, so
  `GET /api/channels/{any}/{dmChannelId}` is refused for the DM's own participants. Anyone who does
  join server 1, which §8.3 lets any user do, passes the membership check for **every** DM.
- `DmChannel`/`DmChannelRepository` model participants properly, but they are unused.

Use the `dm_channels` table, or parse `dmKey`, to authorize DM access by participant.

### 8.8 `DELETE /api/messages/{messageId}` cannot succeed

```java
// controller/MessageController.java:87-92
Message message = messageService.getChannelMessages(null, null)
        .getContent().stream()
        .filter(m -> m.getId().equals(messageId))
        .findFirst()
        .orElseThrow(() -> new RuntimeException("Message not found"));
```

Passing `null` as the `Channel` to the derived query `findByChannelOrderByTimestampDesc` makes Spring
Data generate `WHERE channel_id IS NULL`. `channel_id` is `NOT NULL`, so no row matches, and every
call ends in "Message not found" as a 500. The path variable is a `Long` while `Message.id` is a
`String`, so `m.getId().equals(messageId)` could never be true anyway. It should be
`messageRepository.findById(messageId)` with a `String` ID, followed by a check that the caller is
the author.

### 8.9 `/api/auth/refresh` accepts access tokens, and its validity check is unreachable

```java
Long userId = tokenProvider.getUserIdFromJWT(request.getRefreshToken());   // parses & throws first
if (!tokenProvider.validateToken(request.getRefreshToken())) { ... }       // unreachable
```

1. **Ordering.** `getUserIdFromJWT` throws on any invalid input, and `validateToken` never returns
   `false` anyway (§3), so the 400 branch is dead. Invalid tokens produce a 401 from the JWT
   exception handler instead.
2. **No token-type claim.** `generateToken` and `generateRefreshToken` produce structurally identical
   JWTs that differ only in expiry; neither carries a `type` claim. So an **access token is accepted
   as a refresh token**, which lets a client roll its session forward indefinitely from a single
   access token and defeats the point of the shorter access TTL.
3. **Nothing calls it.** The frontend posts to `/api/refresh-token` instead (§5).

### 8.10 Presence is visible to everyone

`/topic/status` broadcasts every presence change to every connected user, and
`GET /api/users/{id}/status` answers for any user. Blocked users can see the blocker's presence.
See [REDIS.md](REDIS.md#presence-visibility).

### 8.11 Seeded test user has a plaintext password and cannot log in

```java
// config/DataInitializer.java:26
user.setPassword("password123"); // Optional: hash it
userRepository.save(user);
```

`DataInitializer` writes directly through the repository, bypassing `UserService.createUser`, which
is where `passwordEncoder.encode(...)` is applied (`service/UserService.java:31`). The stored value is
plaintext, so `BCryptPasswordEncoder.matches` fails and `testuser` can never authenticate, while the
row itself is a plaintext credential in the database. The seeder also creates "Default Server"
without an owner `Member` row. Its server and channel creation is nested inside
`if testuser missing`, so a database that has `testuser` but lost server 1 is never repaired.

### 8.12 Committed secrets

| Secret | Location |
|---|---|
| SonarCloud token | `build.gradle:29` |
| JWT signing secret | `application.properties:56` |
| Postgres password | `application.properties:13`, `docker-compose.yml` |
| Redis password | `application.properties:68`, `docker-compose.yml` |
| pgAdmin login | `docker-compose.yml` |

The SonarCloud token should be **revoked**: it is in git history, so removing it from the working
tree is not sufficient. The JWT secret is more dangerous. Anyone who holds it can forge a token for
any user ID, since the subject is the only identity claim.

### 8.13 Frontend token refresh is broken

The axios interceptor calls a non-existent `/api/refresh-token` with no token (§5), so sessions end
at the 24-hour access-token expiry, and the WebSocket's auto-reconnect keeps presenting the expired
token ([WEBSOCKETS.md](WEBSOCKETS.md#61-connection-management)).

### 8.14 `ChatArea` leaks a subscription per render

`registerGroupMessageSocket` is called in the component body, not in an effect, so subscriptions
grow with every render and are never released. Redux de-duplication hides the symptom.
[WEBSOCKETS.md](WEBSOCKETS.md#64-message-reception).

### 8.15 The `kafka` profile silently discards all messages

No `@KafkaListener` exists on any branch, and `NoOpMessagePersistenceService` is active under that
profile. Messages are produced and broadcast, then lost. A Kafka outage also blocks STOMP worker
threads for up to 60 s per send. [KAFKA.md](KAFKA.md#4-the-missing-consumer) includes a reference
consumer.

### 8.16 Broken server endpoints

`PUT /api/servers/{id}/roles/{roleId}` always fails (a missing `{userId}` path variable), and
`DELETE /api/servers/{id}` fails on foreign keys (§4).

### 8.17 Single-instance ceiling

`enableSimpleBroker` keeps all subscriptions in process heap, so the application cannot run more than
one replica. [WEBSOCKETS.md](WEBSOCKETS.md#why-the-simple-broker).

### 8.18 Build and CI

Two stale test classes block `./gradlew build` (§7), and the CI workflow's tag, push, and login steps
are wrong (§1).

### 8.19 Smaller items

| Item | Location |
|---|---|
| Business errors thrown as `RuntimeException` → 500 with internal message echoed | `InviteService`, `ServerService`, `UserService` |
| Authorization failures return 401 instead of 403 | `ChannelService` / `UnauthorizedException` |
| Two `UserService` beans, resolved only by parameter name; `UserServiceImpl` never used | `service/` |
| `GET`/`PUT /api/channels/.../{channelId}` return a lazy entity; likely serialization failure | `ChannelController.java:89-102` |
| `PUT /api/channels/.../{channelId}` can change a channel's `type`, including to `DM` | `ChannelService.java:125` |
| `{serverId}` ignored on most channel routes | `ChannelController` |
| `PUT /api/users/{id}/status` is a no-op with no auth check | `UserController.java:62` |
| Invite `uses` increment is not concurrency-safe; invite URL points at a backend `POST` | `InviteService.java:72` |
| `Server.type` never set; `ServerType` meaningless | `ServerService.java:46` |
| Blocking does not stop DMs or presence | `MessageService`, `UserStatusServiceImpl` |
| N+1 queries in friend lists | `FriendshipServiceImpl` |
| `ResourceNotFoundException("Channel", "id", userId)` reports the wrong ID | `MessageService.java:46` |
| Broadcast before commit in the `local` profile | `LocalMessageEventPublisher` |
| Kafka `bootstrap-servers` hardcoded; `spring.kafka.producer.*` ignored | `KafkaProducerConfig.java` |
| Inert settings: `spring.websocket.*`, `spring.h2.*`, `spring.cache.*`, `app.kafka.enabled`, `application1.properties` | §2 |
| `/api` is authenticated and lists stale routes and port 8082 | `HomeController.java` |
| Duplicate OFFLINE broadcast; custom status overrides liveness; ONLINE↔IDLE oscillation | [REDIS.md](REDIS.md#9-summary-of-findings) |
| `/user/queue/errors` has no subscriber | frontend |
| Lombok `@Data` on JPA entities | `model/` |
| Duplicate `UserDTO` in `dto/` and `payload/`; `CurrentUser` annotation unused | — |
| Both `react-query` v3 and `@tanstack/react-query` v5 installed | `frontend/package.json` |
| Unused `sockjs-client` / `sockjs`; unused MySQL connector | `frontend/package.json`, `build.gradle:65` |
| `tsc \|\| true` — type errors never fail the build | `frontend/package.json:8` |
| Committed artifacts: 50 MB `build/libs` jar, root `node_modules/`, `.DS_Store`, H2 `data/*.db`, `docker-compose-bkp.yml`, `updated-docker-compose.yml`, `frontend/src/test_command` | repo root |
| No migrations, no `prod` profile, no controller/integration tests | — |
| README states port 8082; actual is 8080 | `README.md:67` |

## 9. Suggested order of work

1. **Close the exploitable holes first.** None of these depends on the build. Add `@JsonIgnore` to
   `User.password` and stop returning entities (§8.4). Remove or DTO-bind `POST /api/users` (§8.5).
   Add membership checks to the server-member endpoints, message send/read, and `SUBSCRIBE` (§8.3).
   Add an ownership check to message edit (§8.6). Stop logging `LoginRequest`.
2. **Get to a green build.** Restore the Redis starter (§8.1) and repair the two stale test classes
   (§7).
3. Revoke the SonarCloud token, rotate the JWT secret, and move every secret to environment
   variables (§8.12).
4. Enable keyspace notifications (§8.2), and move the liveness check ahead of the custom override
   in `resolveStatus`.
5. Fix the frontend refresh path and add a `type` claim to refresh tokens (§8.9, §8.13). Move
   `registerGroupMessageSocket` into an effect (§8.14).
6. Give DMs participant-based authorization instead of server 1 (§8.7). Fix message delete (§8.8) and
   the seeder (§8.11).
7. Either implement the Kafka consumer or drop the `kafka` profile (§8.15). A profile that loses data
   is worse than no profile.
8. Add migrations (Flyway), move off `ddl-auto=update`, and add controller tests. Every item in step 1
   is an authorization rule that a `@WebMvcTest` would pin down.
