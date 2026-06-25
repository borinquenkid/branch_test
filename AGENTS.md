# AGENTS.md

Landing page for agents working in this repo. It is **not a source of truth** — it states
the do's and don'ts at a glance and points to where each truth actually lives. If a rule and
its source ever disagree, the source wins; fix the source, not this page.

> **Universal principles — Borinquen Terrier LLC engineering standard.**
> Maintained across `college_executive_function` and `oficio`. Project-specific rules follow the divider.

---

## Where the truth lives

| You want…                                   | Read                       | Canon?                   |
|---------------------------------------------|----------------------------|--------------------------|
| The design — *why* the system is this shape | `SPEC.md` (if present)     | ✅ design canon          |
| What's being built now / what's next        | `STATUS.md`, `tasks/`      | ❌ live state, not canon |
| API contract                                | `openapi.yaml` (if present)| ✅ interface canon       |
| Cross-session working memory                | `MEMORY.md` (if present)   | reference                |

---

## Do

- **Test-first (TDD).** Write the failing test before the implementation. Every behavior
  change, no exceptions. A commit that adds behavior without a covering test is rejected.

- **Use real externals in integration tests.** WireMock for HTTP — real network shapes,
  not hand-rolled stubs. Testcontainers for any database or queue. Pure logic needs no
  container; everything at a system boundary does.

- **Run the contrarian loop before adopting a plan.** Attack the assumptions, surface
  failure modes and cheaper alternatives, then revise. A plan that survives the contrarian
  loop is a plan worth building.

- **Derive before asking.** Check existing specs, configs, and test output first. An
  unnecessary question means the canon needs tightening.

- **Exercise every code path, including failure branches.** A testability blocker is a
  design flaw — fix it at the cause, don't waive the test.

- **Flow customization lives in data, never in conditional code.** Behavior that varies by
  input belongs in configuration or strategy objects, not in `if (type == X)` branches.
  Adding a variant is a data operation. Keep it that way.

- **One fork at a time.** Do the work, run tests, update status, then stop for review.
  Do not start more than one feature branch in a turn.

- **Look up dependency versions — never assume them.** Before pinning any library or
  plugin version, verify the current release on Maven Central or the project's release
  page. Assuming a version is outdated or wrong; a wrong assumption that adds a milestone
  repository or pins a stale artifact wastes build time and misleads readers.

---

## CRAP Index — Hard Exit Criterion

> TDD gets you coverage. It does not force refactor. Tested spaghetti is still spaghetti.
> CRAP < 15 is the gate TDD alone doesn't enforce.

The CRAP formula: `complexity² × (1 − coverage)³ + complexity`. Complexity squares — so
splitting one high-complexity method shrinks the score sharply on its own, often more than
adding coverage alone ever could.

### The protocol (mandatory on every task completion)

1. Run tests to generate fresh coverage data (`./mvnw test` or `./gradlew test`).
2. Generate the CRAP / complexity report.
3. If any changed method or file scores ≥ 15 — **the task is not done.**
4. Decompose first, then re-test. Do not write tests against a monolith you are about to
   split — those tests must be rewritten anyway. Decompose first, write targeted tests
   against the smaller stable units that result.

### Complexity & decomposition standards (enforce from spec, not metrics review)

1. **Per-method complexity limit: 5.** Any public method exceeding 5 control flows
   (branches, loops, logical operators, null checks) must be refactored or delegated.

2. **Per-class complexity limit: 15.** No class shall exceed 15 total complexity across
   all methods. Classes nearing this limit are candidates for service extraction.

3. **Thin facade pattern as architectural requirement.** Any class coordinating 3+
   distinct responsibilities must delegate to focused single-responsibility collaborators,
   wired via constructor injection.

4. **Testability-first.** Every public method must be independently testable with focused
   unit tests (1–3 assertions, clear mocks). Methods with 6+ control flows are flagged as
   untestable and must be split before merge.

5. **Coverage-by-design target.** Business logic services must reach 100% line coverage.
   `jacocoTestCoverageVerification` enforces this as a hard build gate (`minimum = 1.0`,
   wired into `check`) — `./gradlew build` fails if coverage drops. Code with 0% coverage
   cannot be merged, even if "it's new."

6. **CRAP as non-functional requirement.** State the CRAP acceptance ceiling in the spec
   before building, not post-hoc after metrics complain.

> **Pattern:** The decomposed three-service structure is what should have been in the
> original spec — not derived through refactoring. Spec decomposition prevents CRAP;
> metrics review only measures what you missed.

---

## Build Verification Protocol

Whenever a task is reported done, verify that **all primary build targets compile and
their tests pass** before confirming. The specific targets are project-defined — see the
project-specific section below. A change that breaks a sibling target is not done.

`./gradlew build` runs the full gate: compile → tests → JaCoCo coverage enforcement →
Spotless format check. All four must be green. A partial pass is not done.

---

## Don't

- **Don't commit without tests running green.** No exceptions for "small" changes or
  "obvious" fixes.

- **Don't treat live-state files as canon.** Task lists and status docs churn every commit.
  Authoritative rules live in spec and ADR files.

- **Don't author rules directly into this page.** Add the rule to its real home (spec,
  ADR, config) and link here.

- **Don't ship untestable architecture.** If a component cannot be cleanly unit tested
  with focused mocks, it is not ready to build against. Fix the design first.

