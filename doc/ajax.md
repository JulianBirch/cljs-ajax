# ajax-request

The `ajax-request` is the simple interface.  It differs from the GET and POST API as follows:
* You can use your own formats.
* You can't use keywords to specify formats.  The API is harder to use.
* It compiles to smaller code when advanced optimizations are switched on.
* It doesn't do Content-Type discovery.
* There's only one handler, so you have to handle errors.

It has a single parameter, which is a map with the following members:
The parameters are:
* `:uri`
* `:method` - (`:get`, `"GET"`, `:post` or `"POST"` etcetera)
* `:format` and `:response-format`, documented in the [formats documentation](formats.md)
* `:handler` - A function that takes a single argument `[ok result]`.  The result will be the response if true and the error response if false.

The following parameters are the same as in the `GET`/`POST` easy api:
* `:params` - the parameters that will be sent with the request,  format dependent: `:transit` and `:edn` can send anything, `:json` and `:raw` need to be given a map.  `GET` will add params onto the query string, `POST` will put the params in the body
* `:timeout` - the ajax call's timeout.  30 seconds if left blank
* `:headers` - a map of the HTTP headers to set with the request
* `:with-credentials` - a boolean, whether to set the `withCredentials` flag on the XHR object.
* `:interceptors` - the [interceptors](doc/interceptors.md) to run for this request. If not set, runs contents of the `default-interceptors` global atom. This is an empty vector by default. For more information, visit the [interceptors page](doc/interceptors.md).

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
