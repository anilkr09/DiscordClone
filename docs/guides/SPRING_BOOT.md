# Spring Boot — Technology Guide (features used in this project)

A working knowledge guide to the Spring Boot and Spring features **that this project actually uses**,
and only those. Each section explains how the feature works, shows where the project uses it, and
notes the behaviour that caught this codebase out. WebSocket/STOMP, Redis and Kafka have their own
guides; this one covers the Spring side of each and links to them for depth.

**Scope.** The main application, on `main` (Spring Boot **3.2.2**), and the separate
[`message-consumer-service`](https://github.com/anilkr09/message-consumer-service) (Spring Boot
**4.0.5**), covered in §15. Paths below are relative to `src/main/java/com/discordclone/` unless they
name a root file.

## Contents

1. [Spring Boot in one page](#1-spring-boot-in-one-page)
2. [Startup and auto-configuration](#2-startup-and-auto-configuration)
3. [Beans and dependency injection](#3-beans-and-dependency-injection)
4. [Configuration and profiles](#4-configuration-and-profiles)
5. [The web layer: Spring MVC](#5-the-web-layer-spring-mvc)
6. [Spring Security](#6-spring-security)
7. [Data access: Spring Data JPA and Hibernate](#7-data-access-spring-data-jpa-and-hibernate)
8. [Transactions](#8-transactions)
9. [Async execution, events and startup runners](#9-async-execution-events-and-startup-runners)
10. [WebSocket and STOMP messaging](#10-websocket-and-stomp-messaging)
11. [Spring Data Redis](#11-spring-data-redis)
12. [Spring for Apache Kafka (producer side)](#12-spring-for-apache-kafka-producer-side)
13. [Logging](#13-logging)
14. [Build, packaging and testing](#14-build-packaging-and-testing)
15. [The consumer service on Spring Boot 4](#15-the-consumer-service-on-spring-boot-4)
16. [Gotchas this project hit](#16-gotchas-this-project-hit)
17. [Self-check questions](#17-self-check-questions)

---

## 1. Spring Boot in one page

**Spring Framework** provides the container: dependency injection, AOP proxies, transactions,
MVC, messaging. **Spring Boot** sits on top and removes the assembly work:

| Boot adds | What it means here |
|---|---|
| **Starters** | One dependency per capability, such as `spring-boot-starter-web` or `-data-jpa`, each pulling a tested set of libraries |
| **Auto-configuration** | Beans created automatically when a library is on the classpath and you have not defined your own: `DataSource`, `EntityManagerFactory`, Jackson `ObjectMapper`, embedded Tomcat, `KafkaTemplate`, and others |
| **Externalized configuration** | `application.properties`, profile-specific files, environment variables and command-line arguments, all merged with a defined precedence |
| **Embedded server** | Tomcat runs inside the application, so the app is a single executable jar |
| **Build plugin** | `bootRun` and `bootJar` (an executable "fat" jar) |

**Versions in the main app.** Spring Boot 3.2.2 brings Spring Framework 6.1.x, Spring Security 6.2.x,
Hibernate ORM 6.4.x and Spring for Apache Kafka 3.1.x. It runs on Java 17 and the Jakarta EE 10
namespace (`jakarta.persistence`, `jakarta.validation`, `jakarta.servlet`). The 3.2 line is past the
end of its open-source support window, so upgrading within 3.x is part of the dependency work in
[IMPROVEMENTS.md](../IMPROVEMENTS.md#dependency-management--next--s).

**Starters declared** (`build.gradle`): `web`, `security`, `data-jpa`, `websocket`, `validation`,
plus `spring-kafka`, `spring-security-messaging`, the PostgreSQL driver, jjwt, and Lombok.
`spring-boot-starter-data-redis` is **missing**, although the code uses Spring Data Redis
([BUGS.md B08](../BUGS.md#b08-the-backend-does-not-compile)).

---

## 2. Startup and auto-configuration

### 2.1 `@SpringBootApplication`

```java
// DiscordCloneApplication.java
@SpringBootApplication
@EntityScan("com.discordclone.model")
@EnableJpaRepositories("com.discordclone.repository")
public class DiscordCloneApplication {
    public static void main(String[] args) { SpringApplication.run(DiscordCloneApplication.class, args); }
}
```

`@SpringBootApplication` combines three annotations:

- `@SpringBootConfiguration`, a `@Configuration`;
- `@EnableAutoConfiguration`, which applies Boot's auto-configuration classes;
- `@ComponentScan`, which scans this class's package and its sub-packages.

`@EntityScan` and `@EnableJpaRepositories` override where JPA looks for entities and repositories.
Here they point at packages that default scanning would find anyway, because they sit under
`com.discordclone`, so both annotations are redundant. They are harmless, but they become a trap if
someone later adds entities in another package and wonders why they are ignored.

### 2.2 How auto-configuration decides

Each auto-configuration class is guarded by **conditions**:

- `@ConditionalOnClass`: is the library on the classpath?
- `@ConditionalOnMissingBean`: has the application defined its own bean of this type?
- `@ConditionalOnProperty`: is a property set?

The rule of thumb is **define your own bean, and Boot's backs off**. This project relies on that in
several places, sometimes without meaning to:

| You define | Boot backs off from | Effect here |
|---|---|---|
| `SecurityFilterChain` bean | Its default chain (form login, HTTP Basic) | The JWT chain is the only one |
| A `UserDetailsService` bean | Its generated in-memory user and password | No "Using generated security password" log line |
| `StringRedisTemplate` bean (`config/RedisConfig`) | Its own `StringRedisTemplate` | Identical result; the bean is redundant |
| `ProducerFactory` and `KafkaTemplate` beans (`config/KafkaProducerConfig`, `kafka` profile) | Its producer factory, **and all `spring.kafka.producer.*` property binding** | The tuning in `application-kafka.properties` is silently ignored ([KAFKA.md §2.2](../KAFKA.md#22-application-kafkaproperties-mostly-inert)) |

To see which auto-configurations matched and why, start with `--debug`, or set `debug=true`. Boot
then prints its **condition evaluation report**.

---

## 3. Beans and dependency injection

### 3.1 Declaring beans

| Mechanism | Used for | Examples |
|---|---|---|
| `@Service`, `@Component`, `@Repository`, `@Controller`, `@RestController` | Classes discovered by component scanning | `MessageService`, `WebSocketPublisher`, controllers |
| `@Configuration` + `@Bean` methods | Beans that need construction logic, or classes you do not own | `SecurityConfig.passwordEncoder()`, `RedisKeyExpirationListenerConfig.redisContainer(...)` |
| Spring Data interfaces | Repositories generated at runtime | `MessageRepository extends JpaRepository<Message, String>` |

`@Repository` on Spring Data interfaces (`MemberRepository`, `FriendshipRepository`) is redundant:
Spring Data detects repository interfaces on its own.

### 3.2 Injection styles

- **Constructor injection** through Lombok `@RequiredArgsConstructor` on `final` fields is the
  dominant style (`MessageService`, `UserStatusServiceImpl`). Spring calls the single constructor;
  no `@Autowired` is needed.
- **Explicit constructors** (`SecurityConfig`, `WebSocketConfig`) behave the same way. Some
  (`FriendController`, `FriendshipServiceImpl`, `UserServiceImpl`) also carry `@Autowired`, which is
  redundant on a class with a single constructor.
- **Field injection** with `@Autowired` remains in `CustomUserDetailsService`. It works, but it hides
  dependencies and makes the class harder to construct in tests.

### 3.3 How Spring picks a bean

1. **By type.** Find beans assignable to the parameter type.
2. **If several match:** a `@Primary` bean wins, then a `@Qualifier`, and finally Spring compares the
   **parameter name** with the bean names.
3. **If none match, or several still do,** startup fails.

This project shows both outcomes:

- `@Primary` on `KafkaProducerConfig.kafkaTemplate()` marks it the preferred template. Only one
  template exists in that profile, so the annotation has no effect.
- `UserService` (a concrete `@Service`) and `UserServiceImpl extends UserService` (also a `@Service`)
  are **two beans of the same type**. Injection works only because every constructor parameter is
  named `userService`, which matches the first bean's name. Parameter names exist at runtime only
  because the Spring Boot Gradle plugin compiles with `-parameters`. Rename one parameter and the
  application fails with `NoUniqueBeanDefinitionException`
  ([BUGS.md B48](../BUGS.md#b48-two-userservice-beans)).

### 3.4 Lifecycle callbacks

`JwtService` uses `@PostConstruct` to build its signing key after `@Value` fields are injected:

```java
@Value("${app.jwt.secret}") private String jwtSecret;
private Key key;

@PostConstruct
public void init() { this.key = Keys.hmacShaKeyFor(jwtSecret.getBytes()); }
```

Field injection with `@Value` happens *after* construction, so the key cannot be built in the
constructor. Constructor-injected values, or `@ConfigurationProperties`, would allow a `final` key.

### 3.5 Proxies, and why self-invocation bypasses them

`@Transactional` and `@Async` work through **proxies**. Spring wraps the bean, and the proxy adds the
behaviour around each call. A call from one method of a bean to another method of **the same bean**
(`this.persistLastSeen(...)`) never goes through the proxy, so the annotation is ignored. That is
why `persistLastSeen` runs synchronously when `resetPresence` calls it
([BUGS.md B42](../BUGS.md#b42-async-is-bypassed-by-self-invocation)). Private methods are never
proxied either.

---

## 4. Configuration and profiles

### 4.1 Where values come from

Highest precedence first:

1. command-line arguments (`--server.port=9090`);
2. OS environment variables, through *relaxed binding*: `SPRING_DATASOURCE_URL` sets
   `spring.datasource.url`, and `SPRING_PROFILES_ACTIVE` sets the active profiles;
3. `application-{profile}.properties` for each active profile;
4. `application.properties`.

Because environment variables override files, secrets can move out of
`src/main/resources/application.properties` without code changes. Today the database password, the
Redis password and the JWT secret are all committed there.

### 4.2 Properties this project relies on

| Property | Effect |
|---|---|
| `spring.profiles.active=local` | Default profile; overridden by `SPRING_PROFILES_ACTIVE=kafka` |
| `spring.datasource.url/username/password` | Boot builds a **HikariCP** pool (10 connections by default) |
| `spring.jpa.hibernate.ddl-auto=update` | Hibernate alters the schema at startup (§7.6) |
| `spring.jpa.open-in-view=false` | Disables the open-session-in-view filter (§7.4) |
| `spring.jpa.show-sql`, `spring.jpa.properties.hibernate.format_sql` | SQL logging. `spring.jpa.properties.*` passes any setting straight to Hibernate |
| `server.port=8080` | Embedded Tomcat port |
| `spring.data.redis.*` | Redis host, port, password, timeout |
| `logging.level.*` | Per-package log levels (§13) |
| `app.jwt.*` | Custom properties, read with `@Value` |

**Inert properties.** These are set but do nothing:

- `spring.websocket.*`, which is not a Spring Boot property;
- `spring.h2.*`, because there is no H2 dependency;
- `spring.cache.*`, because nothing enables caching;
- `app.kafka.enabled`, which nothing reads;
- everything in `application1.properties`, a file name Boot never loads.

Boot does not warn about unknown keys in `.properties` files, so a typo or an obsolete key fails
silently. `@ConfigurationProperties` classes with validation catch this for your own keys.

### 4.3 Profiles

- `application-local.properties` and `application-kafka.properties` load only when that profile is
  active.
- `@Profile("kafka")` and `@Profile("local")` decide **which beans exist**. That is the mechanism
  behind the messaging seam: exactly one `MessageEventPublisher` and one `MessagePersistenceService`
  is created per profile ([KAFKA.md §1](../KAFKA.md#1-the-profile-seam)).

```java
@Service @Profile("local") public class LocalMessageEventPublisher implements MessageEventPublisher { … }
@Service @Profile("kafka") public class KafkaMessageEventPublisher implements MessageEventPublisher { … }
```

If both profiles, or neither, are active, startup fails, with either two candidates or none for
`MessageEventPublisher`.

---

## 5. The web layer: Spring MVC

### 5.1 The request flow

```
HTTP request → Tomcat → servlet filters (Spring Security's chain, §6)
             → DispatcherServlet → HandlerMapping finds the @RequestMapping method
             → argument resolvers (@PathVariable, @RequestBody → Jackson, Pageable, @AuthenticationPrincipal …)
             → controller method → return value → HttpMessageConverter (Jackson) → response
             → on exception: @ExceptionHandler in @ControllerAdvice → else /error
```

### 5.2 Controllers and argument binding

| Feature | Example in this project |
|---|---|
| `@RestController` + `@RequestMapping` | `@RequestMapping("/api/messages")` on `MessageController` |
| `@GetMapping`, `@PostMapping`, `@PutMapping`, `@DeleteMapping` | All controllers |
| `@PathVariable` | `@PathVariable Long channelId` |
| `@RequestParam` with defaults | `InviteController.createInvite(@RequestParam(defaultValue = "10") int maxUses, …)` |
| `@RequestBody` (Jackson) | `@Valid @RequestBody RegistrationRequest request` |
| `ResponseEntity` builders | `ResponseEntity.ok(...)`, `.noContent()`, `.badRequest()`, `.status(HttpStatus.CONFLICT)` |
| `HttpServletRequest` | `InviteController` builds the invite URL from the request |

`@RestController` is `@Controller` plus `@ResponseBody`: return values are written as JSON rather
than resolved as view names.

**Path-template precedence.** `UserController` and `UserStatusController` share `/api/users`. When
`/friends/status` (all literal segments) and `/{userId}/status` (a template) both match a request,
Spring prefers the more specific, literal mapping. That keeps the overlap working, but it is fragile.

### 5.3 JSON with Jackson

Boot auto-configures the `ObjectMapper` used by MVC, and also by STOMP message conversion. Two of its
defaults matter here:

- **Unknown properties are ignored on input.** `@JsonIgnoreProperties(ignoreUnknown = true)` on
  `StatusUpdatePayload` is therefore redundant under Boot.
- **Dates are written as ISO-8601 strings**, not timestamps. A `LocalDateTime` carries no zone, which
  is the root of the time-zone bug ([BUGS.md B20](../BUGS.md#b20-message-times-are-wrong-outside-utc)).

Annotations used:

- `@JsonIgnore` on `UserPrincipal.password` and `Friendship.sender/receiver`;
- `@JsonBackReference` on `Member.user/server`, to stop serialization cycles (`Message` imports it but
  does not use it);
- `@JsonCreator`/`@JsonValue` on `UserStatus`, for case-insensitive parsing, and `@JsonCreator` on
  `StatusUpdatePayload`'s constructor.

Controllers that return **JPA entities** serialize every getter, which is
how `User.password` leaks ([BUGS.md B01](../BUGS.md#b01-password-hashes-are-returned-by-the-api)). Binding
request bodies straight into entities enables mass assignment (B02, B05).

### 5.4 Pagination

```java
@GetMapping("/channels/{channelId}")
public ResponseEntity<Page<MessageResp>> getChannelMessages(
        @PathVariable Long channelId,
        @PageableDefault(page = 0, size = 20, sort = "timestamp") Pageable pageable) { … }
```

- Boot's Spring Data web support resolves `Pageable` from `?page=&size=&sort=`, with
  `@PageableDefault` supplying the defaults.
- A repository method that takes a `Pageable` and returns `Page<T>` runs the data query **plus a
  count query**.
- `Page.map(MessageResp::fromEntity)` converts the content while keeping the paging metadata.
- Serializing `PageImpl` directly to JSON works, but its structure is not a stable API. Later Spring
  Data versions warn about it and offer a DTO-based page format.

### 5.5 Validation

`spring-boot-starter-validation` brings Hibernate Validator. Constraints on request DTOs
(`@NotBlank`, `@Size`, `@Email` on `RegistrationRequest`, `ChannelPayload`, `FriendRequestPayload`)
are enforced when the parameter is marked `@Valid`. A failure throws
`MethodArgumentNotValidException`, which `GlobalExceptionHandler` turns into a 400 with field errors.
`@MessageMapping` payloads and most request DTOs are not validated
([BUGS.md B45](../BUGS.md#b45-missing-input-validation)).

### 5.6 Error handling

`exception/GlobalExceptionHandler` maps exception types to statuses:

| `@ExceptionHandler` for | Status |
|---|---|
| `ResourceNotFoundException`, `NoHandlerFoundException` | 404 |
| `ConflictException`, `DuplicateResourceException`, `DataIntegrityViolationException` | 409 |
| `BadCredentialsException`, `UnauthorizedException`, jjwt exceptions, `JwtAuthenticationException` | 401 |
| `MethodArgumentNotValidException`, `HttpMessageNotReadableException`, `MethodArgumentTypeMismatchException` | 400 |
| `HttpRequestMethodNotSupportedException` | 405 |
| `MessageDeliveryException` | 503 |
| `RuntimeException` (catch-all) | 500, echoing `ex.getMessage()` |
| `Exception` (catch-all) | 500, "Something went wrong" |

- **Most specific match wins.** For a `ConflictException`, the `ConflictException` handler is chosen
  over `RuntimeException`, because Spring picks the handler whose declared type is closest in the
  exception's class hierarchy.
- **`@ResponseStatus` on exception classes is never consulted here.** `ConflictException` and
  `UnauthorizedException` carry it, but `@ResponseStatus` only applies when **no** `@ExceptionHandler`
  handles the exception. Both have explicit handlers, and every other exception falls into a
  catch-all, so the annotations are dead.
- **Bare `RuntimeException`s become 500s** with their message echoed to the client
  ([BUGS.md B29](../BUGS.md#b29-errors-map-to-the-wrong-http-status)).
- **An unknown URL returns 500, not 404.** Boot maps static resources at `/**`, so a request that no
  controller matches is handled by `ResourceHttpRequestHandler`. Since Spring Framework 6.1 (Boot
  3.2), that handler **throws** `NoResourceFoundException` instead of sending a 404. When a
  `@ControllerAdvice` with exception handlers exists, Spring routes that exception to it. It is a
  `ServletException`, not a `RuntimeException`, so it lands in the `Exception` catch-all and an
  authenticated `GET /api/nonexistent` gets a 500. The `NoHandlerFoundException` handler never fires,
  because the resource handler matches first. Adding an `@ExceptionHandler(NoResourceFoundException.class)`
  that returns 404 fixes it. This follows from the Spring Framework 6.1 source; it was not observed
  in a running instance, because the backend does not currently compile (B08).
- **Filter-level errors are handled elsewhere.** An exception that escapes a servlet filter never
  reaches `@ControllerAdvice`. The container forwards to `/error` (an *ERROR dispatch*), where Boot's
  `BasicErrorController` renders a JSON error. That is why `SecurityConfig` permits
  `DispatcherType.ERROR`: Spring Security 6 secures every dispatch type, and without it an error
  response would itself be rejected as 401 or 403.

---

## 6. Spring Security

### 6.1 Architecture

```
Tomcat filter chain
  └─ DelegatingFilterProxy("springSecurityFilterChain")
       └─ FilterChainProxy
            └─ SecurityFilterChain (this app has one)
                 CorsFilter → … → JwtAuthenticationFilter → UsernamePasswordAuthenticationFilter
                 → … → ExceptionTranslationFilter → AuthorizationFilter
```

Spring Security is a chain of servlet filters. The `AuthorizationFilter` at the end applies the
`authorizeHttpRequests` rules, using the `Authentication` that earlier filters placed in the
`SecurityContextHolder`.

### 6.2 The configuration used

```java
// security/SecurityConfig.java (abridged); the class is @Configuration @EnableWebSecurity @EnableMethodSecurity
http.cors(cors -> cors.configurationSource(corsConfigurationSource()))
    .csrf(AbstractHttpConfigurer::disable)
    .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
    .headers(h -> h.frameOptions(f -> f.sameOrigin()))
    .authorizeHttpRequests(auth -> auth
        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
        .requestMatchers("/h2-console/**").permitAll()
        .requestMatchers("/", "/ws/**", "/error", "/health", "/api/auth/**").permitAll()
        .anyRequest().authenticated())
    .addFilterBefore(jwtAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class);
```

| Setting | Why |
|---|---|
| `SessionCreationPolicy.STATELESS` | No `HttpSession`; every request authenticates from its bearer token |
| CSRF disabled | CSRF attacks rely on the browser sending ambient credentials (cookies). Bearer tokens in an `Authorization` header are not sent automatically, so CSRF protection adds nothing. It would be needed again if the token moved to a cookie |
| CORS through `CorsConfigurationSource` | Allows `http://localhost:*` and the Vercel origin, with credentials. Origin *patterns* are required, because a literal `*` cannot be combined with credentials |
| `OPTIONS` permitted | CORS pre-flight requests carry no token |
| `/ws/**` permitted | The WebSocket handshake cannot carry a token; STOMP authenticates at `CONNECT` instead (§10) |
| `frameOptions(sameOrigin)` and the `/h2-console/**` permit | Added for the H2 console, which is not on the classpath, so both are dead |
| `WebSecurityCustomizer` ignoring `/error` | Removes `/error` from Spring Security entirely. Spring Security logs a warning recommending `permitAll` instead, which this config also does |
| `@EnableMethodSecurity` | Enables `@PreAuthorize`, but nothing uses it |

### 6.3 Authentication: login and per-request

**Login and registration** (`AuthController`; registration authenticates the new user straight
away):

```java
Authentication auth = authenticationManager.authenticate(
        new UsernamePasswordAuthenticationToken(username, rawPassword));
```

The `AuthenticationManager` comes from `AuthenticationConfiguration`. Because the context has exactly
one `UserDetailsService` bean (`CustomUserDetailsService`) and a `PasswordEncoder` bean
(`BCryptPasswordEncoder`, strength 10 by default), Spring Security wires a `DaoAuthenticationProvider`
automatically. It loads the user, compares the BCrypt hash, and throws `BadCredentialsException` on
mismatch, which `GlobalExceptionHandler` maps to 401. The seeded `testuser` fails this check, because
`DataInitializer` stores its password unhashed
([BUGS.md B35](../BUGS.md#b35-the-seeder-creates-an-unusable-account-and-server)).
`AuthController` then stores the result in `SecurityContextHolder`, which has no lasting effect: with
`STATELESS` sessions, nothing saves the context after the request ends.

**Every other request** (`JwtAuthenticationFilter extends OncePerRequestFilter`):

1. Read `Authorization: Bearer …`; validate the token; load the user by the ID in its subject.
2. Put a `UsernamePasswordAuthenticationToken` into `SecurityContextHolder`.
3. On any failure, log it and continue unauthenticated. `.anyRequest().authenticated()` then rejects
   protected routes.

### 6.4 Reading the current user

| Where | How |
|---|---|
| MVC controllers | `@AuthenticationPrincipal UserPrincipal user` (Channel, Server and Invite controllers) |
| MVC, the manual way | `SecurityContextHolder.getContext().getAuthentication()` (Friend and UserStatus controllers) |
| STOMP handlers | `SimpMessageHeaderAccessor.getUser()`, the principal bound to the WebSocket session (§10) |

`SecurityContextHolder` is **thread-local** by default. It is empty on `@Async` threads and on STOMP
handler threads, which is why STOMP code reads the session's principal instead. `@CurrentUser`, a
meta-annotation over `@AuthenticationPrincipal`, is defined but unused.

### 6.5 A Boot detail: filter beans are registered twice

`jwtAuthenticationFilter()` is a `@Bean` of type `Filter`. Boot automatically registers **every
`Filter` bean** with the servlet container, in addition to its place in the security chain.
`OncePerRequestFilter` records that it has already run on the request, so the second invocation is
skipped and the double registration is harmless here. A filter that is not a `OncePerRequestFilter`
would run twice. The usual fix is to stop exposing the filter as a bean, or to disable its container
registration with a `FilterRegistrationBean`.

---

## 7. Data access: Spring Data JPA and Hibernate

### 7.1 What Boot sets up

From `spring.datasource.*` and the starter, Boot creates:

- a HikariCP `DataSource`;
- an `EntityManagerFactory` (Hibernate);
- a `JpaTransactionManager`;
- Spring Data repository implementations.

The PostgreSQL driver is on the classpath; the MySQL connector is declared but unused.

### 7.2 Entity mapping features used

| Feature | Where |
|---|---|
| `@Entity`, `@Table(uniqueConstraints = …)` | All entities; `Channel` has a unique `(server_id, name)` |
| `@Id` with `@GeneratedValue(strategy = IDENTITY)` | `User`, `Server`, `Channel`, `Friendship`, … |
| **Assigned `String` ID** (no generator) | `Message` (UUID set in the service) and `Invite` (code) |
| `@EmbeddedId` + `@Embeddable` + `@MapsId` | `Member` with composite key `MemberId(userId, serverId)` |
| `@ManyToOne(fetch = LAZY)` / `EAGER`, `@JoinColumn` | `Message.sender/channel` (lazy); `Server.owner` (eager) |
| `@OneToOne` | `DmChannel.channel`, an entity that is never used |
| `@Enumerated(EnumType.STRING)` | `ChannelType`, `Role`, `FriendshipStatus`, `UserStatus`. Strings survive enum reordering; `ORDINAL` would not |
| `@Column(nullable, unique, columnDefinition = "TEXT")` | `Message.content` |
| `@PrePersist` / `@PreUpdate` | `Friendship` sets `createdAt` and `updatedAt` |

An assigned ID has a side effect: Spring Data's `save()` cannot tell a new entity from an existing
one, so it calls `merge`, which `SELECT`s first. That turns `save()` with a client-supplied ID into an
update of an existing row ([BUGS.md B02](../BUGS.md#b02-any-account-can-be-overwritten-via-post-apiusers)).

### 7.3 Repositories and queries

| Feature | Example |
|---|---|
| CRUD from `JpaRepository<T, ID>` | `findById`, `save`, `delete`, `existsById`, `findAll` |
| **Derived queries** from method names | `findByUsername`, `existsByEmail`, `findByChannelOrderByTimestampDesc(Channel, Pageable)`, `findBySenderAndStatus` |
| `@Query` JPQL with `@Param` | `FriendshipRepository.findFriends` (a `CASE WHEN` select), `existsFriendship` (a `COUNT(f) > 0` boolean) |
| `@Query` with named parameters but **no `@Param`** | `MemberRepository.findServersByUserId(Long userId)`. This works only because `-parameters` keeps parameter names at runtime (§3.3) |
| Paging | `Page<Message> findByChannelOrderByTimestampDesc(Channel, Pageable)` |
| `@EntityGraph(attributePaths = "owner")` | `ServerRepository.findWithOwnerById`, which fetches the owner in the same query. Declared but never called |

### 7.4 Lazy loading and open-in-view

- A `LAZY` association is a proxy, loaded on first access **while the persistence context is open**,
  which normally means inside a transaction.
- Boot enables **open-session-in-view** by default, keeping the context open for the whole web
  request, and logs a warning about it. This project sets `spring.jpa.open-in-view=false`, which is the
  better practice, but it means any lazy association touched after the service method returns throws
  `LazyInitializationException`.
- Mapping to DTOs *inside* `@Transactional` service methods (`MessageService.getChannelMessages1`)
  avoids the exception. Returning an entity with a lazy association from a controller does not
  ([BUGS.md B36](../BUGS.md#b36-single-channel-endpoints-return-a-lazy-entity)).

### 7.5 Exception translation

JPA and JDBC exceptions are translated into Spring's `DataAccessException` hierarchy. A violated
unique constraint arrives as `DataIntegrityViolationException`:

- `ChannelService.getOrCreateDmChannel` and `ServerService.createServer` catch it to handle
  concurrent creates;
- `GlobalExceptionHandler` maps any that escape to 409.

### 7.6 Schema management

`ddl-auto=update` makes Hibernate compare entities with the database at startup, then **add**
missing tables, columns and constraints. It never drops or alters existing ones. It is convenient for
development and unsafe for production, and here two services run it against one schema
([BUGS.md B55](../BUGS.md#b55-two-services-manage-one-schema)). Migrations (Flyway, then `ddl-auto=validate`)
are the standard replacement.

---

## 8. Transactions

`@Transactional` (from `org.springframework.transaction.annotation`) is used on most service
methods.

| Rule | Detail |
|---|---|
| Propagation | Default `REQUIRED`: join an existing transaction, or start one |
| Rollback | On `RuntimeException` and `Error` only. **Checked exceptions commit** unless `rollbackFor` says otherwise |
| `readOnly = true` | A hint: Hibernate skips dirty checking and flushing, and the JDBC connection may be marked read-only. Used on query methods such as `getChannelMessages` |
| Proxies | Applies only to calls that come through the Spring proxy, so not to self-invocation or private methods (§3.5) |
| Scope | One database transaction. It does **not** cover a Kafka send or a WebSocket broadcast made inside it. Those happen immediately, even if the transaction later rolls back ([BUGS.md B28](../BUGS.md#b28-messages-are-broadcast-before-they-are-committed)) |

The consumer service uses `jakarta.transaction.Transactional`, the JTA annotation, instead. Spring
honours it as well, with JTA's `rollbackOn` semantics.

To run something only after a successful commit, Spring offers
`@TransactionalEventListener(phase = AFTER_COMMIT)`. This project does not use it yet; it is the
recommended fix for broadcast-before-commit.

---

## 9. Async execution, events and startup runners

### 9.1 `@EnableAsync` and `@Async`

```java
@Configuration @EnableAsync public class AsyncConfig {}

@Override @Async
public void persistLastSeen(Long userId) { … }      // UserStatusServiceImpl
```

- `@EnableAsync` makes Spring proxy beans with `@Async` methods. Calls through the proxy run on a
  `TaskExecutor`.
- A `void` `@Async` method's exceptions never reach the caller; they go to an
  `AsyncUncaughtExceptionHandler`, which by default only logs.
- The security context, MDC log context and transaction do not cross to the async thread.

**Which executor runs the task: the general rule.** Normally Boot auto-configures
`applicationTaskExecutor`, a `ThreadPoolTaskExecutor` with 8 core threads and an unbounded queue,
tuned through `spring.task.execution.pool.*`. It is also registered under the name `taskExecutor`, the
name `@Async` looks for. Boot creates it **only when the context has no other `Executor` bean**
(`@ConditionalOnMissingBean(Executor.class)` in Boot 3.2).

**What happens in this project.** `@EnableWebSocketMessageBroker` registers its own `TaskExecutor`
beans (`clientInboundChannelExecutor`, `clientOutboundChannelExecutor`, `brokerChannelExecutor`), so
Boot's executor is never created. When `@Async` resolves its default executor, it finds several
`TaskExecutor` beans and none named `taskExecutor`. It logs that at INFO level and falls back to a
**`SimpleAsyncTaskExecutor`**, which starts a new thread for every call, with no pool and no limit.
Each call through the proxy, such as `WebSocketEventListener`'s call to `persistLastSeen`, therefore
creates a thread. That is harmless at this project's scale, but it is unbounded under load. The fix
is to define the executor explicitly: a `ThreadPoolTaskExecutor` bean named `taskExecutor`, or an
`AsyncConfigurer` on `AsyncConfig`. This follows from the Spring Boot 3.2.2 and Spring Framework 6.1
source; it was not observed in a running instance, because the backend does not currently compile
(B08).

### 9.2 Application events and `@EventListener`

Spring publishes events through the `ApplicationContext`, and any bean method annotated
`@EventListener` receives the event types matching its parameter. Listeners run **synchronously on
the publishing thread** unless also marked `@Async`.

```java
// websocket/listener/WebSocketEventListener.java
@EventListener public void handleSessionConnected(SessionConnectedEvent event)  { … }
@EventListener public void handleSessionDisconnect(SessionDisconnectEvent event) { … }
```

Spring's WebSocket support publishes `SessionConnectedEvent`, `SessionSubscribeEvent`,
`SessionUnsubscribeEvent` and `SessionDisconnectEvent`. The disconnect event can be published more
than once for a session, so listeners must be idempotent.

### 9.3 `CommandLineRunner`

`DataInitializer` declares a `CommandLineRunner` bean. Boot runs every runner once, after the context
has started and before the application is reported ready. That makes it suitable for seed data,
though here the seeding bypasses the service layer (§6.3).

---

## 10. WebSocket and STOMP messaging

The full protocol and Spring internals are in [STOMP_WEBSOCKET.md](STOMP_WEBSOCKET.md). The Spring
features this project uses:

| Feature | Where |
|---|---|
| `@EnableWebSocketMessageBroker` + `WebSocketMessageBrokerConfigurer` | `config/WebSocketConfig` |
| `StompEndpointRegistry.addEndpoint("/ws").setAllowedOriginPatterns("*")` | `/ws` endpoint, open to all origins |
| `MessageBrokerRegistry.enableSimpleBroker("/topic", "/queue")`, `setApplicationDestinationPrefixes("/app")`, `setUserDestinationPrefix("/user")` | In-memory broker and prefixes |
| `ChannelRegistration.interceptors(...)` with a `ChannelInterceptor` | `WebSocketAuthInterceptor` authenticates `CONNECT` and checks `SUBSCRIBE`/`SEND` |
| `StompHeaderAccessor.setUser(auth)` | Binds the principal to the STOMP session |
| `addArgumentResolvers(new AuthenticationPrincipalArgumentResolver())` | Enables `@AuthenticationPrincipal` in `@MessageMapping` methods (from `spring-security-messaging`); the handlers use `SimpMessageHeaderAccessor` instead |
| `@MessageMapping` with `@Payload` and `SimpMessageHeaderAccessor` | `MessageController.sendMessage`, `StatusWebSocketController` |
| `SimpMessagingTemplate.convertAndSend` / `convertAndSendToUser` | All publishers |
| `@MessageExceptionHandler` + `@SendToUser("/queue/errors")` in a `@ControllerAdvice` | `WebSocketExceptionHandler` |
| `SessionConnectedEvent` / `SessionDisconnectEvent` | §9.2 |

Boot's role here is small. It supplies the `ObjectMapper` for message conversion; there is no
`spring.websocket.*` property support, which is why those keys in `application.properties` are
inert.

---

## 11. Spring Data Redis

Redis concepts are in [REDIS.md](REDIS.md). The Spring features this project uses:

| Feature | Where |
|---|---|
| `spring.data.redis.*` properties → Boot's `RedisConnectionFactory` (Lettuce) | Requires `spring-boot-starter-data-redis`, which is missing |
| `StringRedisTemplate` bean | `config/RedisConfig`; `opsForValue().set(key, value, Duration)`, `get`, `multiGet`, `delete` in `UserStatusServiceImpl` |
| `RedisMessageListenerContainer` + `PatternTopic("__keyevent@*__:expired")` | `config/RedisKeyExpirationListenerConfig` |
| `MessageListenerAdapter(delegate, "handleMessage")` | Calls `PresenceExpirationListener.handleMessage(String)` by reflection, converting the message body to a `String` |

`spring.cache.type=redis` would select Redis for Spring's cache abstraction, but caching is not
enabled (`@EnableCaching` is absent), so the setting does nothing.

---

## 12. Spring for Apache Kafka (producer side)

Kafka concepts are in [KAFKA.md](KAFKA.md). The main application only produces:

| Feature | Where |
|---|---|
| `DefaultKafkaProducerFactory` with an explicit config map | `config/KafkaProducerConfig` (`@Profile("kafka")`) |
| `org.springframework.kafka.support.serializer.JsonSerializer` | Value serializer; writes `__TypeId__` type headers |
| `KafkaTemplate.send(topic, key, value)` → `CompletableFuture` + `whenComplete` | `KafkaMessageEventPublisher`; the callback runs on the producer's network thread |
| Profile-gated beans | `@Profile("kafka")` on the config, the publisher and `NoOpMessagePersistenceService` |

Under the `local` profile, `spring-kafka` is still on the classpath, so Boot creates its own
(unused) `KafkaTemplate`. Producers connect lazily, so no broker is needed.

---

## 13. Logging

- Boot defaults to **SLF4J with Logback**, from `spring-boot-starter-logging`, which every starter
  pulls in.
- `logging.level.<package>=LEVEL` sets levels per package. Here:
  - `com.discordclone` and `DispatcherServlet` log at `DEBUG`;
  - `org.hibernate.SQL` at `DEBUG`, together with `show-sql=true`, which prints SQL twice;
  - `CommonsRequestLoggingFilter` at `DEBUG`, which has no effect, because no such filter bean is
    defined.
- Loggers come from Lombok's `@Slf4j` (`log`) or `LoggerFactory.getLogger(...)` (`logger`). Both are
  used, sometimes in the same class.
- Log parameters with placeholders (`log.info("… {}", value)`) rather than string concatenation.
  Never log credentials: the login request is logged with its password
  ([BUGS.md B06](../BUGS.md#b06-plaintext-passwords-are-written-to-the-log)).

---

## 14. Build, packaging and testing

### 14.1 Gradle plugins

| Plugin | Role |
|---|---|
| `org.springframework.boot` (3.2.2) | Adds `bootRun` and `bootJar`; compiles with `-parameters`; resolves the main class |
| `io.spring.dependency-management` | Imports Boot's BOM, so starters and managed libraries need no version numbers |
| `jacoco`, `pmd`, `org.sonarqube` | Coverage, static analysis, SonarCloud |

- **`bootJar`** produces an executable fat jar: application classes under `BOOT-INF/classes`,
  dependencies under `BOOT-INF/lib`, and Boot's launcher as the `Main-Class`, which starts the
  `Start-Class` named in the manifest.
- `jar { enabled = false }` disables the plain jar, so `build/libs/` holds exactly one jar. The
  `Dockerfile`'s `COPY build/libs/*.jar` depends on that.
- `./gradlew bootRun` runs the application from the classes directory, with no jar. Pass
  `SPRING_PROFILES_ACTIVE` in the environment to choose the profile.

### 14.2 Testing

`spring-boot-starter-test` brings JUnit 5, Mockito, AssertJ, Hamcrest, JsonPath and Spring's test
support. This project uses only part of it:

- The six test classes are **plain Mockito unit tests** (`@ExtendWith(MockitoExtension.class)`,
  `@Mock`, `@InjectMocks`). They start no Spring context.
- `spring-security-test` is declared but unused.
- Spring's test slices are not used: `@WebMvcTest` for controllers with security, `@DataJpaTest` for
  repositories, `@SpringBootTest` for full integration. A `@WebMvcTest` per controller would have
  caught the authorization gaps in [BUGS.md](../BUGS.md).

`@InjectMocks` constructs the class through its largest constructor and passes `null` for
dependencies it has no mock for. That is how `MessageServiceTest` ended up with a `null` publisher
([BUGS.md B09](../BUGS.md#b09-two-test-classes-do-not-compile)).

---

## 15. The consumer service on Spring Boot 4

`message-consumer-service` uses a smaller set of features on a newer Boot line:

| Feature | How it is used |
|---|---|
| **Modular starters** (Boot 4) | `spring-boot-starter-kafka`, `-data-jpa` and `-validation`, each with a matching test starter (`spring-boot-starter-kafka-test`, `-data-jpa-test`, `-validation-test`), instead of a bare `spring-kafka` dependency |
| No web starter | No embedded server, so `server.port=8081` has no effect and there is no HTTP endpoint |
| Boot's `kafkaListenerContainerFactory` | Built from `spring.kafka.consumer.*` and `spring.kafka.listener.*`: `ack-mode=manual`, `concurrency=2` |
| `@EnableKafka` + `@KafkaListener(topics = "message-events")` | `MessageKafkaConsumer`, with an `Acknowledgment` parameter for manual commits |
| `ErrorHandlingDeserializer` wrapping `JsonDeserializer`, configured in properties | Malformed records go to the error handler instead of crashing the consumer |
| `spring.kafka.consumer.properties.spring.json.*` | Passes raw settings to the deserializer: ignore the producer's `__TypeId__` header (`use.type.headers=false`) and always map to the consumer's own `MessageResponse` (`value.default.type`) |
| `enable-auto-commit=false`, `auto-offset-reset=latest` | Offsets are committed only by `acknowledge()`; a new group starts at the end of the topic ([BUGS.md B54](../BUGS.md#b54-latest-offset-reset-loses-messages)) |
| A custom container factory method **without `@Bean`** | Never called, so the intended back-off and dead-letter handling never applies ([BUGS.md B52](../BUGS.md#b52-the-consumer-drops-messages-it-fails-to-save)) |
| Spring Data JPA, with its own entity copies and `ddl-auto=update` | Shares the main app's schema (B55) |
| `jakarta.transaction.Transactional` | Honoured by Spring (§8) |
| `@SpringBootTest` `contextLoads` | Starts the full context, so it needs live Kafka and PostgreSQL |
| Gradle `application` plugin | Its slash-separated `mainClass` becomes Boot's `Start-Class` (B57) |

Spring Kafka 4, which Boot 4 uses, prefers Jackson 3 classes (`JacksonJsonSerializer`/`Deserializer`).
The Jackson 2 `JsonSerializer`/`JsonDeserializer` used in both services are deprecated but still
functional.

---

## 16. Gotchas this project hit

| Spring behaviour | Consequence here | Reference |
|---|---|---|
| A factory method without `@Bean` is just a method | The consumer's error handling never applies | B52 |
| Defining your own bean disables Boot's, and its property binding | Kafka producer tuning ignored | [KAFKA.md §2.2](../KAFKA.md#22-application-kafkaproperties-mostly-inert) |
| Two beans of one type resolve by parameter name, thanks to `-parameters` | Fragile `UserService` injection | B48 |
| `@Async`/`@Transactional` ignore self-invocation | `persistLastSeen` runs synchronously from `resetPresence` | B42 |
| `open-in-view=false` plus returning lazy entities | Serialization fails outside the transaction | B36 |
| Returning or binding entities in controllers | Password hashes leak; mass assignment | B01, B02, B05 |
| A catch-all `@ExceptionHandler(RuntimeException)` | Business errors become 500s; `@ResponseStatus` ignored | B29 |
| Spring 6.1 throws `NoResourceFoundException` for unmatched paths, and a catch-all `@ExceptionHandler(Exception)` catches it | Unknown URLs return 500 instead of 404 | §5.6 |
| Boot's task executor backs off when any `Executor` bean exists, and the WebSocket broker defines three | `@Async` runs on an unbounded thread-per-call `SimpleAsyncTaskExecutor` | §9.1 |
| Transactions do not cover side effects | Broadcast before commit | B28 |
| Unknown properties are silently ignored | Several inert settings | B50 |
| `ddl-auto=update` in two services | Schema drift | B55 |
| `@InjectMocks` passes `null` for unmocked constructor arguments | Stale test with a `null` publisher | B09 |
| Filter beans auto-registered with the container | Harmless here thanks to `OncePerRequestFilter` | §6.5 |

---

## 17. Self-check questions

1. **What does `@SpringBootApplication` consist of, and what does auto-configuration do?**
   `@SpringBootConfiguration`, `@EnableAutoConfiguration` and `@ComponentScan`. Auto-configuration
   creates beans conditionally, based on the classpath, properties, and whether you defined your own
   (§2).
2. **Why were the `spring.kafka.producer.*` settings ignored?** The project defines its own
   `ProducerFactory`. Boot's factory is `@ConditionalOnMissingBean`, so Boot backs off, and with it the
   property binding (§2.2).
3. **How does Spring choose between two beans of the same type?** `@Primary`, then `@Qualifier`, then
   the parameter name against bean names. If still ambiguous, startup fails (§3.3).
4. **Why doesn't `@Transactional` or `@Async` work on a call from the same class?** They are applied
   by a proxy, and a self-call bypasses the proxy (§3.5).
5. **What decides which profile's beans exist?** The active profiles, from `spring.profiles.active` or
   `SPRING_PROFILES_ACTIVE`, matched against `@Profile` and profile-specific property files (§4.3).
6. **Walk through an authenticated REST request.** Security filter chain, then `JwtAuthenticationFilter`
   sets the `SecurityContext`, then `AuthorizationFilter` checks the rules, then `DispatcherServlet`,
   the controller, and Jackson (§5.1, §6).
7. **Why is CSRF protection disabled, and when would that be wrong?** The client sends tokens in a
   header, so a browser does not attach them automatically. It would be wrong if authentication moved
   to cookies (§6.2).
8. **How does login verify a password?** `AuthenticationManager`, then the auto-wired
   `DaoAuthenticationProvider`, then `CustomUserDetailsService` and a `BCryptPasswordEncoder.matches`
   comparison (§6.3).
9. **What is `LazyInitializationException`, and how does `open-in-view=false` relate to it?**
   Touching a lazy association after the persistence context has closed. With open-in-view off, the
   context closes when the transactional service method returns (§7.4).
10. **Which exceptions roll back a transaction by default?** Unchecked ones (`RuntimeException`,
    `Error`). Checked exceptions commit unless configured otherwise (§8).
11. **Why does `@ResponseStatus` on `ConflictException` have no effect here?** An `@ExceptionHandler`
    handles the exception first, and `@ResponseStatus` only applies to unhandled exceptions (§5.6).
12. **What is an ERROR dispatch, and why is it permitted in `SecurityConfig`?** It is the container
    forwarding to `/error` after an unhandled exception. Spring Security 6 secures every dispatch
    type, so without permitting it the error page itself would be rejected (§5.6).
13. **Which thread pool runs `persistLastSeen`?** None. The WebSocket broker's executor beans stop
    Boot from creating `applicationTaskExecutor`, so `@Async` falls back to `SimpleAsyncTaskExecutor`,
    one new thread per call (§9.1).
14. **Why does `GET /api/nonexistent` with a valid token return 500?** Since Spring 6.1, the static
    resource handler throws `NoResourceFoundException`. The `@ControllerAdvice` receives it, and since
    it is a checked `ServletException`, the `Exception` catch-all maps it to 500 (§5.6).
