# Development Setup

This document describes the requirements currently needed to build and test `cljs-ajax` on a clean machine.

## System Requirements

Install these system packages first:

- Java JDK
- Clojure CLI
- Node.js and npm
- Google Chrome
- `bubblewrap`

On Linux, install Java, Node.js/npm, Chrome, and other system packages using your distro package manager. For example on Ubuntu:

```bash
sudo apt-get update
sudo apt-get install -y default-jdk curl rlwrap bubblewrap
```

Install the official Clojure CLI with the Linux installer script:

```bash
curl -L -O https://github.com/clojure/brew-install/releases/latest/download/linux-install.sh
chmod +x linux-install.sh
sudo ./linux-install.sh
```

Node.js and npm must also be on `PATH`, and Chrome must be installed and available as `google-chrome` on `PATH`.

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
clojure -T:build jar
```

Alias summary:

- `clojure -X:test`: JVM tests
- `npm run test:cljs:node`: ClojureScript tests on Node
- `npm run test:cljs:browser`: ClojureScript tests in headless Chrome
- `clojure -T:build jar`: package build

## Verified Working State

The working command set for this repository is:

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
