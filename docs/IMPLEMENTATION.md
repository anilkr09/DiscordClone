# Implementation

Package-by-package walkthrough, the full API surface, configuration, local setup, and the defect
list.

## 1. Build and toolchain

| | |
|---|---|
| Language | Java 17 (toolchain-pinned in `build.gradle:16-22`) |
| Framework | Spring Boot 3.2.2 |
| Build | Gradle, `bootJar` → `discord-clone-0.0.1-SNAPSHOT.jar` |
| Quality | JaCoCo (XML + HTML), PMD 6.55.0 (`ignoreFailures = true`), SonarQube plugin |
| Frontend | React 18, TypeScript 5.7, Vite 6 |

Checkstyle is configured but entirely commented out (`build.gradle:84-92`, `:101-106`, `:117`).
PMD runs with `ignoreFailures = true`, so violations never break the build. `check` depends on
`pmdMain, pmdTest`.

The frontend build script is `tsc --noEmit --skipLibCheck || true && vite build` — the `|| true`
means **type errors never fail the build**. Given operator precedence this reduces to
`(tsc ... || true) && vite build`, so `vite build` always runs regardless of type-check outcome.

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

Two problems here: **`spring-boot-starter-data-redis` is absent** (§8.1), and the MySQL connector is
declared but nothing uses it.

## 2. Configuration and profiles

### Files

