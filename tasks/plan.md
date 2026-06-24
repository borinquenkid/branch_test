# Implementation Plan: Branch GitHub User API

## Overview

A single-endpoint Spring Boot 4 / Java 25 service that accepts a GitHub username, fetches
data from two GitHub REST APIs in parallel, merges and maps the result, caches it (Caffeine
L1 + Postgres L2), and returns a shaped JSON response. No authentication required.

## Architecture Decisions

See `docs/adr/` for full rationale. Summary:

- **ADR-0001** — Spring Boot 4 / Java 25. `StructuredTaskScope` for parallel GitHub calls,
  `RestClient` for HTTP, virtual threads default-on.
- **ADR-0002** — Jackson (BOM-managed) + MapStruct. Explicit `@JsonProperty` on records,
  `java.time` for RFC 1123 date formatting.
- **ADR-0003** — Postgres only (no H2). Caffeine L1 (5 min TTL) + Postgres L2 (1 hr TTL).
  Testcontainers for integration tests, WireMock for GitHub API stubbing.
- **ADR-0004** — Gradle + Kotlin DSL.

## Dependency Graph

```
[T1] Gradle scaffold
        │
        ▼
[T2] Domain records + MapStruct mapper
        │
        ▼
[T3] GitHubClient (fetch + map — no cache, no DB)
        │
        ▼
[T4] Controller — endpoint live, data flows end-to-end
        │
        ▼                    ▼
[T5] Error handling    [T6] Cache layer (Caffeine L1 + Postgres L2)
        │                    │
        └────────┬───────────┘
                 ▼
[T7] Integration test (Testcontainers + WireMock — full stack)
                 │
                 ▼
[T8] README
```

## Package Structure

```
com.borinquenkid.branchtest
├── BranchTestApplication.java
├── client/
│   └── GitHubClient.java
├── config/
│   ├── CacheConfig.java
│   ├── JacksonConfig.java
│   └── RestClientConfig.java
├── controller/
│   └── GitHubUserController.java
├── exception/
│   ├── GitHubApiException.java
│   ├── GlobalExceptionHandler.java
│   └── UserNotFoundException.java
├── mapper/
│   └── GitHubMapper.java
├── model/
│   ├── github/
│   │   ├── GitHubRepo.java
│   │   └── GitHubUser.java
│   └── response/
│       ├── RepoResponse.java
│       └── UserResponse.java
├── repository/
│   ├── CachedUser.java
│   └── CachedUserRepository.java
└── service/
    └── GitHubUserService.java

src/main/resources/
├── application.yml
└── db/migration/
    └── V1__create_github_user_cache.sql
```

---

## Phase 1: Foundation

### Task 1: Gradle project scaffold

**Description:** Bootstrap the Gradle project so it compiles, resolves dependencies, and
boots an empty Spring Boot application. No business logic — just a green build.

**Acceptance criteria:**
- [ ] `settings.gradle.kts` declares `rootProject.name = "branch-test"`
- [ ] `build.gradle.kts` declares Java 25 toolchain, Spring Boot 4 plugin, all dependencies
      from ADRs (web, data-jpa, cache, caffeine, mapstruct + processor, flyway, postgres,
      testcontainers, wiremock)
- [ ] `.sdkmanrc` pins `java=25.0.3-tem` and `gradle=9.4.1`
- [ ] `gradlew wrapper` is committed (jar included)
- [ ] `BranchTestApplication.java` exists with `@SpringBootApplication @EnableCaching`
- [ ] `application.yml` has app name and datasource placeholder properties
- [ ] `./gradlew build` succeeds (dependency resolution + compile)

**Verification:**
- [ ] `./gradlew build` exits 0
- [ ] `./gradlew dependencies` shows all expected artifacts resolved

**Dependencies:** None

**Files:**
- `settings.gradle.kts`
- `build.gradle.kts`
- `.sdkmanrc`
- `gradlew`, `gradlew.bat`, `gradle/wrapper/*`
- `src/main/java/.../BranchTestApplication.java`
- `src/main/resources/application.yml`

**Scope:** M

---

### Task 2: Domain records and MapStruct mapper

**Description:** Define all Java records (GitHub DTOs and response DTOs) and the MapStruct
mapper that translates between them, including the `created_at` RFC 1123 reformat. This is
pure logic — no HTTP, no database, no Spring context needed.

