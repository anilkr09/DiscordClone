# Redis — Technology Guide

A working knowledge guide to Redis: its execution model, data types, expiry and eviction, atomicity
tools, messaging features, persistence and scaling, plus common patterns with concrete commands. For
how *this project* uses Redis for presence, and what is wrong with it, see
[../REDIS.md](../REDIS.md).

## Contents

1. [What Redis is, and is not](#1-what-redis-is-and-is-not)
2. [Execution model](#2-execution-model)
3. [Data types](#3-data-types)
4. [Keys, expiry and eviction](#4-keys-expiry-and-eviction)
5. [Atomicity: transactions, Lua and functions](#5-atomicity-transactions-lua-and-functions)
6. [Messaging: Pub/Sub, Streams and lists](#6-messaging-pubsub-streams-and-lists)
7. [Persistence](#7-persistence)
8. [Replication, high availability and Cluster](#8-replication-high-availability-and-cluster)
9. [Patterns with scenarios](#9-patterns-with-scenarios)
10. [Spring Data Redis essentials](#10-spring-data-redis-essentials)
11. [Pitfalls checklist](#11-pitfalls-checklist)
12. [Redis compared with alternatives](#12-redis-compared-with-alternatives)
13. [How this project uses it](#13-how-this-project-uses-it)
14. [Self-check questions](#14-self-check-questions)

---

## 1. What Redis is, and is not

Redis is an **in-memory data structure server**. Clients send commands over TCP (the RESP protocol),
and each command operates on a value of a specific type stored under a key: a string, hash, list,
set, sorted set, stream, and others.

| Redis is good at | Redis is not |
|---|---|
| Sub-millisecond reads and writes on small values | A relational database: no joins, no ad hoc queries over values (outside the query engine) |
| Data with a natural lifetime: caches, sessions, presence, rate limits | Durable by default the way a database is: persistence is configurable and asynchronous |
| Atomic operations on shared state: counters, locks, leaderboards | A substitute for Kafka's long-term, replayable log |
| Lightweight messaging between services | Cheap for large datasets: everything lives in RAM |

**Versions and licensing.** Redis moved from the BSD licence to source-available licences (RSALv2 and
SSPLv1) in March 2024. Redis 8, released in May 2025, added the OSI-approved AGPLv3 as a further
option. It also folded former Redis Stack features into the core: JSON, the query engine, time
series, and probabilistic types, and it introduced vector sets. **Valkey**, a Linux Foundation fork of
the last BSD-licensed release, is a drop-in alternative that many cloud providers now offer.

---

## 2. Execution model

- **One thread executes commands.** Redis processes commands one at a time on a single main thread,
  using an event loop over all client connections. Since 6.0, optional I/O threads parse requests and
  write replies in parallel, but command *execution* stays serial.
- **Every single command is atomic.** No other command runs halfway through an `INCR`, a `SET ... NX`
  or a `ZADD`, so many concurrency problems disappear by choosing the right command.
- **Slow commands stall everyone.** `KEYS *`, `SMEMBERS` on a set with millions of members, `DEL` of a
  huge key, or a long Lua script blocks every other client for its duration. Complexity matters: most
  commands are O(1) or O(log N), and the documentation states the complexity of each.
- **Round trips dominate.** At sub-millisecond execution times, network latency is the bottleneck.
  **Pipelining** sends many commands without waiting for each reply, and the multi-key commands
  (`MGET`, `MSET`, `HMGET`) do the same in one command.

```
# 3 round trips                       # 1 round trip
GET presence:status:1                 MGET presence:status:1 presence:status:2 presence:status:3
GET presence:status:2
GET presence:status:3
```

---

## 3. Data types

| Type | What it holds | Key commands | Typical uses |
|---|---|---|---|
| **String** | Bytes, up to 512 MB; integers and floats can be incremented | `GET`, `SET` (with `EX`, `PX`, `NX`, `XX`), `INCR`, `GETDEL`, `MGET` | Cache entries, counters, flags, locks, serialized objects |
| **Hash** | Field → value map under one key | `HSET`, `HGET`, `HGETALL`, `HINCRBY`, `HEXPIRE` (7.4+) | Objects (one hash per user), per-field counters |
| **List** | Ordered sequence, fast at both ends | `LPUSH`, `RPOP`, `LRANGE`, `BLMOVE` | Simple queues, recent-items lists |
| **Set** | Unordered unique members | `SADD`, `SISMEMBER`, `SINTER`, `SCARD` | Tags, unique visitors (exact), "who is in this room" |
| **Sorted set (ZSET)** | Unique members, each with a score, kept ordered by score | `ZADD`, `ZINCRBY`, `ZRANGE ... REV`, `ZRANGEBYSCORE`, `ZREM` | Leaderboards, time-ordered indexes, sliding windows, schedulers |
| **Stream** | Append-only log of field/value entries with IDs, plus consumer groups | `XADD`, `XREADGROUP`, `XACK`, `XAUTOCLAIM` | Durable event queues, activity feeds |
| **Bitmap** | Bit operations on a string | `SETBIT`, `GETBIT`, `BITCOUNT` | Daily-active flags per user ID |
| **HyperLogLog** | Approximate distinct count in at most 12 KB, about 0.81% standard error | `PFADD`, `PFCOUNT`, `PFMERGE` | Unique visitors at scale |
| **Geospatial** | Members with longitude and latitude, stored in a sorted set | `GEOADD`, `GEOSEARCH` | Nearby drivers or stores |
| **JSON, vector sets, time series, probabilistic** | Redis 8 core (earlier as modules) | `JSON.SET`, `VADD`, `TS.ADD`, `BF.ADD` | Documents, similarity search, metrics, Bloom filters |

**Choosing the right type** is the main design decision. For example, presence for many users can be
five string keys per user, one hash per user, or a single sorted set scored by last heartbeat. Each
choice changes how expiry, batch reads and scans work (§9.6).

---

## 4. Keys, expiry and eviction

### 4.1 Key design

- Use a namespace convention: `object-type:id:field`, for example `presence:heartbeat:42`.
- Keep keys short but readable. Keys cost memory too.
- **Never run `KEYS pattern` in production.** It is O(N) over the whole keyspace and blocks the
  server. Use `SCAN` with a cursor, which returns results in small batches.

### 4.2 Expiry (TTL)

```
SET session:abc "{...}" EX 1800     # expires in 30 minutes
EXPIRE cart:42 600                  # set or reset a TTL
TTL cart:42                         # seconds left; -1 = no TTL, -2 = key missing
PERSIST cart:42                     # remove the TTL
HEXPIRE user:42 60 FIELDS 1 otp     # per-field TTL inside a hash (7.4+)
```

- **How keys actually expire.** Redis removes an expired key either **lazily**, when a command touches
  it and finds it expired, or **actively**, through a background cycle that samples keys with TTLs
  several times per second. An expired key is never returned to a client, but it may occupy memory
  for a while after its TTL passes.
- **Writes and TTLs.** `SET` without `KEEPTTL` replaces the value and clears the TTL. `INCR`, `HSET`
  and similar in-place modifications keep it. This catches people out when a refreshed key suddenly
  lives forever.

### 4.3 Keyspace notifications

Redis can publish an event on Pub/Sub channels whenever keys change. This is off by default, because
it costs CPU. It is enabled with `notify-keyspace-events`:

| Flag | Meaning |
|---|---|
| `K` / `E` | Publish on `__keyspace@<db>__:<key>` (message: event name) or `__keyevent@<db>__:<event>` (message: key name). One of the two is required |
| `g`, `$`, `l`, `s`, `h`, `z`, `t` | Generic, string, list, set, hash, sorted set, stream commands |
| `x` | **Expired** events |
| `e` | Evicted events |
| `A` | Alias for most classes (`g$lshztdxea`) |

```
CONFIG SET notify-keyspace-events Ex
PSUBSCRIBE __keyevent@*__:expired       # receives the names of expired keys
```

Three properties limit what they can be used for:

1. **Timing.** The Redis documentation is explicit that `expired` events fire when the server
   *deletes* the key, not when the TTL reaches zero. With many keys and little traffic, the delay can
   be significant.
2. **Fire and forget.** Like all Pub/Sub, a subscriber that is disconnected misses the events, and
   they are not replayed.
3. **Per node in Cluster.** Each node only reports its own keys, so a subscriber must listen to every
   node.

They are fine for best-effort reactions, such as invalidating a local cache. They are a poor
foundation for anything that must happen, such as marking users offline. A sorted set scanned by a
scheduled job is the robust alternative (§9.6).

### 4.4 Eviction

When `maxmemory` is reached, `maxmemory-policy` decides what happens:

| Policy | Evicts |
|---|---|
| `noeviction` (default) | Nothing: writes fail with an out-of-memory error |
| `allkeys-lru` / `allkeys-lfu` | Least recently or least frequently used key, any key |
| `volatile-lru` / `volatile-lfu` | Same, but only keys that have a TTL |
| `allkeys-random` / `volatile-random` | A random key, from all keys or from keys with a TTL |
| `volatile-ttl` | The key with the nearest expiry |

A pure cache usually wants `allkeys-lru` or `allkeys-lfu`. A Redis instance holding state that must
not vanish (locks, sessions, queues) needs `noeviction` and capacity planning, or a separate instance
from the cache.

---

## 5. Atomicity: transactions, Lua and functions

Single commands are atomic (§2). For multi-step logic there are three tools.

### 5.1 MULTI / EXEC / WATCH

```
WATCH balance:42                 # optimistic lock
GET balance:42                   # client reads 100
MULTI
DECRBY balance:42 30
INCRBY balance:7 30
EXEC                             # returns nil (aborted) if balance:42 changed since WATCH
```

- Commands between `MULTI` and `EXEC` are queued and then run together, with no other client's
  command in between.
- **There is no rollback.** If one queued command fails at runtime (for example, `INCR` on a
  non-number), the others still run.
- `WATCH` turns the block into a compare-and-set: `EXEC` aborts if a watched key changed, and the
  client retries.

### 5.2 Lua scripts

A script runs atomically on the server, so it can read, decide and write in one step:

```lua
-- Release a lock only if we still own it
if redis.call("GET", KEYS[1]) == ARGV[1] then
  return redis.call("DEL", KEYS[1])
else
  return 0
end
```

```
EVAL "<script above>" 1 lock:invoice:9 3f2c-...-token
```

- Scripts block the server while running, so keep them short.
- Declare every key a script touches in `KEYS`, so it works in Cluster.
- `EVALSHA` runs a cached script by its hash, avoiding resending the source.

### 5.3 Functions (7.0+)

Functions are named, server-stored Lua libraries (`FUNCTION LOAD`, `FCALL`). They persist and replicate
with the data, which makes them a better home than ad hoc scripts for logic the application depends
on.

---

## 6. Messaging: Pub/Sub, Streams and lists

| | Pub/Sub | Streams | Lists as queues |
|---|---|---|---|
| Delivery | Push to currently connected subscribers | Stored log; consumers read when ready | Stored; one consumer pops each item |
| Durability | **None**: missed if not connected | Persisted with the dataset; trimmed by `MAXLEN`/`MINID` | Persisted |
| Fan-out | Every subscriber gets every message | Each consumer **group** gets every entry; within a group, each entry goes to one consumer | No fan-out |
| Acknowledgement | None | `XACK`, pending-entries list, claiming of stuck entries | Manual, using `BLMOVE` to a processing list |
| Replay | No | Yes, by entry ID | No |
| Typical use | Live notifications, cache invalidation, cross-instance WebSocket fan-out | Job queues, event feeds, light event sourcing | Simple work queues |

### 6.1 Pub/Sub

```
SUBSCRIBE chat:channel:7                 # client A
PUBLISH chat:channel:7 "{...}"           # returns the number of subscribers reached
PSUBSCRIBE chat:channel:*                # pattern subscription
```

In Cluster, classic `PUBLISH` is broadcast to every node. Sharded Pub/Sub (`SPUBLISH`/`SSUBSCRIBE`,
7.0+) routes a channel to the shard that owns its hash slot, which scales much better.

### 6.2 Streams with consumer groups

```
XADD orders * orderId 42 amount 30                    # append; returns an ID like 1728200000000-0
XGROUP CREATE orders billing $ MKSTREAM               # group starts at the end ($) or at 0
XREADGROUP GROUP billing worker-1 COUNT 10 BLOCK 5000 STREAMS orders >
XACK orders billing 1728200000000-0                   # done
XPENDING orders billing                               # delivered but not acknowledged
XAUTOCLAIM orders billing worker-2 60000 0-0          # take over entries idle for > 60 s
XADD orders MAXLEN ~ 100000 * ...                     # cap the stream's length
```

- `>` asks for entries never delivered to this group; an ID such as `0` re-reads this consumer's
  pending entries, for example after a restart.
- An entry stays in the **pending entries list** until acknowledged. A crashed worker's entries are
  reclaimed with `XAUTOCLAIM`, so delivery is at-least-once. Make handlers idempotent.

### 6.3 Lists as a reliable queue

```
BLMOVE jobs jobs:processing RIGHT LEFT 5   # atomically take a job and park it
# ... process ...
LREM jobs:processing 1 "<job>"             # done; a reaper re-queues items stuck in processing
```

---

## 7. Persistence

| Mode | How it works | Data at risk on crash | Cost |
|---|---|---|---|
| **None** | Memory only | Everything | None |
| **RDB snapshots** | `fork()` and write a point-in-time file, on a schedule (`save` rules) or with `BGSAVE` | Everything since the last snapshot (minutes) | Fork overhead; copy-on-write memory spikes with heavy writes |
| **AOF** | Append every write command to a log; `appendfsync everysec` by default (or `always`, `no`) | About one second with `everysec` | Larger files and more I/O; rewritten in the background to stay compact |
| **RDB + AOF** | Both | AOF's guarantee | Both costs; the usual production choice when data matters |

Persistence is local to one node and asynchronous, so Redis is not a system of record in the way a
database with synchronous commits is. Treat it as a fast store whose loss you can survive, or design
for its durability explicitly.

---

## 8. Replication, high availability and Cluster

- **Replication.** Replicas copy a primary asynchronously, and they can serve reads, which may be
  slightly stale. `WAIT n timeout` blocks until *n* replicas acknowledge previous writes. That
  reduces, but does not eliminate, the window for losing acknowledged writes on failover.
- **Sentinel.** Separate processes monitor a primary, agree that it has failed, promote a replica, and
  tell clients the new address. This gives high availability for a single dataset.
- **Cluster.** Shards data across primaries:
  - the keyspace is divided into **16,384 hash slots**: `slot = CRC16(key) mod 16384`;
  - each primary owns a range of slots and has its own replicas;
  - clients are redirected (`MOVED`, `ASK`) to the node owning a key, and smart clients cache the slot
    map;
  - **multi-key operations must stay in one slot**, or they fail with `CROSSSLOT`. Hash tags force
    keys together: only the text inside `{}` is hashed, so `{user:42}:cart` and `{user:42}:session`
    share a slot;
  - Lua scripts and `MULTI` blocks may only touch keys in one slot.
- **Client-side caching** (6.0+, `CLIENT TRACKING`). The server tells a client when keys it has read
  change, so the client can safely keep a local copy and avoid round trips for hot keys.

---

## 9. Patterns with scenarios

### 9.1 Cache-aside

*Scenario:* user profiles are read on every page view, but change rarely.

```
GET user:42:profile                  # hit → return
                                      # miss → load from DB, then:
SET user:42:profile "{...}" EX 600   # cache for 10 minutes
DEL user:42:profile                  # on profile update: invalidate, don't update
```

- **Stampede.** When a hot key expires, many requests miss at once and all hit the database. Remedies:
  a short lock so only one request recomputes (`SET lock:user:42:profile 1 NX EX 5`), refreshing
  early before expiry, and adding random jitter to TTLs so keys do not expire together.
- Prefer deleting over updating the cache on writes. Concurrent update-on-write can leave a stale
  value behind.

### 9.2 Rate limiting

*Scenario:* allow at most 20 messages per user per minute.

Fixed window, with atomic expiry:

```
MULTI
INCR rl:msg:42:202610061230
EXPIRE rl:msg:42:202610061230 60 NX     # set the TTL only once (EXPIRE ... NX is 7.0+)
EXEC                                     # reject if INCR returned > 20
```

A separate `INCR` followed by `EXPIRE` is a classic bug: if the process dies between them, the key
never expires and the user is limited forever.

Sliding window, with a sorted set:

```
MULTI
ZREMRANGEBYSCORE rl:msg:42 0 <now-60000>   # drop events older than the window
ZADD rl:msg:42 <now> <now>-<uuid>          # record this event
ZCARD rl:msg:42                            # count in window → reject if > 20
PEXPIRE rl:msg:42 60000
EXEC
```

A token bucket is usually written as a Lua script, so reading the tokens, refilling and deducting
happen atomically.

### 9.3 Distributed lock

*Scenario:* only one instance may run the nightly invoice job.

```
SET lock:invoices <random-token> NX PX 30000   # acquire if free, auto-release after 30 s
# ... work, extending the TTL if needed ...
EVAL <compare-and-delete script from §5.2> 1 lock:invoices <random-token>
```

- The **random token** prevents releasing a lock that expired and was taken by someone else.
- **Limits.** A process paused longer than the TTL (by GC, swapping, or a VM stall) can still believe
  it holds the lock after it expired. For correctness-critical work, pair the lock with a **fencing
  token**, a counter that downstream storage checks, or use a coordination system built for it. The
  multi-node "Redlock" algorithm is debated for exactly this reason (see Martin Kleppmann's
  critique). Redis locks are best used for efficiency, meaning "avoid duplicate work", not for
  absolute mutual exclusion.

### 9.4 Leaderboard

```
ZINCRBY lb:weekly 15 user:42            # add points
ZRANGE lb:weekly 0 9 REV WITHSCORES     # top 10
ZREVRANK lb:weekly user:42              # my position (0-based)
```

### 9.5 Idempotency keys and de-duplication

*Scenario:* a payment endpoint must not charge twice when a client retries.

```
SET idem:pay:<client-key> "processing" NX EX 86400   # first request wins
# second request: SET returns nil → return the stored result instead of charging again
```

The same `SET ... NX` with a short TTL makes one of several instances "win" an event, for example
processing each expired-key notification once.

### 9.6 Presence and liveness

*Scenario:* show which users are online.

| Approach | How | Weakness |
|---|---|---|
| TTL key + expiry events | `SET presence:hb:42 1 EX 30` on each heartbeat; react to `expired` events | Events are delayed until deletion, lost while disconnected, per node in Cluster, and delivered to every instance (§4.3) |
| **Sorted set + sweeper** | `ZADD presence:alive <now> 42` on each heartbeat; a scheduled job takes `ZRANGEBYSCORE presence:alive -inf <now-30000>`, then `ZREM`s each | `ZREM` returns 1 on exactly one caller, so each offline transition is handled once across instances. Needs a scheduler |
| Session set per user | `SADD presence:sessions:42 <sessionId>` on connect, `SREM` on disconnect | Captures clean disconnects instantly; needs the TTL approach as a fallback for crashes |

This project uses the first approach. [../REDIS.md](../REDIS.md) explains its problems and the
corrections in detail.

### 9.7 Cross-instance fan-out for WebSockets

*Scenario:* two app instances each hold some users' WebSocket connections.

- Each instance subscribes to `ws:broadcast` (Pub/Sub).
- To send to channel 7, an instance publishes `{dest:"/topic/channels/7/messages", payload}`.
- Every instance receives it and relays it to its own local subscribers.

Pub/Sub's at-most-once delivery is acceptable here, because the durable copy of each message lives in
the database. Use sharded Pub/Sub in Cluster.

### 9.8 Work queue

*Scenario:* resize uploaded images in the background, with retries if a worker dies. Use a stream with
a consumer group (§6.2): workers `XREADGROUP`, `XACK` on success, and a reaper `XAUTOCLAIM`s entries
that stay pending too long. For multi-day retention, replay, or many independent consumers, use Kafka
instead (§12).

### 9.9 Counting at scale

```
PFADD dau:2026-10-06 user:42            # approximate daily active users
PFCOUNT dau:2026-10-06
PFMERGE wau dau:2026-10-01 ... dau:2026-10-07

SETBIT active:2026-10-06 42 1           # exact, if user IDs are dense integers
BITCOUNT active:2026-10-06
```

### 9.10 Nearby search

```
GEOADD drivers 77.2090 28.6139 driver:7
GEOSEARCH drivers FROMLONLAT 77.21 28.61 BYRADIUS 3 km ASC COUNT 5 WITHDIST
```

### 9.11 Short-lived UI state

*Scenario:* typing indicators.

```
SET typing:ch7:user42 1 EX 5     # refreshed while typing; disappears on its own
```

Publish a change event for live updates, and let the TTL clean up when a client vanishes.

---

## 10. Spring Data Redis essentials

| Topic | What to know |
|---|---|
| Client | Spring Boot uses **Lettuce** by default (non-blocking, thread-safe, one shared connection). Jedis is the alternative |
| `StringRedisTemplate` | Keys and values as strings. Human-readable in `redis-cli`. The right default for most uses |
| `RedisTemplate<K, V>` | Defaults to **JDK serialization**, which produces unreadable binary keys and couples data to Java class versions. Configure JSON or string serializers explicitly |
| Operations | `opsForValue()`, `opsForHash()`, `opsForList()`, `opsForSet()`, `opsForZSet()`, `opsForStream()`; `executePipelined(...)` for pipelining |
| Scripts | `RedisScript.of(lua, Long.class)` with `execute(script, keys, args)` |
| Pub/Sub and keyspace events | `RedisMessageListenerContainer` plus `MessageListener`s. `KeyExpirationEventMessageListener` subscribes to `__keyevent@*__:expired` and can set `notify-keyspace-events` at startup when the server allows `CONFIG` |
| Streams | `StreamMessageListenerContainer` for consumer-group polling |
| Caching | `@EnableCaching` with `RedisCacheManager` and `@Cacheable`/`@CacheEvict`. `spring.cache.type=redis` alone does nothing without `@EnableCaching` |
| Sessions | Spring Session Data Redis stores HTTP sessions in Redis, so any instance can serve any user |
| Proxies | `@Transactional` and `@Async` on methods that touch Redis follow normal Spring proxy rules: self-invocation bypasses them |

```java
// Atomic "first writer wins", with expiry, through StringRedisTemplate
Boolean first = redis.opsForValue()
        .setIfAbsent("presence:offline_lock:" + userId, "1", Duration.ofSeconds(5));
if (Boolean.TRUE.equals(first)) { /* only one instance gets here */ }
```

---

## 11. Pitfalls checklist

- [ ] **`KEYS` in production.** Use `SCAN`.
- [ ] **Keys without TTLs** in a store meant to be temporary. Memory grows until eviction or
      out-of-memory errors.
- [ ] **Big keys.** A hash or set with millions of members makes some commands slow and replication
      bursty. Split it, and delete it with `UNLINK`, which frees memory in the background.
- [ ] **Hot keys.** One key read by every request saturates one node's CPU in Cluster. Use local
      caching, client-side caching, or replicas.
- [ ] **Pub/Sub or keyspace events for must-not-lose work.** They are at-most-once. Use Streams, a
      database, or Kafka.
- [ ] **Assuming `MULTI` rolls back.** It does not.
- [ ] **Lock without a token, or a lock as a correctness guarantee.** See §9.3.
- [ ] **Multi-key operations in Cluster** without hash tags fail with `CROSSSLOT`.
- [ ] **Non-atomic check-then-act**, such as `GET` then `SET`, or `INCR` then `EXPIRE`. Use
      `SET ... NX`, `MULTI`, or Lua.
- [ ] **JDK serialization** in `RedisTemplate`.
- [ ] **Persistence assumptions.** The defaults favour speed; know what a crash loses.
- [ ] **Fork memory.** `BGSAVE` and AOF rewrites can transiently need much more memory under heavy
      writes.
- [ ] **Eviction surprises.** `volatile-*` policies cannot evict keys without TTLs, and `noeviction`
      turns full memory into write errors.

---

## 12. Redis compared with alternatives

| Need | Redis | Alternative | Notes |
|---|---|---|---|
| Plain key-value cache | Yes | Memcached | Memcached is multithreaded and simpler, but has only strings, no persistence, and no data structures |
| System of record | No | PostgreSQL and similar | Redis persistence is asynchronous and per-node |
| Durable event log, replay, many consumer groups, retention in days | Only partly (Streams, bounded by RAM) | **Kafka** | Kafka stores data on disk, scales partitions horizontally, and keeps history for days or forever |
| Live fan-out between app instances | Pub/Sub | Kafka, RabbitMQ | Redis Pub/Sub is the lightest when loss is acceptable |
| Work queue with acks | Streams | RabbitMQ, SQS, Kafka share groups | Streams are fine at moderate scale |
| Open-source licence | Redis 8 (AGPLv3 option) | Valkey (BSD) | Feature sets have started to diverge since the fork |

---

## 13. How this project uses it

| Topic | Where to read |
|---|---|
| Presence keys and their TTLs | [../REDIS.md §3](../REDIS.md#3-key-schema) |
| Status resolution | [../REDIS.md §4](../REDIS.md#4-status-resolution) |
| Expiry → OFFLINE through keyspace notifications | [../REDIS.md §5](../REDIS.md#5-expiry--offline) |
| Corrections, including the sorted-set sweeper alternative | [../REDIS.md §10](../REDIS.md#10-corrections), [../IMPROVEMENTS.md](../IMPROVEMENTS.md#presence) |
| End-to-end presence scenarios after the fixes | [../REDIS.md §11](../REDIS.md#11-presence-after-the-corrections-how-status-is-resolved-and-broadcast) |
| Defects | [../BUGS.md](../BUGS.md): B08, B14, B15, B25, B26, B41–B43, B51 |

---

## 14. Self-check questions

1. **Redis is single-threaded. How is it fast, and what is the risk?** It works in memory, with an
   event loop and no locking, and most commands are O(1) or O(log N). The risk is that one slow
   command, such as `KEYS` or a big `DEL`, blocks every client (§2).
2. **How do keys expire, and when does an `expired` event fire?** Expiry is lazy on access, plus
   active background sampling. The event fires when the key is deleted, which can be later than the
   TTL (§4.2, §4.3).
3. **Why are keyspace notifications a weak basis for "mark user offline"?** They are delayed,
   fire-and-forget, per node, and delivered to every subscriber (§4.3).
4. **Pub/Sub versus Streams?** Pub/Sub is transient fan-out to whoever is connected. Streams are a
   persisted log with consumer groups, acknowledgements and replay (§6).
5. **Does `MULTI/EXEC` roll back on error?** No. It isolates the queued commands but does not make
   them all-or-nothing. Use `WATCH` for optimistic concurrency, or Lua (§5).
6. **How do you implement a safe lock?** `SET key token NX PX ttl`, and release with a
   compare-and-delete Lua script. Add fencing tokens for correctness, because of pauses and expiry
   (§9.3).
7. **Why do multi-key commands fail in Cluster, and how do you fix it?** The keys hash to different
   slots. Use hash tags so they share one (§8).
8. **RDB versus AOF?** Snapshots lose minutes of data but are compact. AOF with `everysec` loses about
   one second, at the cost of more I/O. Production systems often run both (§7).
9. **How do you rate-limit 20 requests per minute?** A fixed window with `INCR` plus
   `EXPIRE ... NX` in one transaction, or a sliding window with a sorted set (§9.2).
10. **When would you not use Redis?** As the system of record, for large datasets that do not fit in
    RAM, or for a durable, replayable event log, which is Kafka's job (§12).
