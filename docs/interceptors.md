# Interceptors

Interceptors allow you to customize the behaviour of `cljs-ajax`. Examples of why you might want to do this include:
* Adding a session token to every request
* Special casing `DELETE` on Google App Engine
* Treating empty responses as `null` with JSON responses

There will be example implementations of each of these nearer the bottom of the file. 

The request and response formats are implementing using interceptors, so you've got access to everything possible. An interceptor is any object that implements the `ajax.core/Interceptor` protocol. The easiest way to achieve this is to call `to-interceptor` passing a map with the following keys
* `:request` - The request interceptor, optional
* `:response` - The response interceptor, also optional
* `:name` - The name of the interceptor, optional but highly recommended for debugging purposes.

## The Interceptor Life Cycle

A request works like this:
* If you're using the easy API, `GET` or `POST` &c, the request map is converted to a form accepted by `ajax-request`.
* The `ajax-request` map has its method normalized, so `:get` becomes `"GET"` &c
* The response format is determined and fixed 
* If no interceptors are provided, they are replaced with `@default-interceptors`. `default-interceptors` is an atom vector of interceptors.
* Standard interceptors are put around them: `:response-format` at the start and `:request-format` at the end.
* The request interceptors are run. By the end, the request should have a `:body` entry (unless it's a `GET`).
* The ajax query is run, it returns an `AjaxResponse`.
* The response interceptors are run in reverse order. This means `:response-format` interceptor runs last.
* The `:handler` is called with the result of that.

Note that request and response handlers are run using `reduce`. This means that you can use `reduced` to skip the rest of the `interceptors`. Bear in mind that in the case of a response interceptor, this means you need to convert to `[ok result]` format before calling `reduced`.

### Why can't you change the response format in an interceptor?

You can use an interceptor to change the request format, but not the response format. This is partly caused by technical internal considerations, but it's worth considering that most of the reasons you'd want it are either to slightly customize response behaviour, which can be handled with an interceptor, or to switch formats, which can be handled with format detection.

Equally, you can't use an interceptor to change the list of interceptors. This would be technically feasible if complex, but I can't think of any cases where it wouldn't be a code smell. In general terms, interceptors should be independent of one another. If they're not, it's probably best to just create a control interceptor that calls the others rather than trying to 'trick' the architecture to achieve the same ends. 

## Example interceptors

Quite a few people want to put CSRF or session tokens into every request.

```clj
(def token (atom 2334))
     
(def token-interceptor
     (to-interceptor {:name "Token Interceptor"
                      :request #(update-in [:params :token] @token)}))
(swap standard-interceptors conj token-interceptor)
```

Google App Engine doesn't permit a body in `DELETE` requests. (Its implementation of the HTTP spec is wrong, but there's no point arguing.)

```clj
(def delete-is-empty [{:keys [method] :as request}]
     (if (= method "POST")
         (reduced (update request :body nil))
         request))

(def app-engine-delete-interceptor
     (to-interceptor {:name "Google App Engine Delete Rule"
                      :request delete-is-empty}))

(swap standard-interceptors conj app-engine-delete-interceptor)
;;; Since this rule uses `reduced`, its important that it is positioned near the end.
```

Finally, many systems return an empty JSON response when returning `nil`. Strictly speaking, they should return `"null"`.

```clj
(def empty-means-nil [response]
     (if (-response-text response)
         response
         (reduced [(-> response -status success?) nil])))

(def treat-nil-as-empty
     (to-interceptor {:name "JSON special case nil"
                      :response empty-means-nil}))

(swap standard-interceptors conj treat-nil-as-empty)
```
