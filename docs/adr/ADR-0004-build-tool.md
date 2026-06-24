# ADR-0004 — Gradle with Kotlin DSL as Build Tool

**Status:** Accepted  
**Date:** 2026-06-23

---

## Context

The project needs a build tool to manage dependencies, compile sources, run tests, and
produce the application artifact. The two standard choices in the Spring Boot ecosystem
are Maven and Gradle.

## Decision

Use **Gradle** with the **Kotlin DSL** (`build.gradle.kts`).

Gradle is chosen over Maven for the following reasons:

- **Consistent toolchain.** Both sibling projects (`oficio`, `college_executive_function`)
  use Gradle. No context switching; the same task names, wrapper commands, and conventions
  apply across all projects.

- **Kotlin DSL.** `build.gradle.kts` is type-safe and IDE-navigable. Dependency coordinates,
  task configuration, and plugin blocks are checked at edit time. Maven's `pom.xml` is
  untyped XML — errors surface only at build time.

- **MapStruct annotation processor.** Gradle's `annotationProcessor(...)` dependency
  configuration wires the MapStruct processor cleanly in the dependencies block. Maven
  requires a nested `<annotationProcessorPaths>` entry inside the compiler plugin, which
  is easy to misconfigure and invisible in the dependency list.

- **Incremental builds.** Gradle's build cache and daemon skip up-to-date tasks. The
  edit/test loop is faster, which matters when running Testcontainers integration tests
  repeatedly.

- **Task model.** Custom tasks (e.g., `generateCrapReport` from AGENTS.md) are
  first-class Kotlin functions in `build.gradle.kts`. Maven equivalents require plugin
  bindings or Groovy/XML configuration.

- **Spring Boot support.** Spring Boot 4 ships a Gradle plugin with full feature parity
  with the Maven plugin. No capability is sacrificed.

## Consequences

**Gained:**
- Type-safe build configuration with IDE completion.
- Cleaner annotation processor wiring for MapStruct.
- Shared conventions with sibling projects.
- Faster incremental builds via Gradle daemon and build cache.

**Accepted trade-offs:**
- Gradle's Kotlin DSL has a steeper initial learning curve than `pom.xml` for developers
  who have only used Maven. Acceptable — the type safety pays back the ramp-up cost.
- The Gradle wrapper (`gradlew`) must be committed; this is standard practice and adds
  a small number of files to the repository root.

## Alternatives considered

**Maven** — the dominant build tool in enterprise Java and the Spring Initializr default.
Rejected: verbose XML configuration, no type safety, noisier annotation processor wiring,
and inconsistency with the existing project toolchain.
