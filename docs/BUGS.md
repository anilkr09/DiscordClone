# Bugs and Implementation Mistakes

A numbered catalog of every defect found in `main` (`d10d627`) and in the companion
[`message-consumer-service`](https://github.com/anilkr09/message-consumer-service) (`4fcde05`), with
its location, impact, and fix.
The other docs explain *how the system works*. This one is the checklist for *what is wrong with it*.
Forward-looking suggestions that are not defects are in [IMPROVEMENTS.md](IMPROVEMENTS.md).

## How to read this

**Severity**

| Level | Meaning |
|---|---|
| **Critical** | Exploitable by any registered user, or stops the system from building or storing data |
| **High** | A core feature is broken for real users, or there is a significant security or privacy gap |
| **Medium** | Incorrect behavior with a workaround or limited blast radius |
| **Low** | Latent, cosmetic, or only affects maintainability |

**Evidence**

| Tag | Meaning |
|---|---|
| `read` | Confirmed by reading the code path end to end |
| `inferred` | Follows from framework semantics (Spring Data, Kafka client, JPA). Not executed, because no JRE was available when this was written |

Line numbers refer to `main`. `J/` abbreviates `src/main/java/com/discordclone/`, and `F/` abbreviates
`frontend/src/`. `CS/` abbreviates the consumer repository's
`src/main/java/com/discordclone/message/consumer/`; other consumer files are named `CS:<path>`.

IDs are stable. A defect added after the first pass gets the next free number and is listed under
its severity, so numbers are not strictly in severity order.

## Summary

| ID | Title | Severity | Area |
|---|---|---|---|
| [B01](#b01-password-hashes-are-returned-by-the-api) | Password hashes are returned by the API | Critical | Security |
| [B02](#b02-any-account-can-be-overwritten-via-post-apiusers) | Any account can be overwritten via `POST /api/users` | Critical | Security |
| [B03](#b03-server-membership-endpoints-have-no-authorization) | Server membership endpoints have no authorization | Critical | Security |
| [B04](#b04-channels-have-no-read-or-write-authorization) | Channels have no read or write authorization | Critical | Security |
| [B05](#b05-any-message-can-be-rewritten) | Any message can be rewritten | Critical | Security |
| [B06](#b06-plaintext-passwords-are-written-to-the-log) | Plaintext passwords are written to the log | Critical | Security |
| [B07](#b07-signing-secret-and-sonarcloud-token-are-committed) | Signing secret and SonarCloud token are committed | Critical | Security |
| [B08](#b08-the-backend-does-not-compile) | The backend does not compile | Critical | Build |
| [B09](#b09-two-test-classes-do-not-compile) | Two test classes do not compile | Critical | Build |
| [B11](#b11-dms-are-delivered-to-the-wrong-username) | DMs are delivered to the wrong username | High | Messaging |
| [B12](#b12-dm-recipient-and-channel-are-client-controlled) | DM recipient and channel are client-controlled | High | Security |
| [B13](#b13-dms-are-pinned-to-server-1) | DMs are pinned to server 1 | High | Messaging |
| [B14](#b14-presence-never-reaches-offline) | Presence never reaches OFFLINE | High | Redis |
| [B15](#b15-presence-is-visible-to-everyone) | Presence is visible to everyone | High | Privacy |
| [B16](#b16-token-refresh-is-broken-end-to-end) | Token refresh is broken end to end | High | Auth |
| [B17](#b17-access-tokens-are-accepted-as-refresh-tokens) | Access tokens are accepted as refresh tokens | High | Auth |
| [B18](#b18-chatarea-leaks-a-subscription-on-every-render) | `ChatArea` leaks a subscription on every render | High | WebSocket |
| [B19](#b19-a-kafka-outage-stalls-all-websocket-traffic) | A Kafka outage stalls all WebSocket traffic | High | Kafka |
| [B52](#b52-the-consumer-drops-messages-it-fails-to-save) | The consumer drops messages it fails to save | High | Kafka consumer |
| [B20](#b20-message-times-are-wrong-outside-utc) | Message times are wrong outside UTC | Medium | Messaging |
| [B21](#b21-delete-apimessagesid-can-never-succeed) | `DELETE /api/messages/{id}` can never succeed | Medium | API |
| [B22](#b22-put-apiserversidrolesroleid-can-never-succeed) | `PUT /api/servers/{id}/roles/{roleId}` can never succeed | Medium | API |
| [B23](#b23-servers-cannot-be-deleted) | Servers cannot be deleted | Medium | API |
| [B24](#b24-dm-view-hangs-or-mislabels) | DM view hangs or mislabels | Medium | Frontend |
| [B25](#b25-offline-users-reappear-with-their-custom-status) | Offline users reappear with their custom status | Medium | Redis |
| [B26](#b26-active-users-flicker-between-online-and-idle) | Active users flicker between ONLINE and IDLE | Medium | Redis |
| [B27](#b27-batched-status-updates-are-dropped) | Batched status updates are dropped | Medium | Frontend |
| [B28](#b28-messages-are-broadcast-before-they-are-committed) | Messages are broadcast before they are committed | Medium | Messaging |
| [B29](#b29-errors-map-to-the-wrong-http-status) | Errors map to the wrong HTTP status | Medium | API |
| [B30](#b30-channels-appear-twice-after-revisiting-a-server) | Channels appear twice after revisiting a server | Medium | Frontend |
| [B31](#b31-channel-creation-is-broadcast-to-every-user) | Channel creation is broadcast to every user | Medium | Privacy |
| [B32](#b32-failed-sends-are-silent) | Failed sends are silent | Medium | Frontend |
| [B33](#b33-websocket-reconnect-uses-the-original-token) | WebSocket reconnect uses the original token | Medium | WebSocket |
| [B34](#b34-logout-leaves-the-previous-users-data-cached) | Logout leaves the previous user's data cached | Medium | Frontend |
| [B35](#b35-the-seeder-creates-an-unusable-account-and-server) | The seeder creates an unusable account and server | Medium | Data |
| [B36](#b36-single-channel-endpoints-return-a-lazy-entity) | Single-channel endpoints return a lazy entity | Medium | API |
| [B37](#b37-invites-are-racy-unshareable-and-over-created) | Invites are racy, unshareable, and over-created | Medium | API |
| [B38](#b38-the-ci-workflow-cannot-succeed) | The CI workflow cannot succeed | Medium | CI |
| [B39](#b39-channel-routes-ignore-serverid-and-allow-type-changes) | Channel routes ignore `{serverId}` and allow type changes | Medium | API |
| [B40](#b40-frontend-calls-routes-that-do-not-exist) | Frontend calls routes that do not exist | Medium | Frontend |
| [B51](#b51-clean-disconnects-wait-for-the-heartbeat-ttl) | Clean disconnects wait for the heartbeat TTL | Medium | Redis |
| [B10](#b10-kafka-profile-persistence-depends-on-an-unmanaged-external-service) | Kafka-profile persistence depends on an unmanaged external service | Medium | Kafka |
| [B53](#b53-the-consumers-dead-letter-setup-would-fail-if-wired) | The consumer's dead-letter setup would fail if wired | Medium | Kafka consumer |
| [B54](#b54-latest-offset-reset-loses-messages) | `latest` offset reset loses messages | Medium | Kafka consumer |
| [B55](#b55-two-services-manage-one-schema) | Two services manage one schema | Medium | Data |
| [B56](#b56-messages-are-visible-before-they-are-stored) | Messages are visible before they are stored | Medium | Kafka |
| [B57](#b57-the-consumers-packaged-jar-likely-fails-to-start) | The consumer's packaged jar likely fails to start | Medium | Kafka consumer |
| [B41](#b41-offline-is-broadcast-twice) | OFFLINE is broadcast twice | Low | Redis |
| [B42](#b42-async-is-bypassed-by-self-invocation) | `@Async` is bypassed by self-invocation | Low | Redis |
| [B43](#b43-dead-presence-logic) | Dead presence logic | Low | Redis |
| [B44](#b44-stomp-handler-errors-are-swallowed) | STOMP handler errors are swallowed | Low | WebSocket |
| [B45](#b45-missing-input-validation) | Missing input validation | Low | API |
| [B46](#b46-no-op-and-stale-endpoints) | No-op and stale endpoints | Low | API |
| [B47](#b47-latent-websocket-client-and-interceptor-defects) | Latent WebSocket client and interceptor defects | Low | WebSocket |
| [B48](#b48-two-userservice-beans) | Two `UserService` beans | Low | Backend |
| [B49](#b49-event-types-out-of-sync) | Event types out of sync | Low | WebSocket |
| [B50](#b50-infrastructure-drift) | Infrastructure drift | Low | Ops |
| [B58](#b58-consumer-operational-gaps) | Consumer operational gaps | Low | Kafka consumer |

---

## Critical

### B01. Password hashes are returned by the API

`read` · `J/model/User.java`, `J/controller/UserController.java`, `ServerController.java`,
`InviteController.java`

`User.password` has no `@JsonIgnore`, and several controllers return entities:

- `GET /api/users` returns **every** user's email and BCrypt hash.
- `GET /api/users/{id}`, `/username/{name}`, and `/search?prefix=` return the same for matching
  users. The friend search UI calls `/search` on every keystroke, so ordinary use already downloads
  hashes into the browser.
- `Server.owner` is `EAGER`, so every endpoint that returns a `Server` embeds the owner's hash:
  `GET`/`POST /api/servers`, `POST`/`DELETE /api/servers/{id}/members/{userId}`, and
  `POST /api/invites/join/{code}`.

**Impact:** offline cracking of every account's password. BCrypt slows this down but does not stop
it for weak passwords.
**Fix:** add `@JsonIgnore` to `password` as a backstop, and return DTOs from every controller
(`ServerDTO` already exists).

### B02. Any account can be overwritten via `POST /api/users`

`inferred` · `J/controller/UserController.java:54-57`, `J/service/UserService.java:22-34`

The endpoint binds a raw `User` from the body. `createUser` checks that the *username* and *email*
are unused, encodes the password, and calls `save`. It never clears `id`. `SimpleJpaRepository.save`
merges when the ID is non-null, so this request replaces user 7's username, email, and password:

```json
POST /api/users
{"id": 7, "username": "fresh", "email": "fresh@example.com", "password": "mine"}
```

The attacker then logs in as that account, with its memberships, friendships, and history attached.
Only an authenticated session is required.
**Fix:** delete the endpoint (`/api/auth/register` already exists), or bind a DTO without `id`.

### B03. Server membership endpoints have no authorization

`read` · `J/controller/ServerController.java:41-53`, `J/service/ServerService.java:93-129`

`POST /api/servers/{id}/members/{userId}` and `DELETE /api/servers/{id}/members/{userId}` take no
principal and perform no checks.
**Impact:** anyone can join any server, including private ones, add other users without consent,
or remove any non-owner member of any server. Server and user IDs are sequential integers.
**Fix:** allow adding only via invite; allow removal only for self (leave) or for admins/owner.

### B04. Channels have no read or write authorization

`read` · `J/service/MessageService.java:38-74`, `J/controller/MessageController.java:45-75`,
`J/security/WebSocketAuthInterceptor.java:119-128`

| Path | Check performed |
|---|---|
| `SEND /app/chat.send` | authenticated only |
| `GET /api/messages/channels/{id}` | authenticated only |
| `SUBSCRIBE /topic/channels/{id}/messages` | destination prefix only; the membership check is commented out |

**Impact:** any user can read, live-follow, and post into any channel of any server.
**Fix:** call `ChannelService.checkUserIsMember` on all three paths, and use participant-based checks
for DMs (see B13).

### B05. Any message can be rewritten

`read` · `J/controller/MessageController.java:78-83`, `J/service/MessageService.java:98-106`

`PUT /api/messages/{messageId}` binds a raw `Message` and `save`s it. There is no ownership check,
and the path variable is ignored. Because the path variable is a `Long` and IDs are UUIDs,
`PUT /api/messages/{uuid}` fails with a 400. But `PUT /api/messages/1` with
`{"id": "<victim uuid>", "content": "...", "sender": {...}}` overwrites that message's content,
author, and channel, and broadcasts the result.
**Fix:** accept `{content}` only, load by the `String` ID, require `sender == principal`, and set
`edited = true` on the server.

### B06. Plaintext passwords are written to the log

`read` · `J/controller/AuthController.java:72`, `J/payload/LoginRequest.java`

`log.info("➡️ POST /login | request={}", loginRequest)`. `LoginRequest` is `@Data`, so `toString()`
includes `password`. Every login writes the user's password to the INFO log, and to any log
aggregator behind it.
**Fix:** log the username only, and add `@ToString.Exclude` on the password fields.

### B07. Signing secret and SonarCloud token are committed

`read` · `src/main/resources/application.properties:56`, `build.gradle:29`

The JWT HMAC secret is in the repository. The subject claim is the only identity claim, so anyone
with repository access can mint a valid token for any user ID. The SonarCloud token is also
committed. Both are in git history.
**Fix:** rotate the JWT secret, revoke the Sonar token, and inject both from environment variables
or a secret store.

### B08. The backend does not compile

`inferred` · `build.gradle`

`spring-boot-starter-data-redis` is not declared, yet `RedisConfig`,
`RedisKeyExpirationListenerConfig`, and `UserStatusServiceImpl` import
`org.springframework.data.redis.*`. The starter was removed in `cd97e97` ("global exception handler
update") and never restored when the presence rewrite reintroduced Redis code.
**Fix:** `implementation 'org.springframework.boot:spring-boot-starter-data-redis'`.

### B09. Two test classes do not compile

`read` · `src/test/java/com/discordclone/service/`

`UserStatusServiceTest` calls `updateUserStatus(...)` (`:70`, `:87`), which no longer exists on the
interface. `MessageServiceTest` mocks four of `MessageService`'s six dependencies, so
`eventPublisher` is `null`. `./gradlew build` fails at test compilation. That is why
`build_jar_command.txt` skips `test`.
**Fix:** rewrite `UserStatusServiceTest` against `handleHeartbeat`/`handleActivity`/`getUserStatus`,
and add `@Mock`s for the two publisher interfaces.

---

## High

### B11. DMs are delivered to the wrong username

`read` · `F/components/chat/ChatArea.tsx:91`, `F/components/chat/DirectMessage.tsx:66`

The DM recipient is not the friend's username. It is the chat header's **display name**,
lower-cased:

```tsx
// DirectMessage.tsx
name={friend?.username ? capitalizeFirst(friend.username) : "Unknown User"}
// ChatArea.tsx
<MessageInput ... receiver={name.toLowerCase().trim()} />
```

The backend delivers with `convertAndSendToUser(request.getReceiver(), ...)`, and Spring matches
principal names case-sensitively. So:

- A friend whose username contains **any** uppercase letter (`Alice`, `JohnDoe`) never receives your
  DMs in real time. They appear only after a history reload.
- If the friend is not found, the target becomes `"unknown user"`, which matches no one.

Usernames are not normalized at registration, so mixed case is common.
**Fix:** pass `friend.username` (or better, the friend's ID) straight through to `MessageInput`, and
have the server derive the recipient from the DM channel instead (see B12).

### B12. DM recipient and channel are client-controlled

`read` · `J/service/LocalMessageEventPublisher.java`, `J/service/KafkaMessageEventPublisher.java`

When `dm = true`, the server delivers to whatever `receiver` username the client sends, and stores
the message in whatever `channelId` the client sends. Neither is checked against the other, or
against the sender. A user can:

- write into another pair's DM channel, which then shows up in their history;
- push a message to any user's `/user/queue/messages`, attributed to a real channel;
- message users who have blocked them, because blocking is never consulted.

**Fix:** for DMs, derive the recipient from the channel's participants (`dmKey`), verify the sender
is one of them, and reject the send if either side has blocked the other.

### B13. DMs are pinned to server 1

`read` · `J/service/ChannelService.java:62-96`, `F/components/chat/DirectMessage.tsx:33`

`getOrCreateDmChannel` attaches every DM to `serverService.getServerById(1L)`. The frontend hardcodes
`/channels/1/dm/{id}` too, although the backend ignores that `1`.

- If server 1 does not exist, DM creation returns 404.
- Server-membership checks treat DMs as channels of server 1. The seeder creates no members for
  server 1, so `GET /api/channels/{any}/{dmId}` refuses DM participants. Anyone who *does* join
  server 1, which B03 lets them do, passes the check for **every** DM.
- The purpose-built `DmChannel` entity and repository are never used.

**Fix:** leave `server` null for DMs, record participants (in `DmChannel` or a channel-member
table), and authorize DMs by participation.

### B14. Presence never reaches OFFLINE

`read` · `docker-compose.yml:45`, `J/service/PresenceExpirationListener.java`

Redis ships with `notify-keyspace-events ""`, and nothing enables it, so expired-key events are never
published and `PresenceExpirationListener` never runs. Disconnect deliberately does not set OFFLINE,
so users who close the app keep their last status on everyone's screen.
**Fix:** `redis-server --notify-keyspace-events Ex`, or `CONFIG SET` at startup. See
[IMPROVEMENTS.md](IMPROVEMENTS.md#presence) for a design that does not depend on this at all.
Even once OFFLINE works, a user who closes the tab keeps their status for 20–30 s, because the
disconnect itself is ignored. That is B51.

### B15. Presence is visible to everyone

`read` · `J/service/impl/UserStatusServiceImpl.java:298-306`, `J/controller/UserStatusController.java:29`

Every status change goes to the single global topic `/topic/status`, and
`GET /api/users/{id}/status` answers for any user. Blocked users still see the blocker come online.
`F/providers/FriendStatusProvider.tsx` stores every user's status, friend or not.
**Fix:** fan out to each friend's `/user/queue/presence`, and require friendship or a shared server
for the REST read.

### B16. Token refresh is broken end to end

`read` · `F/services/api.ts:27-31`, `F/providers/AuthProvider.tsx:92-104`

- The axios interceptor posts to `/api/refresh-token` with an empty body. The real route is
  `/api/auth/refresh` with `{refreshToken}`. Every refresh fails, and the user is logged out.
- On page load, `AuthProvider` sees an expired access token and **deletes the refresh token too**,
  so it could not refresh even with the right route.

**Impact:** sessions last exactly 24 hours. The 7-day refresh token is never used.
**Fix:** call `authService.refreshToken({refreshToken})` from the interceptor, and try a refresh
before clearing state on load.

### B17. Access tokens are accepted as refresh tokens

`read` · `J/controller/AuthController.java:97-112`, `J/security/JwtService.java:44-66`

The two token types are identical except for their expiry; neither has a type claim. `/refresh`
accepts either, so one leaked access token can be rolled forward indefinitely. The endpoint's
`if (!validateToken(...))` 400 branch is unreachable, because `getUserIdFromJWT` throws first and
`validateToken` never returns `false`.
**Fix:** add a `typ` claim, check it in `/refresh`, and rotate refresh tokens with server-side
revocation.

### B18. `ChatArea` leaks a subscription on every render

`read` · `F/components/chat/ChatArea.tsx:20`

`registerGroupMessageSocket(client, id)` is called in the component body. Every render, including
the one each incoming message triggers, adds another STOMP subscription to the channel. None is
ever removed, even after leaving the channel. The Redux reducer's de-duplication hides duplicate
messages, but the broker sends N copies of every frame, the browser parses N copies, and N grows
for the life of the tab.
**Fix:** subscribe in a `useEffect` keyed on `[client, id, connected]` that returns `unsubscribe`.

### B19. A Kafka outage stalls all WebSocket traffic

`inferred` · `J/service/KafkaMessageEventPublisher.java:28`, `J/config/KafkaProducerConfig.java`

When the broker is unreachable, `KafkaProducer.send` blocks for `max.block.ms` (default 60 s)
waiting for metadata. It blocks on the `clientInboundChannel` worker that is processing the STOMP
frame. A few concurrent senders can occupy the whole pool, which stops heartbeats, activity, and
every other inbound frame for every user.
**Fix:** set `max.block.ms` to about 1–2 s, and hand the send off to a separate executor.

### B52. The consumer drops messages it fails to save

`read` · `CS/config/KafkaConsumerConfig.java:19-38`, `CS/consumer/MessageKafkaConsumer.java:34-37`

`KafkaConsumerConfig` builds a listener container factory with a back-off and a dead-letter
recoverer. The method has **no `@Bean` annotation**, so it is never called. Spring Boot's
auto-configured factory runs instead, with Spring Kafka's default error handler, `FixedBackOff(0, 9)`:
10 delivery attempts with no delay. After that, the record is logged at ERROR, its offset is
committed, and it is skipped.

**Impact:** any transient failure while saving makes messages disappear from history. A PostgreSQL
restart, a failover, or a stalled connection pool exhausts the ten attempts in milliseconds. A
deleted user or channel does too, after ten pointless retries. Users already saw the message live,
because it was broadcast on Kafka's acknowledgement, and it is gone on their next reload.
**Fix:** register a `DefaultErrorHandler` **bean** (Boot attaches it to its own factory), with an
exponential back-off in seconds and a working dead-letter recoverer (B53). Classify not-found errors
as non-retryable. Delete the unused factory method. See
[KAFKA.md §4.4](KAFKA.md#44-error-handling-what-was-written-versus-what-runs).

---

## Medium

### B20. Message times are wrong outside UTC

`read`+`inferred` · `J/model/Message.java`, `J/service/MessageService.java:50`,
`F/components/chat/MessageList.tsx:114`

Timestamps are `LocalDateTime.now()`, which carries no zone and is serialized without an offset.
The browser runs `new Date("2026-09-28T10:00:00")`, which interprets the value in the **viewer's**
local zone. With the server in UTC (the Render deployment), a user in IST sees every message
5 h 30 m in the past. The only zone setting, `spring.jackson.time-zone=UTC`, is in the unloaded
`application1.properties`, and it would not affect `LocalDateTime` anyway.
**Fix:** use `Instant` (or `OffsetDateTime`) for every timestamp.

### B21. `DELETE /api/messages/{id}` can never succeed

`read`+`inferred` · `J/controller/MessageController.java:85-95`

It calls `getChannelMessages(null, null)`. Spring Data turns the null `Channel` into
`WHERE channel_id IS NULL`, which matches nothing. The in-memory filter also compares a `String` ID
to a `Long` path variable, which is never equal. Every call returns a 500 "Message not found".
**Fix:** `messageRepository.findById(String id)`, check that the caller is the author, then delete.

### B22. `PUT /api/servers/{id}/roles/{roleId}` can never succeed

`read` · `J/controller/ServerController.java:70-77`

The method declares `@PathVariable Long userId`, but the route has no `{userId}`.
`MissingPathVariableException` becomes a 500. It also maps `roleId` to `Role.values()[roleId]`,
which couples the API to enum declaration order and lets the owner create extra `OWNER`s.
**Fix:** route `/{serverId}/members/{userId}/role` with a `{role}` body naming the enum, and forbid
granting `OWNER`.

### B23. Servers cannot be deleted

`inferred` · `J/service/ServerService.java:131-139`

`serverRepository.delete(server)` does not cascade. The owner's `server_members` row always exists,
along with channels and invites, and their foreign keys make the delete fail. It surfaces as 409
through the `DataIntegrityViolationException` handler.
**Fix:** delete dependents first in one transaction, use `ON DELETE CASCADE`, or soft-delete.

### B24. DM view hangs or mislabels

`read` · `F/components/chat/DirectMessage.tsx:21-51`

- The effect returns early when `friends.data.length === 0`. A user with no friends who opens
  `/channels/@me/{id}` sees "Loading..." forever.
- When the ID is not in the friends list, the DM is still created, the header shows
  "Unknown User", and sends go to `"unknown user"` (B11).

**Fix:** treat "friends loaded but not found" as its own state, and show an error or navigate away.

### B25. Offline users reappear with their custom status

`read` · `J/service/impl/UserStatusServiceImpl.java:100-128`

`resolveStatus` returns the custom override before it checks the heartbeat. After a DND user goes
offline, the cached OFFLINE expires 60 s later, and pull-based reads report `DO_NOT_DISTURB` for up
to 24 hours. Clients that only saw the push still show OFFLINE, so different viewers disagree.
**Fix:** check liveness first, and apply the override only to connected users.

### B26. Active users flicker between ONLINE and IDLE

`read` · `F/providers/IdleProvider.tsx`, `J/service/impl/UserStatusServiceImpl.java:40-56`

The client sends `/app/activity` only while its status is not ONLINE. Heartbeats do not refresh
`last_activity`. So a continuously active user goes ONLINE, stops sending activity, drops to IDLE
about 30 s later, and goes back to ONLINE on the next keystroke. Each transition broadcasts to every
user.
**Fix:** send activity at a low rate while ONLINE (the server already throttles at 5 s), or refresh
`last_activity` from the client side when there is recent input.

### B27. Batched status updates are dropped

`read` · `F/providers/FriendStatusProvider.tsx:36-45`, `F/providers/PresenceProvider.tsx`

Both providers read only `messages[messages.length - 1]`. When several frames arrive within one React
batch, only the last one is applied, and the others are lost until that user's next change. Bursts
are exactly when presence changes cluster: server restarts, reconnect storms, and expiry waves.
**Fix:** handle each frame in the subscription callback, or track a processed index.

### B28. Messages are broadcast before they are committed

`read` · `J/service/LocalMessageEventPublisher.java`

In the `local` profile, save and broadcast both run inside `MessageService.sendMessage`'s
transaction, so recipients see the message before it commits. A failed commit leaves clients showing
a message that does not exist.
**Fix:** broadcast from `@TransactionalEventListener(phase = AFTER_COMMIT)`.

### B29. Errors map to the wrong HTTP status

`read` · `J/exception/GlobalExceptionHandler.java:308-322`, `InviteService`, `ServerService`,
`UserService`

Expired invites, non-owner actions, "already a member", "user not found", and "invalid role" all
throw bare `RuntimeException`. They are mapped to **500**, and the internal message is echoed to the
client. `ChannelService` uses `UnauthorizedException`, mapped to **401**, for authorization failures
that should be **403**.
**Fix:** throw the specific exceptions that the handler already maps (404, 409, 400), and add a
`ForbiddenException` mapped to 403.

### B30. Channels appear twice after revisiting a server

`read` · `F/hooks/useChannels.ts:47-89`

The effect subscribes to `/topic/channels` without returning a cleanup, so each visit to a server
adds another subscription. The `CHANNEL_CREATED` handler appends without de-duplicating. Visit
server 1, then server 2, then server 1 again, and every new channel in server 1 appears twice.
**Fix:** return `() => sub.unsubscribe()`, and de-duplicate by ID in the handler.

### B31. Channel creation is broadcast to every user

`read` · `J/service/ChannelService.java:56`

`CHANNEL_CREATED` goes to `/topic/channels`, which every client subscribes to. The client filters by
`serverId`, but the name, description, and server ID of every new channel, including those in
private servers, reach every browser.
**Fix:** use a per-server topic gated by membership (B04).

### B32. Failed sends are silent

`read` · `F/components/chat/MessageInput.tsx:30-47`, `F/providers/WebSocketProvider.tsx:190-195`

- If the socket is disconnected, `sendMessage` logs a warning and returns. `MessageInput` clears the
  text box anyway, so the message is lost without any notice.
- Server-side failures go to `/user/queue/errors`, which no client subscribes to.

`react-hot-toast` is installed, but none of this is surfaced.
**Fix:** return success from `sendMessage` and keep the text on failure. Subscribe to
`/user/queue/errors` and show a toast.

### B33. WebSocket reconnect uses the original token

`read` · `F/providers/WebSocketProvider.tsx:57-64`, `J/config/WebSocketConfig.java`

`connectHeaders` is captured when the client is created. After the access token expires, every
auto-reconnect is rejected. There are also no STOMP heart-beats: the simple broker has no
`TaskScheduler`, so it negotiates `0,0`, and half-open connections go undetected.
**Fix:** use a `beforeConnect` callback that reads the current token, and configure
`setTaskScheduler(...).setHeartbeatValue(new long[]{10000, 10000})`.

### B34. Logout leaves the previous user's data cached

`read` · `F/providers/AuthProvider.tsx:195-210`

Logout clears four `localStorage` keys. It does not clear the React Query cache (servers, friends,
channels), the Redux message store, or `localStorage.currentUser`. The next user to log in on the
same tab briefly sees the previous user's servers and friends. Channels already marked `loaded`
are never refetched, so their cached messages persist.
**Fix:** on logout, call `queryClient.clear()`, dispatch a store reset, and remove `currentUser`.

### B35. The seeder creates an unusable account and server

`read` · `J/config/DataInitializer.java`

`testuser` is saved with the plaintext password `password123`, bypassing BCrypt, so it can never log
in. "Default Server", which DMs depend on (B13), gets no `Member` row, not even for its owner.
Server creation is nested inside "testuser missing", so a database that has `testuser` but lost
server 1 is never repaired.
**Fix:** create the seed user through `UserService`, add the owner membership, and make each seed
step independent.

### B36. Single-channel endpoints return a lazy entity

`inferred` · `J/controller/ChannelController.java:89-102`

`GET` and `PUT /api/channels/{serverId}/{channelId}` return `Channel`, whose `server` is a lazy
proxy. With `open-in-view=false` and no Hibernate Jackson module, serialization touches the proxy
after the session has closed. That typically fails with `LazyInitializationException` or a
ByteBuddy serializer error.
**Fix:** return `ChannelDTO`.

### B37. Invites are racy, unshareable, and over-created

`read` · `J/service/InviteService.java:41-76`, `J/controller/InviteController.java:33-34`,
`F/components/servers/InviteDialog.tsx:52-57`

- `uses` is read, checked, and incremented without a lock, so concurrent joins exceed `maxUses`.
- The returned `inviteUrl` is the backend's `POST /api/invites/join/{code}`, which a browser cannot
  open.
- Every time the invite dialog opens, it creates a new invite row.

**Fix:** use an atomic `UPDATE invites SET uses = uses + 1 WHERE code = ? AND uses < max_uses`,
return a frontend URL, and reuse an unexpired invite.

### B38. The CI workflow cannot succeed

`read` · `.github/workflows/docker-image.yml`

`./gradlew build` fails (B08, B09). The image is tagged `discord-backend`, but the workflow pushes
`anil0003/discord-clone:latest`. There is no `docker login`. The actions are pinned to deprecated
`@v2` versions.
**Fix:** see [IMPROVEMENTS.md](IMPROVEMENTS.md#ci-and-delivery).

### B39. Channel routes ignore `{serverId}` and allow type changes

`read` · `J/controller/ChannelController.java`, `J/service/ChannelService.java:120-127`

Get, update, delete, and DM-create ignore `{serverId}`, so `/api/channels/999/5` operates on
channel 5. Update copies `type` from a raw `Channel` body, so an admin can turn a text channel into
`DM`, which changes how it is authorized.
**Fix:** verify that `channel.server.id == serverId`, and accept a DTO that excludes `type`, or
restrict which transitions are allowed.

### B40. Frontend calls routes that do not exist

`read` · `F/services/server.service.ts:24,34`, `F/services/channel.service.ts:17,22`,
`F/services/message.service.ts:10,30,39`

| Frontend call | Backend reality |
|---|---|
| `POST /servers/{id}/leave`, `POST /servers/{id}/join` | no such routes |
| `PUT`/`DELETE /channels/{channelId}` | route is `/channels/{serverId}/{channelId}`; this returns 405 |
| `PUT`/`DELETE /channels/{cid}/messages/{mid}`, `POST /messages/channels/{id}` | no such routes |

These are latent today. `leaveServer`, `deleteServer`, and `deleteChannel` are exported from hooks,
but no component calls them. They will fail the moment someone wires up a button.
**Fix:** align the client with the API, ideally from a generated client (see
[IMPROVEMENTS.md](IMPROVEMENTS.md#frontend)).

### B51. Clean disconnects wait for the heartbeat TTL

`read` · `J/websocket/listener/WebSocketEventListener.java:44-59`

When a user closes the tab, quits the browser, or logs out, the socket closes cleanly and Spring
fires `SessionDisconnectEvent` immediately. `handleSessionDisconnect` only records last-seen. Its
comment reads *"DO NOT force OFFLINE / Let Redis TTL handle it"*. So the user keeps showing ONLINE
or IDLE until the heartbeat key expires, 20–30 s after the tab closed, and only once B14 is fixed.
Clean closes are the most common way users leave, so almost every departure is shown late.

The disconnect is ignored on purpose. `feature/kafka` marked OFFLINE on every disconnect, and commit
`dc1ca97` removed that because it made users flicker OFFLINE → ONLINE on every page reload, and
showed them OFFLINE when they closed one of several open tabs. The fix has to keep both of those
behaviours correct.

**Impact:** friends see departures 20–30 s late. The status is never wrong, only slow.
**Fix:** track each user's open STOMP sessions in a Redis set, which heartbeats re-add and a 30 s
TTL cleans up. When the last session closes, start an 8 s grace period with an expiring Redis key.
A reconnect on any instance cancels it. When it expires, the existing expiry listener and lock mark
the user OFFLINE, but only if no session has reopened and the heartbeat key still exists, and they
delete the heartbeat key so reads agree. Keep the heartbeat TTL as the fallback for unclean drops.
The 8 s value comes from the client's default 5 s reconnect delay plus the reconnect handshake.
Full reasoning, rejected alternatives, per-file changes, and limitations are in
[REDIS.md §10.14](REDIS.md#1014-finding-14--mark-offline-on-a-clean-disconnect-with-a-grace-period).
It depends on B14 being fixed.

### B10. Kafka-profile persistence depends on an unmanaged external service

`read` · `J/service/NoOpMessagePersistenceService.java`, `J/service/KafkaMessageEventPublisher.java`

*Earlier editions rated this Critical, as "the `kafka` profile never stores messages", because this
repository has no consumer. The consumer lives in a separate repository,
[`message-consumer-service`](https://github.com/anilkr09/message-consumer-service), which persists
the messages, so this was re-rated.*

Under the `kafka` profile, the main app persists nothing itself and relies entirely on that service.
Nothing here starts it, configures it, documents it, or checks that it is running. It is not in
`docker-compose.yml`, and neither repository explains how the two fit together.

**Impact:** if the consumer is not running, messages appear live but not in history until it
catches up. If it is first started after messages were produced, they are lost (B54). Its failure
handling drops messages (B52).
**Fix:** run the consumer as a compose service next to Kafka, with a health check. Document the
start-up order. Add consumer-group lag to monitoring. The details are in
[KAFKA.md §4–5](KAFKA.md#4-the-consumer-service).

### B53. The consumer's dead-letter setup would fail if wired

`inferred` · `CS/config/KafkaConsumerConfig.java:21`, `CS/config/KafkaDLTProducerConfig.java:3,22,24`

This is the trap waiting for whoever fixes B52 by adding `@Bean`:

1. The factory method injects `KafkaTemplate<String, MessageResponse>`, but the only template defined
   is `KafkaTemplate<String, Object>`. Spring matches generic types when injecting, so the consumer
   would fail to start.
2. The dead-letter producer's value serializer is `com.fasterxml.jackson.databind.JsonSerializer`,
   Jackson's abstract serializer base class, not a Kafka `Serializer`. The producer is created lazily,
   so creating it would fail on the first dead-letter publish. Spring Kafka then resets its back-off
   and redelivers the record, so one bad message is retried forever and blocks every channel on its
   partition.

The producer's broker address is also hard-coded to `localhost:9092`.
**Fix:** build the recoverer's template with Spring Kafka's JSON serializer (`JacksonJsonSerializer`
on Spring Kafka 4) and the bootstrap address from properties. Inject it as
`KafkaOperations<?, ?>`. Create `message-events-dlt` with at least the source topic's 4 partitions,
because the recoverer publishes to the same partition number.

### B54. `latest` offset reset loses messages

`read` · `CS:src/main/resources/application.properties:13-14`

`spring.kafka.consumer.auto-offset-reset=latest`, with the `earliest` line commented out. A consumer
group with no committed offset starts at the **end** of the topic. That happens on the group's first
start, and again whenever its committed offsets expire, which Kafka does by default 7 days after the
group becomes empty.

**Impact:** messages produced before the consumer was first deployed, or during an outage longer
than offset retention, are never stored. Deployment order becomes a source of data loss.
**Fix:** use `earliest`. The idempotent insert makes re-reading old records harmless.

### B55. Two services manage one schema

`read` · `CS/entity/*.java`, `CS:src/main/resources/application.properties:42`,
`src/main/resources/application.properties`

The consumer keeps its own copies of the `User`, `Server`, `Channel`, and `Message` entities, and it
runs `ddl-auto=update` against the same database as the main app. Its `Message` join columns lack
`nullable = false`. If the consumer starts first on an empty database, it creates `messages.channel_id`
and `messages.user_id` as nullable, and the main app's later `update` never tightens them. Any future
entity change made in one repository but not the other drifts the schema silently.

**Impact:** constraints depend on which service started first, and the two definitions can diverge
without any error.
**Fix:** the consumer should not manage schema. Set its `ddl-auto` to `validate` or `none`, and let
the main app own the tables through migrations.

### B56. Messages are visible before they are stored

`read` · `J/service/KafkaMessageEventPublisher.java`, consumer service

In the `kafka` profile, the broadcast happens on Kafka's acknowledgement, and the row is written
later by a different process. The gap is normally milliseconds. It is unbounded while the consumer is
down or lagging, and permanent for any message the consumer skips (B52).

**Impact:** a reload or a history fetch can miss messages everyone has already seen. Edits and
deletes, which the main app makes directly in the database, cannot find a message that is not yet
written. Nothing monitors the gap.
**Fix:** treat consumer lag as a monitored metric. In the client, keep live-received messages until
history confirms them, rather than replacing the list on fetch. Longer term, consider the
transactional outbox in [IMPROVEMENTS.md](IMPROVEMENTS.md#messaging-pipeline).

### B57. The consumer's packaged jar likely fails to start

`inferred` · `CS:build.gradle:5,8-10`

`application { mainClass = 'com/discordclone/message/consumer/MessageConsumerServiceApplication' }`
uses slashes. When the `application` plugin is applied, the Spring Boot Gradle plugin takes its main
class from it, and `bootJar` writes that value into the jar manifest. A slash-separated name is not a
valid binary class name, so the packaged jar would fail when it loads the main class. `bootRun` may
still work. This was not run.

**Impact:** the consumer may not be deployable as a jar, the normal way to ship it.
**Fix:** remove the `application` plugin, which Spring Boot does not need, or use the dotted class
name.

---

## Low

### B41. OFFLINE is broadcast twice

`read` · `J/service/PresenceExpirationListener.java`. `setOfflineAndBroadCast` already broadcasts,
and the listener then calls `broadcastStatusChange` again. `setOfflineAndBroadCast` also writes
`OFFLINE` to Redis while broadcasting its `status` parameter, so the two can disagree.

### B42. `@Async` is bypassed by self-invocation

`read` · `J/service/impl/UserStatusServiceImpl.java:266`. `resetPresence` calls `persistLastSeen`
through `this`, so it runs synchronously. The same applies to `@Transactional` on
`updateCustomStatus` → `clearCustomStatus`, which is harmless here.

### B43. Dead presence logic

`read` · `J/service/impl/UserStatusServiceImpl.java:124-125`, `J/constants/PresenceKeys.java`. The
`seenDiff > 60_000 → OFFLINE` branch is unreachable, because it reads a key with a 30 s TTL.
`presence:last_seen:*` is written on every heartbeat, never read, and never expires. `IdleProvider`'s
`markIdle` only logs. The custom status in `UserStatusEntity` is written, but Redis is never
rehydrated from it, so the override vanishes when Redis restarts.

### B44. STOMP handler errors are swallowed

`read` · `J/exception/WebSocketExceptionHandler.java`. Only `JwtAuthenticationException` has a
`@MessageExceptionHandler`. Anything else thrown from a `@MessageMapping` method, such as an unknown
`channelId`, is logged and dropped. The not-found message also reports the wrong ID:
`ResourceNotFoundException("Channel", "id", userId)` at `J/service/MessageService.java:46`.

### B45. Missing input validation

`read`

- `MessageRequest` has no constraints: empty, whitespace-only, or megabyte messages, and a null
  `channelId`, are all accepted.
- `ServerPayload` has none. A null name hits the NOT NULL constraint and is reported as "Server with
  name null already exists" (409).
- `POST /api/users/status/custom?status=foo` calls `UserStatus.valueOf` and returns a 500.
- `@MessageMapping` payloads are never `@Validated`.

### B46. No-op and stale endpoints

`read`

- `PUT /api/users/{id}/status` has its setter commented out. It re-saves the user unchanged, for
  any `id`, with no auth check.
- `GET /api` requires authentication, and it advertises routes and a port (8082) that do not exist.
- `Server.type` is never set.

### B47. Latent WebSocket client and interceptor defects

`read`

- `isConnecting` is not reset in `onWebSocketError`, so a failed connect can block retries.
- `messageStore` grows without bound per topic.
- `WebSocketAuthInterceptor.java:111` and `:139` dereference a possibly null principal. This is
  unreachable today only because `CONNECT` is enforced.
- Invalid tokens are rejected by an exception, not by the `validateToken` guard that appears to
  handle them.

### B48. Two `UserService` beans

`read` · `J/service/UserService.java`, `J/service/impl/UserServiceImpl.java`. Both are `@Service`
and both are assignable to `UserService`. Injection resolves only by parameter-name fallback, so
`UserServiceImpl`, with its 404-throwing overrides, is never used. Renaming a parameter breaks
startup.

### B49. Event types out of sync

`read`. The frontend handles `FRIEND_REQUEST_CANCELLED`, `FRIEND_REQUEST_SENT`, `CHANNEL_DELETED`,
and `CHANNEL_UPDATED`, none of which the backend emits. Channel deletions and edits never reach other
clients. `WsEventType.USER_ONLINE`/`USER_OFFLINE` and `WsDestinations.PRESENCE`/`MESSAGES` are
unused.

### B50. Infrastructure drift

`read`

- `kafka-data` is declared but not mounted.
- `kafka-init` relies on `sleep 10`.
- `spring.websocket.*`, `spring.h2.*`, `spring.cache.*`, `app.kafka.enabled`, and every
  `spring.kafka.producer.*` tuning property are inert.
- `application1.properties` is never loaded.
- Committed artifacts include a stale 50 MB jar, root `node_modules/`, `.DS_Store`, H2 database
  files, and two unused compose files.

### B58. Consumer operational gaps

`read` · consumer repository

- **No health signal.** There is no web starter, so `server.port=8081` does nothing and no Actuator
  endpoint exists. A stopped or stuck consumer is visible only as Kafka lag.
- **The only test, `contextLoads`, needs live Kafka and PostgreSQL**, so it fails in CI.
- **Four queries per message** (existence check, user, channel, and a pre-insert `SELECT`, because the
  entity has an assigned ID) where two would do. Use `getReferenceById` and `Persistable`.
- **Dead code.** `exception/KafkaErrorHandler` is an empty class. The listener's `event == null`
  branch is reached only for genuinely null payloads, because records that fail to deserialize go
  straight to the error handler as fatal.
- **Contract by copy.** `dto/MessageResponse` duplicates the producer's DTO by hand (see
  [KAFKA.md §4.5](KAFKA.md#45-the-event-contract)). `spring.json.trusted.packages=*` is unused while
  type headers are ignored, but should be narrowed if they are ever used.
- **Deprecated deserializer.** The Jackson 2 `JsonDeserializer` is deprecated in Spring Kafka 4, the
  version Boot 4 uses. It still works, but should move to `JacksonJsonDeserializer`. The two services
  are also on different Spring Boot major versions (3.2.2 and 4.0.5).

---

## Implementation mistakes: the patterns behind the bugs

Most of the bugs above come from a handful of recurring decisions. Fixing each pattern once is
cheaper than fixing its bugs one at a time.

| Pattern | Where | Bugs it causes |
|---|---|---|
| **Returning and binding JPA entities at the API boundary.** Controllers accept `User`, `Message`, `Channel`, `Server` bodies and return entities | `UserController`, `MessageController`, `ChannelController`, `ServerController`, `InviteController` | B01, B02, B05, B36, B39 |
| **Per-method, opt-in authorization.** Every service method must remember its own check; no central policy | services, `WebSocketAuthInterceptor` | B03, B04, B12, B15, B31 |
| **Trusting client-supplied identity.** Recipient, channel, and IDs in bodies are taken at face value | `MessageRequest`, `POST /api/users`, message edit | B02, B05, B11, B12 |
| **Modeling DMs as a special case of server channels** instead of their own participant model | `ChannelService.getOrCreateDmChannel` | B13, B12, B24 |
| **Side effects inside transactions or on I/O threads.** Broadcasts before commit, blocking sends on inbound workers | publishers | B19, B28 |
| **Error signalling by exception where a boolean was expected**, and bare `RuntimeException` for business errors | `JwtService.validateToken`, services | B17, B29, B47 |
| **Zone-less time.** `LocalDateTime` everywhere | `Message`, `Friendship`, `UserStatusEntity` | B20 |
| **Subscriptions outside effect lifecycles.** Subscribing in render bodies or effects without cleanup | `ChatArea`, `useChannels` | B18, B30 |
| **Reading an accumulated array's last element** instead of handling events | `useWebSocketTopic` consumers | B27 |
| **Configuration that looks live but is not.** Explicit beans that silently override properties, unloaded files, and a factory method without `@Bean` | `KafkaProducerConfig`, `application1.properties`, `spring.websocket.*`, consumer `KafkaConsumerConfig` | B19, B50, B52, B53 |
| **A second service with nothing tying it in.** The consumer is not started, configured, tested, or monitored from the main repository, and the two share a database and a DTO by copy | consumer repository | B10, B54, B55, B56, B57, B58 |
| **Two sources of truth without reconciliation.** Custom status in Redis and Postgres; server/client API contracts hand-maintained | presence, frontend services | B40, B43 |
| **Code and tests not updated together.** Refactors land without updating tests or the client | tests, frontend services | B09, B40, B49 |