| File | Loaded? | Contents |
|---|---|---|
| `application.properties` | yes | Datasource, JPA, JWT, Redis, logging, `spring.profiles.active=local` |
| `application-local.properties` | yes, under `local` | `app.kafka.enabled=false` |
| `application-kafka.properties` | yes, under `kafka` | Kafka producer settings (mostly inert — see [KAFKA.md](KAFKA.md#22-application-kafkaproperties-mostly-inert)) |
| `application1.properties` | **no** | Dead file; does not match Spring's naming convention |

`application1.properties` is not a profile file — Spring loads `application.properties` and
`application-{profile}.properties` only. It contains a `spring.kafka.consumer.group-id` that reads as
live consumer configuration but is never applied. It does not exist on `feature/kafka`.

### Profile matrix

| Profile | `MessageEventPublisher` | `MessagePersistenceService` | Messages persisted? |
|---|---|---|---|
| `local` (default) | `LocalMessageEventPublisher` | `LocalMessagePersistenceService` | Yes |
| `kafka` | `KafkaMessageEventPublisher` | `NoOpMessagePersistenceService` | **No** — no consumer exists |

`app.kafka.enabled` is defined in both profile files but **read by nothing** — no
`@ConditionalOnProperty` or `@Value` references it. Profile selection alone drives the wiring.

### Key settings

```properties
server.port=8080                       # note: README says 8082; the property wins
spring.jpa.hibernate.ddl-auto=update   # no migration tool
spring.jpa.open-in-view=false          # good — no lazy loading in the view layer
spring.jpa.show-sql=true               # verbose; disable for production
app.jwt.expiration=86400000            # 24h access token
app.jwt.refresh-expiration=604800000   # 7d refresh token
spring.cache.type=redis                # inert — no @EnableCaching anywhere
```

There is no `application-prod.properties`, and every value — database credentials, Redis password,
JWT secret — is a literal rather than an `${ENV_VAR}` placeholder.

## 3. Package walkthrough

### `security/`

| Class | Role |
|---|---|
| `SecurityConfig` | Filter chain, CORS, `PasswordEncoder`, `AuthenticationManager` |
| `JwtService` | Issue/validate/parse tokens; `@PostConstruct` builds the HMAC key |
| `JwtAuthenticationFilter` | Per-request bearer token → `SecurityContext` |
| `CustomUserDetailsService` | `loadUserByUsername` + `loadUserById` |
| `UserPrincipal` | `UserDetails`; `getName()` returns the **username** |
| `WebSocketAuthInterceptor` | STOMP frame authentication ([WEBSOCKETS.md](WEBSOCKETS.md#3-authentication)) |
| `CurrentUser` | Annotation for principal injection |

`JwtService.validateToken` **throws** `JwtAuthenticationException` on every failure path rather than
returning `false` — it can only ever return `true` or throw. Callers written as
`if (!validateToken(t))` therefore never take the false branch; they either proceed or propagate an
exception. `JwtAuthenticationFilter` wraps the call in a try/catch and continues unauthenticated,
which works, but the boolean return type is misleading.

### `service/`

| Class | Notes |
|---|---|
| `MessageService` | Loads entities, assigns UUID, builds DTO, delegates to publisher |
| `MessageEventPublisher` + 2 impls | The profile seam ([KAFKA.md](KAFKA.md#1-the-profile-seam)) |
| `MessagePersistenceService` + 2 impls | `Local` saves; `NoOp` returns unsaved |
| `UserStatusService` / `impl` | Redis presence ([REDIS.md](REDIS.md)) |
| `PresenceExpirationListener` | Keyspace-expiry → OFFLINE |
| `FriendshipService` / `impl` | Friend graph + `WsEvent` fan-out |
| `UserService` | **Concrete class** holding `PasswordEncoder`; BCrypt-encodes on create |
| `UserServiceImpl` | `extends UserService` and calls `super(...)` — not an interface implementation |
| `ChannelService`, `ServerService`, `InviteService` | Concrete, no interface |

`UserServiceImpl extends UserService` (a concrete class), rather than implementing an interface. The
`impl` package name implies a contract that does not exist.

### `model/`

Entities use Lombok `@Data` on JPA classes. This generates `equals`/`hashCode` across **all** fields,
including lazy `@ManyToOne` associations — touching them on a detached entity risks
`LazyInitializationException`, and mutable-field hashing breaks `HashSet` membership when a field
changes. `Message` additionally carries `@Data` plus redundant `@Setter`/`@Getter`. The conventional
fix is `@Getter/@Setter` with an explicit ID-based `equals`/`hashCode`.

`User` has no relationship mappings at all — no `@OneToMany` to messages or members. Navigation is
done through repositories instead, which is a defensible choice that avoids `@Data` recursion, but
it is inconsistent with `Channel`/`Server`, which do map associations.

## 4. API surface

### Auth — `/api/auth` (public)

| Method | Path | Body | Returns |
|---|---|---|---|
| POST | `/register` | `RegistrationRequest` | `JwtAuthResponse` |
| POST | `/login` | `LoginRequest` | `JwtAuthResponse` |
| POST | `/refresh` | `RefreshTokenRequest` | `JwtAuthResponse` (no user fields) |

`/register` creates the user, then immediately authenticates with the raw password to issue tokens.
`/refresh` has two problems — see §8.6.

### Messages — `/api/messages`

| Method | Path | Notes |
|---|---|---|
| GET | `/channels/{channelId}` | Paged, default size 20, sorted by `timestamp` |
| PUT | `/{messageId}` | Takes a full `Message` entity as the body |
| DELETE | `/{messageId}` | See §8.5 — implementation is broken |
| STOMP | `/app/chat.send` | `MessageRequest` |

`PUT /{messageId}` binds a raw JPA entity from the request body and passes it to
`messageRepository.save(...)`, so a client can set `sender`, `channel`, `timestamp`, and `id`
directly. The path variable `messageId` is ignored. This is a mass-assignment hole: any authenticated
user can rewrite any message, including reassigning its author.

### Status — `/api/users`

| Method | Path |
|---|---|
| GET | `/{userId}/status` |
| GET | `/me/status` |
| GET | `/friends/status` |
| POST | `/status/custom?status=` |
| DELETE | `/status/custom` |
| DELETE | `/status/reset` |

### Users — `/api/users`

| Method | Path |
|---|---|
| GET | `/`, `/{id}`, `/username/{username}`, `/search` |
| POST | `/` |
| PUT | `/{id}/status` |

`UserController` and `UserStatusController` **share the `/api/users` base path**. Spring resolves the
overlaps (`/friends/status` and `/me/status` vs. `/{userId}/status`) by preferring literal segments
over templates, so startup succeeds. It is fragile though: adding `GET /{id}/{something}` to either
controller would create a genuine ambiguity, and the split means the `/api/users` surface is
described in two files.

### Friends — `/api/friends`

`GET ""`, `GET /requests`, `GET /requests/outgoing`, `POST /request`, `PUT /accept/{requestId}`,
`PUT /reject/{requestId}`, `DELETE /{friendId}`, `POST /block/{targetId}`, `DELETE /block/{targetId}`,
`GET /check/{friendId}`

### Servers — `/api/servers`

`POST ""`, `GET ""`, `GET /{serverId}`, `POST /{serverId}/members/{userId}`,
`DELETE /{serverId}/members/{userId}`, `DELETE /{serverId}`, `PUT /{serverId}`,
`PUT /{serverId}/roles/{roleId}`

### Channels — `/api/channels/{serverId}`

`POST ""`, `POST /dm/{userId}`, `GET ""`, `GET /{channelId}`, `PUT /{channelId}`,
`DELETE /{channelId}`

Note that DM creation sits under a `{serverId}`-scoped base path (`POST /api/channels/{serverId}/dm/{userId}`)
even though DMs have no server — `Channel.server_id` is nullable precisely for this case. The path
variable is structurally meaningless here.

### Invites — `/api/invites`

`POST /create`, `POST /join/{code}`

### Misc

`GET /`, `GET /api`, `GET /health` — all in `HomeController`. `/` and `/health` are in the
`permitAll` list; **`/api` is not**, so it falls through to `.anyRequest().authenticated()` and
requires a token despite sitting alongside two public endpoints.

## 5. Frontend structure

```
frontend/src/
├── App.tsx                 Route table + provider composition
├── components/             auth, chat, friends, layout, servers, user
├── providers/
│   ├── AuthProvider        Login state, token storage (localStorage)
│   ├── WebSocketProvider   STOMP client, subscription registry
│   ├── StatusProvider      Composes the three presence providers
│   ├── PresenceProvider    Self status + 10s heartbeat loop
│   ├── IdleProvider        DOM activity → /app/activity
│   └── FriendStatusProvider Friend status map
├── hooks/                  useServers, useChannels, useFriends, useStatus, useUserStatus
├── store/                  Redux Toolkit (messages slice)
├── websocket/              message.socket.ts, friends.events.ts
├── services/api.ts         Axios instance
└── config/api.ts           API_BASE_URL / WS_BASE_URL
```

### State management

Three systems coexist: **Redux Toolkit** (messages), **React Query** (servers, channels, friends),
and **React Context** (auth, websocket, presence). React Query and Redux overlap in purpose —
React Query is the better fit for server state, and the messages slice exists mainly because
WebSocket pushes arrive outside the query lifecycle. `react-query` v3 and `@tanstack/react-query` v5
are **both** in `package.json`; only the latter is used by `lib/reactQuery.ts`.

### Routing

`/channels` is the authenticated shell (`MainLayout`), with `@me` for DMs/friends and `:serverId`
for servers. `ProtectedRoute` gates the subtree. Note `<Route path="*" element={<Navigate to="/login" />} />`
is nested *inside* `ProtectedRoute`, so unmatched authenticated routes redirect to login rather than
showing a 404.

Tokens are stored in `localStorage`, which is readable by any script on the origin — an XSS bug
becomes full account takeover for the token's 24-hour lifetime.

## 6. Local setup

```bash
# 1. infrastructure
docker compose up -d db redis                      # local profile
docker compose up -d                               # + kafka, kafka-ui, pgadmin

# 2. backend (http://localhost:8080)
./gradlew bootRun                                  # local profile (default)
SPRING_PROFILES_ACTIVE=kafka ./gradlew bootRun     # kafka profile — see warning below

# 3. frontend (http://localhost:5173)
cd frontend
npm install
echo "VITE_API_BASE_URL=http://localhost:8080/api" > .env
npm run dev
```

`VITE_API_BASE_URL` is required — `config/api.ts` derives `WS_BASE_URL` from it by rewriting the
scheme, and will throw on `undefined` if the variable is unset.

> **Before any of this works**, apply the two blocking fixes in §8.1 and §8.2. As committed, the
> backend does not compile, and presence never reaches OFFLINE.

> **Do not use the `kafka` profile** until a consumer exists — messages are broadcast but never
> stored ([KAFKA.md](KAFKA.md#4-the-missing-consumer)).

Supporting UIs: pgAdmin <http://localhost:5050> (`admin@dev.com` / `admin`), Kafka UI
<http://localhost:8085>.

## 7. Testing

Six Mockito-based unit test classes exist under `src/test/java/com/discordclone/service/`, covering
the service layer with 50 `@Test` methods in total:

| Class | `@Test` methods | Lines |
|---|---|---|
| `ServerServiceTest` | 12 | 221 |
| `UserServiceTest` | 9 | 143 |
| `ChannelServiceTest` | 8 | 155 |
| `UserStatusServiceTest` | 8 | 174 |
| `InviteServiceTest` | 7 | 156 |
| `MessageServiceTest` | 6 | 156 |

They are plain `@ExtendWith(MockitoExtension.class)` unit tests — no Spring context, no
`@SpringBootTest`, no integration or controller tests, and nothing exercising the WebSocket, Kafka,
or Redis layers.

**Two of these classes are stale and will not compile against `main`:**

1. `UserStatusServiceTest` calls `userStatusService.updateUserStatus(1L, UserStatus.IDLE)`
   (`:70`, `:87`). That method was removed from `UserStatusService` in the presence rewrite — the
   interface now exposes `handleHeartbeat`/`handleActivity`/`resolveStatus`-backed queries instead.
   The test still targets the `feature/kafka`-era API, so `src/test` compilation fails on `main`.
2. `MessageServiceTest` declares four `@Mock`s — `MessageRepository`, `UserRepository`,
   `ChannelRepository`, `SimpMessagingTemplate` — but `MessageService` now takes **six** constructor
   arguments, having gained `MessagePersistenceService` and `MessageEventPublisher`. With
   `@InjectMocks` against a `@RequiredArgsConstructor` class, the two unmatched parameters are
   injected as `null`, so any test reaching `eventPublisher.publish(...)` at `MessageService.java:71`
   throws `NullPointerException`.

In other words, the tests were written before the Kafka seam and the Redis presence rewrite and were
not updated alongside either. Since `build.gradle` sets `test { finalizedBy jacocoTestReport }` and
`check.dependsOn pmdMain, pmdTest`, a `./gradlew build` currently fails at test compilation —
independently of the missing Redis dependency (§8.1).

`frontend/README.md` is referenced by the root README for frontend testing instructions, but there is
no test tooling in `frontend/package.json` — no Vitest, Jest, or Testing Library.

## 8. Known issues

Ordered by severity. Items 1–3 prevent the system from working as committed.

### 8.1 The backend does not compile on `main`

`build.gradle` declares no Redis dependency, and none of the declared starters brings Spring Data
Redis in transitively. Meanwhile the following files import `org.springframework.data.redis.*`:

- `config/RedisConfig.java` — `StringRedisTemplate`, `RedisConnectionFactory`
- `config/RedisKeyExpirationListenerConfig.java` — `RedisMessageListenerContainer`, `PatternTopic`, `MessageListenerAdapter`
- `service/impl/UserStatusServiceImpl.java` — `StringRedisTemplate`

Affects `main` and `feature/redis` (`feature/kafka` predates the Redis code and is unaffected).

```groovy
implementation 'org.springframework.boot:spring-boot-starter-data-redis'
```

*Not verified by compilation* — no JRE is installed in this environment (`./gradlew` reports
"Unable to locate a Java Runtime"). The conclusion is from static inspection of the dependency block
against the imports, and no build output exists anywhere in the tree.

### 8.2 Presence never reaches OFFLINE

Redis keyspace notifications are disabled by default and nothing enables them, so
`PresenceExpirationListener` never fires. Fix in `docker-compose.yml`:

```yaml
command: redis-server --requirepass redis123 --notify-keyspace-events Ex
```

Full analysis: [REDIS.md](REDIS.md#keyspace-notifications-are-not-enabled).

### 8.3 The `kafka` profile silently discards all messages

No `@KafkaListener` exists on any branch, and `NoOpMessagePersistenceService` is active under that
profile. Messages are produced and broadcast, then lost.
[KAFKA.md](KAFKA.md#4-the-missing-consumer) includes a reference consumer.

### 8.4 Committed credentials

| Secret | Location |
|---|---|
| SonarCloud token | `build.gradle:29` |
| JWT signing secret | `application.properties:56` |
| Postgres password | `application.properties:13`, `docker-compose.yml` |
| Redis password | `application.properties:68`, `docker-compose.yml` |

The SonarCloud token should be **revoked** — it is in git history, so removing it from the working
tree is not sufficient. The JWT secret is the more dangerous of the two: anyone holding it can forge
a token for any user ID, since the subject is the only identity claim.

### 8.5 `DELETE /api/messages/{messageId}` is broken

```java
// controller/MessageController.java:85-95
Message message = messageService.getChannelMessages(null, null)
        .getContent().stream()
        .filter(m -> m.getId().equals(messageId))
        .findFirst()
        .orElseThrow(() -> new RuntimeException("Message not found"));
```

This passes `null` for both `Channel` and `Pageable` into
`findByChannelOrderByTimestampDesc`, then filters in memory. A null `Pageable` will fail inside
Spring Data before the query runs; even if it did execute, matching `channel = null` would return no
rows for any real message. The endpoint cannot succeed. It should be
`messageRepository.findById(messageId)` followed by an authorization check that the caller is the
author.

### 8.6 `/api/auth/refresh` accepts access tokens, and its validity check is unreachable

```java
Long userId = tokenProvider.getUserIdFromJWT(request.getRefreshToken());   // parses & throws first
if (!tokenProvider.validateToken(request.getRefreshToken())) { ... }       // unreachable
```

Two distinct problems:

1. **Ordering.** `getUserIdFromJWT` parses the token and throws `JwtAuthenticationException` on any
   invalid input, so the `validateToken` guard below it can never return the 400. Swap the two.
2. **No token-type claim.** `generateToken` and `generateRefreshToken` produce structurally identical
   JWTs differing only in expiry — neither carries a `type` claim. So an **access token is accepted
   as a refresh token**, letting a client roll its session forward indefinitely from a single access
   token and defeating the point of the shorter access TTL. Add a `type` claim and assert it here.

### 8.7 Seeded test user has a plaintext password and cannot log in

```java
// config/DataInitializer.java:26
user.setPassword("password123"); // Optional: hash it
userRepository.save(user);
```

`DataInitializer` writes directly through the repository, bypassing `UserService.createUser`, which
is where `passwordEncoder.encode(...)` is applied (`service/UserService.java:31`). The stored value is
plaintext, so `BCryptPasswordEncoder.matches` fails and `testuser` can never authenticate — while the
row itself is a plaintext credential in the database. Route the seed through `UserService`.

### 8.8 No per-channel subscription authorization

Any authenticated user can `SUBSCRIBE` to `/topic/channels/{anyId}/messages`. The membership check is
commented out at `WebSocketAuthInterceptor.java:119-128`.
[WEBSOCKETS.md](WEBSOCKETS.md#32-subscribe-and-send).

### 8.9 Mass assignment on message edit

`PUT /api/messages/{messageId}` binds a raw `Message` entity from the body and saves it, ignoring the
path variable and performing no ownership check (§4).

### 8.10 Single-instance ceiling

`enableSimpleBroker` keeps all subscriptions in process heap, so the application cannot run more than
one replica. [WEBSOCKETS.md](WEBSOCKETS.md#why-the-simple-broker).

### 8.11 Two stale test classes break the build

**Build blocker.** `UserStatusServiceTest` targets a method removed in the presence rewrite, and
`MessageServiceTest` mocks four of `MessageService`'s six constructor dependencies. Test compilation
fails on `main`, so `./gradlew build` cannot pass even once §8.1 is fixed. Detail in §7.

### 8.12 Smaller items

| Item | Location |
|---|---|
| Kafka `bootstrap-servers` hardcoded to `localhost:9092` | `KafkaProducerConfig.java:27` |
| `spring.kafka.producer.*` tuning silently ignored | `KafkaProducerConfig.java` |
| `application1.properties` is dead config | `src/main/resources/` |
| Duplicate OFFLINE broadcast | `PresenceExpirationListener.java` |
| `presence:last_seen:` written, never read, never expires | `PresenceKeys.java` |
| Unreachable OFFLINE branch in `resolveStatus` | `UserStatusServiceImpl.java:124-125` |
| ONLINE↔IDLE oscillation for continuously active users | [REDIS.md](REDIS.md#7-client-cadence) |
| `markIdle` is a no-op | `IdleProvider.tsx` |
| `isConnecting` guard not reset on WebSocket error | `WebSocketProvider.tsx` |
| Group message subscriptions never unsubscribed | `message.socket.ts` |
| `messageStore` grows unbounded | `WebSocketProvider.tsx` |
| Lombok `@Data` on JPA entities | `model/` |
| `UserServiceImpl extends` a concrete `UserService` | `service/impl/` |
| Duplicate `UserDTO` in `dto/` and `payload/` | — |
| Both `react-query` v3 and `@tanstack/react-query` v5 installed | `frontend/package.json` |
| Unused `sockjs-client` / `sockjs` | `frontend/package.json` |
| Unused MySQL connector | `build.gradle:65` |
| `tsc \|\| true` — type errors never fail the build | `frontend/package.json:8` |
| No integration/controller tests, no migrations, no `prod` profile | — |
| README states port 8082; actual is 8080 | `README.md:67` |

## 9. Suggested order of work

1. Add the Redis starter (§8.1) and repair the two stale test classes (§8.11) — together these are
   what stand between the repo and a green `./gradlew build`.
2. Enable keyspace notifications (§8.2) — presence is wrong without it.
3. Revoke the SonarCloud token; externalise all secrets to environment variables (§8.4).
4. Fix `DELETE` (§8.5), the refresh flow (§8.6), and the data seeder (§8.7).
5. Restore the channel-membership check on `SUBSCRIBE` (§8.8) and add an ownership check on edit (§8.9).
6. Either implement the Kafka consumer or drop the `kafka` profile (§8.3) — a profile that loses data
   is worse than no profile.
7. Add migrations (Flyway) and move off `ddl-auto=update`.
8. Repair the two stale test classes (§7) so `./gradlew build` passes, then extend coverage — the
   seams around `MessageEventPublisher` and `resolveStatus` are the natural next targets, since both
   are pure logic behind narrow interfaces.
