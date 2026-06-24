# ADR-0001 — Spring Boot 4 as Application Framework

**Status:** Accepted  
**Date:** 2026-06-23

---

## Context

The exercise requires a JVM server that exposes a single HTTP endpoint, calls two external
GitHub REST APIs, merges the results, and returns a shaped JSON response. The platform team
at Branch runs Java / Spring Boot, so the framework is constrained.

The decisions are: which Spring Boot major version, and which Java LTS.

## Decision

Use **Spring Boot 4** (Spring Framework 7 / Jakarta EE 11 baseline) on **Java 25**.

Java 25 is the current LTS (GA September 2025), the successor to Java 21. Spring Boot 4
requires Java 21 as its minimum; Java 25 is a drop-in upgrade and the sensible target for
new projects started in 2026.

Java 25 brings several features to GA that were preview or incubating in Java 21:

- **Structured Concurrency** (GA) — `StructuredTaskScope` lets the two parallel GitHub API
  calls (`/users/{username}` and `/users/{username}/repos`) be expressed as a single scoped
  fork/join block. Both calls run on virtual threads; if either fails, the scope shuts the
  other down and surfaces the exception cleanly. No `CompletableFuture` chaining required.
- **Scoped Values** (GA) — immutable, inheritable per-thread context; cleaner than
  `ThreadLocal` for passing request-scoped data (e.g., correlation IDs) across virtual
  threads.
- **Virtual threads** (GA since Java 21, default-on in Spring Boot 4) — blocking I/O on
  the GitHub API and Postgres runs on virtual threads without reactive types.
- **Pattern matching enhancements and record patterns** (GA since Java 21) — used for
  concise null-safe mapping in the service layer.

`RestClient` is the idiomatic synchronous HTTP client in Spring Boot 4. `RestTemplate` is
legacy; `WebClient` carries reactive overhead we do not need when virtual threads handle
concurrency.

## Consequences

**Gained:**
- `StructuredTaskScope.ShutdownOnFailure` cleanly models the two-API fan-out: both calls
  fire in parallel; if GitHub returns 404 on the user, the repo call is cancelled and a
  clean error surfaces immediately.
- Java 25 LTS lifecycle extends support through 2030+ — appropriate baseline for a new
  service.
- `jakarta.*` namespace throughout — no `javax.*` residue.
- Auto-configured `ObservationRegistry` for tracing and metrics with no extra wiring.

**Accepted trade-offs:**
- Java 25 is the floor. Teams still on Java 21 need a runtime upgrade before running this
  service.
- Spring Boot 4 GA timeline should be confirmed before pinning a version; use the Spring
  milestone repository if only RC artifacts are available and pin the exact version in
  `pom.xml`.

## Alternatives considered

**Spring Boot 3.x on Java 21** — stable and widely deployed, but Structured Concurrency
is still preview in Java 21 and virtual threads are opt-in. No reason to cap at 21 for a
greenfield service in 2026.

**WebFlux (reactive)** — handles concurrency without threads, but introduces a fully
reactive programming model (Mono/Flux) for two HTTP calls and one database query. The
complexity is not justified when virtual threads achieve the same concurrency with
blocking code.

**Micronaut / Quarkus** — faster cold start, but not the Branch team stack. Rejected on
constraint grounds, not merit.
