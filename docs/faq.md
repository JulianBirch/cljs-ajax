# Frequently asked questions

## Features

### Why is cljs-ajax making OPTIONS requests when I didn't ask for them?

You're doing a cross-site request. Take a look at the full documentation in
[server.md](server.md).

### Why does DELETE submit a body?

Because that the correct thing to do according to the spec. There are, however,
servers that don't implement this correctly. Particularly annoyingly, the Google
App Engine throws an error if you send it one. However, it's perfectly possible 
to solve this with the use of an [interceptor](interceptors.md) (and the linked
file contains the exact code you need).

### Why doesn't JSON format treat empty string as `null`?

The JSON spec is pretty ill specified in many ways, but it's pretty clear
that the correct encoding for `null` is "null". So, cljs-ajax does the correct
thing. Again, though, you can put an (interceptor)[interceptors.md] in to fix 
this. Equally again, the linked file contains the full code you need.

## Design

### Why does `cljs-ajax` not use `core.async`?

One of the first things I did when I started working on this project, well
before I officially took over, was propose that we did exactly that. Quite
a bit of the evolving design (especially of `ajax-request`) was designed to
work well with `core.async`. But ultimately, when all the preparatory work
was done, all that would be added was putting in callback handlers that wrote 
to channels and frankly we weren't adding any value by including that code 
ourselves. Also, it was an extra dependency in a project that takes advanced
optimization very seriously.

In short, `core.async` is great, and `cljs-ajax` is designed to work well
with it. But if your design doesn't use it, you don't need to use it to
use `cljs-ajax`.

### Why doesn't `cljs-ajax` use the `ring` data model?

There's certainly advantages in the ring model, but as a request format
it leaves some things to be desired. For one, wheras a ring handler
will populate `:params` and `:json-params` and most users can happily
ignore the existence of `:json-params`, a request *has* to 
specify `:json-params` and `:params` is useless. This leads to the API
changing for not only every format, but every feature you might want to use.

The second major issue is that ring servers typically achieve extensibility
by inserting synchronous wrapping handler methods. Building up this stack
for a client is not only not very extensible, it's pretty hostile to Google
Closure's advanced optimisations.

### How does cljs-ajax compare with its alternatives?

There's are two other libraries occupying the same design space as `cljs-ajax`:
HTTP support with extensions supporting common formats. They're both excellent and I've happily ripped off their code where appropriate. (Thanks guys.) Equally, they've got decent documentation and solid test coverage. `clj-http` is Java only, works synchronously (so you spin up extra threads if you don't want to block) and is easily the most popular and robust of the three libraries. `cljs-http` is JavaScript only, marginally less popular than `cljs-ajax` and uses a `core.async` style API. Both try to mimic the ring data model, which you may prefer as a user. 

These are the things that inform the design of `cljs-ajax` that I think distinguish it from its competition.

* It has a lot of extension points where you can customize behaviour.
* It is specifically written to work well with advanced optimisations.

It's also the only cross-platform library, but unless the code you're
writing is cross-platform itself, this is unlikely to be a major
consideration.

As an aside, two of the defining philosophies of Clojure are simplicity
and being data driven. It's interesting to compare the libraries using
this approach, since it demonstrates that the two can sometimes be in
tension with one another. Where such a conflict exists, `cljs-ajax`
has always gone for the more composable approach (although the easy API
exists for programmer convenience).
