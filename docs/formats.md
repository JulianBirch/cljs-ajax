# Advanced Formats

The ajax commands in the main API can take arbitrary maps as request and response formats.  Indeed, `ajax-request` requires that you do this rather than use keywords as formats.  This file documents the behaviour.

A request format (given by `:format`) has two keys:
* `:content-type` The content type to send to the server
* `:write` A function taking `:params` and returning a string

A response format (given by `:response-format`) is a bit more complex:
* `:description` A description of the format, for use in error messages.
* `:read` A function that takes the underlying `goog.net.XhrIo` and converts it to a response.  Exceptions thrown by this will be caught.
* `:content-type` The content type to put in the `Accept` header.
* `:type` Optional.  One of `:blob`, `:document`, `:json` (which uses the browser's JSON decoding to a JS object), `:text` (same as blank), `:arraybuffer`. Set `:read` to `pr/-body` (after adding a `:require` for `[ajax.protocols :as pr]`) if you want to use this.

## Standard Formats

There are functions that return request and response formats.  Most of these functions don't take parameters.  These correspond to the keywords mentioned in the main documentation.

| Keyword | Request | Response |
| ------- | ------- | -------- |
| `:transit`  | `transit-request-format` | `transit-response-format` |
| `:json` | `json-request-format` | `json-response-format` |
| `:url`  | `url-request-format` | |
| `:ring` | | `ring-response-format` |
| `:raw`  | | `raw-response-format` |
| `:text`  | `text-request-format` | `text-response-format` |
| `:detect` | | `detect-response-format` |

`text-response-format` and `raw-response-format` are identical in ClojureScript, but `raw-response-format` returns the byte stream in Clojure, while `text-response-format` returns a string. `text-request-format` is a pass-through in ClojureScript, but converts a string to a byte stream in Clojure (which is what you want).

### Transit parameters

`transit-request-format` takes options
* `:writer` explicit Transit writer.  If not supplied one will be created using the other options.
* `:type` specifies transit format: `json`, `json-verbose` or `msgpack`. The default is `json` for ClojureScript and `msgpack` for Clojure.
* `:handlers` Custom handlers passed through to Transit.

`transit-response-format` takes options
* `:reader` (CLJS only) explicit Transit reader. If not supplied one will be created using the other options.
* `:handlers` Custom handlers passed through to Transit.

The content-type of the response is used to determine the Transit type (json or msgpack).

### JSON parameters

`json-response-format` takes an options map with three possible options
* `:prefix` is a string that needs to be stripped off the front of the response before parsing it as JSON, which is useful for dealing with external APIs that put things like `while(1);` in front.  (And if you're using cljs-ajax with `GET`, learn about cross-site scripting and then employ this feature in your own code.)  Defaults to `nil`.
* `:keywords?`, which if true returns the keys as keywords and if false or unprovided returns them as strings.
* `:raw`, if true, returns a JS object rather than a CLJS object.

### URL parameters

`url-request-format` takes one parameter: `vec-strategy`.
* `:java` will render `{:a [1 2]}` as `a=1&a=2`. This works with yada, ASP and Jetty (ring). It also matches the behaviour of superagent.
* `:rails` will render `{:a [1 2]}` as `a[]=1&a[]=2`. This is also the correct setting for working with PHP and matches the behaviour of jQuery.
* `:indexed` will render `{:a [1 2]}` as `a[0]=1&a[1]=2`. This is mostly kept for backwards compatibility and shouldn't be used in new code.

### Ring parameters

`ring-response-format` takes one parameter: `format`. This can be any
valid response format map as described above. If a format is not specified
then `raw-response-format` will be used. (This is also what happens if you use
the `:ring` keyword.)

### Detect parameters

`detect-response-format` has one parameter: `:response-format`, which is a list of pairs.  The first item in the pair is a substring that starts the content type.  The second item is the response format function to call.  It will be passed the options in.  So, you can, for instance, have `:raw` set to `true` and content detection available at the same time.  If you use the zero-arity version, `:response-format` is set to `default-formats`.

### EDN

EDN is deprecated, but the functions `edn-request-format` and `edn-response-format` are available in the `ajax.edn` namespace.

### Google Closure JSON

Earlier versions used Google Closure's implementation of JSON. This was the
correct choice at the time since native implementations were pretty
inconsistent. These days, it's more likely that you'll want to be using the
browser native JSON implementation which is vastly faster and handles dates
better, but if you still need the old behaviour you can get it by using
`goog-json-request-format` and `goog-json-response-format` in the
`ajax.goog-json` namespace. They support the same options as the standard JSON
implementation and share most of the internal code.

## Non-standard formats

To get the raw XhrIo object back:

```clj
{:read identity :description "raw"}
```

Obviously, this will do something surprisingly different if you're running this
code in Java.
