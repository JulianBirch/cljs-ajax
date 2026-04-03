# Development Setup

This document describes the requirements currently needed to build and test `cljs-ajax` on a clean machine.

The commands below were verified on Ubuntu 24.04 and run successfully as the non-root user `jessica`.

## System Requirements

Install these system packages first:

- Java JDK
- Clojure CLI
- Node.js and npm
- Google Chrome
- `bubblewrap`
- `gh`

On Ubuntu, the working package set is:

```bash
sudo apt-get update
sudo apt-get install -y default-jdk clojure bubblewrap gh
```

Node.js and npm must also be on `PATH`.

Chrome must also be installed and available as `google-chrome` on `PATH`.

## Build And Test Commands

Run all commands as your normal user, not with `sudo`.

From the repository root:

```bash
clojure -X:test
npm run test:cljs:node
npm run test:cljs:browser
clojure -T:build jar
```

Alias summary:

- `clojure -X:test`: JVM tests
- `npm run test:cljs:node`: ClojureScript tests on Node
- `npm run test:cljs:browser`: ClojureScript tests in headless Chrome
- `clojure -T:build jar`: package build

## Verified Working State

The following commands were verified successfully in this repository as user `jessica`:

```bash
clojure -X:test
npm run test:cljs:node
npm run test:cljs:browser
clojure -T:build jar
```

## Operational Notes

- Do not run normal project work with `sudo`, or the generated files and caches may become owned by `root`.
- If you accidentally run build steps as `root`, fix ownership before continuing.
- Browser tests require headless Chrome or Chrome to be installed and available on `PATH`.
