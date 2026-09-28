# DiscordClone — Documentation

Engineering documentation for the DiscordClone real-time chat application.

## Index

| Document | Covers |
|---|---|
| [ARCHITECTURE.md](ARCHITECTURE.md) | System topology, module layout, domain model, end-to-end flows, branch topology |
| [IMPLEMENTATION.md](IMPLEMENTATION.md) | Package-by-package walkthrough, REST/STOMP surface, configuration, profiles, local setup |
| [WEBSOCKETS.md](WEBSOCKETS.md) | STOMP broker internals, handshake & authentication, destination map, client implementation |
| [KAFKA.md](KAFKA.md) | Producer configuration, the profile seam, delivery semantics, the missing consumer |
| [REDIS.md](REDIS.md) | Presence key schema, TTL state machine, keyspace-expiry listener, status resolution |
| [BUGS.md](BUGS.md) | **Numbered defect catalog** (B01–B50) with severity, evidence, location, and fix, plus the implementation patterns behind them |
| [IMPROVEMENTS.md](IMPROVEMENTS.md) | Prioritized improvements (security, API, data, messaging, presence, scaling, frontend, testing, ops) and a phased roadmap |

## Scope

These documents describe three branches:

| Branch | Commit | Relationship |
|---|---|---|
| `main` | `d10d627` | Merge of PR #6 from `feature/redis` |
| `origin/feature/redis` | `7270c74` | **Tree-identical to `main`** — `git diff main origin/feature/redis` is empty |
| `origin/feature/kafka` | `ec67190` | Ancestor of `main`; the pre-Redis state |

Because `main` and `feature/redis` have identical trees, the documentation describes them as one
codebase and treats `feature/kafka` as the historical predecessor. Where the two differ in design,
the difference is called out explicitly — see [REDIS.md](REDIS.md#8-evolution-from-featurekafka-to-main)
for the presence rewrite, which is the single largest behavioural change between them.

## Read this first

The full defect list is in [IMPLEMENTATION.md](IMPLEMENTATION.md#8-known-issues). The items below
are summarised here because they affect everything else.

### Exploitable by any registered user

1. **Password hashes are served over the API.** `User.password` has no `@JsonIgnore`, and
   `GET /api/users` returns every account's email and BCrypt hash. Endpoints that return a `Server`
   embed its owner's hash as well. `/api/auth/login` logs plaintext passwords at INFO.
2. **Any account can be overwritten.** `POST /api/users` binds a raw `User`, including `id`, and
   `save` merges it onto the existing row.
3. **Server membership has no authorization.** Anyone can add any user to any server, or remove any
   non-owner member, by ID.
4. **Channels have no read or write authorization.** Anyone can post to, read the history of, or
   live-subscribe to any channel. DMs are ordinary channels attached to a hard-coded server 1, and
   the recipient of a DM is chosen by the client.
5. **Any message can be rewritten** through `PUT /api/messages/{id}` (raw-entity mass assignment).

### Blocking build or correct operation

6. **The backend does not compile on `main`.** `spring-boot-starter-data-redis` was removed in
   `cd97e97`, and the Redis presence code added afterwards never restored it.
7. **Two test classes are stale and fail to compile**, so `./gradlew build` fails even after (6).
8. **Presence never reaches `OFFLINE`.** Redis keyspace notifications are not enabled.
9. **Token refresh is broken end to end.** The frontend calls a non-existent `/api/refresh-token`,
   so sessions end at the 24-hour access-token expiry.
10. **In the `kafka` profile, messages are never persisted.** The topic has no consumer.

### Secrets in git history

11. **A live SonarCloud token** at `build.gradle:29`. Revoke it; deleting the line is not enough.
12. **The JWT signing secret** at `src/main/resources/application.properties:56`. It can forge a
    token for any user.

## Verification notes

Every claim was checked against the source on `main` (commit `d10d627`). A few could not be
executed, because no JRE was available where these docs were written. Those are marked
*not exercised* or *likely* where they appear. They are the compile failure (§8.1, inferred from
the dependency block against the imports), the server-delete foreign-key failure, the lazy-entity
serialization failure on `GET /api/channels/.../{id}`, and the `POST /api/users` account overwrite
(from Spring Data `save`/`merge` semantics).