**Acceptance criteria:**
- [ ] `GitHubUser` record captures: `login`, `name`, `avatarUrl`, `location`, `email`,
      `url`, `createdAt` — annotated with `@JsonIgnoreProperties(ignoreUnknown = true)`
- [ ] `GitHubRepo` record captures: `name`, `url` — same annotation
- [ ] `UserResponse` record matches the required JSON shape exactly (all `@JsonProperty`
      annotations explicit)
- [ ] `RepoResponse` record has `name` and `url`
- [ ] `GitHubMapper` interface maps `GitHubUser` + `List<GitHubRepo>` → `UserResponse`
      with all renames declared via `@Mapping`
- [ ] `created_at` reformat: `Instant.parse` → `ZoneOffset.UTC` →
      `DateTimeFormatter.RFC_1123_DATE_TIME`
- [ ] Unit test: mapper produces correct `user_name`, `display_name`, `avatar`,
      `geo_location` values given a known input
- [ ] Unit test: `created_at` `"2011-01-25T18:44:36Z"` maps to
      `"Tue, 25 Jan 2011 18:44:36 GMT"`
- [ ] Unit test: `null` email passes through as `null`

**Verification:**
- [ ] `./gradlew test` passes (mapper unit tests green)
- [ ] MapStruct generates `GitHubMapperImpl` — visible in `build/generated/sources/`

**Dependencies:** Task 1

**Files:**
- `src/main/java/.../model/github/GitHubUser.java`
- `src/main/java/.../model/github/GitHubRepo.java`
- `src/main/java/.../model/response/UserResponse.java`
- `src/main/java/.../model/response/RepoResponse.java`
- `src/main/java/.../mapper/GitHubMapper.java`
- `src/test/java/.../mapper/GitHubMapperTest.java`

**Scope:** M

---

### Checkpoint: Phase 1

- [ ] `./gradlew build` exits 0
- [ ] Mapper unit tests pass
- [ ] MapStruct generated source visible
- [ ] **Human review before proceeding**

---

## Phase 2: Core Data Flow

### Task 3: GitHub API client

**Description:** Implement `GitHubClient` using `RestClient` and `StructuredTaskScope` to
call both GitHub endpoints in parallel. Returns the raw GitHub DTOs; mapping happens in
the service. No cache, no database.

**Acceptance criteria:**
- [ ] `RestClientConfig` produces a `RestClient` bean with base URL
      `https://api.github.com` and `Accept: application/vnd.github+json` header
- [ ] `GitHubClient.fetch(username)` uses `StructuredTaskScope.ShutdownOnFailure` to fork
      both calls concurrently
- [ ] If `/users/{username}` returns 404, a `UserNotFoundException` is thrown
- [ ] If either call fails with a non-404 HTTP error, a `GitHubApiException` is thrown
- [ ] Unit test: happy path — both stubs return valid JSON, method returns both DTOs
- [ ] Unit test: 404 on user endpoint → `UserNotFoundException`
- [ ] Unit test: 500 from GitHub → `GitHubApiException`

**Verification:**
- [ ] `./gradlew test` — all three `GitHubClientTest` cases pass
- [ ] HTTP tests use `MockWebServer` (OkHttp) — no real network calls

**Dependencies:** Task 2

**Files:**
- `src/main/java/.../config/RestClientConfig.java`
- `src/main/java/.../client/GitHubClient.java`
- `src/main/java/.../exception/UserNotFoundException.java`
- `src/main/java/.../exception/GitHubApiException.java`
- `src/test/java/.../client/GitHubClientTest.java`

**Scope:** M

---

### Task 4: Controller — endpoint live, data flows end-to-end

**Description:** Wire `GitHubUserController` → `GitHubUserService` → `GitHubClient` →
`GitHubMapper` so a real `GET /users/{username}` request returns shaped JSON. No cache
yet — every request hits GitHub. Service and controller are thin; all logic lives in
client and mapper.

**Acceptance criteria:**
- [ ] `GET /users/{username}` returns `200` with the correct JSON shape
- [ ] `GitHubUserService.getUser(username)` calls client, maps result, returns
      `UserResponse` — no cache logic yet
