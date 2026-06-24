# ADR-0003 — Persistence and Caching Strategy

**Status:** Accepted  
**Date:** 2026-06-23

---

## Context

GitHub's unauthenticated REST API rate-limits clients to 60 requests per hour per IP.
The exercise explicitly suggests a caching mechanism. Two questions:

1. **What caching layer?** In-memory only, or durable (survives restarts)?
2. **What database?** H2 (in-process) or PostgreSQL?

## Decision

### Caching: two-tier

**L1 — Caffeine (in-memory).** Spring Cache with Caffeine as the provider. TTL = 5 minutes,
maximum 500 entries. Serves hot paths without hitting the database on every request.

**L2 — PostgreSQL (durable).** A single `github_user_cache` table stores the serialized
response JSON and a `cached_at` timestamp. Cache misses check L2 before calling GitHub.
This means a restart does not immediately evict all cached users, and multiple service
instances share the same durable cache.

Cache write path: GitHub API → map to response → write to Postgres → populate Caffeine → return.  
Cache read path: Caffeine hit → return. Caffeine miss → Postgres hit (if `cached_at` within TTL) → repopulate Caffeine → return. Both miss → call GitHub.

### Database: PostgreSQL only — no H2

**H2 is not used anywhere** — not in tests, not locally.

The engineering standard (AGENTS.md) is explicit: use real externals in integration tests.
H2 diverges from Postgres in ways that matter:

- DDL differences: `SERIAL` / `IDENTITY` vs Postgres sequences, `TEXT` vs `VARCHAR` edge
  cases, `JSONB` not supported in H2.
- Query behavior: H2's Postgres compatibility mode covers common cases but has known gaps in
  window functions, CTEs, and `ON CONFLICT` syntax.
- A test suite that passes on H2 and fails on Postgres is worse than no test suite — it
  gives false confidence.

Testcontainers manages the Postgres lifecycle for both integration tests and local
development (via `docker-compose.yml`). There is no separate "test dialect."

### Schema

```sql
CREATE TABLE github_user_cache (
    username    TEXT        PRIMARY KEY,
    response    JSONB       NOT NULL,
    cached_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

`JSONB` stores the full serialized `UserResponse`. On a cache hit, the response is
deserialized from the stored JSON — no need to re-call GitHub or re-map fields.

Expiry is application-enforced: a row is considered stale if
`now() - cached_at > interval '1 hour'`. Stale rows are overwritten on next fetch, not
deleted eagerly (no background job needed at this scale).

## Test strategy

**Unit tests (JUnit 5 + Mockito):** test the service layer with a mocked repository. No
database, no Spring context. Fast and focused.

**Integration tests (`@SpringBootTest` + Testcontainers):** spin up a real Postgres
container and a real WireMock server stubbing both GitHub endpoints. Verify the full stack:
HTTP request → controller → service → repository → Postgres → response JSON shape. These
tests carry the `IT` suffix (e.g., `GitHubUserControllerIT`).

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class GitHubUserControllerIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",      postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    // WireMock stubs GitHub API; test asserts full response shape and cache behavior
}
```

Testcontainers reuses the container across tests in the same JVM via `@Container` on a
`static` field — startup cost is paid once per test run, not once per test class.

## Consequences

**Gained:**
- Tests exercise the real Postgres JSONB path — no dialect surprises in production.
- Durable cache survives restarts and is shareable across instances (horizontal scaling).
- Caffeine L1 keeps the common case sub-millisecond without a DB round-trip.
- No H2 dependency in the classpath — no risk of accidentally running on the wrong dialect.

**Accepted trade-offs:**
- Integration tests require Docker. The test suite fails without it — this is intentional;
  it is the price of dialect honesty.
- Two-tier cache adds complexity over Caffeine alone. For a single-instance exercise, L2
  could be omitted; it is included to demonstrate a production-honest pattern.

## Alternatives considered

**Caffeine only (no database).** Correct for the exercise's minimal scope. Rejected in
favor of showing a durable pattern — the trade-off is clearly documented here.

**H2 for tests, Postgres for production.** Dialect risk outweighs the convenience of
not needing Docker. Rejected per engineering standard.

**Redis for caching.** Appropriate for distributed caching at scale. Over-engineered for
this scope and introduces a second external service with no added value here.
