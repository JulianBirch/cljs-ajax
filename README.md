#cljs-ajax

simple Ajax client for ClojureScript

## Usage

Leiningen

[![Leiningen version](http://clojars.org/cljs-ajax/latest-version.svg)](http://clojars.org/cljs-ajax)

Note that there are breaking changes since 0.3, detailed near the bottom of this readme. One of them is serious if you're using js/FormData or binary blobs.

The client provides an easy way to send Ajax queries to the server using `GET`, `POST`, and `PUT` functions.
It also provides a simple way using `ajax-request`.

There are four formats currently supported for communicating with the server:  `:transit`, `:json`, `:edn` and `:raw`.
(`:raw` will send parameters up using normal form submission and return the raw text.)

## GET/POST examples

```clojure

(ns foo
  (:require [ajax.core :refer [GET POST]]))

(defn handler [response]
  (.log js/console (str response)))

(defn error-handler [{:keys [status status-text]}]
  (.log js/console (str "something bad happened: " status " " status-text)))

(GET "/hello")

(GET "/hello" {:params {:a 0
                        :b [1 2]
                        :c {:d 3 :e 4}
                        "f" 5}})
;;; writes "a=0&b[0]=1&b[1]=2&c[d]=3&c[e]=4&f=5"

(GET "/hello" {:handler handler
               :error-handler error-handler})

(POST "/hello")

(POST "/send-message"
        {:params {:message "Hello World"
                  :user    "Bob"}
         :handler handler
         :error-handler error-handler})

```

For full usage see [client side details](doc/client.md).

## Handling responses on the server

The easiest way to respond is to use ring middleware which will transparently convert your response format into ordinary clojure maps.  Middleware is available for the `:transit`, `:json` and `:edn` formats.  Please see [server side details](doc/server.md).

## Cross origin requests

cljs-ajax supports cross origin requests, but you must be careful when setting server side headers.  For details see [here](doc/server.md).

## ajax-request

The `ajax-request` is the simple interface.  It differs from the GET and POST API as follows:
* You can use your own formats.
* You can't use keywords to specify formats.  The API is harder to use.
* It compiles to smaller code when advanced optimizations are switched on.
* It doesn't do Content-Type discovery.
* There's only one handler, so you have to handle errors.

For details see [ajax-request](doc/ajax.md).

## Interceptors

[Interceptors](doc/interceptors.md) enable you to pre-process both requests and responses.

## Breaking Changes Since 0.3

* EDN support is now in its own namespace: `ajax.edn`
* The `:edn` keyword no longer works.
* The definition of the `AjaxImpl` protocol has changed.
* Submitting a `GET` with `:params {:a [10 20]}` used to produce `?a=10&a=20`. It now produces `?a[0]=10&a[1]=20`.
* `js/FormData` and `js/ArrayBuffer` &c are now submitted using a `:body` tag, not the `:params` tag
* [Interceptors](doc/interceptors.md) were added. Whilst not strictly speaking a breaking change, the optimal way of solving certain problems has definitely changed.

## Breaking Changes Since 0.2

* The default response format is now transit.
* The default request format is now transit.
* Format detection is now "opt in" with `ajax-request`.  See [formats](doc/formats.md).  It remains the default with `GET` and `POST`.  This means that code using `ajax-request` will be smaller with advanced optimizations.
* `:is-parse-error`, `:timeout?` and `:aborted?` have been removed, in favour of `:failure`
* `ajax-request` now has `:format` and `:response-format` parameters, same as `POST`
* The functions that returned merged request/response formats have been removed.

## Breaking Changes Since 0.1

* `ajax-request`'s API has significantly changed.  It used to be pretty equivalent to GET and POST.
* `:keywordize-keys` is now `:keywords?`
* `:format` used to be the response format.  The request format used to always to be `:url`

## Contributing

All pull requests are welcome, but we do ask that any changes come with tests that demonstrate the original problem.  For this, you'll need to get the test environment working.  First, you need to install phantom.js somewhere on your path.  We recommend you download directly from the website [http://phantomjs.org/download.html].  (Do _not_ give into the temptation of using apt-get on Ubuntu; it installs v1.4 and won't work.)

Second, you need to install [ClojureScript.test](https://github.com/cemerick/clojurescript.test) as a leiningen plugin.  The instructions are in the README.

After that `lein test` should run the tests.

## License

Distributed under the Eclipse Public License, the same as Clojure.


