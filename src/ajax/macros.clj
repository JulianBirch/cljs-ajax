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

(defn- curry
  [[params1 params2] body]
  (cons (vec params1)
        (if (empty? params2)
          body
          (list (apply list 'fn (vec params2) body)))))

(defn- do-curried [symbol to-fn params]
  (let [result (split-with (complement vector?) params)
        [[name doc meta] [args & body]] result
        [doc meta] (if (string? doc) [doc meta] [nil doc])
        body (if meta (cons meta body) body)
        arity-for-n #(-> % inc (split-at args) (to-fn body))
        arities (->>
                 (range 0 (count args))
                 (map arity-for-n)
                 reverse)
        before (keep identity [symbol name doc])]
    (concat before arities)))

(defmacro defn-curried
  "Builds a multiple arity function similar that returns closures
          for the missing parameters, similar to ML's behaviour."
  [& params]
  (do-curried 'defn curry params))

(defmacro defn-curried-
  "Builds a multiple arity function similar that returns closures
          for the missing parameters, similar to ML's behaviour."
  [& params]
  (do-curried 'defn- curry params))

(defmacro fn-curried
  "Builds a multiple arity function similar that returns closures
          for the missing parameters, similar to ML's behaviour."
  [& params]
  (do-curried 'fn curry params))
