# Build Tool Migration Design

## Summary

Migrate `cljs-ajax` from a Leiningen-based build to a modern toolchain centered on `deps.edn`, `tools.build`, and `shadow-cljs`.

The target state is:

- `deps.edn` is the source of truth for Clojure dependencies, source paths, and aliases
- `build.clj` owns clean/package/release tasks
- `shadow-cljs.edn` owns ClojureScript compilation and test builds
- `package.json` declares JavaScript-side tooling needed for Node and browser test execution
- `project.clj` and Leiningen-specific workflows are removed after parity is verified

This is a clean migration, not a compatibility layer. Commands and tooling may change significantly if the result is simpler and more maintainable.

## Goals

- Remove Leiningen as the project build entrypoint
- Move the project to standard Clojure CLI conventions for dependency management and build automation
- Use `shadow-cljs` for ClojureScript compilation and test orchestration
- Preserve current library behavior and existing test coverage
- Make system and npm requirements explicit in repo metadata instead of relying on prose alone
- Produce a contributor and CI workflow that is straightforward to run on a clean machine

## Non-Goals

- Preserve existing `lein` commands or aliases
- Rewrite library APIs or alter runtime behavior beyond what is required by the tool migration
- Refactor test code unless needed to make the new test runners work
- Introduce a second compatibility toolchain after migration is complete

## Current State

The repository currently uses:

- `project.clj` for dependencies, profiles, build definitions, and aliases
- `lein-cljsbuild` for ClojureScript builds
- `doo` aliases for Node and headless Chrome test execution
- transient npm installs for `xmlhttprequest` and `karma-cljs-test`
- contributor documentation that describes required tools in prose

The source layout is already conventional:

- Clojure and ClojureScript library code under `src/`
- tests under `test/`
- browser integration code under `browser-test/`

This means the migration can focus on tool replacement rather than large-scale source reorganization.

## Target Architecture

### Clojure Build Layer

`deps.edn` becomes the top-level project configuration. It will define:

- base source paths
- test paths
- Maven dependencies
- aliases for development, JVM tests, and build tasks

`build.clj` provides scripted build tasks such as:

- clean
- jar
- install
- deploy or other release-oriented packaging tasks

These tasks should be invokable through standard Clojure CLI commands so CI and contributors use the same interface.

### ClojureScript Build Layer

`shadow-cljs.edn` becomes the source of truth for ClojureScript builds. It should define separate builds for:

- Node-based CLJS tests
- browser-based CLJS tests
- any local development or integration target still needed by the repo

The existing `cljs.test` model remains in place. Shadow replaces build and execution orchestration, not the test framework itself.

### JavaScript Tooling Layer

`package.json` will be added so JavaScript runtime and test dependencies are explicit and reproducible. This should include the packages needed for:

- Node execution support currently provided by `xmlhttprequest`
- browser test execution in headless Chrome
- any Shadow or Karma-side support packages required by the chosen test flow

The project should no longer depend on undocumented or ad hoc npm installs.

## Migration Approach

The migration should be executed in two phases within one branch:

1. Add the new toolchain and make it pass all required tests.
2. Remove Leiningen files, aliases, and documentation once parity is confirmed.

The final repository state is a clean break from Leiningen. Keeping both toolchains long-term is intentionally out of scope because it increases maintenance cost and creates drift.

## Command Model

The new command surface should be small and explicit.

Expected commands:

- JVM tests through a Clojure CLI alias such as `clojure -X:test`
- build packaging through `clojure -T:build jar`
- ClojureScript Node tests through `npx shadow-cljs` commands
- ClojureScript browser tests through `npx shadow-cljs` plus the selected headless browser runner

Exact command names may vary slightly during implementation, but the design requirement is that:

- JVM tasks run through `clojure`
- CLJS tasks run through `shadow-cljs`
- npm installation is performed through standard package manager commands

## Testing Design

### JVM Tests

Existing Clojure tests should continue running without semantic changes. The migration should keep the current test namespaces and move execution to a `deps.edn` alias-backed workflow.

### Node ClojureScript Tests

Existing CLJS test namespaces should compile and run under a Node-targeted Shadow build. Any required Node bootstrap glue should be kept minimal and isolated to test runner code or build configuration.

### Browser ClojureScript Tests

Existing browser-relevant CLJS tests should continue to run in headless Chrome. The design assumes Shadow handles compilation and a browser test runner handles execution. The exact runner may remain Karma if that yields the simplest parity with current behavior.

### Parity Requirement

Migration is only considered complete when the new toolchain reproduces all of these categories successfully:

- JVM tests
- CLJS tests in Node
- CLJS tests in headless Chrome

If a test category cannot be reproduced cleanly, the migration must stop and resolve that mismatch before Leiningen is removed.

## Contributor Workflow

Contributor setup should become metadata-backed and concise.

The repository should document:

- required system dependencies
- required npm dependencies
- the minimal install sequence on a clean machine
- the canonical commands for each test category

The documentation should be updated to remove obsolete references to `lein`, `lein-cljsbuild`, `doo`, or PhantomJS unless one of those remains intentionally in use.

## CI Design

CI should install:

- Java
- Clojure CLI
- Node.js
- headless Chrome or an equivalent browser package used by the runner

CI should then run separate steps for:

- JVM tests
- Node CLJS tests
- browser CLJS tests

Keeping these steps separate makes failures easier to diagnose and avoids hiding category-specific issues inside one large alias.

## Risks And Mitigations

### Browser Test Parity

Risk:
The current browser test behavior depends on the old `doo` and Karma-oriented flow, and the replacement may not behave identically by default.

Mitigation:
Treat browser tests as the primary migration risk. Validate them explicitly before removing Leiningen, and prefer the smallest working runner setup over a more ambitious redesign.

### Hidden Build Assumptions

Risk:
Some current behavior may be encoded implicitly in Lein profiles, CLJS build settings, or contributor machine state.

Mitigation:
Translate configuration deliberately, compare old and new commands category by category, and update documentation whenever a hidden assumption is discovered.

### Dependency Drift Between Clojure And npm Layers

Risk:
A partial migration could leave Clojure and JavaScript dependencies inconsistently declared.

Mitigation:
Make `deps.edn` and `package.json` the only supported dependency entrypoints and remove transient install instructions from docs.

## Success Criteria

The migration is successful when:

- `project.clj` is removed
- the repo builds and tests through `deps.edn` and `shadow-cljs`
- all existing test categories run successfully in the new toolchain
- contributor docs describe only the new workflow
- CI uses the new commands exclusively

## Open Decisions Resolved By This Design

- The migration is a clean break, not a long-term dual-toolchain transition.
- `deps.edn` and `tools.build` are the Clojure-side standard.
- `shadow-cljs` is the ClojureScript build and test orchestrator.
- Existing tests stay on `cljs.test` unless a targeted compatibility adjustment is required.
- Browser coverage in headless Chrome is a mandatory requirement, not an optional enhancement.
