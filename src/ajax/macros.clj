(ns ajax.macros)

(defmacro easy-api [method]
  (let [uri (symbol "uri")
        opts (symbol "opts")
        easy-ajax-request (symbol "ajax.easy/easy-ajax-request")]
    `(defn ~method
       "accepts the URI and an optional map of options, options include:
        :handler - the handler function for successful operation
                   should accept a single parameter which is the
                   deserialized response
        :progress-handler - the handler function for progress events.
                            this handler is only available when using the goog.net.XhrIo API
        :error-handler - the handler function for errors, should accept a
                         map with keys :status and :status-text
        :format - the format for the request
        :response-format - the format for the response
        :params - a map of parameters that will be sent with the request"
       [~uri & ~opts]
       (let [f# (first ~opts)]
         (~easy-ajax-request ~uri ~(name method)
                            (if (keyword? f#)
                              (apply hash-map ~opts)
                              f#))))))