- [ ] `GlobalExceptionHandler` maps `UserNotFoundException` → `404` and
      `GitHubApiException` → `502`
- [ ] `@ControllerAdvice` maps blank/null username (path variable validation) → `400`
- [ ] Unit test (MockMvc): happy path returns correct JSON shape
- [ ] Unit test (MockMvc): unknown username → `404`
- [ ] Unit test (MockMvc): blank username → `400`

**Verification:**
- [ ] `./gradlew test` — all controller and service unit tests pass
- [ ] `curl localhost:8080/users/octocat` returns the correct shape when run with a live
      GitHub connection (manual smoke test)

**Dependencies:** Task 3

**Files:**
- `src/main/java/.../service/GitHubUserService.java`
- `src/main/java/.../controller/GitHubUserController.java`
- `src/main/java/.../exception/GlobalExceptionHandler.java`
- `src/main/java/.../config/JacksonConfig.java`
- `src/test/java/.../controller/GitHubUserControllerTest.java`
- `src/test/java/.../service/GitHubUserServiceTest.java`

**Scope:** M

---

### Checkpoint: Phase 2

- [ ] `./gradlew test` exits 0 — all unit tests green
- [ ] Manual smoke: `./gradlew bootRun` + `curl localhost:8080/users/octocat` returns
      correct JSON (requires Docker for Postgres, or datasource temporarily disabled)
- [ ] **Human review before proceeding**

---

## Phase 3: Cache Layer

### Task 5: Persistence layer — Flyway + JPA + repository

**Description:** Add the `github_user_cache` table via Flyway migration, the `CachedUser`
JPA entity, and `CachedUserRepository`. This task does not wire caching into the service —
it only establishes the persistence foundation.

**Acceptance criteria:**
- [ ] `V1__create_github_user_cache.sql` creates the table with `username TEXT PRIMARY KEY`,
      `response JSONB NOT NULL`, `cached_at TIMESTAMPTZ NOT NULL DEFAULT now()`
- [ ] `CachedUser` is a JPA entity mapped to the table; `response` column stored as `TEXT`
      (JPA), converted to/from `String` (Jackson serializes `UserResponse` to JSON string)
- [ ] `CachedUserRepository` extends `JpaRepository<CachedUser, String>`; adds a query
      method to find by username where `cached_at` is within the 1-hour TTL
- [ ] Unit test: repository saves and retrieves a `CachedUser` using Testcontainers
      Postgres — no mocks for the DB layer
- [ ] Flyway migration runs cleanly on startup

**Verification:**
- [ ] `./gradlew test` — repository test passes with a real Postgres container
- [ ] `./gradlew bootRun` starts without Flyway errors (requires Docker)

**Dependencies:** Task 1

**Files:**
- `src/main/resources/db/migration/V1__create_github_user_cache.sql`
- `src/main/java/.../repository/CachedUser.java`
- `src/main/java/.../repository/CachedUserRepository.java`
- `src/test/java/.../repository/CachedUserRepositoryTest.java`

**Scope:** M

---

### Task 6: Two-tier cache wired into the service

**Description:** Extend `GitHubUserService` to implement the full cache read/write path:
Caffeine L1 (via `@Cacheable`) → Postgres L2 (via repository) → GitHub API (via client).
Add `CacheConfig` to declare the Caffeine cache with a 5-minute TTL.

**Acceptance criteria:**
- [ ] `CacheConfig` declares a `CaffeineCache` named `"github-users"` with 5-minute TTL
      and maximum 500 entries
- [ ] `GitHubUserService.getUser` checks Postgres L2 before calling GitHub; if a fresh
      row exists (within 1-hour TTL), deserializes and returns it
- [ ] On a GitHub API fetch, the response is written to Postgres and then returned
- [ ] `@Cacheable("github-users")` on `getUser` populates Caffeine automatically after
      the method returns
- [ ] Unit test: L2 hit — repository returns a fresh row, client is never called
- [ ] Unit test: L2 stale — repository row is older than 1 hour, client is called, row
      is overwritten
- [ ] Unit test: L2 miss — no row in repository, client is called, row is written

**Verification:**
- [ ] `./gradlew test` — all cache unit tests pass (repository mocked with Mockito)
- [ ] Manual: two identical requests to `GET /users/octocat` — second returns instantly
      (Caffeine hit, no DB or network call)

