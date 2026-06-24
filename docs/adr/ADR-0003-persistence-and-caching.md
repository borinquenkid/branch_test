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

**Integration tests (`@SpringBootTest` + programmatic Testcontainers):** spin up a real
Postgres container and an embedded WireMock server. Both are started in a `static {}`
initializer — before JUnit touches the class and before Spring initializes its context.
`@DynamicPropertySource` then injects both URLs. Tests carry the `IT` suffix.

**Why programmatic startup instead of `@Container`:**  
`@Container` delegates lifecycle to the JUnit Testcontainers extension. When combined with
`@DynamicPropertySource`, there is no guaranteed ordering between the extension starting
the container and Spring reading the injected properties. A static initializer removes the
race: containers are running before any JUnit extension or Spring lifecycle hook fires.

**Why `WireMockServer` (embedded) instead of a WireMock container:**  
WireMock does not need Docker. An in-process `WireMockServer` on a dynamic port is
lighter, faster to start, and sufficient to stub the two GitHub endpoints. Only Postgres
requires a real container.

Startup sequence:
```
static { POSTGRES.start(); GITHUB_API.start(); }   ← before Spring context
@DynamicPropertySource injects datasource + github.base-url
Spring context initializes with correct properties
@AfterAll { GITHUB_API.stop(); POSTGRES.stop(); }
```

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
