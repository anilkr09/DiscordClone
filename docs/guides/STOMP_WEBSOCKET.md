# STOMP over WebSocket — Technology Guide

A working knowledge guide to WebSocket and to STOMP running on top of it: how each layer works, what
STOMP adds to a raw WebSocket, how Spring and `@stomp/stompjs` implement it, and when to choose
something else. Examples use this project's chat domain where that makes them concrete. For how
*this codebase* uses the technology, and what is wrong with it, see
[../WEBSOCKETS.md](../WEBSOCKETS.md).

## Contents

1. [The layers](#1-the-layers)
2. [WebSocket in depth](#2-websocket-in-depth)
3. [What a raw WebSocket leaves to you](#3-what-a-raw-websocket-leaves-to-you)
4. [STOMP in depth](#4-stomp-in-depth)
5. [STOMP versus raw WebSocket](#5-stomp-versus-raw-websocket)
6. [Spring's STOMP implementation](#6-springs-stomp-implementation)
7. [The browser client: @stomp/stompjs](#7-the-browser-client-stompstompjs)
8. [Feature scenarios](#8-feature-scenarios)
9. [Alternatives](#9-alternatives)
10. [Pitfalls checklist](#10-pitfalls-checklist)
11. [How this project uses it](#11-how-this-project-uses-it)
12. [Self-check questions](#12-self-check-questions)

---

## 1. The layers

```
┌──────────────────────────────────────────────┐
│ Application    chat messages, presence, …    │  your JSON payloads
├──────────────────────────────────────────────┤
│ STOMP          SEND / SUBSCRIBE / MESSAGE …  │  messaging semantics: routing, subscriptions, acks
├──────────────────────────────────────────────┤
│ WebSocket      text/binary frames, ping/pong │  a full-duplex message pipe
├──────────────────────────────────────────────┤
│ HTTP/1.1       one Upgrade request           │  only used to open the connection
├──────────────────────────────────────────────┤
│ TCP (+ TLS)    reliable ordered byte stream  │
└──────────────────────────────────────────────┘
```

- **WebSocket** (RFC 6455) is a *transport*. It turns one HTTP connection into a long-lived,
  bidirectional channel for discrete messages. It says nothing about what the messages mean.
- **STOMP** (Simple Text Oriented Messaging Protocol, current version 1.2) is an *application
  messaging protocol*. It defines commands such as `SUBSCRIBE` and `SEND`, named destinations, and
  acknowledgements. STOMP predates WebSocket and was designed for plain TCP; "STOMP over WebSocket"
  carries one STOMP frame per WebSocket message.
- **SockJS** is an optional *fallback transport*. It emulates a WebSocket over HTTP streaming or
  polling when a real WebSocket cannot be established. STOMP can run on SockJS exactly as on
  WebSocket.

Keeping these apart is the key to most questions about the topic. "WebSocket versus STOMP" is not a
choice between two equivalent things; it is a choice between building your own messaging protocol on
a transport, or adopting an existing one.

---

## 2. WebSocket in depth

### 2.1 The opening handshake

A WebSocket starts life as an ordinary HTTP/1.1 request that asks to switch protocols:

```http
GET /ws HTTP/1.1
Host: chat.example.com
Upgrade: websocket
Connection: Upgrade
Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==
Sec-WebSocket-Version: 13
Sec-WebSocket-Protocol: v12.stomp, v11.stomp
Origin: https://chat.example.com
```

```http
HTTP/1.1 101 Switching Protocols
Upgrade: websocket
Connection: Upgrade
Sec-WebSocket-Accept: s3pPLMBiTxaQ9kYGzzhZRbK+xOo=
Sec-WebSocket-Protocol: v12.stomp
```

- `Sec-WebSocket-Accept` is the base64 SHA-1 of the client's key concatenated with a fixed GUID. It
  proves the server understood the WebSocket handshake; it is **not** authentication.
- `Sec-WebSocket-Protocol` negotiates a **subprotocol**. `v10.stomp`, `v11.stomp` and `v12.stomp` are
  the registered names for STOMP versions.
- After the `101`, HTTP is finished. Everything that follows on that TCP connection is WebSocket
  frames.
- RFC 8441 and RFC 9220 define bootstrapping WebSockets over HTTP/2 and HTTP/3 streams, but the
  HTTP/1.1 upgrade remains the common case.

### 2.2 Frames

Each WebSocket *message* is sent as one or more *frames*:

| Field | Meaning |
|---|---|
| `FIN` | Last frame of the message. A large message can be split into a start frame plus continuation frames |
| opcode | `0x1` text (UTF-8), `0x2` binary, `0x0` continuation, `0x8` close, `0x9` ping, `0xA` pong |
| `MASK` + masking key | Frames from client to server **must** be masked with a random 4-byte key. This exists to stop malicious pages from poisoning intermediary caches, not for confidentiality; TLS (`wss://`) provides that |
| payload length | 7 bits, or 16 or 64 bits for larger payloads |

Control frames (close, ping, pong) carry at most 125 bytes and cannot be fragmented.

### 2.3 Liveness and closing

- **Ping/pong.** Either side may send a ping; the peer must answer with a pong. Servers commonly use
  this for liveness. Browsers answer pings automatically, but the JavaScript API can neither send a
  ping nor observe a pong, so browser code cannot use it to detect a dead server.
- **Closing.** A clean close is a close frame carrying a status code, echoed by the peer, followed by
  the TCP close. Common codes:

  | Code | Meaning |
  |---|---|
  | 1000 | Normal closure |
  | 1001 | Going away (page navigation, server shutdown) |
  | 1002 | Protocol error |
  | 1003 | Unsupported data type |
  | 1006 | Abnormal closure. Reserved: never sent on the wire, reported locally when the connection died without a close frame |
  | 1008 | Policy violation (for example, rejected authentication) |
  | 1009 | Message too big |
  | 1011 | Server internal error |

- **Dead connections.** A laptop that sleeps or a network that drops sends no close frame. Neither
  side learns of it until a write fails or a liveness check times out. This is why every serious
  WebSocket deployment needs heartbeats at some layer.

### 2.4 The browser API

```js
const ws = new WebSocket("wss://chat.example.com/ws", ["v12.stomp"]);
ws.onopen    = () => ws.send(JSON.stringify({ type: "hello" }));
ws.onmessage = (event) => console.log(event.data);   // string, Blob or ArrayBuffer
ws.onclose   = (event) => console.log(event.code, event.reason);
```

Constraints that shape every design built on it:

- **No custom headers on the handshake.** Only the subprotocol list can be set. Tokens therefore
  travel in the URL query string (which leaks into logs), in a cookie, or inside the first message
  after connecting. STOMP's `CONNECT` frame is exactly such a first message.
- **Cookies are sent automatically.** Combined with the fact that WebSockets are not covered by CORS,
  this enables *cross-site WebSocket hijacking*: a malicious page opens a socket to your server with
  the victim's cookies. Servers must check the `Origin` header, or authenticate with something a
  foreign page cannot obtain, such as a token in `localStorage` sent inside `CONNECT`.
- **No built-in reconnect, back-pressure, or acknowledgement.** `bufferedAmount` reports how much
  data is queued locally, and that is all.

### 2.5 Infrastructure concerns

- **Proxies must pass the upgrade.** For example, nginx needs `proxy_http_version 1.1` and the
  `Upgrade` and `Connection` headers forwarded.
- **Idle timeouts.** Many proxies and load balancers close connections that are idle for about a
  minute (nginx's `proxy_read_timeout` defaults to 60 s). Application traffic or heartbeats must flow
  more often than that.
- **Connections are stateful and long-lived.** Each costs memory and a file descriptor on the
  server, and a load balancer pins it to one instance for its whole life. Scaling out means solving
  "the user is connected to instance A, but the event happened on instance B".

---

## 3. What a raw WebSocket leaves to you

A raw WebSocket hands the application an ordered pipe of messages and nothing else. A chat
application built directly on it must invent:

| Concern | Question you have to answer yourself |
|---|---|
| Message format | How does the server know whether a message is "send chat", "join room" or "typing"? Usually a `type` field in JSON |
| Routing | Which handler processes each type? |
| Subscriptions | How does a client say "send me channel 7's messages", and how does the server remember it? |
| Fan-out | How does the server find every socket interested in channel 7? |
| Addressing users | How do you send to "user alice" when she has three tabs open? |
| Acknowledgements | How does the sender know the server processed its message? |
| Errors | How does the server report "you may not post here" in a way the client can match to a request? |
| Liveness | Who sends heartbeats, and what happens when they stop? |
| Authentication | Where does the token go, given the handshake cannot carry headers? |
| Interoperability | Can a message broker or a second backend speak this protocol? |

None of these is hard individually. Together they amount to designing, documenting and maintaining a
protocol. STOMP is a ready-made answer to most of the list.

---

## 4. STOMP in depth

### 4.1 Frame anatomy

A STOMP frame is text: a command line, header lines, a blank line, an optional body, and a NULL
octet (shown here as `^@`).

```
SEND
destination:/app/chat.send
content-type:application/json
content-length:42

{"channelId":7,"content":"hello","dm":false}^@
```

- Header values escape carriage return, line feed and colon (`\r`, `\n`, `\c`), and backslash
  (`\\`).
- `content-length` lets a body contain NULL bytes; without it, the body ends at the first NULL.
- Bodies are opaque to STOMP. JSON with `content-type:application/json` is the common choice.

### 4.2 Commands

| Client frames | Purpose |
|---|---|
| `CONNECT` / `STOMP` | Open a STOMP session; negotiate version and heart-beats; pass credentials |
| `SEND` | Send a message to a destination |
| `SUBSCRIBE` | Register interest in a destination, with a client-chosen subscription `id` |
| `UNSUBSCRIBE` | Cancel a subscription by `id` |
| `ACK` / `NACK` | Acknowledge, or reject, a received message (for non-`auto` subscriptions) |
| `BEGIN` / `COMMIT` / `ABORT` | Group sends and acks into a transaction |
| `DISCONNECT` | Close the STOMP session gracefully |

| Server frames | Purpose |
|---|---|
| `CONNECTED` | Session accepted; carries negotiated `version` and `heart-beat` |
| `MESSAGE` | A message delivered on a subscription; carries `subscription`, `message-id`, `destination` |
| `RECEIPT` | Confirms a client frame that carried a `receipt` header |
| `ERROR` | Something went wrong. The server usually closes the connection afterwards |

### 4.3 Destinations

A destination is an opaque string; its meaning belongs to the broker. Conventions differ:

| Broker | Typical destinations |
|---|---|
| Spring simple broker | Path-like strings under configured prefixes, conventionally `/topic/...` for publish-subscribe and `/queue/...` for point-to-point, plus Ant-style patterns |
| RabbitMQ STOMP plugin | `/topic/<routing.key>` (topic exchange, `.`-separated keys with `*`/`#` wildcards), `/queue/<name>`, `/exchange/<name>/<key>`, `/amq/queue/<name>` |
| ActiveMQ | `/topic/<name>`, `/queue/<name>` |

Pattern and separator rules differ between brokers, so moving from the simple broker to RabbitMQ
can require renaming destinations (`/topic/channels/7` becomes `/topic/channels.7`).

### 4.4 Subscriptions and acknowledgement modes

```
SUBSCRIBE
id:sub-0
destination:/topic/channels/7/messages
ack:client-individual

^@
```

| `ack` mode | Behaviour |
|---|---|
| `auto` (default) | The server considers the message delivered once sent. No `ACK` frames |
| `client` | The client must `ACK`. An `ACK` is **cumulative**: it acknowledges that message and every earlier unacknowledged message on the subscription |
| `client-individual` | Like `client`, but each `ACK`/`NACK` covers only one message |

In STOMP 1.2 a `MESSAGE` carries an `ack` header, and the client's `ACK`/`NACK` echoes it in an `id`
header. What happens on `NACK` (redeliver, dead-letter, discard) is up to the broker.

### 4.5 Receipts

Any client frame may carry `receipt:<id>`. The server replies with `RECEIPT` and `receipt-id:<id>`
once it has processed the frame. The spec's graceful shutdown is:

```
DISCONNECT
receipt:77

^@
```

Then the client waits for `RECEIPT` with `receipt-id:77` before closing the socket, so it knows every
earlier frame was received.

### 4.6 Transactions

```
BEGIN
transaction:tx1

^@
SEND
destination:/queue/orders
transaction:tx1

{"order":1}^@
COMMIT
transaction:tx1

^@
```

Sends and acks within a transaction take effect on `COMMIT` and are discarded on `ABORT`. Support is
broker-dependent.

### 4.7 Heart-beating

Each side declares `heart-beat:<cx>,<cy>` in `CONNECT` and `CONNECTED`: the first number is "I can
send every *cx* ms", the second "I want to receive every *cy* ms". For client-to-server beats, if
*cx* (client) or *sy* (server) is 0 there are none; otherwise they flow every `max(cx, sy)` ms, and
symmetrically for the other direction. A heart-beat is a single end-of-line. Any frame counts as
activity, so heart-beats only flow on otherwise idle connections.

Unlike WebSocket ping/pong, STOMP heart-beats are visible to browser JavaScript, so they give the
**client** a way to detect a dead server.

### 4.8 Errors

```
ERROR
message:Access denied
content-type:text/plain

You may not subscribe to /topic/channels/9/messages^@
```

An `ERROR` frame is usually fatal: the server closes the connection after sending it. Clients
therefore treat it as "reconnect and possibly re-authenticate", not as a per-request error.
Recoverable application errors are better delivered as ordinary `MESSAGE`s on an error destination
such as `/user/queue/errors`.

---

## 5. STOMP versus raw WebSocket

| Concern | Raw WebSocket | STOMP over WebSocket |
|---|---|---|
| Message meaning | Yours to define | Standard commands and headers |
| Routing on the server | Hand-written dispatch on a `type` field | By destination; in Spring, `@MessageMapping` methods |
| Subscriptions | Hand-written registry | `SUBSCRIBE`/`UNSUBSCRIBE`, managed by the broker |
| Fan-out | Loop over sockets yourself | The broker delivers to every matching subscription |
| One user, many tabs | Track sessions per user yourself | User destinations (`/user/...`) in Spring |
| Delivery confirmation | Invent it | Receipts; `ACK`/`NACK` with a real broker |
| Liveness | Ping/pong (server only), or invent your own | Heart-beats, negotiated, visible to both sides |
| Authentication | Query string, cookie, or first message by your own convention | Credentials in `CONNECT` headers, a standard place |
| External brokers | Not without a custom bridge | RabbitMQ, ActiveMQ and others speak STOMP natively |
| Overhead | Minimal; binary-friendly | Text headers per frame; fine for JSON, wasteful for tiny binary messages |
| Tooling | Generic | Mature clients in most languages; Spring support built in |

**Choose STOMP when** the traffic is pub-sub shaped (rooms, channels, notifications, presence), when
users need addressing across sessions, or when you may later want a real broker or multiple backend
instances.

**Choose raw WebSocket when** the protocol is tight and custom: binary game state at 60 Hz, a
collaborative-editing protocol with its own operation semantics, streaming audio, or a single
request/response stream where STOMP's headers and routing add nothing.

---

## 6. Spring's STOMP implementation

### 6.1 Enabling it

```java
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws").setAllowedOriginPatterns("https://chat.example.com");
        // .withSockJS() would add the HTTP fallback transports
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.setApplicationDestinationPrefixes("/app");   // → @MessageMapping methods
        registry.enableSimpleBroker("/topic", "/queue");     // → in-memory broker
        registry.setUserDestinationPrefix("/user");          // → per-user destinations
    }
}
```

### 6.2 The message flow

```
           ┌──────────────── clientInboundChannel ────────────────┐
WebSocket  │                                                      │
 session ──┤  destination /app/** ──▶ @MessageMapping methods ──┐ │
           │  destination /topic/**, /queue/** ─────────────────┼─┼──▶ broker
           │  destination /user/** ──▶ UserDestinationMessageHandler ─▶ broker
           └────────────────────────────────────────────────────┼─┘
                                                                 ▼
                                         brokerChannel (SimpMessagingTemplate sends here)
                                                                 │
                                                       SimpleBrokerMessageHandler
                                                                 │ matches subscriptions
                                                                 ▼
                                                      clientOutboundChannel ──▶ WebSocket sessions
```

- **Three channels.** `clientInboundChannel` carries frames from clients; `brokerChannel` carries
  messages from application code to the broker; `clientOutboundChannel` carries frames to clients.
  The inbound and outbound channels are backed by thread pools.
- **`StompSubProtocolHandler`** decodes WebSocket messages into STOMP frames and back, buffering a
  frame that arrives split across several WebSocket messages.
- **Interceptors** (`ChannelInterceptor`) on `clientInboundChannel` see every inbound frame. This is
  where `CONNECT` authentication usually happens.

### 6.3 Annotated handlers

```java
@Controller
public class ChatController {

    @MessageMapping("/chat.send")                    // client SENDs to /app/chat.send
    @SendTo("/topic/channels/7/messages")            // return value is broadcast here
    public ChatMessage send(ChatMessage in, Principal user) { ... }

    @MessageMapping("/typing")
    @SendToUser("/queue/acks")                       // reply only to the sender's sessions
    public Ack typing(TypingEvent e) { ... }

    @SubscribeMapping("/channels/{id}/members")       // client SUBSCRIBEs to /app/channels/7/members
    public List<Member> members(@DestinationVariable long id) { ... }   // one-time reply, no broker
}
```

`@SubscribeMapping` is a request/response over a subscription: the return value goes straight back
to that one subscriber and is not broadcast. It suits "load initial state when the view opens".
Outside controllers, `SimpMessagingTemplate.convertAndSend(destination, payload)` and
`convertAndSendToUser(user, destination, payload)` send from anywhere.

### 6.4 User destinations

Spring rewrites `/user/...` destinations so that one logical name maps to every session of one user:

1. A client in session `s1` subscribes to `/user/queue/messages`. Spring rewrites it to the
   session-specific `/queue/messages-users1` and registers that with the broker.
2. `convertAndSendToUser("alice", "/queue/messages", m)` targets `/user/alice/queue/messages`.
   `UserDestinationMessageHandler` looks up Alice's sessions in the `SimpUserRegistry` and sends one
   copy to each session-specific destination.
3. Outgoing frames carry the original `/user/queue/messages` destination, so the client sees the
   name it subscribed to.

The user is identified by `Principal.getName()`, set on the session at `CONNECT`. An unknown name
resolves to zero sessions, silently.

### 6.5 Simple broker versus broker relay

| | Simple broker | Broker relay |
|---|---|---|
| Where | In the application's JVM | External broker (RabbitMQ, ActiveMQ …) over TCP |
| STOMP coverage | A subset: per Spring's docs, it "does not support acks, receipts, and some other features" | Whatever the broker supports |
| Heart-beats | Only with a configured `TaskScheduler` | Supported, both client-to-relay and relay-to-broker |
| Multiple app instances | No: subscriptions live in one JVM's heap | Yes: the broker holds subscriptions; enable user-registry broadcast for user destinations |
| Durability | None | Durable queues, if configured |

```java
registry.enableStompBrokerRelay("/topic", "/queue")
        .setRelayHost("rabbitmq").setRelayPort(61613)
        .setClientLogin("guest").setClientPasscode("guest")
        .setUserDestinationBroadcast("/topic/unresolved-user-destination")
        .setUserRegistryBroadcast("/topic/simp-user-registry");
```

### 6.6 Heart-beats on the simple broker

```java
private TaskScheduler messageBrokerTaskScheduler;

@Autowired
public void setMessageBrokerTaskScheduler(@Lazy TaskScheduler taskScheduler) {
    this.messageBrokerTaskScheduler = taskScheduler;
}

@Override
public void configureMessageBroker(MessageBrokerRegistry registry) {
    registry.enableSimpleBroker("/topic", "/queue")
            .setHeartbeatValue(new long[] {10000, 10000})
            .setTaskScheduler(this.messageBrokerTaskScheduler);
}
```

The `@Lazy` avoids a cycle with Spring's own WebSocket configuration, which declares the scheduler.
Without a scheduler, the simple broker answers `heart-beat:0,0`, and no heart-beats flow either way.
With one, it closes sessions that send nothing for three times the agreed interval.

### 6.7 Security

Authenticate in `CONNECT`, then authorize per destination. Spring Security 6 does the second part
declaratively:

```java
@Configuration
@EnableWebSocketSecurity
public class WebSocketSecurityConfig {

    @Bean
    AuthorizationManager<Message<?>> messageAuthorizationManager(
            MessageMatcherDelegatingAuthorizationManager.Builder messages) {
        messages
            .simpTypeMatchers(SimpMessageType.CONNECT, SimpMessageType.HEARTBEAT,
                              SimpMessageType.UNSUBSCRIBE, SimpMessageType.DISCONNECT).permitAll()
            .simpDestMatchers("/app/**").authenticated()
            .simpSubscribeDestMatchers("/user/queue/**").authenticated()
            .simpSubscribeDestMatchers("/topic/channels/**").access(channelMembership())
            .anyMessage().denyAll();
        return messages.build();
    }
}
```

`@EnableWebSocketSecurity` requires a CSRF token on `CONNECT` by default, which a token-authenticated
SPA must handle deliberately. Restrict `setAllowedOriginPatterns` to your real origins; `"*"` gives up
the `Origin` check described in §2.4.

### 6.8 Limits, ordering and monitoring

| Setting (`configureWebSocketTransport`) | Default | Meaning |
|---|---|---|
| `messageSizeLimit` | 64 KB | Largest inbound STOMP message after reassembly |
| `sendTimeLimit` | 10 s | If one send to a session takes longer, the session is closed |
| `sendBufferSizeLimit` | 512 KB | If more than this is queued for one slow session, it is closed |
| `timeToFirstMessage` | 60 s | A socket that sends no STOMP frame within this time is closed |

- **Ordering.** Inbound and outbound channels are thread pools, so two frames to the same session
  can be reordered under load. `MessageBrokerRegistry.setPreservePublishOrder(true)` keeps outbound
  frames in order per session, and `StompEndpointRegistry.setPreserveReceiveOrder(true)` processes a
  session's inbound frames in arrival order. Both cost a little throughput.
- **Monitoring.** Spring logs `WebSocketMessageBrokerStats` (sessions, frames, thread-pool queues)
  every 30 minutes, and the bean can be exposed as metrics.

---

## 7. The browser client: @stomp/stompjs

```ts
import { Client } from "@stomp/stompjs";

const client = new Client({
  brokerURL: "wss://chat.example.com/ws",
  connectHeaders: { Authorization: `Bearer ${token}` },
  beforeConnect: async () => {                    // refresh the token before every (re)connect
    client.connectHeaders = { Authorization: `Bearer ${await getFreshToken()}` };
  },
  reconnectDelay: 5000,                           // automatic reconnect (default 5 s)
  heartbeatIncoming: 10000,                       // expect server activity every 10 s
  heartbeatOutgoing: 10000,
  onConnect: () => {
    client.subscribe("/topic/channels/7/messages", (msg) => render(JSON.parse(msg.body)));
    client.subscribe("/user/queue/errors", (msg) => toast(msg.body));
  },
  onStompError: (frame) => console.error(frame.headers.message),
  onWebSocketClose: (evt) => console.warn("socket closed", evt.code),
});
client.activate();

client.publish({ destination: "/app/chat.send", body: JSON.stringify(payload) });
```

- **Subscriptions do not survive a reconnect.** Re-subscribe in `onConnect`, which runs after every
  successful connect, not just the first.
- **`subscribe` returns a handle.** Keep it and call `unsubscribe()` when the view goes away. In
  React, subscribe inside `useEffect` and unsubscribe in its cleanup.
- **Acks, receipts and transactions** are available when the broker supports them:
  `message.ack()`/`message.nack()` on subscriptions with an `ack` header,
  `client.watchForReceipt(id, cb)` together with a `receipt` header, and
  `client.begin()` → `tx.commit()`/`tx.abort()`.
- **Heart-beat tolerance.** In 7.0.0, the client closes the socket when the server is silent for
  twice the agreed interval. Later releases make the multiplier configurable and add a Web Worker
  ticker for outgoing heart-beats (`heartbeatStrategy`), which resists background-tab timer
  throttling.

---

## 8. Feature scenarios

Each scenario shows which STOMP feature solves which problem.

### 8.1 Chat channel broadcast — destinations and subscriptions

*Problem:* everyone viewing channel 7 must see new messages.

- Each client: `SUBSCRIBE destination:/topic/channels/7/messages`.
- Sender: `SEND destination:/app/chat.send` with the message as JSON.
- Server: a `@MessageMapping("/chat.send")` method stores it and calls
  `convertAndSend("/topic/channels/7/messages", saved)`.
- Broker: delivers one `MESSAGE` per matching subscription.

*Raw-WebSocket equivalent:* a map of channel to sockets, maintained on join and leave messages, and
cleaned up on every disconnect path.

### 8.2 Private notifications across tabs — user destinations

*Problem:* a friend request must reach every open tab of the recipient, and only that user.

- Each tab: `SUBSCRIBE /user/queue/friends`.
- Server: `convertAndSendToUser("alice", "/queue/friends", event)`.
- Spring resolves Alice's sessions and sends one copy per tab (§6.4).

### 8.3 Initial state on open — @SubscribeMapping

*Problem:* when the members panel opens, show the current members, then live changes.

- `SUBSCRIBE /app/channels/7/members` returns the current list once, without involving the broker.
- `SUBSCRIBE /topic/channels/7/members` delivers subsequent joins and leaves.

### 8.4 "Delivered" ticks — receipts

*Problem:* show a tick once the server has received a message.

- Client: `SEND` with `receipt:msg-123`; register `client.watchForReceipt("msg-123", markSent)`.
- Server: `RECEIPT receipt-id:msg-123` after processing the frame.
- Caveat: Spring's simple broker does not support receipts. With it, send an application-level
  acknowledgement on `/user/queue/acks` instead.

### 8.5 Work that must not be lost — ack modes with a real broker

*Problem:* a moderation worker subscribes to `/queue/reports` and must not lose a report if it crashes
mid-processing.

- Subscribe with `ack:client-individual`. The broker keeps each report unacknowledged until the worker
  sends `ACK id:<ack>`, and redelivers it if the worker disconnects first.
- `NACK` hands a report back, for example for another worker. What happens next is broker policy.
- Requires a broker relay to RabbitMQ or ActiveMQ; the simple broker does not implement acks.

### 8.6 Detecting dead connections — heart-beats

*Problem:* after Wi-Fi switches networks, a client keeps "showing connected" but receives nothing.

- Negotiate `heart-beat:10000,10000`. If nothing arrives from the server for the tolerance window,
  the client closes the socket and its automatic reconnect takes over.
- On the server, a client silent for three intervals is disconnected, which frees its session and
  fires `SessionDisconnectEvent`.

### 8.7 Rejecting unauthenticated clients — CONNECT headers and ERROR

*Problem:* only logged-in users may connect.

- Client: `CONNECT` with `Authorization:Bearer <jwt>`.
- Server: a channel interceptor validates the token and binds the `Principal` to the session.
  Otherwise it rejects, and the client receives `ERROR`.
- Because `CONNECT` authenticates only once, long-lived sessions outlive their tokens unless the
  server disconnects them at expiry.

### 8.8 Scaling to several servers — broker relay

*Problem:* two app instances; Bob on instance A must see a message posted through instance B.

- With the simple broker, impossible: each instance only knows its own subscriptions.
- With `enableStompBrokerRelay` to RabbitMQ, both instances relay to one broker that holds every
  subscription. Enable user-registry broadcast so that `convertAndSendToUser` can find sessions on
  other instances.

---

## 9. Alternatives

| Technology | What it is | Choose it when |
|---|---|---|
| **Raw WebSocket** | Transport only | You need a custom or binary protocol, or minimal overhead |
| **STOMP over WebSocket** | Standard messaging protocol on WebSocket | Pub-sub chat, notifications, dashboards, especially in Spring stacks |
| **Socket.IO** | Its own protocol over Engine.IO, with HTTP long-polling fallback, rooms, acknowledgement callbacks and auto-reconnect | Node.js-centric stacks wanting batteries included. Not interoperable with plain WebSocket or STOMP clients |
| **Server-Sent Events** | One-way server-to-client text stream over plain HTTP, with automatic reconnect and `Last-Event-ID` resume | Feeds, notifications and live updates where the client only *receives*; it works through most proxies unchanged |
| **MQTT over WebSocket** | IoT pub-sub protocol with QoS 0/1/2, retained messages and "last will" | Devices and constrained networks, or when an MQTT broker already exists |
| **GraphQL subscriptions** | Subscriptions inside a GraphQL API, usually over the `graphql-ws` protocol | The API is already GraphQL |
| **WebTransport** | HTTP/3-based streams and unreliable datagrams | Low-latency media or games that benefit from unordered or unreliable delivery |
| **Long polling** | Repeated HTTP requests held open | Legacy environments only |

---

## 10. Pitfalls checklist

- [ ] **Origins:** `setAllowedOriginPatterns("*")` disables the main defence against cross-site
      WebSocket hijacking when cookies are involved.
- [ ] **Subscription authorization:** authenticating `CONNECT` is not enough. Authorize every
      `SUBSCRIBE` and `SEND` by destination.
- [ ] **Token lifetime:** a session authenticated at `CONNECT` lives on after the token expires.
- [ ] **Heart-beats:** the simple broker has none without a `TaskScheduler`, so dead connections go
      unnoticed on both sides.
- [ ] **Subscriptions in render code:** in React, subscribe in effects with an unsubscribe cleanup, or
      every render adds a subscription.
- [ ] **Re-subscribe after reconnect:** subscriptions belong to the STOMP session and die with it.
- [ ] **`ERROR` is fatal:** do not use it for per-message validation errors.
- [ ] **Message size:** a STOMP message above `messageSizeLimit` (64 KB by default) is rejected
      with an `ERROR` frame, which ends the session.
- [ ] **Slow consumers:** one slow client fills its send buffer and is disconnected by
      `sendBufferSizeLimit`/`sendTimeLimit`, not throttled.
- [ ] **Ordering:** without `setPreservePublishOrder(true)`, frames to one session may be reordered.
- [ ] **Scaling:** the simple broker pins you to a single instance.
- [ ] **Destination naming:** separators and wildcards differ between brokers; plan names before
      adopting a relay.
- [ ] **Proxies:** forward `Upgrade`/`Connection`, and keep idle timeouts above the heart-beat
      interval.

---

## 11. How this project uses it

| Topic | Where to read |
|---|---|
| Endpoint, broker and prefixes | [WEBSOCKETS.md §1](../WEBSOCKETS.md#1-choice-of-stack) |
| `CONNECT` authentication with a JWT | [WEBSOCKETS.md §3](../WEBSOCKETS.md#3-authentication) |
| Every destination, and who publishes or subscribes | [WEBSOCKETS.md §4](../WEBSOCKETS.md#4-destination-map) |
| The React client and its subscription bugs | [WEBSOCKETS.md §6](../WEBSOCKETS.md#6-browser-client) |
| Heart-beats: current state and recommendation | [WEBSOCKETS.md §6.5](../WEBSOCKETS.md#65-stomp-heart-beats) |
| Presence signals carried over STOMP | [REDIS.md §11](../REDIS.md#11-presence-after-the-corrections-how-status-is-resolved-and-broadcast) |
| Defects | [BUGS.md](../BUGS.md): B04, B11, B12, B18, B27, B30–B33 |

---

## 12. Self-check questions

1. **What does STOMP add that a raw WebSocket lacks?** Standard commands, destination-based routing,
   broker-managed subscriptions, receipts, acks, transactions, negotiated heart-beats, and a standard
   place for credentials. With a raw WebSocket you design all of that yourself (§3, §5).
2. **Why does the JWT go in the `CONNECT` frame rather than an HTTP header?** Browsers cannot set
   custom headers on the WebSocket handshake. Query strings leak into logs, and cookies invite
   cross-site hijacking.
3. **How does `convertAndSendToUser` reach a user with three tabs open?** The user registry maps the
   username to three sessions. Each session's subscription was rewritten to a session-specific queue,
   and Spring sends one copy to each (§6.4).
4. **Why can't the simple broker run on two instances?** Subscriptions live in each JVM's heap, so an
   instance cannot deliver to sessions held by another. Use a broker relay (§6.5).
5. **What is the difference between `client` and `client-individual` ack modes?** `client` acks are
   cumulative; `client-individual` acks cover one message (§4.4).
6. **Why are WebSocket ping/pong not enough for a browser?** The browser answers pings
   automatically, but JavaScript can neither send pings nor see pongs. STOMP heart-beats are visible
   to the client (§2.3, §4.7).
7. **When would you pick raw WebSocket, SSE, or MQTT over STOMP?** Raw for binary or custom protocols,
   SSE for one-way feeds, MQTT for devices (§9).
8. **What happens when a client sends a 100 KB message to a default Spring setup?** It exceeds the
   64 KB `messageSizeLimit`. Spring rejects it with an `ERROR` frame, which ends the session (§6.8).
9. **What does an `ERROR` frame do to the connection?** The server normally closes it. Treat it as
   fatal, and send recoverable errors as ordinary messages (§4.8).
10. **How do you keep a session's frames in order in Spring?**
    `MessageBrokerRegistry.setPreservePublishOrder(true)` for outbound and
    `StompEndpointRegistry.setPreserveReceiveOrder(true)` for inbound (§6.8).
