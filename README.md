#cljs-ajax

simple Ajax client for ClojureScript

## Usage

Leiningen

```clojure
[cljs-ajax "0.2.0"]
```
Note that there are breaking changes since 0.1, detailed near the bottom of this readme.

The client provides an easy way to send Ajax queries to the server using `GET`, and `POST` functions.
It also provides a simple way using `ajax-request`.

There are three formats currently supported for communicating with the server:  `:json`, `:edn` and `:raw`.
(`:raw` will send parameters up using normal form submission and return the raw text.)

## GET/POST

The `GET` and `POST` helpers accept a URI followed by a map of options:

* `:handler` - the handler function for successful operation should accept a single parameter which is the deserialized response
* `:error-handler` - the handler function for errors, should accept an error response (detailed below)
* `:format` - the format for the request.  If you leave this blank, it will use `:raw`
* `:response-format`  the response format.  If you leave this blank, it will detect the format from the Content-Type header
* `:params` - The parameters that will be sent with the request.  Format dependent: `:edn` can send anything, `:json` and `:raw` need to be given a map.  `GET` will add params onto the query string, `POST` will put the params in the body.
* `:timeout` - The ajax call's timeout.  30 seconds if left blank.

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

(POST "/send-message"
        {:params {:message "Hello World"
                  :user    "Bob"}
         :handler handler
         :error-handler error-handler
         :response-format :json
         :keywords? true})
```

## Error Responses

An error response is a map with the following keys passed to it:

* `:status` - the HTTP status code
* `:status-text` - the HTTP status message, or feedback from a parse failure
* `:response` - the EDN/JSON response if it's valid
* `:original-text` The response as raw text (if parsing failed)
* `:is-parse-error` Is true if this is feedback from a parse failure
* `:parse-error` If the server returned an error, and that then failed to parse, the map contains the error, and this contains the parse failure

The `error-handler` for `GET` and `POST` is passed one parameter which is an error response.

## ajax-request

The `ajax-request` is the simple interface.  It differs from the GET and POST API as follows:
* You can use your own formats.
* You can't use keywords to specify formats.  The API is harder to use.
* It compiles to smaller code when advanced optimizations are switched on.
* It doesn't do Content-Type discovery.
* There's only one handler, so you have to handle errors.

The parameters are: uri, method (`:get` or `:post` etcetera) and options.
* `:format` - a keyword indicating the response format, can be either `:json`, `:edn` or `:raw`\*, defaults to `:edn`
* `:handler` - A function that takes a single argument `[ok result]`.  The result will be the response if true and the error response if false.
* `:params` - The parameters that will be sent with the request.  Same as GET and POST.
* `:timeout` - The ajax call's timeout.  30 seconds if left blank.

### Formats

A format is a map with the following keys:
* `:content-type` - The content type to send to the server
* `:write` - A function taking params and returning a string
* `:description` - A description of the format, for use in error messages.
* `:read` - A function that takes the underlying `goog.net.XhrIo` and converts it to a response.  Exceptions thrown by this will be caught.

A request format is just a `:content-type` and `:write`.  A response format is a `:description` and `:read`.
The function `codec` takes a request format and a response format and creates a single format map.

The following functions are provided to construct format objects:  (they have no parameters except where specified)
* `json-request-format`
* `json-response-format` takes a map of JSON specific settings (see above)
* `json-format` both of the above:  takes a map of JSON specific settings
* `edn-request-format`
* `edn-response-format`
* `edn-format` both of the above
* `url-request-format`  (submits parameters as a normal form submission)
* `raw-response-format`
* `raw-format` both of the above

### Example

```clj

(defn handler2 [[ok response]]
  (if ok
    (.log js/console (str response))
    (.error js/console (str response)))

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

## Breaking Changes Since 0.1

* `ajax-request`'s API has significantly changed.  It used to be pretty equivalent to GET and POST.
* `:keywordize-keys` is now `:keywords?`
* `:format` used to be the response format.  The request format used to always to be `:url`

## Contributing

All pull requests are welcome, but we do ask that any changes come with tests that demonstrate the original problem.  For this, you'll need to get the test environment working.  First, you need to install phantom.js somewhere on your path.  We recommend you download directly from the website [http://phantomjs.org/download.html].  (Do _not_ give into the temptation of using apt-get on Ubuntu; it installs v1.4 and won't work.)

After that `lein test` should run the tests.
