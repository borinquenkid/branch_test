# ADR-0002 — Jackson for JSON Serialization / Deserialization

**Status:** Accepted  
**Date:** 2026-06-23

---

## Context

The service receives JSON from two GitHub API endpoints and must return a merged JSON
response whose field names and date format differ from GitHub's. The key mapping obligations
are:

| GitHub field   | Response field   | Notes                                    |
|----------------|------------------|------------------------------------------|
| `login`        | `user_name`      | rename                                   |
| `name`         | `display_name`   | rename                                   |
| `avatar_url`   | `avatar`         | rename                                   |
| `location`     | `geo_location`   | rename                                   |
| `email`        | `email`          | pass-through; nullable                   |
| `url`          | `url`            | pass-through (API URL, not html_url)     |
| `created_at`   | `created_at`     | **format change**: ISO 8601 → RFC 1123   |
| repos `name`   | `repos[].name`   | pass-through                             |
| repos `url`    | `repos[].url`    | pass-through (API URL, not html_url)     |

`created_at` from GitHub: `"2011-01-25T18:44:36Z"` (ISO 8601 / UTC)  
`created_at` required:   `"Tue, 25 Jan 2011 18:44:36 GMT"` (RFC 1123)

## Decision

Use **Jackson 2.18+** (auto-configured by Spring Boot 4) with the following configuration:

### Response DTOs — Java records with explicit `@JsonProperty`

Use Java records for all response types. Field renaming is declared with `@JsonProperty`
on the record component. No naming-strategy annotation — strategy-level renaming is implicit
and breaks readability; explicit annotations are self-documenting.

```java
public record UserResponse(
    @JsonProperty("user_name")    String userName,
    @JsonProperty("display_name") String displayName,
    @JsonProperty("avatar")       String avatar,
    @JsonProperty("geo_location") String geoLocation,
                                  String email,        // name matches; no annotation needed
                                  String url,
    @JsonProperty("created_at")   String createdAt,
                                  List<RepoResponse> repos
) {}
```

### Date formatting

Parse GitHub's ISO 8601 string with `Instant.parse(...)`, then format with
`DateTimeFormatter.RFC_1123_DATE_TIME` (zone = UTC). This is pure `java.time` — no
`SimpleDateFormat`, no `Date`, no timezone ambiguity.

```java
Instant instant = Instant.parse(githubCreatedAt);          // "2011-01-25T18:44:36Z"
ZonedDateTime zdt = instant.atZone(ZoneOffset.UTC);
String formatted = DateTimeFormatter.RFC_1123_DATE_TIME.format(zdt); // "Tue, 25 Jan 2011 18:44:36 GMT"
```

`created_at` is stored and returned as a plain `String` in the response record — the
formatting happens in the service layer, not in Jackson. Jackson writes it verbatim.

### ObjectMapper configuration

Register `JavaTimeModule` and disable `WRITE_DATES_AS_TIMESTAMPS` to ensure any `Instant`
or `ZonedDateTime` fields elsewhere in the codebase serialize to ISO 8601 strings, not
epoch numbers. Spring Boot 4 auto-configures this when `jackson-datatype-jsr310` is on
the classpath (it is, via `spring-boot-starter-web`).

```java
@Bean
public Jackson2ObjectMapperBuilderCustomizer jacksonCustomizer() {
    return builder -> builder
        .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .featuresToDisable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
}
```

`FAIL_ON_UNKNOWN_PROPERTIES = false` is required for the GitHub API response DTOs —
GitHub returns many fields we do not map; strict deserialization would throw on every call.

### GitHub API response DTOs

Separate, internal-only records that mirror the fields we actually consume. Annotated with
`@JsonIgnoreProperties(ignoreUnknown = true)` as a belt-and-suspenders guard.

```java
@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubUser(
    String login,
    String name,
    @JsonProperty("avatar_url") String avatarUrl,
    String location,
    String email,
    String url,
    @JsonProperty("created_at") String createdAt
) {}
```

## Consequences

**Gained:**
- Explicit `@JsonProperty` annotations make the field contract readable at a glance —
  no implicit strategy to decode.
- Java records are immutable by construction; no setters, no accidental mutation.
- `java.time` formatting is timezone-safe and testable without `Clock` mocking.
- `FAIL_ON_UNKNOWN_PROPERTIES = false` insulates the service from GitHub API additions.

**Accepted trade-offs:**
- `created_at` as `String` in the response DTO means the date format is set in service code,
  not enforced by Jackson. A custom serializer would centralize this, but adds indirection
  for a single field — not worth it at this scale.

## Alternatives considered

**`@JsonNaming(SnakeCaseStrategy.class)`** — converts camelCase to snake_case automatically.
Rejected: implicit transformation makes the mapping invisible to a reader. A new developer
cannot see `userName → user_name` without knowing the strategy is active.

**`ZonedDateTime` field in response record + custom serializer** — more type-safe but adds
a Jackson module and a custom `JsonSerializer<ZonedDateTime>` for a single date field.
Over-engineered for this scope.
