## Cross Origin Requests

By default the browser blocks ajax requests from a server which is different to the current page.  To enable such cross origin requests configure your server code as follows.

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

`Access-Control-Allow-Origin` is the standard header telling the browser to permit a cross origin request.  Set it to the server you expect the ajax requests from or a wildcard (less secure).  For Google Chrome we must also include the header `Access-Control-Allow-Headers` to prevent it stripping the `Content-Type` header from our requests.  We must also change the request method from GET or POST to ANY.  The browser will actually submit two requests.  The first is an OPTIONS request submitted in order to probe the endpoint.  The second is the main GET or POST request.  Early versions of compojure may not support this correctly.
