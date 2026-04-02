# Development Setup

This document describes the requirements currently needed to build and test `cljs-ajax` on a clean machine.

The commands below were verified on Ubuntu 24.04 and run successfully as the non-root user `jessica`.

## System Requirements

Install these system packages first:

- Java JDK
- Leiningen
- Node.js and npm
- Google Chrome
- `bubblewrap`
- `gh`

On Ubuntu, the working package set is:

```bash
sudo apt-get update
sudo apt-get install -y default-jdk leiningen bubblewrap gh
```

Node.js and npm must also be on `PATH`.

Chrome must also be installed and available as `google-chrome` on `PATH`.

## npm Requirements

This repository currently has no `package.json`, but two npm modules are still required for the test environment:

- `xmlhttprequest`
- `karma-cljs-test`

The global Karma runner and Chrome launcher are also required:

- `karma`
- `karma-chrome-launcher`
- `karma-cljs-test`

One working setup is:

```bash
sudo npm install -g karma karma-cli karma-chrome-launcher karma-cljs-test
cd /path/to/cljs-ajax
npm install --no-save --no-package-lock xmlhttprequest karma-cljs-test
```

Notes:

- `xmlhttprequest` is required by the Node test target through `src/ajax/xml_http_request.cljs`.
- `karma-cljs-test` is required by the Chrome `doo` test target.
- Because there is no `package.json`, these repo-local npm installs are intentionally transient.

## Build And Test Commands

Run all commands as your normal user, not with `sudo`.

From the repository root:

```bash
lein clean
lein clj-test
lein cljs-node-test
lein cljs-test
```

Alias summary from `project.clj`:

- `lein clj-test`: JVM tests
- `lein cljs-node-test`: ClojureScript tests on Node
- `lein cljs-test`: ClojureScript tests in headless Chrome
- `lein run-tests`: `clean`, `clj-test`, and `cljs-test`

## Verified Working State

The following commands were verified successfully in this repository as user `jessica`:

```bash
lein clj-test
lein cljs-node-test
lein cljs-test
```

## Operational Notes

- Do not run normal project work with `sudo`, or the generated files and caches may become owned by `root`.
- If you accidentally run build steps as `root`, fix ownership before continuing.
- The current `CONTRIBUTING.md` mentions PhantomJS, but the working browser test path in this repository is `doo` with headless Chrome.
- The Node and browser test dependencies are partly documented in prose only. They are not yet declared in repo metadata.
