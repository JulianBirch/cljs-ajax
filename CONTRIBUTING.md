`cljs-ajax` is fundamentally a collaborative project. Each issue raised, every question asked informs its design. All pull requests are welcome, but we do ask that any changes come with tests that demonstrate the original problem. For this, you'll need to get the test environment working.

See [DEVELOPMENT.md](DEVELOPMENT.md) for the current setup instructions.

The verified test setup uses Leiningen, Node.js/npm, headless Chrome, and Karma. The older PhantomJS-based guidance is no longer the working path for this repository.
Obviously, commits that fail the Travis build are unlikely to be accepted.
New releases tend to get announced on Twitter with credit to contributors. 
If you want an @mention, make sure to tell us your handle.
Equally, if you don't want to be mentioned, make sure to tell us that.

If you're looking for something to do some open source work, take a look at the issue tracker. `cljs-ajax` always has a lot of [issues marked "PR welcome"](https://github.com/JulianBirch/cljs-ajax/issues?q=is%3Aissue+is%3Aopen+label%3A%22PR+welcome%22). Drop a line if you're interested in working on anything.

After that, the usual test commands are:

- `lein clj-test`
- `lein cljs-node-test`
- `lein cljs-test`
- `lein run-tests`

If you just think the documentation needs improving, please send a PR. We've accepted over 100 documentation PRs already. 
