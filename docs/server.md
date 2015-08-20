# Server

If you're looking for working examples of how to reply to cljs-ajax from a ring server, take a look at [the integration test server source code](../dev/user.clj).

If you're using transit, take a look at [ring-transit](https://github.com/jalehman/ring-transit).  It will populate the request `:params` with the contents of the transit request.

For handling JSON requests use [ring-json](https://github.com/ring-clojure/ring-json) middleware instead.  This populates the data in the `:body` tag.  However, note that it does not provide protection against [JSON hijacking](https://github.com/ring-clojure/ring-json/issues/14) yet, so do not use it with JSON format GETs, even for internal websites.  (As an aside, if you need lower level JSON access, e.g. for formatting, we'd recommend [Cheshire](https://github.com/dakrone/cheshire) over `data.json`.)

For using EDN then you may wish to take a look at [ring-edn](https://github.com/tailrecursion/ring-edn) middleware. It will populate the request `:params` with the contents of the EDN request.

If your tastes/requirements run more to a standardized multi-format REST server, you might want to investigate [ring-middleware-format](https://github.com/ngrunwald/ring-middleware-format).

## Cross Origin Requests

By default the browser blocks ajax requests from a server which is different to the current page.  To enable such cross origin requests add the `Access-Control-Allow-Origin` and `Access-Control-Allow-Headers` headers to your response as follows.

```clojure

;;using the compojure library

(defroutes my-routes
 ...
 (ANY "/my-endpoint" []
   {:status 200
    :headers {
              "Access-Control-Allow-Origin" "*"
              "Access-Control-Allow-Headers" "Content-Type"
              }
    :body body
    }))

```

`Access-Control-Allow-Origin` is the standard header telling the browser to permit a cross origin request.  Set it to the server you expect the ajax requests from or a wildcard (less secure).  For Google Chrome we must include the header `Access-Control-Allow-Headers` to prevent it stripping the `Content-Type` header from our requests.  We must also change the request method from GET or POST to ANY.  The browser will actually submit two requests.  The first is an OPTIONS request submitted in order to probe the endpoint.  The second is the main GET or POST request.  Early versions of compojure may not support this correctly.
