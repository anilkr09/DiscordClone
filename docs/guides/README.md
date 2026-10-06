# Technology Guides

General knowledge guides for the three infrastructure technologies this project uses. The rest of
[`docs/`](../README.md) explains how this codebase uses them and what is wrong with it. These guides
explain how the technologies themselves work, so that those documents, and design discussions
about them, make sense.

| Guide | Covers |
|---|---|
| [STOMP_WEBSOCKET.md](STOMP_WEBSOCKET.md) | The WebSocket protocol, what STOMP adds on top, STOMP versus raw WebSocket, Spring's STOMP internals (channels, user destinations, simple broker versus relay, heart-beats, security, limits), the `@stomp/stompjs` client, feature scenarios, alternatives such as Socket.IO, SSE and MQTT |
| [REDIS.md](REDIS.md) | Execution model, data types, expiry and eviction, keyspace notifications, transactions and Lua, Pub/Sub versus Streams, persistence, replication and Cluster, patterns (caching, rate limiting, locks, leaderboards, presence, fan-out, queues), Spring Data Redis |
| [KAFKA.md](KAFKA.md) | The log model, partitions and ordering, replication and KRaft, producers (keys, batching, idempotence, transactions), consumer groups (offsets, rebalancing, lag), delivery semantics, error handling and dead-letter topics, schemas, Connect/Streams/Debezium, patterns (outbox, CDC, event sourcing, fan-out), Spring Kafka |

## How each guide is organised

1. **Fundamentals:** what the technology is, and how it works internally.
2. **Features in depth:** each with commands, frames, or configuration.
3. **Feature scenarios:** a problem, the feature that solves it, and the trade-offs, using a chat
   application as the running example where possible.
4. **Comparisons:** alternatives, and when to choose them.
5. **Pitfalls checklist.**
6. **How this project uses it:** links into the project docs and BUGS.md.
7. **Self-check questions:** short questions with answers, for review.

## Sources and version notes

Version-specific statements were checked on 2026-10-06 against primary sources:

- **STOMP:** the STOMP 1.2 specification.
- **Spring:** the Spring Framework reference documentation and Javadoc, covering the simple broker's
  STOMP coverage, heart-beat scheduling, transport limit defaults and message ordering.
- **Redis:** the Redis documentation, covering keyspace notification flags, expiry timing and
  per-node delivery in Cluster, and `HEXPIRE` availability. Redis's own announcement covers the
  Redis 8 licensing and core features.
- **Kafka:** the Apache Kafka 4.0 release summary, covering the ZooKeeper removal, the KIP-848 and
  KIP-932 status, and Java requirements.
- **Spring Kafka:** the Spring for Apache Kafka reference, covering default error handling,
  dead-letter naming, fatal exceptions and Jackson 3 support.
- **@stomp/stompjs:** the source, at 7.0.0 (installed here) and the development branch.

Feature status changes between releases, especially Kafka share groups and anything marked early
access, so check current release notes before relying on it.
