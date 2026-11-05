# Security

This document reviews every security aspect of the system: what is protected, how, where the
protection is missing or wrong, and what to do about it, in priority order. It covers the backend,
the frontend, the WebSocket layer, the infrastructure in `docker-compose.yml`, the build and
delivery pipeline, and the companion
[`message-consumer-service`](https://github.com/anilkr09/message-consumer-service).

**Scope and versions.**

- **Backend and frontend:** branch `docs1` at `57013bc`. That branch is `main` (`d10d627`) plus
  documentation and one code change, the channel authorization in `c06ad5f`. Where the two branches
  differ, both are described.
- **Consumer service:** `4fcde05`.

**Relationship to other docs.** Most defects referenced here are already catalogued in
[BUGS.md](BUGS.md), with full reproduction details. This document groups them by security concern
and adds the analysis around them. Findings that are **not yet in BUGS.md** are marked **New** and
numbered S1–S7 (§15). Roadmap items are in [IMPROVEMENTS.md](IMPROVEMENTS.md#security-and-authentication).

**Evidence tags**, as in BUGS.md:

- `read`: confirmed by reading the code path end to end.
- `inferred`: follows from framework semantics. Not executed, because the backend does not compile
  ([B08](BUGS.md#b08-the-backend-does-not-compile)).

`J/` abbreviates `src/main/java/com/discordclone/`, and `F/` abbreviates `frontend/src/`.

**Secrets.** This document names the files and lines where secrets are committed, but it never
repeats their values.

## Contents

1. [Posture at a glance](#1-posture-at-a-glance)
2. [Threat model](#2-threat-model)
3. [Authentication](#3-authentication)
4. [Authorization](#4-authorization)
5. [WebSocket security](#5-websocket-security)
6. [Data exposure and privacy](#6-data-exposure-and-privacy)
7. [Input handling](#7-input-handling)
8. [Transport and browser security](#8-transport-and-browser-security)
9. [Secrets management](#9-secrets-management)
10. [Infrastructure](#10-infrastructure)
11. [Logging, monitoring and audit](#11-logging-monitoring-and-audit)
12. [Availability and abuse resistance](#12-availability-and-abuse-resistance)
13. [Supply chain and delivery](#13-supply-chain-and-delivery)
14. [What is done right](#14-what-is-done-right)
15. [New findings](#15-new-findings)
16. [Remediation plan](#16-remediation-plan)
17. [Security test checklist](#17-security-test-checklist)

---

## 1. Posture at a glance

The authentication mechanism is sound in outline: BCrypt passwords, signed stateless tokens, and a
token check on every HTTP request and on every WebSocket connection. **Authorization is where the
system fails.** Any registered user can take over any account, read every password hash, join any
server, read and post into any channel, and forge real-time events. Separately, the token signing
secret is in the repository, so anyone who can read the repository can impersonate any user without
registering at all.

The ten most serious problems, in priority order:

| # | Problem | Who can exploit it | Reference |
|---|---|---|---|
| 1 | JWT signing secret committed: anyone with repository access can mint a token for any user | Anyone who can read the repo or any jar/image built from it | [B07](BUGS.md#b07-signing-secret-and-sonarcloud-token-are-committed), §9 |
| 2 | Any account can be overwritten through `POST /api/users` | Any registered user | [B02](BUGS.md#b02-any-account-can-be-overwritten-via-post-apiusers) |
| 3 | Every user's BCrypt hash and email is returned by user and server endpoints | Any registered user | [B01](BUGS.md#b01-password-hashes-are-returned-by-the-api) |
| 4 | Any channel, including every DM, can be live-followed by subscribing to its topic | Any registered user | [B04](BUGS.md#b04-channels-have-no-read-or-write-authorization), §5.2 |
| 5 | Clients can publish directly to broker topics and other users' queues, forging messages and presence | Any registered user | **New**: [S1](#s1-clients-can-publish-directly-to-broker-destinations) |
| 6 | Anyone can add or remove members of any server | Any registered user | [B03](BUGS.md#b03-server-membership-endpoints-have-no-authorization) |
| 7 | Any message can be rewritten | Any registered user | [B05](BUGS.md#b05-any-message-can-be-rewritten) |
| 8 | Plaintext passwords in the server log, and in the browser console at sign-up | Anyone with log or console access | [B06](BUGS.md#b06-plaintext-passwords-are-written-to-the-log), **New**: [S3](#s3-the-frontend-logs-the-sign-up-password-and-tokens) |
| 9 | Access tokens accepted as refresh tokens, with no revocation, so a stolen token lives forever | Anyone holding one leaked token | [B17](BUGS.md#b17-access-tokens-are-accepted-as-refresh-tokens) |
| 10 | DM recipient and channel are client-controlled | Any registered user | [B12](BUGS.md#b12-dm-recipient-and-channel-are-client-controlled) |

**On `docs1`,** the new channel authorization targets items 4 and 7, but it does not compile, it
leaves `SUBSCRIBE` open, and it authorizes DMs incorrectly (**New**:
[S2](#s2-the-docs1-channel-authorization-does-not-compile-and-mis-authorizes-dms)). As written,
it would not close either item.

---

## 2. Threat model

### 2.1 Assets

| Asset | Where it lives | Why it matters |
|---|---|---|
| Account credentials | `users.password` (BCrypt) in PostgreSQL | Password reuse makes a cracked hash dangerous beyond this app |
| Tokens | Browser `localStorage`; in transit on every request | A token is a full session for 24 h (access) or 7 days (refresh) |
| JWT signing secret | `application.properties`, and every jar or image built from the repo | Whoever holds it can impersonate every user |
| Messages, especially DMs | `messages` table; Kafka topic `message-events` under the `kafka` profile; WebSocket frames | Private conversations |
| Social graph | `friendships`, `server_members` | Who knows whom, and blocks |
| Presence | Redis `presence:*` keys and the `/topic/status` stream | Reveals when someone is online, which is personal data |
| Server integrity | Servers, channels, memberships, roles | Defacement, eviction, takeover of communities |
| Infrastructure credentials | `docker-compose.yml`, properties files, `build.gradle` | Database, Redis, pgAdmin and SonarCloud access |

### 2.2 Actors

| Actor | Capability |
|---|---|
| Anonymous internet user | Can reach `/`, `/health`, `/api/auth/**` and the `/ws` handshake; can register freely |
| **Registered user** | Holds a valid token. Because authorization is missing in many places, this is the most important attacker in practice |
| Repository reader | Anyone who can read the source, its history, the committed jar, or a built image, including every past collaborator and every CI system |
| Same-network attacker | Can reach any `docker compose` port bound on all interfaces (§10) |
| Script in the page | XSS or a compromised npm dependency; can read `localStorage` |
| Operator with log access | Sees whatever the application logs, including passwords today |

### 2.3 Trust boundaries

```
 Browser (React SPA, tokens in localStorage)
   │  HTTPS: REST with Authorization: Bearer
   │  WSS:   STOMP, token sent once in the CONNECT frame
   ▼
 Spring Boot API ──────────────► PostgreSQL   (one owner account with DDL rights)
   │                ├──────────► Redis        (password auth, no TLS)
   │                └──────────► Kafka        (PLAINTEXT, no auth; kafka profile only)
   │                                  │
   │                                  ▼
   │                     message-consumer-service ──► the same PostgreSQL, same account
   ▼
 In-memory STOMP broker: every subscription and fan-out happens inside the API process
```

Everything to the right of the API trusts the API completely. No database row, Redis key or Kafka
record is authenticated per user, so every check must happen in the API. That is exactly where
checks are missing.

---

## 3. Authentication

### 3.1 Passwords

| Aspect | Current state | Assessment |
|---|---|---|
| Storage | `BCryptPasswordEncoder`, default strength 10 (`J/security/SecurityConfig.java`) | Good |
| Policy | `RegistrationRequest`: `@Size(min = 6)`, no maximum | Weak minimum. BCrypt only uses the first 72 bytes, so a cap of 72 bytes, or a pre-hash, should be explicit |
| Breached-password check | None | Recommended for any public sign-up |
| Login throttling or lockout | None (§12) | Online guessing is unlimited |
| Seeded account | `DataInitializer` stores `testuser` with a **plaintext** password, bypassing the encoder | The account cannot log in ([B35](BUGS.md#b35-the-seeder-creates-an-unusable-account-and-server)), but a plaintext credential sits in a table that normally holds only hashes |
| Change and reset | No endpoints | Users cannot recover from compromise; tokens are not invalidated by a password change because none exists |
| Hash exposure | Hashes are returned by the API ([B01](BUGS.md#b01-password-hashes-are-returned-by-the-api)) | Turns BCrypt into the *only* defence, which it was never meant to be |

### 3.2 Token design

`J/security/JwtService.java` issues two JWTs with **jjwt 0.11.5**:

| Property | Value |
|---|---|
| Algorithm | HS512, chosen by jjwt from the 64-byte key |
| Key | `app.jwt.secret`, a 64-character hex string used as raw ASCII bytes. That gives 512 bits of key length but 256 bits of entropy, which is adequate. Its being **committed** is the problem ([B07](BUGS.md#b07-signing-secret-and-sonarcloud-token-are-committed)) |
| Claims | `sub` (user ID), `iat`, `exp`. No `iss`, `aud`, `jti` or token-type claim |
| Access lifetime | 24 h (`app.jwt.expiration`) |
| Refresh lifetime | 7 days (`app.jwt.refresh-expiration`) |
| Parsing | `parserBuilder().setSigningKey(key).parseClaimsJws(...)` rejects unsigned (`alg: none`) and wrongly signed tokens. No algorithm-confusion risk with a symmetric key |
| Clock skew | None allowed |

Weaknesses:

- **No token type** ([B17](BUGS.md#b17-access-tokens-are-accepted-as-refresh-tokens)). Access and
  refresh tokens differ only in expiry. `/api/auth/refresh` accepts either, so a stolen access token
  can be exchanged for fresh tokens indefinitely.
- **No revocation.** There is no logout endpoint, no token store and no `jti`. A token stays valid
  until it expires, through logout, a ban, or a deleted account. For a deleted account, the HTTP
  filter does fail, because it reloads the user (§3.3); a WebSocket session does not (§5.1).
- **No rotation.** `/refresh` issues new tokens without invalidating the old refresh token.
- **The refresh flow is broken anyway** ([B16](BUGS.md#b16-token-refresh-is-broken-end-to-end)), so
  in practice sessions end after 24 h and the refresh token's only effect is to widen exposure.
- **One key for everything.** Rotating the key logs every user out, and there is no key ID (`kid`),
  so two keys cannot overlap during a rotation.

### 3.3 Per-request authentication over HTTP

`JwtAuthenticationFilter` (a `OncePerRequestFilter` placed before
`UsernamePasswordAuthenticationFilter`):

1. reads `Authorization: Bearer …`;
2. validates the token and reads the user ID from `sub`;
3. **reloads the user from the database** with `CustomUserDetailsService.loadUserById`;
4. sets the `SecurityContext`.

On any failure it logs and continues unauthenticated, so `.anyRequest().authenticated()` rejects
protected routes. Reloading the user on every request costs one query, but it means a deleted
account stops working immediately over HTTP. Roles are not carried, because the application has
none (§4.1).

The `if (!validateToken(...))` guards in the filter and in the STOMP interceptor are unreachable,
because `validateToken` throws instead of returning `false`. This is harmless today, but misleading
([B47](BUGS.md#b47-latent-websocket-client-and-interceptor-defects)).

### 3.4 Authentication over WebSocket

The token is checked **once**, on the STOMP `CONNECT` frame, in `WebSocketAuthInterceptor`. The
principal is then bound to the session for its whole lifetime. Consequences are in §5.1.

### 3.5 Token handling in the browser

| Aspect | Current state | Risk |
|---|---|---|
| Storage | `accessToken`, `refreshToken`, `username` and `id` in `localStorage` (`F/providers/AuthProvider.tsx:134-140`) | Readable by any script in the origin. One XSS, or one malicious dependency, exfiltrates both tokens, and B17 makes that permanent |
| Logging | Sign-up logs the username, email and **plaintext password**, then the full response including both tokens, to the console (`AuthProvider.tsx:160-162`) | **New**: [S3](#s3-the-frontend-logs-the-sign-up-password-and-tokens) |
| Logout | Client-side only: removes the `localStorage` keys. Nothing is revoked, and cached data survives ([B34](BUGS.md#b34-logout-leaves-the-previous-users-data-cached)) | A copied token keeps working |
| Reconnect | The WebSocket reconnects with the token it started with ([B33](BUGS.md#b33-websocket-reconnect-uses-the-original-token)) | Fails after expiry rather than refreshing |
| Expired token on load | Deletes the refresh token too ([B16](BUGS.md#b16-token-refresh-is-broken-end-to-end)) | Availability, not confidentiality |

The common alternative, an `HttpOnly`, `Secure`, `SameSite` cookie for the refresh token and a
short-lived access token kept in memory, removes token theft by XSS. It brings CSRF back into scope
for the refresh endpoint, which then needs `SameSite=Strict` or a CSRF token (§8.2).

---

## 4. Authorization

### 4.1 The model

- **No roles or authorities.** `UserPrincipal.getAuthorities()` carries nothing that any rule
  checks. `@EnableMethodSecurity` is on, but there is no `@PreAuthorize` anywhere.
- **Every check is hand-written**, per service method (`ServerService.isUserMember`/`isUserAdmin`,
  `ChannelService.checkUserIsAdmin`, owner comparisons, and on `docs1`
  `ChannelAuthorizationService`). A new endpoint inherits nothing.
- **Wrong status codes.** Failures are thrown as `UnauthorizedException` (401) or as bare
  `RuntimeException` (500), instead of 403 ([B29](BUGS.md#b29-errors-map-to-the-wrong-http-status)).
  `docs1` adds `ChannelAccessDeniedException`, mapped to 403, which is the right shape.
- **IDs are sequential integers** for users, servers and channels, so every missing check is
  trivially enumerable. Message IDs are UUIDs, which only obscures them; they are not access
  control, and channel history and topics expose them anyway.

### 4.2 Endpoint matrix

What each route checks beyond "has a valid token". The `docs1` column shows only rows that changed.

| Route | Check on `main` | Change on `docs1` | Gap |
|---|---|---|---|
| `POST /api/users` | **none**; binds a raw `User`, including `id` | — | Account takeover ([B02](BUGS.md#b02-any-account-can-be-overwritten-via-post-apiusers)) |
| `GET /api/users`, `/{id}`, `/username/{u}`, `/search` | none | — | Returns entities with password hashes ([B01](BUGS.md#b01-password-hashes-are-returned-by-the-api)) |
| `PUT /api/users/{id}/status` | none; no-op | — | Stale endpoint ([B46](BUGS.md#b46-no-op-and-stale-endpoints)) |
| `GET /api/users/{id}/status` | none | — | Presence of anyone ([B15](BUGS.md#b15-presence-is-visible-to-everyone)) |
| `POST /api/servers` | creator becomes owner | — | — |
| `GET /api/servers/{id}` | **none** | — | Any server's details (returned as a DTO, so no hash) |
| `PUT`/`DELETE /api/servers/{id}` | owner only | — | Returns 500 instead of 403 |
| `PUT /api/servers/{id}/roles/{roleId}` | owner only | — | Route cannot succeed ([B22](BUGS.md#b22-put-apiserversidrolesroleid-can-never-succeed)) |
| `POST`/`DELETE /api/servers/{id}/members/{userId}` | **none** | — | Join or evict anywhere ([B03](BUGS.md#b03-server-membership-endpoints-have-no-authorization)) |
| `POST /api/channels/{sid}` | admin of server | — | `{sid}` is used here, but ignored by the other channel routes ([B39](BUGS.md#b39-channel-routes-ignore-serverid-and-allow-type-changes)) |
| `GET /api/channels/{sid}`, `/{cid}` | member of server | — | DMs: membership of server 1 ([B13](BUGS.md#b13-dms-are-pinned-to-server-1)) |
| `PUT`/`DELETE /api/channels/{sid}/{cid}` | admin of the channel's server | — | Admins of server 1 can rename, retype or delete any DM |
| `POST /api/channels/{sid}/dm/{userId}` | none | — | A DM channel can be created with anyone, blocked or not |
| `GET /api/messages/channels/{id}` | **none** | channel access | See §4.3 |
| `PUT /api/messages/{id}` | **none**; saves the body | owner + channel access | Body still saved as-is ([B05](BUGS.md#b05-any-message-can-be-rewritten)) |
| `DELETE /api/messages/{id}` | none; cannot succeed ([B21](BUGS.md#b21-delete-apimessagesid-can-never-succeed)) | owner + channel access | Does not compile |
| `POST /api/invites/create` | member of server | — | — |
| `POST /api/invites/join/{code}` | valid, unexpired code | — | Moot while B03 allows joining directly; 32-bit codes (§6.4) |
| `/api/friends/**` | caller must be the request's receiver, or a party | — | Correct |
| `POST`/`DELETE /api/users/status/custom`, `DELETE /status/reset` | applies to the caller | — | Correct |
| STOMP `SEND /app/chat.send` | **none** | channel access | DM recipient is client-chosen ([B12](BUGS.md#b12-dm-recipient-and-channel-are-client-controlled)) |
| STOMP `SUBSCRIBE /topic/**` | prefix only | — | Live read of any channel (§5.2) |
| STOMP `SEND` to `/topic/**`, `/user/**` | **none** | — | Forgery (§5.3, S1) |

### 4.3 The channel authorization on `docs1`

`c06ad5f` adds `J/security/ChannelAuthorizationService.java`, calls it from `MessageController`, and
maps `ChannelAccessDeniedException` to 403 over HTTP and to `/user/queue/errors` over STOMP. The
intent is right: one service, called before every channel read and write, with a dedicated
exception. The implementation has these problems (**New**:
[S2](#s2-the-docs1-channel-authorization-does-not-compile-and-mis-authorizes-dms)):

1. **It does not compile, for two reasons.**
   - It references `ChannelType.GROUP_DM`, but `ChannelType` has only `TEXT`, `ANNOUNCEMENT` and
     `DM`.
   - `MessageController.editMessage` and `deleteMessage` pass their `@PathVariable Long messageId` to
     `MessageService.getMessageById(String)`.

   This adds to the existing build failure (B08).
2. **DMs are authorized as server-1 channels.** The method checks `channel.getServer() != null`
   first, and every DM is attached to server 1
   ([B13](BUGS.md#b13-dms-are-pinned-to-server-1)). The DM branch is never reached. The effect is
   the same as B13: DM participants are refused unless they are members of server 1, and every
   member of server 1, which B03 lets anyone become, can read and post into every DM.
3. **The DM parser expects the wrong key format.** It splits `dmKey` on `_` and expects
   `userId1_userId2`, but `ChannelService` writes `dm-{min}-{max}`. Even with the server check
   removed, `Long.parseLong("dm-1-2")` throws, the exception is caught, and access is denied to
   everyone. `CODE_CHANGES.md` documents the wrong format.
4. **`SUBSCRIBE` is still unchecked**, so the live stream of any channel remains readable. That is
   the more important read path, since it delivers every new message (§5.2).
5. **Edit still persists the request body.** The ownership check loads the message named by the
   path, but `messageService.editMessage(message)` saves the *body*, whose `id`, `sender` and
   `channel` the client controls. An attacker can name their own message in the path and a victim's
   ID in the body ([B05](BUGS.md#b05-any-message-can-be-rewritten)). The fix is to bind `{content}`
   only and update the loaded entity.
6. **Smaller issues.**
   - A missing channel on send is reported as "access denied", which is acceptable because it
     avoids an existence oracle.
   - A `null` `channelId` reaches `findById(null)`, which throws `IllegalArgumentException` and
     yields a 500 rather than a 400.
   - Deletion is owner-only, with no moderator path. That is a product decision, already noted in a
     comment.

A version that would close B04 and B13 together:

- authorize DMs by participation, from a participant table or a parsed `dm-{a}-{b}` key, *before*
  any server check;
- call the same service from `WebSocketAuthInterceptor` on `SUBSCRIBE /topic/channels/{id}/**`;
- fix B12 by deriving the DM recipient from the channel.

### 4.4 DMs

DMs combine three weaknesses:

- **Pinned to server 1** ([B13](BUGS.md#b13-dms-are-pinned-to-server-1)), so server membership is
  meaningless for them.
- **Client-controlled routing** ([B12](BUGS.md#b12-dm-recipient-and-channel-are-client-controlled)).
  The server delivers to whatever `receiver` the client sends, and stores into whatever `channelId`
  it sends. Blocks are never consulted.
- **Open topic.** A DM's `/topic/channels/{id}/messages` can be subscribed to by anyone (§5.2).

The unused `DmChannel` entity already models two participants. Using it, or a channel-member table,
is the basis for every DM fix.

### 4.5 Mass assignment

Controllers that bind JPA entities from request bodies let clients set fields they should not:

| Endpoint | Bound type | What the client can set |
|---|---|---|
| `POST /api/users` | `User` | `id`, which leads to takeover ([B02](BUGS.md#b02-any-account-can-be-overwritten-via-post-apiusers)) |
| `PUT /api/messages/{id}` | `Message` | `id`, `sender`, `channel`, `edited`, `timestamp` ([B05](BUGS.md#b05-any-message-can-be-rewritten)) |
| `PUT /api/channels/{sid}/{cid}` | `Channel` | `type`, so a text channel can be turned into a DM ([B39](BUGS.md#b39-channel-routes-ignore-serverid-and-allow-type-changes)) |
| `PUT /api/servers/{id}` | `Server` | Copied fields only; still an entity at the boundary |

The structural fix is the one in [IMPROVEMENTS.md](IMPROVEMENTS.md#enforce-a-dto-boundary-on-every-controller--now--m):
request DTOs that contain only writable fields, and response DTOs everywhere.

---

## 5. WebSocket security

Protocol background is in [WEBSOCKETS.md](WEBSOCKETS.md) and
[guides/STOMP_WEBSOCKET.md](guides/STOMP_WEBSOCKET.md).

### 5.1 Session authentication

- **Handshake.** `/ws/**` is `permitAll()`. A browser cannot set an `Authorization` header on a
  WebSocket handshake, so the token travels in the STOMP `CONNECT` frame instead. This is a standard
  and acceptable design.
- **Missing or invalid token.** The interceptor returns `null`, which drops the frame, or
  `validateToken` throws. Either way, no principal is bound.
- **No re-validation.** Once connected, the session is trusted until the socket closes. A token that
  expires after 24 h, a logout, or a deleted account does not end an open session. Without STOMP
  heart-beats ([WEBSOCKETS.md §6.5](WEBSOCKETS.md#65-stomp-heart-beats)), such a session can stay
  open for as long as the network path does. Mitigations:
  - record the token's `exp` in the session attributes, and reject frames after it;
  - close sessions on logout through a user-to-session registry;
  - keep access tokens short-lived.
- **Frames before `CONNECT`.** The `SUBSCRIBE` branch casts a possibly `null` principal. That
  rejects the frame only by accident, through a `NullPointerException`
  ([B47](BUGS.md#b47-latent-websocket-client-and-interceptor-defects)). Use an explicit null check.

### 5.2 Subscriptions

`SUBSCRIBE` is checked only against a destination allow-list:

```java
destination.startsWith("/topic/") || destination.startsWith("/app/") || destination.startsWith("/user/queue/")
```

- **Any `/topic/**` is allowed.** `/topic/channels/{id}/messages` for any channel or DM, and the
  global `/topic/status`. The membership check is commented out
  (`J/security/WebSocketAuthInterceptor.java:119-128`). This is the **live read** half of
  [B04](BUGS.md#b04-channels-have-no-read-or-write-authorization), and `docs1` does not change it.
- **Direct `/queue/**` subscriptions are rejected**, which is correct. Only `/user/queue/**` is
  allowed, and Spring resolves that against the subscriber's own principal. A user therefore cannot
  subscribe to another user's queue.
- **`/app/**` subscriptions** are allowed, but no `@SubscribeMapping` exists, so they do nothing.
  They should be rejected.

### 5.3 Client `SEND` frames

The interceptor checks only that a `SEND` frame comes from an authenticated session. It does not
check the **destination**. In Spring's STOMP model, frames whose destination starts with a broker
prefix (`/topic`, `/queue`) go **straight to the broker**. Frames that start with `/user` go to the
user-destination handler. Neither passes through any `@MessageMapping` method. A client can
therefore:

- `SEND /topic/channels/{id}/messages` with an invented message, author included. Every current
  subscriber of that channel sees it. It is not stored, so it vanishes on reload;
- `SEND /topic/status` with any user's ID and status, to fake presence for everyone;
- `SEND /user/{username}/queue/messages` or `/user/{username}/queue/friends`, to push fake DMs or
  friend events to a specific user.

This is **New**: [S1](#s1-clients-can-publish-directly-to-broker-destinations) (`inferred`).

**Fix:** reject every client `SEND` whose destination does not start with `/app/`. Spring
Security's message authorization does the same declaratively: `@EnableWebSocketSecurity` with an
`AuthorizationManager<Message<?>>` that permits `SEND` only to `/app/**`, permits `SUBSCRIBE` to
`/topic/**` and `/user/queue/**` subject to channel checks, and denies everything else. Note that
`@EnableWebSocketSecurity` enables CSRF checking on `CONNECT` by default, which this token-based
client does not need and would have to disable.

### 5.4 Origins and cross-site WebSocket hijacking

`/ws` allows every origin: `setAllowedOriginPatterns("*")`. Cross-site WebSocket hijacking depends
on the browser attaching **ambient** credentials, such as cookies, to a handshake from a malicious
page. Authentication here is a token that the page's own script must put in the `CONNECT` frame, so
a foreign page has nothing to borrow. The open origin policy is therefore not exploitable today. It
becomes exploitable the moment authentication moves to cookies, so restrict it to the known
frontend origins anyway.

### 5.5 Message limits

No transport limits are configured: the `spring.websocket.*` properties are inert. Spring's
defaults therefore apply: a 64 KB inbound message limit, a 512 KB send buffer, and a 10 s send
timeout, after which slow clients are disconnected. These are reasonable. Nothing limits frame
*rate* (§12).

---

## 6. Data exposure and privacy

### 6.1 Entities at the API boundary

`User.password` has no `@JsonIgnore`, and `Server.owner` is `EAGER`. Any endpoint that returns a
`User` or a `Server` includes a BCrypt hash. That covers the user list, user search on every
keystroke, server endpoints and invite joins ([B01](BUGS.md#b01-password-hashes-are-returned-by-the-api)).
`UserPrincipal.password` *is* `@JsonIgnore`, which shows the intent; the entity was missed.

### 6.2 Presence

All status changes go to the global `/topic/status`, and `GET /api/users/{id}/status` answers for
anyone. Online and offline patterns of every user are visible to every other user, blocked users
included ([B15](BUGS.md#b15-presence-is-visible-to-everyone)). Under GDPR-style rules, presence is
personal data; scope it to friends and shared servers.

### 6.3 Broadcasts to the wrong audience

- Channel creation is broadcast to every connected user
  ([B31](BUGS.md#b31-channel-creation-is-broadcast-to-every-user)), which leaks private server
  structure.
- Under the `kafka` profile, every message, DMs included, is written in plaintext to the
  `message-events` topic. Anyone with access to Kafka or Kafka UI can read it (§10).

### 6.4 Invites

Invite codes are the first 8 hex characters of a random UUID (`J/service/InviteService.java:31`):
32 bits from `SecureRandom`. That is guessable at scale without rate limiting, though slowly. While
B03 lets anyone join any server directly, invites protect nothing. Once B03 is fixed, use at least
64 bits (for example, 11 Base62 characters) and rate-limit joins.

### 6.5 Information disclosure

- **Internal error messages** from bare `RuntimeException`s are echoed to clients
  ([B29](BUGS.md#b29-errors-map-to-the-wrong-http-status)). Stack traces are not, because Boot's
  default is `include-stacktrace=never`.
- **Unknown URLs return 500** ([B60](BUGS.md#b60-unknown-urls-return-500-instead-of-404)), which
  makes probes look like faults, and floods ERROR logs.
- `GET /` (public) reports a version string. `GET /api` advertises routes and a port
  ([B46](BUGS.md#b46-no-op-and-stale-endpoints)).
- **Existence oracles.** Registration reports "Username already taken" and "Email already registered"
  separately. That is normal for sign-up, but it allows account enumeration without rate limiting.

### 6.6 Retention and deletion

There is no account deletion, no message deletion that works on `main`
([B21](BUGS.md#b21-delete-apimessagesid-can-never-succeed)), and no retention policy. Redis's
`presence:last_seen:*` keys never expire ([B43](BUGS.md#b43-dead-presence-logic)). These matter for
privacy-law compliance, if the app is ever offered to real users.

---

## 7. Input handling

| Class | State | Notes |
|---|---|---|
| **SQL / JPQL injection** | Not present | All queries are Spring Data derived queries or `@Query` JPQL with named parameters. There is no native SQL, `JdbcTemplate` or string-built query |
| **Bean Validation** | Partial | Registration, channel and friend-request DTOs are validated. `MessageRequest`, `ServerPayload`, most request bodies and all `@MessageMapping` payloads are not ([B45](BUGS.md#b45-missing-input-validation)) |
| **Enum parsing** | 500 on bad input | `POST /api/users/status/custom?status=foo` throws from `UserStatus.valueOf` (B45) |
| **XSS** | Low risk | React escapes text by default. The frontend has no `dangerouslySetInnerHTML`, `innerHTML` or `eval`, and message content is rendered as plain text. Forged frames (S1) are also rendered as text |
| **JSON deserialization** | Safe | No polymorphic typing (`@JsonTypeInfo`, default typing) anywhere. The consumer sets `spring.json.trusted.packages=*`, but it also sets `use.type.headers=false` with a fixed default type, so type headers cannot pick a class. Narrow `trusted.packages` anyway |
| **Path and file handling** | Not applicable | No file uploads, downloads or filesystem access |
| **Message size** | Bounded only over STOMP | 64 KB per STOMP frame by default (§5.5). The REST message edit has no size check, and `content` is a `TEXT` column |
| **Usernames** | Not normalized | Case variants such as `alice` and `Alice` can coexist, which enables impersonation and breaks DM routing ([B11](BUGS.md#b11-dms-are-delivered-to-the-wrong-username)) |

---

## 8. Transport and browser security

### 8.1 TLS

The production frontend is on Vercel and the backend on Render (per `frontend/.env.production`).
Both platforms terminate TLS. `config/api.ts` derives `wss://` from `https://`, so production traffic
is encrypted. Inside `docker compose`, nothing is encrypted: the database, Redis and Kafka all use
plaintext protocols (§10).

**HSTS behind a proxy** (`inferred`). Spring Security sends `Strict-Transport-Security` only on
requests it sees as secure. Behind a TLS-terminating proxy, a request looks like plain HTTP unless
forwarded headers are honoured, and `server.forward-headers-strategy` is not set. HSTS is therefore
probably not being sent. Set `server.forward-headers-strategy=native` or `framework` on Render, or
add HSTS at the edge.

### 8.2 CSRF

CSRF protection is disabled. That is correct for an API authenticated by a bearer header, because a
cross-site page cannot make the browser attach the token. It must be revisited if any credential
ever moves into a cookie (§3.5).

### 8.3 CORS

`SecurityConfig.corsConfigurationSource()`:

- allows origin patterns `http://localhost:*` and the Vercel origin;
- allows methods `GET`, `POST`, `PUT`, `DELETE` and `OPTIONS`;
- allows all headers, exposes `Authorization`, and sets `allowCredentials=true`.

- `localhost:*` is allowed in the **same configuration as production**. Any page served from any
  port on a user's machine can call the production API. Without cookies this grants nothing by
  itself, but it is unnecessary in production; load the allowed origins from configuration per
  profile.
- `allowCredentials=true` is unnecessary while no cookies are used.
- CORS is not access control. It protects browsers, and does nothing against `curl`. Every gap in §4
  is reachable regardless.

### 8.4 Security headers

**From Spring Security, on API responses:** `X-Content-Type-Options: nosniff`, `Cache-Control:
no-store`, `X-XSS-Protection: 0`, and `X-Frame-Options: SAMEORIGIN` (relaxed from `DENY` for the H2
console).

**From the frontend:** none. `frontend/vercel.json` only rewrites routes to `index.html`, so the SPA
is served without a `Content-Security-Policy`. A CSP is the main defence that would limit the damage
of an XSS while tokens live in `localStorage`. A starting point:

```
Content-Security-Policy: default-src 'self'; connect-src 'self' https://<api-host> wss://<api-host>;
  img-src 'self' data:; style-src 'self' 'unsafe-inline'; frame-ancestors 'none'; base-uri 'self'
```

MUI's runtime styles need `'unsafe-inline'` for styles, or a nonce.

### 8.5 Dead exposures

`/h2-console/**` is `permitAll()`, and frame options are relaxed for it, but H2 is not on the
classpath. Both are harmless today and become an unauthenticated database console the moment
someone adds the H2 dependency. Remove both.

---

## 9. Secrets management

### 9.1 What is committed

Values are deliberately not reproduced here.

| Secret | Location | Severity |
|---|---|---|
| JWT signing secret | `src/main/resources/application.properties:56` | **Critical.** It mints tokens for any user ID |
| SonarCloud token | `build.gradle:29` | High. It grants project access on SonarCloud. It must be **revoked**, not just deleted |
| PostgreSQL password | `application.properties`, `docker-compose.yml`, the consumer's properties | Medium. Development credentials, but reused across both services |
| Redis password | `application.properties`, `docker-compose.yml` | Medium |
| pgAdmin login | `docker-compose.yml` | Medium, because the service is exposed on all interfaces (§10) |
| Seeded `testuser` password | `J/config/DataInitializer.java` | Low. The account cannot log in |

### 9.2 Where the secrets spread

- **Git history.** Deleting the lines does not remove them. Anyone with a clone keeps them.
- **The committed jar.** `build/libs/*.jar` is tracked, and it contains `application.properties`.
- **Every build.** `bootJar` packages `application.properties`, and the Dockerfile copies the jar
  into the image. Any image pushed by the CI workflow (`anil0003/discord-clone:latest`) carries the
  JWT secret. If that repository is public, the secret is public.
- **The consumer repository** has its own copy of the database credentials.

### 9.3 Remediation

1. **Revoke** the SonarCloud token in SonarCloud. Generate a new one, and store it as a GitHub
   Actions secret.
2. **Rotate the JWT secret.** Generate at least 64 random bytes (`openssl rand -base64 64`) and
   provide it through an environment variable (`APP_JWT_SECRET`) or the platform's secret store.
   Every existing token, including any forged with the old key, then becomes invalid.
3. Move every credential out of committed properties. Keep `application.properties` free of
   secrets, and use `${ENV_VAR}` placeholders with no defaults in production profiles, so that a
   missing secret fails startup instead of falling back.
4. Rotate the database and Redis passwords on any environment that used the committed values.
5. Untrack `build/libs/` (`git rm --cached`) and add secret scanning to CI, such as gitleaks or
   GitHub secret scanning with push protection.
6. Optionally rewrite history (`git filter-repo`). This helps only if every clone and fork is also
   replaced, so rotation is the real fix.

---

## 10. Infrastructure

Everything in `docker-compose.yml` is development-grade. It is listed here because the same file is
the only documented way to run the system.

| Component | Finding | Fix |
|---|---|---|
| **Port bindings** | `"5432:5432"`, `"6379:6379"`, `"9092:9092"`, `"5050:80"` and `"8085:8080"` bind on **all interfaces**, so anyone on the same network can reach the database, Redis, Kafka, pgAdmin and Kafka UI | Bind to loopback (`"127.0.0.1:5432:5432"`), or do not publish ports for services only the app needs |
| **PostgreSQL** | One account, which owns the schema, used by both services; `ddl-auto=update` requires DDL rights at runtime | Separate migration and runtime roles; the runtime role gets only `SELECT`/`INSERT`/`UPDATE`/`DELETE` on its tables; the consumer gets `INSERT` on `messages` and `SELECT` on what it reads |
| **Redis** | Password-only authentication with a committed password; no ACL user; no TLS; no `rename-command` for `FLUSHALL`/`CONFIG` | Use an ACL user restricted to `presence:*` and the needed commands; TLS when not on localhost |
| **Kafka** | `PLAINTEXT` listeners, no SASL, no ACLs. Anyone who reaches 9092 can read every message, or produce events that the consumer persists as real messages, with arbitrary author and channel IDs | SASL/SCRAM or mTLS, plus ACLs: producer-only for the API, consumer-only for the consumer service |
| **Kafka UI** | `provectuslabs/kafka-ui:latest`, unauthenticated, unpinned, and exposed. It shows every message's content, and can produce to topics | Do not run it outside a developer's machine; pin the version; enable its authentication |
| **pgAdmin** | Default login committed, exposed on all interfaces | Loopback only, with a non-default password |
| **Backend image** | `eclipse-temurin:17-jdk` runs as **root** and ships a full JDK | Use a JRE base image, a non-root `USER`, and a read-only filesystem where the platform allows |
| **Consumer service** | No web server, so it has no HTTP attack surface. It trusts every record on the topic, and writes with the shared database account | Topic ACLs, so only the API can produce; its own database role |
| **Redis keyspace notifications** | When enabled (the B14 fix), expiry events reveal key names to any subscriber | Covered by the Redis ACL above |

The in-memory STOMP broker (§5) has no network exposure of its own. It runs inside the API process.

---

## 11. Logging, monitoring and audit

| Concern | State |
|---|---|
| **Credentials in logs** | Every login writes the plaintext password at INFO (`AuthController.java:72`; `LoginRequest` is `@Data`) ([B06](BUGS.md#b06-plaintext-passwords-are-written-to-the-log)). Registration logs the request object too, but `RegistrationRequest` has no generated `toString`, so it prints only a reference. Mark password fields `@ToString.Exclude` and never log request bodies |
| **Tokens in logs** | Not logged; the line that would have logged them is commented out. Good |
| **Default verbosity** | The base `application.properties`, which production also loads unless overridden, sets `com.discordclone` and `DispatcherServlet` to DEBUG, and enables `show-sql` and `org.hibernate.SQL` DEBUG. Message contents, usernames and query traffic all reach the logs |
| **Browser console** | Sign-up logs the password and tokens (S3). Many other `console.log` calls dump state. Strip them in production builds |
| **Security events** | Failed logins, 401/403 responses, rejected STOMP frames and access denials are logged only at WARN/DEBUG, with no structured fields. Nothing alerts |
| **Audit trail** | None. Membership changes, role changes, message edits and server deletions record no actor or time. `Message.edited` is client-settable (B05) |
| **Health and metrics** | A hand-written `/health` with no dependency checks, and no Actuator, so a security-relevant outage, such as Redis down, is invisible |

---

## 12. Availability and abuse resistance

| Concern | State | Fix |
|---|---|---|
| **Rate limiting** | None anywhere: login, registration, refresh, user search, invite join, message send, STOMP frames | Bucket4j or a gateway in front of the auth endpoints; per-session frame budgets in the STOMP interceptor |
| **Registration abuse** | Unlimited, with no email verification or CAPTCHA | Verification and rate limits |
| **Thread exhaustion** | `@Async` starts an unbounded thread per call ([B59](BUGS.md#b59-async-starts-a-new-thread-for-every-call)) | A bounded executor |
| **Kafka outage** | A broker outage stalls all WebSocket traffic ([B19](BUGS.md#b19-a-kafka-outage-stalls-all-websocket-traffic)) | Asynchronous send with a short `max.block.ms` |
| **Fan-out amplification** | `ChatArea` subscribes again on every render, so each message is delivered N times ([B18](BUGS.md#b18-chatarea-leaks-a-subscription-on-every-render)); presence goes to everyone (B15) | An effect with cleanup; scoped presence |
| **Single instance** | The in-memory broker prevents horizontal scaling, so one instance carries all load | A broker relay ([IMPROVEMENTS.md](IMPROVEMENTS.md#move-off-the-in-memory-broker--later--l)) |
| **Large requests** | STOMP frames are capped at 64 KB; REST JSON bodies are not | `spring.servlet.multipart` does not apply to JSON; add DTO `@Size` limits and a proxy body-size limit |

---

## 13. Supply chain and delivery

- **Known vulnerabilities.** On 2026-10-06, GitHub reported 98 Dependabot alerts on the default
  branch: 2 critical, 47 high, 44 moderate and 5 low. Review them in the repository's Security tab.
- **End-of-life framework.** Spring Boot 3.2.2 is past open-source support, so security fixes no
  longer reach it. Upgrade to a supported 3.x line, and jjwt from 0.11.5 to 0.12.x
  ([IMPROVEMENTS.md](IMPROVEMENTS.md#dependency-management--next--s)).
- **Unused dependencies widen the surface.** On the backend, `mysql-connector-j` is unused. On the
  frontend, `socket.io-client`, `sockjs` (a *server* library) and `sockjs-client` are unused, and
  both `react-query` v3 and `@tanstack/react-query` v5 are installed.
- **Floating versions.** `kafka-ui:latest`, and caret ranges throughout `package.json`. Commit the
  lockfile, and pin images by digest.
- **CI** (`.github/workflows/docker-image.yml`):
  - it uses `actions/checkout@v2` and `actions/setup-java@v2`, which are outdated and run on a
    deprecated Node runtime;
  - it has no `permissions:` block, so it runs with the default token scope;
  - it pushes `:latest` without a registry login step, and cannot succeed today
    ([B38](BUGS.md#b38-the-ci-workflow-cannot-succeed));
  - it runs no dependency scan, secret scan, or SAST.

  Add `permissions: contents: read`, current action versions pinned by SHA, Dependabot or Renovate,
  gitleaks, and CodeQL. The SonarCloud setup exists, but its token is the committed one.
- **No security tests.** The six test classes are Mockito unit tests that do not exercise Spring
  Security, and two do not compile ([B09](BUGS.md#b09-two-test-classes-do-not-compile)). See §17.

---

## 14. What is done right

These should be kept when the problems above are fixed:

- **BCrypt** for password storage, applied in one place (`UserService.createUser`).
- **Stateless sessions, with CSRF off for the right reason**: bearer tokens are not ambient
  credentials.
- **jjwt parsing with a fixed key**, which rejects unsigned and wrongly signed tokens.
- **The user is reloaded on every HTTP request**, so deleted accounts stop working at once.
- **WebSocket authentication at `CONNECT`**, the standard approach when browsers cannot set
  handshake headers.
- **Only `/user/queue/**` is allowed for queue subscriptions**, so users cannot subscribe to each
  other's queues.
- **Parameterised queries throughout**, with no native SQL.
- **React rendering with no raw-HTML sinks.**
- **Correct ownership checks** on friend-request accept and reject, server update and delete, and
  channel administration.
- **Error responses never include stack traces.**
- **On `docs1`,** a dedicated `ChannelAccessDeniedException` mapped to 403, and a single
  authorization service for channels. That is the right shape, once the defects in §4.3 are fixed.

---

## 15. New findings

These are not yet in [BUGS.md](BUGS.md).

### S1. Clients can publish directly to broker destinations

`inferred` · `J/security/WebSocketAuthInterceptor.java` (`SEND` branch), `J/config/WebSocketConfig.java`

The interceptor accepts any `SEND` from an authenticated session, whatever its destination. Frames
addressed to `/topic/**` go straight to the simple broker. Frames addressed to `/user/{name}/**` go
to the user-destination handler. Either way, no application code runs.

**Impact:** any user can inject fake chat messages into any channel's live view, fake any user's
presence on `/topic/status`, and push fake DMs or friend events to any user. Nothing is persisted, so
the forgeries vanish on reload, which also makes them hard to report.
**Fix:** reject `SEND` unless the destination starts with `/app/`, either in the interceptor or with
Spring Security message authorization (§5.3). Severity: **High**.

### S2. The `docs1` channel authorization does not compile and mis-authorizes DMs

`read` · `J/security/ChannelAuthorizationService.java`, `J/controller/MessageController.java:91-128`

The service references a nonexistent `ChannelType.GROUP_DM`, and the controller passes a `Long` to
`getMessageById(String)`. Neither compiles. Beyond that:

- DMs are checked as server-1 channels, because the server check runs first;
- the DM-key parser expects `a_b`, but keys are `dm-a-b`;
- `SUBSCRIBE` is still unchecked;
- edits still persist the client's body.

Details are in §4.3.
**Impact:** the change does not build. If it did, it would refuse DM participants who are not
members of server 1, and it would leave the main read path, live subscriptions, and B05 open.
**Fix:**

- use `String messageId`;
- drop `GROUP_DM`, or add it to the enum;
- check DMs by participation before the server check, and parse the real key format, or use a
  participant table;
- call the service from the `SUBSCRIBE` branch;
- update only `content` on edit.

Severity: **High**, since it is the intended fix for a Critical bug.

### S3. The frontend logs the sign-up password and tokens

`read` · `F/providers/AuthProvider.tsx:160-162`

`signup` logs the username, email and plaintext password, then the full response, including the
access and refresh tokens, with `console.log`.

**Impact:** credentials appear in the console of a shared or screen-shared machine, in browser
extensions with console access, and in any front-end error-reporting tool that captures console
output.
**Fix:** remove the lines, and strip `console.*` from production builds (for example with esbuild's
`drop: ['console']` in `vite.config.ts`). Severity: **Medium**.

### S4. WebSocket sessions outlive their token

`read` · `J/security/WebSocketAuthInterceptor.java` (`CONNECT` branch)

The token's expiry is checked only at `CONNECT`. An open session keeps full access after the token
expires, after logout, and after the account is deleted. No server-side heart-beat bounds the
session's life (§5.1).
**Fix:** store `exp` in the session attributes, and reject frames once it has passed; close
sessions on logout. Severity: **Medium**.

### S5. Development services are exposed on every network interface

`read` · `docker-compose.yml`

PostgreSQL, Redis, Kafka (plaintext, no authentication), pgAdmin, and an unauthenticated Kafka UI
that displays message contents all publish their ports on `0.0.0.0`, with committed credentials
(§10).
**Fix:** bind to `127.0.0.1`, and require authentication on Kafka and Kafka UI. Severity: **Medium**.

### S6. Secrets ship inside every jar and image

`read` · `build/libs/*.jar` (tracked), `Dockerfile`, `.github/workflows/docker-image.yml`

`application.properties`, which holds the JWT secret and the database and Redis passwords, is
packaged into the jar. The jar is committed and copied into the pushed image (§9.2).
**Fix:** as for B07, plus untrack `build/` and keep secrets out of packaged resources.
Severity: **High**, as part of B07.

### S7. HSTS is probably not sent behind the TLS proxy

`inferred` · `src/main/resources/application.properties`

`server.forward-headers-strategy` is unset. Behind Render's TLS termination, requests therefore look
like plain HTTP, and Spring Security omits `Strict-Transport-Security` (§8.1).
**Fix:** `server.forward-headers-strategy=native`, then confirm that the header appears.
Severity: **Low**.

---

## 16. Remediation plan

Ordered by risk removed per unit of effort. Each step is independent unless noted.

### Now: close the takeover and read-everything paths

1. **Rotate the JWT secret, and revoke the SonarCloud token** (§9.3). This is the only step that
   needs no code. Do it before anything else.
2. **Delete `POST /api/users`** (B02). `/api/auth/register` already exists.
3. **Add `@JsonIgnore` to `User.password`** as a backstop, and return DTOs from `UserController`,
   `ServerController` and `InviteController` (B01).
4. **Restrict client `SEND` to `/app/**`** (S1). This is one condition in the interceptor.
5. **Authorize server membership changes**: invite or self only to add; self, admin or owner to
   remove (B03).
6. **Fix the channel authorization** (S2), and extend it to `SUBSCRIBE` (B04). Authorize DMs by
   participation, and derive the DM recipient on the server (B12, B13).
7. **Bind `{content}` only on message edit**, and update the loaded entity (B05).
8. **Stop logging credentials**, on the server (B06) and in the browser (S3).
9. **Bind the compose ports to loopback** (S5).

### Next: make sessions trustworthy

10. Add a `typ` claim, check it in `/refresh`, rotate refresh tokens, and keep a revocation list
    keyed by `jti`, held in Redis with a TTL equal to the token's remaining life (B17).
11. Fix the refresh flow (B16), then shorten access tokens to 15 minutes.
12. Bound WebSocket sessions by token expiry, and close them on logout (S4).
13. Rate-limit login, registration, refresh, search, invite join and message send (§12).
14. Return 403 rather than 401 or 500 for authorization failures, using the `docs1` exception type
    throughout (B29).
15. Scope presence to friends and shared servers (B15), and channel-creation events to server
    members (B31).
16. Add a CSP on Vercel, set `server.forward-headers-strategy` (S7), restrict CORS and WebSocket
    origins by profile, and remove the H2 console rules.
17. Upgrade Spring Boot and jjwt, remove unused dependencies, add Dependabot, gitleaks and CodeQL
    to CI, and set `permissions:` (§13).

### Later: defence in depth

18. Move the refresh token into an `HttpOnly`, `SameSite=Strict` cookie, keep the access token in
    memory, and add CSRF protection for that one endpoint (§3.5).
19. Central authorization: `@PreAuthorize` with a permission evaluator, and Spring Security message
    authorization for STOMP. That way a new endpoint is denied by default
    ([IMPROVEMENTS.md](IMPROVEMENTS.md#replace-ad-hoc-checks-with-a-central-authorization-layer--now--m)).
20. Least-privilege database roles, Redis ACLs, and Kafka SASL with ACLs (§10).
21. An audit log for membership, role, server and message changes (§11).
22. Account deletion, data retention, password change and reset, and email verification (§3.1,
    §6.6).

---

## 17. Security test checklist

Each check should become an automated test: a `@WebMvcTest` or `@SpringBootTest` with
`spring-security-test` for REST, and a STOMP client test for WebSocket. Each one fails today
unless marked otherwise. User A and user B are two ordinary registered accounts.

| # | Test | Expected after the fix |
|---|---|---|
| 1 | A token signed with the committed secret, for any user ID, calls `GET /api/servers` | 401 |
| 2 | A calls `POST /api/users` with B's `id` | 404 (route removed) or 400 |
| 3 | A calls `GET /api/users/search?prefix=b`; the response contains a `password` field | No `password` field |
| 4 | A calls `POST /api/servers/{B's private server}/members/{A}` | 403 |
| 5 | A calls `DELETE /api/servers/{B's server}/members/{B's friend}` | 403 |
| 6 | A calls `GET /api/messages/channels/{channel in B's server}` | 403 |
| 7 | A sends STOMP `SUBSCRIBE /topic/channels/{B's DM channel}/messages` | Frame rejected; no messages received |
| 8 | A sends STOMP `SEND /topic/channels/{id}/messages` with a forged body | Frame rejected; subscribers receive nothing |
| 9 | A sends STOMP `SEND /user/b/queue/messages` | Frame rejected |
| 10 | A sends `SEND /app/chat.send` with `dm=true`, a `channelId` A does not belong to, and `receiver=c` | Rejected; nothing stored or delivered |
| 11 | A calls `PUT /api/messages/{A's message}` with B's message `id` in the body | B's message unchanged |
| 12 | A calls `/api/auth/refresh` with an **access** token | 400 or 401 |
| 13 | A reuses a refresh token after one successful refresh | 401 |
| 14 | A logs in; the server log is searched for the password | Not found |
| 15 | A's token expires while A's STOMP session stays open; A then sends a frame | Session closed or frame rejected |
| 16 | 20 failed logins for one username within a minute | 429 after the threshold |
| 17 | `nc <host> 6379` or `nc <host> 9092` from another machine on the LAN | Connection refused |
| 18 | `GET /health` over HTTPS in production; inspect the response headers | `Strict-Transport-Security` present |
| 19 | A calls `GET /api/users/{B}/status` when A and B share no friendship or server | 403 |
| 20 | An unknown URL, with a valid token | 404, not 500 (B60) |
