# Kafka — Technology Guide

A working knowledge guide to Apache Kafka: the log model, brokers and replication, producers,
consumers and consumer groups, delivery guarantees, error handling, schemas, the surrounding
ecosystem, and common patterns with concrete scenarios. For how *this project* produces to Kafka, how
the separate consumer service persists messages, and what is wrong with both, see
[../KAFKA.md](../KAFKA.md).

## Contents

1. [What Kafka is: a log, not a queue](#1-what-kafka-is-a-log-not-a-queue)
2. [The core model](#2-the-core-model)
3. [Brokers, replication and KRaft](#3-brokers-replication-and-kraft)
4. [Producers](#4-producers)
5. [Consumers and consumer groups](#5-consumers-and-consumer-groups)
6. [Delivery semantics](#6-delivery-semantics)
7. [Error handling](#7-error-handling)
8. [Schemas and contracts](#8-schemas-and-contracts)
9. [The ecosystem](#9-the-ecosystem)
10. [Patterns with scenarios](#10-patterns-with-scenarios)
11. [Sizing and operations](#11-sizing-and-operations)
12. [Spring for Apache Kafka essentials](#12-spring-for-apache-kafka-essentials)
13. [Kafka compared with alternatives](#13-kafka-compared-with-alternatives)
14. [Pitfalls checklist](#14-pitfalls-checklist)
15. [How this project uses it](#15-how-this-project-uses-it)
16. [Self-check questions](#16-self-check-questions)

---

## 1. What Kafka is: a log, not a queue

Kafka is a **distributed, replicated, append-only log**. Producers append records to the end of a
log. Consumers read from positions (offsets) of their choosing. Records stay for a configured
retention period **whether or not anyone has read them**.

| Traditional queue (RabbitMQ-style) | Kafka |
|---|---|
| A message is removed once a consumer acknowledges it | A record stays until retention removes it. Consumers only move their own offset |
| Many consumers *compete* for messages | Each consumer **group** receives every record; competition happens only *within* a group |
| Per-message acknowledgement and redelivery | Per-partition offset commits: "I have processed everything up to offset N" |
| Broker routes and filters (exchanges, bindings) | Brokers are mostly dumb storage; consumers decide what to read |
| Replay is not part of the model | Replay is natural: reset a group's offset and read again |

That difference drives everything else. Kafka excels when several independent systems need the same
stream of events, when history must be replayable, and when throughput is high. It is clumsy for
per-message work queues with individual retries and priorities, although share groups (§5.7) are
closing that gap.

---

## 2. The core model

```
topic: message-events  (4 partitions)

partition 0: [0][1][2][3][4][5] ...      ← each partition is an ordered log
partition 1: [0][1][2] ...
partition 2: [0][1][2][3] ...
partition 3: [0][1] ...
                         ↑ offset: the record's position within its partition
```

- **Record.** A key (optional), a value, headers, and a timestamp.
- **Topic.** A named stream of records, such as `message-events`.
- **Partition.** A topic is split into partitions, which are the unit of **ordering**, **parallelism**
  and **storage distribution**. Order is guaranteed **only within a partition**.
- **Offset.** A record's sequence number within its partition. A consumer's position is just an
  offset.
- **Segments.** Each partition is stored on disk as a series of segment files. Retention deletes or
  compacts whole old segments, and tiered storage can move older segments to object storage.

### 2.1 Retention and compaction

| `cleanup.policy` | Keeps | Use for |
|---|---|---|
| `delete` (default) | Everything within `retention.ms` (7 days by default) and/or `retention.bytes` | Event streams: chat messages, clicks, logs |
| `compact` | At least the **latest record per key**. A record with a `null` value, a *tombstone*, deletes its key after `delete.retention.ms` | Changelogs and current-state tables: user profiles, settings |
| `compact,delete` | Latest per key, but also bounded by time | Changelogs that may expire |

A compacted topic is effectively a durable key-value table that consumers can rebuild state from.
Kafka Streams state stores, and the internal `__consumer_offsets` topic, work this way.

---

## 3. Brokers, replication and KRaft

- **Brokers** are the servers that store partitions. Each partition has one **leader** broker, which
  handles all its reads and writes, and **follower** replicas, which copy it.
- **Replication factor (RF).** The number of copies of each partition. RF 3 is the usual production
  value.
- **ISR (in-sync replicas).** The replicas currently caught up with the leader. If the leader fails, a
  new leader is chosen from the ISR.
- **`min.insync.replicas`.** With `acks=all`, a write succeeds only if at least this many replicas
  (leader included) have it. The common setting, RF 3 with `min.insync.replicas=2`, survives one
  broker loss with no data loss and no write outage.
- **Unclean leader election** (`unclean.leader.election.enable`, default `false`). It allows an
  out-of-sync replica to become leader, trading data loss for availability. Leave it off when data
  matters.
- **KRaft.** Kafka's built-in Raft-based controller quorum, which manages metadata such as topics,
  partitions and leaders. **Apache Kafka 4.0 (March 2025) removed ZooKeeper entirely**; clusters run
  in KRaft mode only. This project's `docker-compose.yml` runs a single node acting as both broker and
  controller.

---

## 4. Producers

### 4.1 Choosing the partition

| Record key | Partition |
|---|---|
| Present | `hash(key) mod partitionCount`. The same key always goes to the same partition (while the count is unchanged), so **records with one key keep their order** |
| Absent | Spread out with "sticky" partitioning: fill a batch for one partition, then move on. No ordering relationship between records |
| Explicit partition | Whatever the producer specifies |

**Choose the key by what must stay ordered.** For chat, that is the channel (`channelId`): messages in
one channel stay in order, and different channels spread across partitions. Keying by user would
order a user's messages across channels, which nobody needs, and would let one channel's messages
interleave out of order.

Increasing a topic's partition count **changes where existing keys map**, which breaks per-key
ordering across the change. Partitions can never be removed. Choose the count up front, with
headroom.

### 4.2 Batching, compression and latency

Producers accumulate records per partition into batches:

- `batch.size` (default 16 KB) bounds a batch.
- `linger.ms` waits briefly for a batch to fill, trading a few milliseconds of latency for much higher
  throughput.
- `compression.type` (`gzip`, `snappy`, `lz4`, `zstd`) compresses whole batches. Text payloads such
  as JSON compress very well.

### 4.3 Durability and ordering settings

| Setting | Meaning | Recommended |
|---|---|---|
| `acks` | `0` fire and forget; `1` leader wrote it; `all` every in-sync replica wrote it | `all` (the default since Kafka 3.0) |
| `enable.idempotence` | The broker de-duplicates retries using a producer ID and sequence numbers, so a retry never creates a duplicate or reorders records | `true` (the default since 3.0) |
| `retries` / `delivery.timeout.ms` | Keep retrying transient errors until the delivery timeout (default 120 s) expires | Keep the defaults; bound the total time with `delivery.timeout.ms` |
| `max.in.flight.requests.per.connection` | Unacknowledged requests per broker connection | Up to 5 keeps ordering with idempotence on |
| `max.block.ms` | How long `send()` may **block** waiting for metadata or buffer space (default 60 s) | Lower it if `send()` runs on a latency-sensitive thread |

`send()` is asynchronous: it returns a future, and the callback runs later on the producer's network
thread. Two practical consequences: never do slow work in the callback, and remember that `send()`
can still block the caller for up to `max.block.ms` when the cluster is unreachable.

### 4.4 Transactions

```
transactional.id = order-processor-1
initTransactions()
beginTransaction()
  send(topicA, ...); send(topicB, ...)
  sendOffsetsToTransaction(consumedOffsets, consumerGroupMetadata)
commitTransaction()            // or abortTransaction()
```

A transaction makes writes to several partitions, plus the consumer's offset commit, atomic.
Consumers with `isolation.level=read_committed` see only committed records; the default is
`read_uncommitted`. This is how Kafka achieves exactly-once *read-process-write* **within Kafka**.

---

## 5. Consumers and consumer groups

### 5.1 Groups and assignment

```
topic message-events: P0 P1 P2 P3

group "persistence"  (2 consumers)      group "search-indexer"  (4 consumers)
  consumer A ← P0, P1                     w1 ← P0   w2 ← P1   w3 ← P2   w4 ← P3
  consumer B ← P2, P3
```

- Each partition is assigned to **exactly one consumer within a group**, so a group's parallelism
  equals at most the partition count. A fifth consumer in a 4-partition group sits idle.
- **Different groups are independent.** Each gets every record and keeps its own offsets. This is how
  one topic feeds many systems.

### 5.2 Offsets and commits

- A group's progress is stored as committed offsets in the internal `__consumer_offsets` topic.
- **Auto-commit** (`enable.auto.commit=true`, the default, every 5 s) commits whatever has been
  *returned by `poll()`*, whether or not it was processed. That makes it easy to lose records on a
  crash.
- **Manual commit** after processing gives at-least-once delivery (§6).
- **`auto.offset.reset`** applies only when a group has **no valid committed offset**: on its first
  start, or after its offsets expire (`offsets.retention.minutes`, 7 days by default, counted from
  when the group becomes empty). `latest` (the default) skips everything already in the topic, and
  `earliest` reads from the oldest retained record. For anything that must not lose data, choose
  `earliest` and make processing idempotent.

### 5.3 The poll loop and its timeouts

| Setting | Default | Meaning |
|---|---|---|
| `max.poll.records` | 500 | Records returned per `poll()` |
| `max.poll.interval.ms` | 5 min | Maximum time between `poll()` calls. Exceeding it makes the consumer leave the group, which triggers a rebalance |
| `session.timeout.ms` | 45 s | No heartbeat for this long, and the broker considers the consumer dead |
| `heartbeat.interval.ms` | 3 s | Background heartbeat frequency |

Slow processing per batch is the classic cause of "rebalance storms". Process faster, lower
`max.poll.records`, or raise `max.poll.interval.ms`.

### 5.4 Rebalancing

When consumers join or leave, or partitions change, ownership of partitions is reassigned:

| Protocol | Behaviour |
|---|---|
| Eager (classic) | Every consumer gives up every partition, then the group reassigns. Processing stops group-wide during it |
| Cooperative incremental (`CooperativeStickyAssignor`) | Only partitions that actually move are revoked; others keep processing |
| **New consumer group protocol (KIP-848)** | The broker coordinates assignment incrementally, with no group-wide barrier. Generally available in Kafka 4.0 and enabled on the server; clients opt in with `group.protocol=consumer` |

**Static membership** (`group.instance.id`) lets a restarting consumer reclaim its partitions without
a rebalance, if it returns within the session timeout. It is useful for rolling deployments.

### 5.5 Lag

**Consumer lag** is, per partition, the log-end offset minus the group's committed offset: how far
behind the group is. It is the single most important consumer health metric.

```
kafka-consumer-groups --bootstrap-server localhost:9092 --describe --group persistence
# TOPIC           PARTITION  CURRENT-OFFSET  LOG-END-OFFSET  LAG
# message-events  0          1520            1520            0
```

### 5.6 Resetting offsets

```
kafka-consumer-groups --bootstrap-server localhost:9092 --group persistence \
  --topic message-events --reset-offsets --to-earliest --execute     # group must be stopped
```

### 5.7 Share groups ("queues for Kafka")

KIP-932 adds **share groups**: consumers in a share group cooperatively consume partitions with
**per-record acknowledgement** and redelivery, like a classic queue, and without the "one consumer
per partition" limit. It was **early access in Kafka 4.0**. Check its current status before relying
on it.

---

## 6. Delivery semantics

| Semantics | How it happens | Failure mode |
|---|---|---|
| **At-most-once** | Commit the offset *before* processing (or auto-commit) | A crash after the commit loses the record |
| **At-least-once** | Process, *then* commit | A crash after processing but before the commit redelivers the record. **Processing must be idempotent** |
| **Exactly-once within Kafka** | Idempotent producer + transactions + `read_committed` consumers (§4.4) | Covers Kafka-to-Kafka pipelines only |
| **Effectively-once into a database** | At-least-once delivery plus an **idempotent write**: upsert by a unique event ID, or store the consumed offset in the same database transaction | The standard approach for sinks outside Kafka |

**The dual-write problem.** A service that writes to its database *and* publishes to Kafka as two
separate steps can do one and then crash before the other. Neither order is safe. The **transactional
outbox** (§10.5) solves it.

---

## 7. Error handling

A consumer that throws on a record has to decide what happens to it:

| Strategy | How | Trade-off |
|---|---|---|
| Retry in place (blocking) | Re-seek and redeliver the same record, with back-off | Preserves order, but **blocks the partition**: every later record waits |
| Dead-letter topic (DLT) | After N attempts, publish the record to `<topic>-dlt` and move on | The partition keeps flowing. Someone must monitor and reprocess the DLT |
| Non-blocking retry topics | Publish failures to `topic-retry-1`, `topic-retry-2` … with increasing delay, and finally the DLT | Main traffic keeps flowing, but **order is lost** for retried records |
| Skip and log | Give up after N attempts | Simple, and silently loses data. Rarely acceptable |

**Classify errors.** A transient failure (database down, timeout) deserves retries with back-off. A
permanent failure (a malformed record, a validation error, a missing referenced entity) should go
straight to the DLT, because retrying cannot help. Records that cannot even be deserialized are
"poison pills": wrap the deserializer so they reach the error handler instead of crashing the consumer
in a loop.

---

## 8. Schemas and contracts

Producers and consumers evolve separately, so the record format is a contract.

| Format | Pros | Cons |
|---|---|---|
| JSON | Human-readable; no tooling needed | No enforced schema; field renames silently become `null`; verbose |
| Avro | Compact; schema evolution rules; strong tooling | Needs a schema registry; less readable |
| Protobuf | Compact; widely used; good evolution rules | Needs generated code; a registry is still recommended |

A **schema registry** stores versioned schemas and rejects incompatible changes at publish time.
Compatibility modes:

| Mode | Guarantee | Example of an allowed change |
|---|---|---|
| `BACKWARD` | New consumers can read old data | Add a field with a default |
| `FORWARD` | Old consumers can read new data | Remove a field that had a default |
| `FULL` | Both | Add or remove fields that have defaults |

Version events explicitly (`MessageCreatedV1`), and keep the event schema separate from API or
WebSocket DTOs, so a UI change cannot break the log.

---

## 9. The ecosystem

| Component | What it does |
|---|---|
| **Kafka Connect** | Runs source and sink connectors: databases, S3, Elasticsearch and more, with no custom code |
| **Debezium** | Change data capture: streams row changes from database logs (such as PostgreSQL logical decoding) into Kafka. Includes an outbox event router |
| **Kafka Streams** | Java library for stream processing: filters, joins, windowed aggregations, and local state stores backed by compacted changelog topics |
| **ksqlDB** | SQL over Kafka streams, built on Kafka Streams |
| **MirrorMaker 2** | Replicates topics between clusters, for disaster recovery or migration |

```java
// Kafka Streams: messages per channel per minute
builder.stream("message-events", Consumed.with(Serdes.String(), messageSerde))
       .groupByKey()                                              // key = channelId
       .windowedBy(TimeWindows.ofSizeWithNoGrace(Duration.ofMinutes(1)))
       .count()
       .toStream()
       .to("channel-activity-per-minute");
```

---

## 10. Patterns with scenarios

### 10.1 Event notification

*Scenario:* after a message is sent, several systems react independently: persistence, search
indexing, push notifications, analytics. Each is its own consumer group on `message-events`. Adding a
new reaction means adding a group, with no change to the producer.

### 10.2 Event-carried state transfer

*Scenario:* the notification service needs usernames without calling the user service on every
event. Publish user changes to a **compacted** `users` topic. The notification service consumes it
into a local table and stays correct even if the user service is down.

### 10.3 Per-key ordering

*Scenario:* chat messages must appear in the order sent, per channel. Key by `channelId`. All of a
channel's messages share one partition, and one consumer thread processes them in order. Retries must
then block (§7), or ordering is lost.

### 10.4 Persistence consumer with an idempotent write

*Scenario:* store every chat message exactly once, despite redeliveries.

- The producer assigns a UUID to each message.
- The consumer runs `INSERT ... ON CONFLICT (id) DO NOTHING`, or checks existence before inserting.
- It commits the offset only after the insert succeeds (manual acknowledgement).
- Failures retry with back-off and then go to a DLT. They are never silently skipped.

This is the design of this project's `message-consumer-service`, minus its error-handling gaps (see
[../KAFKA.md §4](../KAFKA.md#4-the-consumer-service)).

### 10.5 Transactional outbox

*Scenario:* save an order to PostgreSQL **and** publish `OrderCreated`, without the dual-write problem.

1. In one database transaction, insert the order *and* a row in an `outbox` table.
2. A relay publishes outbox rows to Kafka: a polling job, or Debezium tailing the database log.
3. Consumers are idempotent, because the relay can publish a row more than once.

The database transaction is the single source of truth. Kafka is guaranteed to receive the event
eventually.

### 10.6 Change data capture

*Scenario:* keep a search index in sync with PostgreSQL without touching application code. Debezium
streams row changes into Kafka, and an Elasticsearch sink connector applies them.

### 10.7 Event sourcing and CQRS

*Scenario:* keep the full history of an aggregate, such as a channel's settings, as events, and build
read models from them. Events are the source of truth; projections are rebuilt by replaying from
offset 0. Kafka's ordered, retained log suits this. Querying a single aggregate's events, though,
usually needs a dedicated event store or a compacted snapshot topic.

### 10.8 Fan-out across WebSocket servers

*Scenario:* several app instances each hold some users' WebSocket connections.

- Each instance runs a consumer in its **own** group (for example `fanout-<instanceId>`), so every
  instance sees every event.
- Each pushes events to its locally connected sessions.
- A separate shared group handles persistence (§10.4).

### 10.9 Buffering and back-pressure

*Scenario:* a burst of 100,000 events arrives faster than a downstream API accepts. Kafka absorbs the
burst on disk, and consumers drain at their own pace. Lag grows temporarily instead of requests
failing.

### 10.10 Log and metrics pipelines

*Scenario:* hundreds of services ship logs. They produce to `logs`, partitioned by service, and
Connect sinks deliver them to storage and search. Retention bounds the cost.

---

## 11. Sizing and operations

- **Partitions.** Enough for peak consumer parallelism, with headroom, because adding partitions later
  remaps keys. Too many partitions cost memory, file handles, and failover time.
- **Hot partitions.** A skewed key, such as one huge channel, overloads one partition and its single
  consumer. Spread the load with a compound key (`channelId:bucket`) if strict per-channel ordering
  is not required.
- **Message size.** The broker's `message.max.bytes` defaults to about 1 MB. Large payloads belong in
  object storage, with only a reference in Kafka.
- **What to monitor:**
  - consumer lag per group;
  - under-replicated and offline partitions;
  - produce and fetch request latency;
  - ISR shrink and expand rates;
  - disk usage against retention;
  - DLT volume.
- **Retention.** Size it for the longest consumer outage you must survive. Remember that committed
  offsets also expire (§5.2).
- **Security.** TLS for encryption; SASL (SCRAM, OAUTHBEARER, mTLS) for authentication; ACLs per topic
  and group.
- **Java versions.** In Kafka 4.0, clients and Streams need Java 11+, and brokers, Connect and tools
  need Java 17+.

---

## 12. Spring for Apache Kafka essentials

| Topic | What to know |
|---|---|
| Producing | `KafkaTemplate.send(topic, key, value)` returns a `CompletableFuture<SendResult>`. Configure through `spring.kafka.producer.*`; defining your own `ProducerFactory` bean disables that property binding |
| Consuming | `@KafkaListener(topics = "...", groupId = "...", concurrency = "2")`. Concurrency above the partition count leaves threads idle |
| Container factory | Boot auto-configures `kafkaListenerContainerFactory` from `spring.kafka.consumer.*` and `spring.kafka.listener.*`. A custom factory must be a `@Bean` to take effect |
| Ack modes | `spring.kafka.listener.ack-mode`: `BATCH` (default), `RECORD`, `MANUAL`, `MANUAL_IMMEDIATE`. Manual modes inject an `Acknowledgment` parameter |
| Default error handling | Without a configured handler, `DefaultErrorHandler` with `FixedBackOff(0, 9)`: 10 attempts with no delay, then the record is logged and skipped |
| Fatal exceptions | `DeserializationException`, `MessageConversionException`, `ClassCastException` and others skip retries and go straight to the recoverer |
| Dead letters | `DeadLetterPublishingRecoverer` publishes to `<topic>-dlt`, on the same partition number, so the DLT needs at least as many partitions. If the recoverer itself fails, the record is redelivered again |
| Non-blocking retries | `@RetryableTopic(attempts = "4")` on a listener creates retry topics and a DLT automatically, with a configurable back-off between attempts. The back-off annotation type differs between Spring Kafka versions, so check the reference for the version in use |
| JSON | Spring Kafka 4 prefers Jackson 3 (`JacksonJsonSerializer`/`JacksonJsonDeserializer`); the Jackson 2 classes are deprecated but work. Type headers name the producer's class, so consumers in other services usually disable them and set a default type |
| Poison pills | Wrap deserializers in `ErrorHandlingDeserializer` |
| Testing | `@EmbeddedKafka`, or Testcontainers for a real broker |

```java
@Bean
DefaultErrorHandler kafkaErrorHandler(KafkaOperations<Object, Object> template) {
    var recoverer = new DeadLetterPublishingRecoverer(template);      // → <topic>-dlt
    var backOff = new ExponentialBackOffWithMaxRetries(5);
    backOff.setInitialInterval(1_000);
    backOff.setMultiplier(2.0);
    backOff.setMaxInterval(30_000);
    var handler = new DefaultErrorHandler(recoverer, backOff);
    handler.addNotRetryableExceptions(IllegalArgumentException.class);
    return handler;                       // Boot attaches a CommonErrorHandler bean to its factory
}
```

```java
@KafkaListener(topics = "message-events", groupId = "message-persistence-group")
public void consume(MessageEvent event, Acknowledgment ack) {
    repository.insertIfAbsent(event);     // idempotent on event.id()
    ack.acknowledge();                    // commit only after the write succeeds
}
```

---

## 13. Kafka compared with alternatives

| | Kafka | RabbitMQ | Redis Streams | Apache Pulsar | SQS / SNS |
|---|---|---|---|---|---|
| Model | Partitioned log | Smart broker with queues and exchanges (plus streams since 3.9) | In-memory log with consumer groups | Log (BookKeeper storage), with queue and stream subscriptions | Managed queue / fan-out |
| Retention and replay | Days to forever; replay by offset | Until acknowledged (classic queues) | Bounded by RAM and `MAXLEN` | Days to forever; tiered | Up to 14 days (SQS); no replay |
| Ordering | Per partition | Per queue, with a single consumer | Per stream | Per partition or key | FIFO queues per group |
| Per-message ack and redelivery | Offsets; share groups emerging | Yes, with rich routing, priorities, TTLs | Yes (pending entries list) | Yes | Yes |
| Throughput | Very high | High | High, memory-bound | Very high | High, managed |
| Operational weight | Significant | Moderate | Light if Redis already exists | Significant | None (managed) |
| Best at | Event streaming, multiple independent consumers, replay, CDC | Task queues, complex routing, RPC-style messaging | Lightweight queues next to an existing Redis | Multi-tenant streaming, geo-replication | Serverless and AWS-native decoupling |

---

## 14. Pitfalls checklist

- [ ] **Wrong key.** No key means no ordering. A key with skewed values means a hot partition.
- [ ] **Adding partitions** to a keyed topic breaks per-key ordering across the change.
- [ ] **Auto-commit** with slow or failing processing loses records. Commit after processing.
- [ ] **`auto.offset.reset=latest`** for a consumer that must not lose data: first start and offset
      expiry skip records.
- [ ] **Non-idempotent consumers** under at-least-once delivery create duplicates.
- [ ] **Silent skip** after retries (including Spring Kafka's default) without a DLT.
- [ ] **Blocking retries** on a partition stall every later record behind one bad message.
- [ ] **Processing longer than `max.poll.interval.ms`** causes rebalance loops.
- [ ] **Dual writes** to a database and Kafka without an outbox.
- [ ] **Using DTOs as event schemas**, or hand-copied event classes across services, with no registry
      or contract tests.
- [ ] **`send()` blocking for up to `max.block.ms`** when brokers are unreachable, on a request
      thread.
- [ ] **A replication factor of 1**, or `min.insync.replicas=1` with `acks=all`, is not durable.
- [ ] **Large messages** over about 1 MB fail by default.
- [ ] **Unmonitored lag.** Kafka hides a stuck consumer well: producers keep succeeding.

---

## 15. How this project uses it

| Topic | Where to read |
|---|---|
| The profile seam and why `Message.id` is a producer-generated UUID | [../KAFKA.md §1](../KAFKA.md#1-the-profile-seam) |
| Producer configuration, and why the tuning properties are ignored | [../KAFKA.md §2](../KAFKA.md#2-producer-configuration) |
| Broadcast-on-acknowledge, broker outage behaviour, delivery semantics | [../KAFKA.md §3](../KAFKA.md#3-publish-path) |
| The consumer service: configuration, error handling, offsets, shared schema | [../KAFKA.md §4](../KAFKA.md#4-the-consumer-service) |
| Running both services | [../KAFKA.md §5](../KAFKA.md#5-operating-the-kafka-profile) |
| Defects | [../BUGS.md](../BUGS.md): B10, B19, B52–B58 |

---

## 16. Self-check questions

1. **How is Kafka different from a traditional message queue?** It keeps records for a retention
   period regardless of consumption. Consumers track their own offsets, groups read independently,
   and replay is natural (§1).
2. **What guarantees ordering, and how do you choose a key?** Order holds only within a partition.
   Key by the entity whose events must stay ordered, such as `channelId` for chat (§4.1, §10.3).
3. **What do `acks=all`, idempotence and `min.insync.replicas` each contribute?** `acks=all` waits for
   every in-sync replica. Idempotence makes retries safe from duplicates and reordering.
   `min.insync.replicas` sets how many copies "all" must mean for the write to succeed (§3, §4.3).
4. **What is a consumer group, and what limits its parallelism?** Consumers sharing a group ID split
   the partitions between them, one consumer per partition, so the partition count caps parallelism
   (§5.1).
5. **When does `auto.offset.reset` apply, and why can `latest` lose data?** Only when the group has no
   valid committed offset: on its first start, or after its offsets expire. `latest` then skips every
   existing record (§5.2).
6. **How do you get effectively-once writes into PostgreSQL?** At-least-once delivery, an idempotent
   upsert keyed by an event ID, and a commit after the write. Alternatively, store offsets in the same
   database transaction (§6, §10.4).
7. **What is the dual-write problem, and what solves it?** Writing to a database and to Kafka
   separately can do one without the other. The transactional outbox solves it (§6, §10.5).
8. **What does Spring Kafka do with a failing record by default?** It makes 10 attempts with no
   back-off, then logs the record and skips it. Configure a back-off and a dead-letter recoverer as a
   bean (§12).
9. **Blocking retries versus retry topics?** Blocking retries keep order but stall the partition. Retry
   topics keep traffic flowing but give up ordering for the retried records (§7).
10. **Why can't a single topic easily power WebSocket fan-out across instances in a consumer group?**
    A group delivers each record to only one consumer. Every instance needs its own group (§10.8).
11. **What changed in Kafka 4.0?** ZooKeeper was removed (KRaft only), the KIP-848 consumer protocol
    became generally available, share groups arrived in early access, and brokers now need Java 17
    (§3, §5.4, §5.7, §11).
12. **When would you choose RabbitMQ or Redis Streams instead?** RabbitMQ for task queues with
    per-message acknowledgement, routing and priorities. Redis Streams for lightweight queues where
    Redis already exists (§13).
