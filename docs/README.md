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

## Scope

These documents describe three branches:

| Branch | Commit | Relationship |
|---|---|---|
| `main` | `d10d627` | Merge of PR #6 from `feature/redis` |
| `origin/feature/redis` | `7270c74` | **Tree-identical to `main`** — `git diff main origin/feature/redis` is empty |
| `origin/feature/kafka` | `ec67190` | Ancestor of `main`; the pre-Redis state |

Because `main` and `feature/redis` have identical trees, the documentation describes them as one
codebase and treats `feature/kafka` as the historical predecessor. Where the two differ in design,
the difference is called out explicitly — see [REDIS.md](REDIS.md#8-evolution-from-feature-kafka-to-main)
for the presence rewrite, which is the single largest behavioural change between them.

## Read this first

Six issues block the code from building or running as committed. They are documented in detail in
[IMPLEMENTATION.md](IMPLEMENTATION.md#8-known-issues), and summarised here because they affect
everything else:

1. **The backend does not compile on `main`.** `spring-boot-starter-data-redis` is missing from
   `build.gradle` while the code imports `org.springframework.data.redis.*`.
2. **Two test classes are stale and fail to compile.** `UserStatusServiceTest` calls a method removed
   in the presence rewrite; `MessageServiceTest` mocks four of six constructor dependencies. So
   `./gradlew build` fails even after fixing (1).
3. **Presence never reaches `OFFLINE`.** Redis keyspace notifications are not enabled, so the
   expiry listener never fires.
4. **In the `kafka` profile, messages are never persisted.** The topic has no consumer and
   persistence is a no-op.
5. **A live SonarCloud token is committed** at `build.gradle:29`.
6. **The JWT signing secret is committed** at `src/main/resources/application.properties:56`.
