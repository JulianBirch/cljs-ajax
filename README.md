#cljs-ajax

simple Ajax client for ClojureScript

## Usage

Leiningen

!["Leiningen version"](https://clojars.org/cljs-ajax/latest-version.svg)

Note that there are breaking changes since 0.1, detailed near the bottom of this readme.

The client provides an easy way to send Ajax queries to the server using `GET`, `POST`, and `PUT` functions.
It also provides a simple way using `ajax-request`.

There are three formats currently supported for communicating with the server:  `:transit`, `:json`, `:edn` and `:raw`.
(`:raw` will send parameters up using normal form submission and return the raw text.)

## GET/POST/PUT

The `GET`, `POST`, and `PUT` helpers accept a URI followed by a map of options:

* `:handler` - the handler function for successful operation should accept a single parameter which is the deserialized response
* `:error-handler` - the handler function for errors, should accept an error response (detailed below)
* `:finally` - a function that takes no parameters and will be triggered during the callback in addition to any other handlers
* `:format` - the format for the request.  If you leave this blank, it will use `:transit` as the default
* `:response-format`  the response format.  If you leave this blank, it will detect the format from the Content-Type header
* `:params` - the parameters that will be sent with the request,  format dependent: `:transit` and `:edn` can send anything, `:json` and `:raw` need to be given a map.  `GET` will add params onto the query string, `POST` will put the params in the body
* `:timeout` - the ajax call's timeout.  30 seconds if left blank
* `:headers` - a map of the HTTP headers to set with the request

Everything can be blank, but if you don't provide an `:error-handler` you're going to have a bad time.

### JSON specific settings

The following settings affect the interpretation of JSON responses:  (You must specify `:response-format` as `:json` to use these.)

* `:keywords?` - true/false specifies whether keys in maps will be keywordized
* `:prefix` - the prefix to be stripped from the start of the JSON response. e.g. `while(1);`.  You should *always* use this if you've got a GET request.

### GET/POST examples

```clojure
(ns foo
  (:require [ajax.core :refer [GET POST]]))

(defn handler [response]
  (.log js/console (str response)))

(defn error-handler [{:keys [status status-text]}]
  (.log js/console (str "something bad happened: " status " " status-text)))

(GET "/hello")

(GET "/hello" {:params {:foo "foo"}})

(GET "/hello" {:handler handler
               :error-handler error-handler})

(POST "/hello")

(POST "/send-message"
        {:params {:message "Hello World"
                  :user    "Bob"}
         :handler handler
         :error-handler error-handler})

; Will send file inputs that are in the form
(POST "/send-form-modern" {:params (js/FormData. form-element)})

; Send file explicitly
(let [form-data (doto
                    (js/FormData.)
                  (.append "id" "10")
                  (.append "file" js-file-value "filename.txt"))]
  (POST "/send-file" {:params form-data
                      :response-format (raw-response-format)
                      :timeout 100}))

(POST "/send-message"
        {:params {:message "Hello World"
                  :user    "Bob"}
         :handler handler
         :error-handler error-handler
         :response-format :json
         :keywords? true})
         
(PUT "/add-item"
     {:params {:id 1 :name "mystery item"}})         
```

### FormData support

Note that `js/FormData` is not supported before IE10, so if you need to support those browsers, don't use it.  `cljs-ajax` doesn't have any other support for file uploads (although pull requests are welcome).  Also note that you *must* include `ring.middleware.multipart-params\wrap-multipart-params` in your ring handlers as FormData always submits as multipart even if you don't use it to submit files.

### Error Responses

An error response is a map with the following keys passed to it:

* `:status` - the HTTP status code
* `:status-text` - the HTTP status message, or feedback from a parse failure
* `:response` - the transit/EDN/JSON response if it's valid
* `:original-text` The response as raw text (if parsing failed)
* `:is-parse-error` Is true if this is feedback from a parse failure
* `:parse-error` If the server returned an error, and that then failed to parse, the map contains the error, and this contains the parse failure

The `error-handler` for `GET`, `POST`, and `PUT` is passed one parameter which is an error response.

### Handling responses on the server

If you're looking for working examples of how to reply to cljs-ajax from a ring server, take a look at [the integration test server source code](dev/user.clj).

If you're using transit, take a look at [ring-transit](https://github.com/jalehman/ring-transit).  It will populate the request `:params` with the contents of the transit request.

For handling JSON requests use [ring-json](https://github.com/ring-clojure/ring-json) middleware instead.  This populates the data in the `:body` tag.  However, note that it does not provide protection against [JSON hijacking](https://github.com/ring-clojure/ring-json/issues/14) yet, so do not use it with JSON format GETs, even for internal websites.  (As an aside, if you need lower level JSON access, e.g. for formatting, we'd recommend [Cheshire](https://github.com/dakrone/cheshire) over `data.json`.)

For using EDN then you may wish to take a look at [ring-edn](https://github.com/tailrecursion/ring-edn) middleware. It will populate the request `:params` with the contents of the EDN request.

If your tastes/requirements run more to a standardized multi-format REST server, you might want to investigate [ring-middleware-format](https://github.com/ngrunwald/ring-middleware-format).

## ajax-request

The `ajax-request` is the simple interface.  It differs from the GET and POST API as follows:
* You can use your own formats.
* You can't use keywords to specify formats.  The API is harder to use.
* It compiles to smaller code when advanced optimizations are switched on.
* It doesn't do Content-Type discovery.
* There's only one handler, so you have to handle errors.

It has a single parameter, which is a map with the following members:
The parameters are: uri, method (`:get` or `:post` etcetera) and options.
* `:uri`
* `:method` - (`:get` or `:post` etcetera)  
* `:format` and `:response-format`, documented in the [formats documentation](formats.md)
* `:handler` - A function that takes a single argument `[ok result]`.  The result will be the response if true and the error response if false.
* `:params` - The parameters that will be sent with the request.  Same as for GET and POST.
* `:timeout` - The ajax call's timeout.  30 seconds if left blank.

### `ajax-request` examples

```clj
(defn handler2 [[ok response]]
  (if ok
    (.log js/console (str response))
    (.error js/console (str response))))

(ajax-request "/send-message" :post
        {:params {:message "Hello World"
                 :user    "Bob"}
         :handler handler2
         :format (json-format {:keywords? true})})

(ajax-request "/send-message" :post
        {:params {:message "Hello World"
                  :user    "Bob"}
         :handler handler2
         :format (codec (url-request-format) (json-response-format {:keywords? true}))})
```

## Breaking Changes Since 0.2

* The default response format is now transit.
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


