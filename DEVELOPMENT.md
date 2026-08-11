# Development Setup

This document describes the requirements currently needed to build and test `cljs-ajax` on a clean machine.

## System Requirements

Install these system packages first:

- Java JDK
- Clojure CLI
- Node.js and npm
- Google Chrome or Chromium
- `bubblewrap`

On Linux, install the packages your distro provides with its package manager. For example on Ubuntu, install the base system packages first:

```bash
sudo apt-get update
sudo apt-get install -y default-jdk curl rlwrap bubblewrap
```

Then install Node.js/npm and Chrome or Chromium using the method that fits your Ubuntu setup.

Install the official Clojure CLI with the Linux installer script:

```bash
curl -L -O https://github.com/clojure/brew-install/releases/latest/download/linux-install.sh
chmod +x linux-install.sh
sudo ./linux-install.sh
```

Node.js and npm must also be on `PATH`, and Chrome or Chromium must be installed and available on `PATH`. If needed, set `CHROME_BIN` to the browser executable path instead.

## Repository Setup

From the repository root, install the npm dependencies before running any npm-based commands:

```bash
npm ci
```

## Build And Test Commands

Run all commands as your normal user, not with `sudo`.

From the repository root:

```bash
clojure -X:test
npm run test:cljs:node
npm run test:cljs:browser
npm run test:integration
clojure -T:build jar
```

Alias summary:

- `clojure -X:test`: JVM tests, including the property based tests for the
  Apache implementation
- `npm run test:cljs:node`: ClojureScript tests on Node
- `npm run test:cljs:browser`: ClojureScript tests in headless Chrome
- `npm run test:integration`: shared JVM + dedicated Node + dedicated browser integration tests against one live local server
- `clojure -X:mutation`: mutation testing of the Apache implementation against
  the property suite
- `clojure -T:build jar`: package build

## Verified Working State

The working command set for this repository is:

```bash
clojure -X:test
npm run test:cljs:node
npm run test:cljs:browser
npm run test:integration
clojure -T:build jar
```

## Operational Notes

- Do not run normal project work with `sudo`, or the generated files and caches may become owned by `root`.
- If you accidentally run build steps as `root`, fix ownership before continuing.
- Browser tests require headless Chrome or Chromium to be installed and available on `PATH`, or reachable via `CHROME_BIN`.
- `npm run test:integration` starts a local integration server and then runs the JVM, Node, and browser integration suites against it.
