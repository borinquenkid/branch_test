# Task List

## Phase 1: Foundation

- [ ] **T1** — Gradle scaffold (`build.gradle.kts`, `.sdkmanrc`, wrapper, app entry point)
- [ ] **T2** — Domain records + MapStruct mapper (DTOs, field renames, date reformat)

### Checkpoint 1
- [ ] `./gradlew build` clean, mapper unit tests green — human review

## Phase 2: Core Data Flow

- [ ] **T3** — `GitHubClient` (RestClient + StructuredTaskScope, MockWebServer tests)
- [ ] **T4** — Controller + Service wired end-to-end (no cache, unit tests + smoke test)

### Checkpoint 2
- [ ] All unit tests green, manual smoke passes — human review

## Phase 3: Cache Layer

- [ ] **T5** — Flyway migration + `CachedUser` entity + repository (Testcontainers test)
- [ ] **T6** — Two-tier cache wired into service (Caffeine L1 + Postgres L2, unit tests)

### Checkpoint 3
- [ ] All tests green, bootRun clean, cache verified manually — human review

## Phase 4: Verification and Documentation

- [ ] **T7** — Full-stack integration test (`GitHubUserControllerIT`, Testcontainers + WireMock)
- [ ] **T8** — README

### Checkpoint 4 (Final)
- [ ] `./gradlew build` clean, all tests pass, CRAP ≤ 15, README accurate — ready to push
