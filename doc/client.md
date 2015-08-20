# GET/POST/PUT

The `GET`, `POST`, and `PUT` helpers accept a URI followed by a map of options:

* `:handler` - the handler function for successful operation should accept a single parameter which is the deserialized response
* `:error-handler` - the handler function for errors, should accept an error response (detailed below)
* `:finally` - a function that takes no parameters and will be triggered during the callback in addition to any other handlers
* `:format` - the format for the request.  If you leave this blank, it will use `:transit` as the default
* `:response-format`  the response format.  If you leave this blank, it will detect the format from the Content-Type header
* `:params` - the parameters that will be sent with the request,  format dependent: `:transit` and `:edn` can send anything, `:json` and `:raw` need to be given a map.  `GET` will add params onto the query string, `POST` will put the params in the body
* `:timeout` - the ajax call's timeout.  30 seconds if left blank
* `:headers` - a map of the HTTP headers to set with the request
* `:with-credentials` - a boolean, whether to set the `withCredentials` flag on the XHR object.
* `:body` the exact data to send with in the request. If specified, both `:params` and `:request-format` are ignored.  Note that you can submit js/FormData and other "raw" javascript types through this.
* `:interceptors` - the [interceptors](doc/Interceptors.md) to run for this request. If not set, runs contents of the `default-interceptors` global atom. This is an empty vector by default. For more information, visit the [interceptors page](doc/Interceptors.md).

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

(GET "/hello" {:params {:a 0
                        :b [1 2]
                        :c {:d 3 :e 4}
                        "f" 5}})
;;; writes "a=0&b[0]=1&b[1]=2&c[d]=3&c[e]=4&f=5"

(GET "/hello" {:handler handler
               :error-handler error-handler})

(POST "/hello")

; Post a transit format message
(POST "/send-message"
        {:params {:message "Hello World"
                  :user    "Bob"}
         :handler handler
         :error-handler error-handler})


; Will send file inputs that are in the form
(POST "/send-form-modern" {:body (js/FormData. form-element)})

; Send file explicitly
(let [form-data (doto
                    (js/FormData.)
                  (.append "id" "10")
                  (.append "file" js-file-value "filename.txt"))]
  (POST "/send-file" {:body form-data
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

(ajax-handler {:url "/generate.png" ; Request a PNG and get it back as a js/ArrayBuffer
               :api (js/XMLHttpRequest.)
               :response-format {:content-type "image/png" :description "PNG image" :read -body :type :arraybuffer})
```

### FormData support

Note that `js/FormData` is not supported before IE10, so if you need to support those browsers, don't use it.  `cljs-ajax` doesn't have any other support for file uploads (although pull requests are welcome).  Also note that you *must* include `ring.middleware.multipart-params\wrap-multipart-params` in your ring handlers as FormData always submits as multipart even if you don't use it to submit files.

### Error Responses

An error response is a map with the following keys passed to it:

* `:status` - the HTTP status code, numeric.  A dummy number is provided if you didn't get to the server.
* `:status-text` - the HTTP status message, or feedback from a parse failure
* `:failure` - a keyword describing the type of failure
  * `:error` an error on the server
  * `:parse` the response from the server failed to parse
  * `:aborted` the client aborted the request
  * `:timeout` the request timed out

If the failure had a valid response, it will be stored in the `:response` key.

If the error is `:parse` then the raw text of the response will be stored in `:original-text`.

Finally, if the server returned an error, and that then failed to parse, it will return the error map, but add a key `:parse-error` that contains the parse failure.

The `error-handler` for `GET`, `POST`, and `PUT` is passed one parameter which is an error response.  Note that *in all cases* either `handler` or `error-handler` will be called.  You should never get an exception returned by `GET`, `POST` etcetera.
