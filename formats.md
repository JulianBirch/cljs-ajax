# Advanced Formats

The ajax commands in the main API can take arbitrary maps as request and response formats.  Indeed, `ajax-request` requires that you do this rather than use keywords as formats.  This file documents the behaviour.

A request format (given by `:format`) has two keys:
* `:content-type` - The content type to send to the server
* `:write` - A function taking `:params` and returning a string

A response format (given by `:response-format`) also has two keys:
* `:description` - A description of the format, for use in error messages.
* `:read` - A function that takes the underlying `goog.net.XhrIo` and converts it to a response.  Exceptions thrown by this will be caught.

## Standard Formats

There are functions that return request and response formats.  Most of these functions don't take parameters.  These correspond to the keywords mentioned in the main documentation.

| Keyword | Request | Response |
| ------- | ------- | -------- |
| `:json` | `json-request-format` | `json-response-format` |
| `:edn`  | `edn-request-format` | `edn-response-format` |
| `:url`  | `url-request-format` | |
| `:raw`  | | `raw-response-format` |
 
`json-response-format` takes two parameters
* `:prefix` is a string that needs to be stripped off the front of the response before parsing it as JSON, which is useful for dealing with external APIs that put things like `while(1);` in front.  (And if you're using cljs-ajax with `GET`, learn about cross-site scripting and then employ this feature in your own code.)  Defaults to `nil`.
* `:keywords?`, which if true returns the keys as keywords and if false or unprovided returns them as strings.

## Non-standard formats

Here's some things people have wanted to do before:  read a JSON response as a pure JavaScript object:

```clj
{ :read #(.getResponseJSON %} :description "raw json" }
```

To get the raw XhrIo object back:

```clj
{:read identity :description "raw"}
```

Incidentally, if anyone wants to implement a response format that returns the response in a ring-style format, I accept pull requests.
