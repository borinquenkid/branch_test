# ADR-0002 — Jackson for JSON Serialization / Deserialization

**Status:** Accepted  
**Date:** 2026-06-23

---

## Context

The service calls two GitHub endpoints and merges them into a single shaped response.
Field names and the date format differ from GitHub's. The transformation is:

### Source 1 — `GET https://api.github.com/users/{username}` (selected fields only)

```jsonc
{
  "login":      "octocat",               // → user_name
  "name":       "The Octocat",           // → display_name
  "avatar_url": "https://avatars…",      // → avatar        (field renamed)
  "location":   "San Francisco",         // → geo_location   (field renamed)
  "email":      null,                    // → email          (nullable, pass-through)
  "url":        "https://api.github.com/users/octocat",  // → url (API URL, NOT html_url)
  "created_at": "2011-01-25T18:44:36Z", // → created_at     (FORMAT CHANGE, see below)

  // All other fields (id, node_id, html_url, company, blog, bio, followers, …) are dropped.
}
```

### Source 2 — `GET https://api.github.com/users/{username}/repos` (selected fields only)

```jsonc
[
  {
    "name": "boysenberry-repo-1",                                    // → repos[].name
    "url":  "https://api.github.com/repos/octocat/boysenberry-repo-1" // → repos[].url (NOT html_url)

    // All other fields (id, full_name, html_url, description, fork, …) are dropped.
  },
  …
]
```

### Output — our response

```jsonc
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
      "name": "boysenberry-repo-1",
      "url":  "https://api.github.com/repos/octocat/boysenberry-repo-1"
    }
  ]
}
```

### Field mapping summary

| GitHub field        | Transform              | Our field        |
|---------------------|------------------------|------------------|
| `login`             | rename                 | `user_name`      |
| `name`              | rename                 | `display_name`   |
| `avatar_url`        | rename                 | `avatar`         |
| `location`          | rename                 | `geo_location`   |
| `email`             | pass-through (nullable)| `email`          |
| `url`               | pass-through           | `url`            |
| `created_at`        | **reformat** ¹         | `created_at`     |
| repos `name`        | pass-through           | `repos[].name`   |
| repos `url`         | pass-through ²         | `repos[].url`    |

¹ ISO 8601 `"2011-01-25T18:44:36Z"` → RFC 1123 `"Tue, 25 Jan 2011 18:44:36 GMT"`  
² `url` is the GitHub API URL (`https://api.github.com/repos/…`), not `html_url` (`https://github.com/…`)

## Decision

Use **the Jackson version managed by Spring Boot 4's dependency BOM** — do not pin a
version manually. Spring Boot 4's parent POM declares a tested, compatible Jackson bill of
materials; overriding it is unnecessary and risks misalignment between modules.

### Jackson module version alignment

Jackson's first-party modules (`jackson-databind`, `jackson-datatype-jsr310`,
`jackson-module-parameter-names`, `jackson-annotations`) ship together on the same version
and move in lock-step. These are safe to leave under Spring Boot's BOM control.

Some modules have not kept pace with core releases:

- **`jackson-dataformat-*`** (CSV, YAML, CBOR, etc.) — occasionally lags a minor version
  behind `jackson-databind`. Do not use any of these here; note the lag if added later.
- **Third-party Jackson modules** (Kotlin, Scala, Guava, etc.) — release on their own
  cadence and may not support the BOM-managed core version. Verify compatibility before
  adding. If a module is stuck on an older core, isolate it and document the pin.

For this service the only Jackson dependencies are first-party ones pulled in by
`spring-boot-starter-web` and `spring-boot-starter-json`. No module version pin is needed.

---

### DTOs — Java records

Use Java records for all DTO types: immutable by construction, no setters, no accidental
mutation. Two sets:

- **GitHub DTOs** (internal) — mirror only the fields we consume from each endpoint.
  Decorated with `@JsonIgnoreProperties(ignoreUnknown = true)` so GitHub API additions
  never cause deserialization failures.
- **Response DTOs** (public) — carry our field names as declared; Jackson serializes them
  verbatim using `@JsonProperty` annotations on the record components (see Mapping section).

### Field mapping — MapStruct

Use **MapStruct** to translate GitHub DTOs to response DTOs. MapStruct is an annotation
processor: it reads `@Mapping` declarations at compile time and generates plain Java
mapping classes. There is no runtime reflection and the generated code is readable and
verifiable.

Field renames are declared explicitly on the mapper interface (`source` → `target`).
The `created_at` reformat is expressed as a named default method on the same interface,
keeping the date logic co-located with the mapping declarations rather than scattered
in service code.

MapStruct integrates with Spring via `componentModel = "spring"` — the generated mapper
is a Spring bean, injectable wherever needed.

### Jackson configuration

`FAIL_ON_UNKNOWN_PROPERTIES = false` is set globally — GitHub returns many fields we do
not map and strict deserialization would throw on every call.

`WRITE_DATES_AS_TIMESTAMPS = false` ensures any `java.time` type elsewhere in the
codebase serializes as an ISO 8601 string, not an epoch number. Spring Boot auto-configures
`JavaTimeModule` when `jackson-datatype-jsr310` is on the classpath (it is, via
`spring-boot-starter-web`).

### Date formatting

`created_at` is reformatted in the MapStruct mapper using `java.time` only:
`Instant.parse` → `atZone(UTC)` → `DateTimeFormatter.RFC_1123_DATE_TIME`. No
`SimpleDateFormat`, no `Date`, no timezone ambiguity. The result is stored as a plain
`String` in the response record; Jackson writes it verbatim.

## Consequences

**Gained:**
- MapStruct field declarations are self-documenting — every rename is an explicit
  `source`/`target` pair visible at the mapper interface, not inferred from a naming strategy.
- Compile-time generation: a missing or mistyped field name is a build error, not a runtime
  surprise.
- Java records enforce immutability at the language level.
- `FAIL_ON_UNKNOWN_PROPERTIES = false` insulates the service from GitHub API additions.

**Accepted trade-offs:**
- MapStruct requires an annotation processor entry in the Maven compiler plugin. One extra
  build configuration step; worth it for compile-time safety.
- `created_at` as `String` in the response DTO means the format contract lives in the mapper
  method, not enforced by the type system. Acceptable at this scale.

## Alternatives considered

**Manual mapping method** — a plain static method in the service. Zero dependencies, fully
transparent. Rejected in favour of MapStruct because explicit `@Mapping` declarations
catch renames and field additions at compile time; a manual method silently accepts a
wrong constructor argument order.

**`@JsonNaming(SnakeCaseStrategy.class)`** — auto-converts camelCase to snake_case.
Rejected: implicit transformation; a reader cannot see `userName → user_name` without
knowing the strategy is active.

**ModelMapper** — reflection-based, runtime. Convention-driven mapping breaks on our renames
anyway, leaving verbose configuration with none of MapStruct's compile-time guarantees.
Rejected.

**`ZonedDateTime` in the response record + custom Jackson serializer** — more type-safe
but adds a custom `JsonSerializer` for a single field. Over-engineered for this scope.
