# Branch GitHub User API

A production-ready Spring Boot 4 / Java 25 REST service that accepts a GitHub username,
fetches user and repository data in parallel from the GitHub API, merges the results,
caches the response (Caffeine L1 + Postgres L2), and returns a shaped JSON payload.

## Tech stack

| Layer | Choice |
|---|---|
| Runtime | Java 25 (preview), virtual threads default-on |
| Framework | Spring Boot 4.1.0 |
| HTTP client | Spring `RestClient` + `StructuredTaskScope` (parallel calls) |
| Mapping | MapStruct 1.6.3 |
| Cache L1 | Caffeine (5 min TTL, max 500 entries) |
| Cache L2 | Postgres via JPA + Flyway (1 hr TTL) |
| Build | Gradle 9 Kotlin DSL, Version Catalog |
| Tests | JUnit 5 · AssertJ · Testcontainers · WireMock · RestTestClient |
| Coverage | JaCoCo 100% instruction gate |

## Prerequisites

- Java 25 (`sdk use java 25.0.3-tem` via SDKMAN)
- Docker Desktop (for Testcontainers and local Postgres)

## Running locally

### 1. Start Postgres

```bash
docker compose up -d
```

This starts a Postgres 17 container on `localhost:5432` with database `branchtest`,
username `branchtest`, password `branchtest`.

### 2. Start the application

```bash
./gradlew bootRun --args='--spring.profiles.active=local'
```

The `local` profile reads datasource config from `application-local.yml` (which points
at the Docker Compose Postgres). The GitHub base URL defaults to `https://api.github.com`.

### 3. Call the endpoint

```bash
curl http://localhost:8080/users/octocat
```

## API

### `GET /users/{username}`

Returns a shaped view of a GitHub user and their public repositories.

**Path parameter**

| Name | Type | Description |
|---|---|---|
| `username` | string | GitHub username. Must not be blank. |

**Success — 200 OK**

```json
{
  "user_name":    "octocat",
  "display_name": "The Octocat",
  "avatar":       "https://avatars.githubusercontent.com/u/583231?v=4",
  "geo_location": "San Francisco",
  "email":        null,
  "url":          "https://api.github.com/users/octocat",
  "created_at":   "Tue, 25 Jan 2011 18:44:36 GMT",
  "repos": [
    {
      "name": "Hello-World",
      "url":  "https://api.github.com/repos/octocat/Hello-World"
    }
  ]
}
```

**Error responses**

| Status | Condition |
|---|---|
| 400 Bad Request | Username is blank or whitespace-only |
| 404 Not Found | GitHub user does not exist |
| 502 Bad Gateway | GitHub API returned an error |

## Caching

Responses are cached at two levels:

- **L1 — Caffeine**: in-process cache, 5-minute TTL, max 500 entries. Keyed by username.
- **L2 — Postgres**: persistent cache in the `github_user_cache` table, 1-hour TTL. Hit on
  L1 miss before calling the GitHub API.

The L2 TTL is configurable via `github.cache.l2-ttl-hours` (default `1`).

## Configuration

| Property | Default | Description |
|---|---|---|
| `spring.datasource.url` | — | JDBC URL (required; set via `DATABASE_URL` env var in prod) |
| `spring.datasource.username` | — | DB username (`DATABASE_USERNAME`) |
| `spring.datasource.password` | — | DB password (`DATABASE_PASSWORD`) |
| `github.base-url` | `https://api.github.com` | GitHub API base URL |
| `github.cache.l2-ttl-hours` | `1` | How long a Postgres-cached response is considered fresh |
| `spring.cache.caffeine.spec` | `maximumSize=500,expireAfterWrite=5m` | Caffeine spec |

## Running tests

```bash
./gradlew test        # unit + integration tests (Testcontainers pulls postgres:17-alpine)
./gradlew check       # tests + JaCoCo 100% coverage gate
```

Integration tests (`*IT`) use Testcontainers and WireMock — Docker must be running.

## Architecture notes

- **`StructuredTaskScope.open(Joiner.awaitAllSuccessfulOrThrow())`** parallelises the
  `/users/{username}` and `/users/{username}/repos` GitHub calls. Both succeed or the
  scope fails immediately.
- **`UserResponseConverter`** is a Spring-managed JPA `AttributeConverter` that serialises
  `UserResponse` to/from JSON in the `github_user_cache.response` column. Hibernate loads
  it via `SpringBeanContainer` so the Spring `ObjectMapper` (with snake_case naming) is
  used consistently.
- **`GitHubUserService`** is kept to three dependencies (client, mapper, cache); all L2
  persistence logic lives in `UserCache`.
- `@EnableCaching` lives in `CachingConfig` (not on `@SpringBootApplication`) so
  `@JsonTest` slices do not inadvertently activate the cache infrastructure.
