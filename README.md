# cljs-ajax

simple Ajax client for ClojureScript and Clojure

[![Build Status](https://travis-ci.org/JulianBirch/cljs-ajax.svg?branch=master)](https://travis-ci.org/JulianBirch/cljs-ajax)

`cljs-ajax` exposes the same interface (where useful) in both Clojure and ClojureScript. On ClojureScript it operates as a wrapper around [`goog.net.XhrIo`](https://developers.google.com/closure/library/docs/xhrio?hl=en) or [`js/XmlHttpRequest`](https://developer.mozilla.org/en-US/docs/Web/API/XMLHttpRequest), while on the JVM it's a wrapper around the [Apache HttpAsyncClient](https://hc.apache.org/httpcomponents-asyncclient-dev/) library. 

In addition to this document, there's an [FAQ](docs/faq.md), a [change log](CHANGES.md) and a [contribution document](CONTRIBUTING.md). Furthermore, there is detailed documentation on specific features and design advice in the [docs folder](docs).

## Usage

Leiningen/Boot: `[cljs-ajax "0.7.5"]`

[![Leiningen version](http://clojars.org/cljs-ajax/latest-version.svg)](http://clojars.org/cljs-ajax)

The client provides an easy way to send Ajax requests to the server using `GET`, `POST`, and `PUT` functions. It also provides a simple way using `ajax-request`. All requests are asynchronous, accepting callback functions for response and error handling.

There are four formats currently supported for communicating with the server:  `:transit`, `:json`, `:text` and `:raw`.
(`:text` will send parameters up using normal form submission and return the raw text. `:raw` does the same, but on the JVM it returns the body's `java.io.InputStream` and *doesn't close it*.)

For advice on how to set up the server side in Clojure to work with `cljs-ajax`, please see the page on [handling responses on the server](docs/server.md).

## GET/POST/PUT

The `GET`, `POST`, and `PUT` helpers accept a URI followed by a map of options:

* `:handler` - the handler function for successful operation should accept a single parameter which is the deserialized response. If you do not provide a handler, the contents of the `default-handler` atom will be called instead. By default this is `println`.
* `:progress-handler` - the handler function for progress events. **This handler is only available when using the `goog.net.XhrIo` API**
* `:error-handler` - the handler function for errors, should accept an error response (detailed below). If you do not provide an error-handler, the contents of the `default-error-handler` atom will be called instead. By default this is `println` for Clojure and writes an error to the console for ClojureScript.
* `:finally` - a function that takes no parameters and will be triggered during the callback in addition to any other handlers
* `:format` - specifies the format for the body of the request (Transit, JSON, etc.). Also sets the appropriate `Content-Type` header.  Defaults to `:transit` if not provided.
* `:response-format` - specifies that you'd like to receive a certain format of data from the server (by setting the `Accept` header and forcing the response to be parsed as the desired format).  If not provided, a permissive `Accept` header will be sent, and the response body will be interpreted according to the response's `Content-Type` header.
* `:params` - the parameters that will be sent with the request,  format dependent: `:transit` and `:edn` can send anything, `:json`, `:text` and `:raw` need to be given a map.  `GET` will add params onto the query string, `POST` will put the params in the body
* `:url-params` - parameters that will be added onto query string. In the case of a GET request, parameters defined here will replace parameters defined in `:params`.
* `:timeout` - the ajax call's timeout in milliseconds.  30 seconds if left blank
* `:headers` - a map of the HTTP headers to set with the request
* `:cookie-policy` - a keyword for the cookie management specification. **Only available in Java**. Optional. One of `:none`, `:default`, `:netscape`, `:standard`, `:standard-strict`.

* `:with-credentials` - a boolean, whether to set the `withCredentials` flag on the XHR object.
* `:body` the exact data to send with in the request. If specified, both `:params` and `:request-format` are ignored.  Note that you can submit js/FormData and other "raw" javascript types through this.
* `:interceptors` - the [interceptors](docs/interceptors.md) to run for this request. If not set, runs contents of the `default-interceptors` global atom. This is an empty vector by default. For more information, visit the [interceptors page](docs/interceptors.md).

Note that you can override `default-handler` and `default-error-handler`, but they are global to your application/page.

### JSON specific settings

The following settings affect the interpretation of JSON responses:  (You must specify `:response-format` as `:json` to use these.)

* `:keywords?` - true/false specifies whether keys in maps will be keywordized
* `:prefix` - the prefix to be stripped from the start of the JSON response. e.g. `while(1);` which is added by some APIs to [prevent JSON hijacking](https://stackoverflow.com/questions/2669690/why-does-google-prepend-while1-to-their-json-responses).  You should *always* use this if you've got a GET request.

### GET specific settings

The `:vec-strategy` setting affects how sequences are written out. 
A `:vec-strategy` of `:java` will render `{:a [1 2]}` as `a=1&a=2`.
A `:vec-strategy` of `:rails` will render `{:a [1 2]}` as `a[]=1&a[]=2`. This is also the correct setting for working with HTTP.

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
;;; writes "a=0&b=1&b=2&c[d]=3&c[e]=4&f=5"

(GET "/hello" {:params {:a 0
                        :b [1 2]
                        :c {:d 3 :e 4}
                        "f" 5}
               :vec-strategy :rails})
;;; writes "a=0&b[]=1&b[]=2&c[d]=3&c[e]=4&f=5"


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

; Send file explicitly, ClojureScript specific
(let [form-data (doto
                    (js/FormData.)
                  (.append "id" "10")
                  (.append "file" js-file-value "filename.txt"))]
  (POST "/send-file" {:body form-data
                      :response-format (raw-response-format)
                      :timeout 100}))

; Send multiple files explicitly, ClojureScript specific
; input-element is an html element of file type.
(let [form-data (let [f-d (js/FormData.)
                      files (.-files input-element)
                      name (.-name input-element)]
                  (doseq [file-key (.keys js/Object files)]
                    (.append f-d name (aget files file-key)))
                  f-d)]
  (POST "/send-files" {:body form-data
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
     
(GET {:url "/generate.png" ; Request a PNG and get it back as a js/ArrayBuffer
      :response-format {:content-type "image/png" :description "PNG image" :read -body :type :arraybuffer})
```

### FormData support

Note that `js/FormData` is not supported before IE10, so if you need to support those browsers, don't use it.  `cljs-ajax` doesn't have any other support for file uploads (although pull requests are welcome).  Also note that you *must* include `ring.middleware.multipart-params/wrap-multipart-params` in your ring handlers as `js/FormData` always submits as multipart even if you don't use it to submit files.

### Error Responses

An error response is a map with the following keys passed to it:

* `:status` - the HTTP status code, numeric. A dummy number is provided if you didn't get to the server.
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

## ajax-request

The `ajax-request` is the simple interface.  It differs from the GET and POST API as follows:

* **You can't use keywords to specify formats. You must provide a complete format in your request (see the [formats documentation](docs/formats.md) for more details)**
* The API is harder to use.
* You can use your own formats.
* It compiles to smaller code when advanced optimizations are switched on.
* It doesn't do Content-Type discovery.
* There's only one handler, so you have to handle errors.

It has a single parameter, which is a map with the following members:
The parameters are: 
* `:uri`
* `:method` - (`:get`, `"GET"`, `:post` or `"POST"` etcetera)  
* `:format` and `:response-format`, documented in the [formats documentation](docs/formats.md)
* `:handler` - A function that takes a single argument `[ok result]`.  The result will be the response if true and the error response if false.

The following parameters are the same as in the `GET`/`POST` easy api:
* `:params` - the parameters that will be sent with the request,  format dependent: `:transit` and `:edn` can send anything, `:json` and `:raw` need to be given a map.  `GET` will add params onto the query string, `POST` will put the params in the body
* `:timeout` - the ajax call's timeout.  30 seconds if left blank
* `:headers` - a map of the HTTP headers to set with the request
* `:cookie-policy` - a keyword for the cookie management specification. **Only available in Java**. Optional. One of `:none`, `:default`, `:netscape`, `:standard`, `:standard-strict`.
* `:with-credentials` - a boolean, whether to set the `withCredentials` flag on the XHR object.
* `:interceptors` - the [interceptors](docs/interceptors.md) to run for this request. If not set, runs contents of the `default-interceptors` global atom. This is an empty vector by default. For more information, visit the [interceptors page](docs/interceptors.md).

### `ajax-request` examples

```clj
(defn handler2 [[ok response]]
  (if ok
    (.log js/console (str response))
    (.error js/console (str response))))

(ajax-request
        {:uri "/send-message"
         :method :post
         :params {:message "Hello World"
                  :user    "Bob"}
         :handler handler2
         :format (json-request-format)
         :response-format (json-response-format {:keywords? true})})

(ajax-request
        {:uri "/send-message"
         :method :post
         :params {:message "Hello World"
                  :user    "Bob"}
         :handler handler2
         :format (url-request-format) 
         :response-format (json-response-format {:keywords? true})})
```

These examples will use the Google Closure library `XhrIo` API. If you want to use `XMLHttpRequest` API directly, add `:api (js/XMLHttpRequest.)` to the map.

## License

Distributed under the Eclipse Public License, the same as Clojure.