**Dependencies:** Tasks 4, 5

**Files:**
- `src/main/java/.../config/CacheConfig.java`
- `src/main/java/.../service/GitHubUserService.java` (updated)
- `src/test/java/.../service/GitHubUserServiceTest.java` (updated)

**Scope:** M

---

### Checkpoint: Phase 3

- [ ] `./gradlew test` exits 0 — all unit tests green including repository test
- [ ] `./gradlew bootRun` starts cleanly with Flyway migration applied
- [ ] Manual: repeated requests demonstrate Caffeine cache hit (log output or response
      time difference)
- [ ] **Human review before proceeding**

---

## Phase 4: Verification and Documentation

### Task 7: Full-stack integration test

**Description:** Write `GitHubUserControllerIT` — a `@SpringBootTest` test that spins up
a real Postgres container (Testcontainers) and stubs both GitHub endpoints (WireMock).
Verifies the complete request-to-response path including cache behavior.

**Acceptance criteria:**
- [ ] Postgres container starts once for the test class (static `@Container`)
- [ ] WireMock stubs `/users/octocat` and `/users/octocat/repos` with realistic payloads
- [ ] Happy path: `GET /users/octocat` returns `200` with correct JSON shape, all fields
      mapped correctly, `created_at` in RFC 1123 format
- [ ] Cache hit: second identical request does not call the WireMock stubs (verify via
      stub call count)
- [ ] 404 path: WireMock returns `404` for unknown user → service returns `404`
- [ ] 502 path: WireMock returns `500` → service returns `502`
- [ ] `GET /users/ ` (blank) → `400`

**Verification:**
- [ ] `./gradlew test` exits 0 — all IT cases pass
- [ ] Docker must be running — test fails fast with a clear error if not

**Dependencies:** Tasks 4, 5, 6

**Files:**
- `src/test/java/.../GitHubUserControllerIT.java`

**Scope:** M

---

### Task 8: README

**Description:** Write a README that lets a new developer clone, configure, and run the
service in under 5 minutes. Covers architecture decisions, prerequisites, and the API.

**Acceptance criteria:**
- [ ] Prerequisites listed: Java 25 (via SDKMAN), Docker (for Postgres/Testcontainers)
- [ ] `sdk env` / `sdk use` instructions for `.sdkmanrc`
- [ ] `./gradlew build` and `./gradlew bootRun` run instructions
- [ ] `GET /users/{username}` documented with example request and response
- [ ] Error responses documented: `400`, `404`, `502`
- [ ] Architecture section references the four ADRs by decision (not just file names)
- [ ] Caching strategy summarised: Caffeine TTL, Postgres TTL, rate-limit context

**Verification:**
- [ ] A developer unfamiliar with the project can run it using only the README

**Dependencies:** Tasks 1–7

**Files:**
- `README.md`

**Scope:** S

---

### Checkpoint: Complete

- [ ] `./gradlew build` exits 0
- [ ] All unit tests and IT pass
- [ ] `./gradlew bootRun` + curl smoke test works
- [ ] README is accurate and complete
- [ ] CRAP report generated — no class exceeds threshold of 15
- [ ] **Human review — ready to push**

---

## Risks and Mitigations

| Risk | Impact | Mitigation |
|------|--------|------------|
| Spring Boot 4 not yet GA | High | Pin milestone artifact; add Spring milestone repo to `build.gradle.kts` |
| `StructuredTaskScope` API differs from previews | Med | Verify with a real compilation test in Task 3 before building on top |
| Postgres JSONB via JPA `TEXT` column | Med | Integration test in Task 5 confirms round-trip before Task 6 depends on it |
| WireMock 3.x + Spring Boot 4 classpath conflicts | Med | Verify in Task 7 setup; isolate to `testImplementation` only |
| GitHub rate limit during development | Low | Caffeine cache in place by Task 6; manual dev can reuse cached responses |

## Open Questions

- Should `application.yml` provide a `docker-compose.yml` for local Postgres, or rely on
  developers running Postgres themselves? (Affects Task 1 and the README)
- Should the cache TTL values (5 min Caffeine, 1 hr Postgres) be externalised as
  `application.yml` properties, or hardcoded constants? (Affects Task 6)
