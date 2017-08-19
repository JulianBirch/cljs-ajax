## Version 0.7

* Submitting a `GET` with `:params {:a [10 20]}` used to produce `?a=10&a=20` in 0.3, it now does so again. I'd like to apologise to everyone for this particular breakage, and for the long time it's taken to fix. I know a lot of people have had to work around this issue.
* If you want to get `?a[]=10&a[]=20` instead, add `:vec-strategy :rails` to your map.
* There is some work being done with (progress handlers)[(https://github.com/JulianBirch/cljs-ajax/pull/183)]. Be warned that this API is subject to change in later versions and should be considered unsupported at the present time.
* The json implementation now defaults to the browser native code. You can revert to the previous Google Closure behaviour using the notes in (docs/formats.md)[formats.md].
* It is intended that the default implementation for JavaScript uses XmlHttpRequest directly and XhrIO is deprecated in 0.8. Be warned. 
* `default-formats` is now an atom.
* A lot of the internal implementation has moved around into specific files. If you rely upon the documented API this should not cause you any problems.

## Version 0.6

- Bug fix: [close the apache closeable client](https://github.com/JulianBirch/cljs-ajax/pull/178)
- [better error messages with keywords as formats](https://github.com/JulianBirch/cljs-ajax/pull/161)
- The [PURGE method](https://github.com/JulianBirch/cljs-ajax/pull/169) is now supported in the easy API. Whilst not part of the HTTP standard, it's sufficiently common on things like Varnish that it seems worth supporting.
- The handling of `+` under [url encoding](https://github.com/JulianBirch/cljs-ajax/pull/163) got fixed. This was a breaking change, but too important not to fix.
- Experimental [nodejs support](https://github.com/JulianBirch/cljs-ajax/pull/166). Whilst this works (albeit requiring you to also include @pupeno/xmlhttprequest from npm), it's not part of our test suite. We'd welcome further contributions on this.

## Version 0.4

cljs-ajax never had a stable 0.4.0 release, so there's no breaking changes.

## Version 0.5 (breaking only)

* EDN support is now in its own namespace: `ajax.edn`
* The `:edn` keyword no longer works.
* The definition of the `AjaxImpl` protocol has changed.
* Submitting a `GET` with `:params {:a [10 20]}` used to produce `?a=10&a=20`. It now produces `?a[0]=10&a[1]=20`.
* `js/FormData` and `js/ArrayBuffer` &c are now submitted using a `:body` tag, not the `:params` tag
* [Interceptors](docs/interceptors.md) were added. Whilst not strictly speaking a breaking change, the optimal way of solving certain problems has definitely changed.
* Keywords that are used as request parameter values are stringified using `(str my-keyword)` instead of `(name my-keyword)` causing leading colons to be preserved.

## Version 0.3 (breaking only)

* The default response format is now `:transit`.
* The default request format is now `:transit`.
* Format detection is now "opt in" with `ajax-request`.  See [formats.md](docs/formats.md).  It remains the default with `GET` and `POST`.  This means that code using `ajax-request` will be smaller with advanced optimizations.
* `:is-parse-error`, `:timeout?` and `:aborted?` have been removed, in favour of `:failure`
* `ajax-request` now has `:format` and `:response-format` parameters, same as `POST`
* The functions that returned merged request/response formats have been removed.

## Version 0.2 (breaking only)

* `ajax-request`'s API has significantly changed.  It used to be pretty equivalent to GET and POST.
* `:keywordize-keys` is now `:keywords?`
* `:format` used to be the response format.  The request format used to always to be `:url`