- **Don't act on an LLM API claim without a real verification test.** Confidence is not
  correctness. Verify before building on top of it.

- **Don't fabricate scaffold detail you haven't earned.** Forward pointers for open-ended
  vision stay one-liners. Concrete starter elements are only for load-bearing parked work.

---

## ADRs

Architectural decisions live in `docs/adr/`. Before making a significant choice, check
whether an ADR already settles it. If it does, follow it. If it doesn't, write one.

Key decisions already settled:

- Spring Boot 4 / Java 25 / virtual threads — `docs/adr/ADR-0001`
- Jackson serde + MapStruct field mapping — `docs/adr/ADR-0002`
- Postgres only (no H2), Caffeine L1 + Postgres L2 cache, Testcontainers — `docs/adr/ADR-0003`
- Gradle + Kotlin DSL (no Maven) — `docs/adr/ADR-0004`

---

## Project-specific rules

**Product:** Branch coding exercise — GitHub user profile aggregation API.
**Owner:** Walter Duque de Estrada
**Stack:** Java 25 / Spring Boot 4 / Gradle — `src/main/java` (application) + `src/test/java` (tests).

| You want…                              | Read                              | Canon?          |
|----------------------------------------|-----------------------------------|-----------------|
| Requirements & expected response shape | `Platform_Coding_Exercise.pdf`    | ✅ design canon |
| Current task state                     | `STATUS.md` (if present)          | ❌ live state   |

### Build targets

```bash
./gradlew build        # compile + all tests + JaCoCo gate + Spotless check + artifact
./gradlew test         # unit + integration tests only
./gradlew bootRun      # run the server
bash smoke-test.sh     # full Docker lifecycle smoke test (requires Docker)
```

All of the first three must pass on every task completion. `smoke-test.sh` is the
manual end-to-end gate before pushing.

### CRAP toolchain

This project uses JaCoCo for coverage. Generate the report with:

```bash
./gradlew test jacocoTestReport
# Report: build/reports/jacoco/test/html/index.html
```

CRAP threshold: **15**. Any changed class exceeding this at task completion is a blocker.

### Test stack

- **Unit tests:** JUnit 5 + Mockito
- **HTTP client tests:** Spring's `MockRestServiceServer` (not OkHttp MockWebServer) —
  `GitHubClient` uses Spring `RestClient`, so Spring's test infrastructure intercepts at
  the right layer. Wire it via `MockRestServiceServer.bindTo(builder).build()`.
- **Integration tests:** `@SpringBootTest` + WireMock (embedded `WireMockServer`, no
  Docker) + Testcontainers `PostgreSQLContainer`. Start both in a `static {}` initializer
  before the Spring context; inject URLs via `@DynamicPropertySource`. Do **not** use
  `@Container` — it races with `@DynamicPropertySource` and the context may start before
  the container is ready. See `docs/adr/ADR-0003` for the full rationale.
- **Naming:** Integration test classes end in `IT` (e.g., `GitHubUserControllerIT`)

### API shape obligations

- `GET /users/{username}` — returns the merged JSON shape from the exercise PDF
- `created_at` must be RFC 1123 (`"Tue, 25 Jan 2011 18:44:36 GMT"`), not ISO 8601
- Field mapping: `login → user_name`, `name → display_name`, `avatar_url → avatar`,
  `location → geo_location`, repo `url` field (not `html_url`)
- GitHub's `/repos` endpoint is **paginated** — follow `Link: <...>; rel="next"` headers,
  100 repos per page, until no `next` rel is present
- Cache responses by username (Caffeine, TTL ≥ 5 min) to avoid GitHub rate limiting
- Return `404` for unknown usernames, `400` for blank/invalid input, `502` on upstream failure

### Static analysis — NullAway + JSpecify (hard build gate)

NullAway runs as an ErrorProne check at `ERROR` severity. Any new code that introduces an
unannotated nullable reference breaks the compile. Rules:

- Every package must have a `package-info.java` annotating it `@NullMarked`.
- Use `@Nullable` from `org.jspecify.annotations` (not Spring's, not `javax`).
- MapStruct-generated classes are excluded from NullAway via `ExcludedClassAnnotations`.
- `StructuredTaskScope` is a Java 25 preview API — the class that uses it must carry
  `@SuppressWarnings("preview")` or rely on `-Xlint:-preview` in compiler args (already
  wired in `build.gradle.kts`).

### Code formatting — Spotless / Google Java Format (hard build gate)

`./gradlew build` runs `spotlessCheck` (Google Java Format 1.35.0). Unformatted code
fails the build. Before committing, run `./gradlew spotlessApply` to auto-format.

### WireMock / Jetty version conflict

WireMock 3.x requires Jetty 12.0.x. Spring Boot 4's BOM pins Jetty 12.1.x. Without
the forced override in `build.gradle.kts`, tests fail at startup with `NoSuchMethodError`.
Do not remove or weaken this resolution strategy block:

```kotlin
configurations.matching { it.name.contains("test", ignoreCase = true) }.all {
    resolutionStrategy.eachDependency {
        if (requested.group.startsWith("org.eclipse.jetty")) {
            useVersion("12.0.36")
        }
    }
}
```

### Human pushes — agents do not push

`git push` is the human's step. Agents commit locally and stop.
