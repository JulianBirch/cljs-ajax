(ns ajax.easy
    (:require [ajax.simple :as simple]
              [ajax.transit :as t]
              [ajax.json :as json]
              [ajax.url :as url]
              [ajax.formats :as f]))

(def default-formats
  (atom
    [["application/transit+json" t/transit-response-format]
     ["application/transit+transit" t/transit-response-format]
     ["application/json" json/json-response-format]
     ["text/plain" f/text-response-format]
     ["text/html" f/text-response-format]
     ["*/*" f/raw-response-format]]))

(defn detect-response-format
  ([] (f/detect-response-format {:response-format @default-formats}))
  ([opts] (f/detect-response-format opts)))

(defn keyword-request-format [format format-params]
  "Converts an easy API request format specifier to an `ajax-request`
  request format specifier."
  (cond
   (map? format) format
   (fn? format) {:write format}
   (nil? format) (t/transit-request-format format-params)
   :else (case format
           :transit (t/transit-request-format format-params)
           :json (json/json-request-format)
           :text (f/text-request-format)
           :raw (url/url-request-format format-params)
           :url (url/url-request-format format-params)
           nil)))

(defn- keyword-response-format-element [format format-params]
  (cond
   (vector? format) [(first format)
                  (keyword-response-format-element (second format)
                                             format-params)]
   (map? format) format
   (fn? format) {:read format :description "custom"}
   (nil? format) (detect-response-format)
   :else (case format
           :transit (t/transit-response-format format-params)
           :json (json/json-response-format format-params)
           :text (f/text-response-format)
           :raw (f/raw-response-format)
           :detect (detect-response-format)
           nil)))

(defn keyword-response-format [format format-params]
  "Converts an easy API format specifier to an `ajax-request`
   format specifier. Mostly this is just a case of replacing `:json`
   with `json-response-format`. However, it gets complex when you
   specify a detection format such as `[[\"application/madeup\" :json]]`."
  (if (vector? format)
    (->> format
         (map #(keyword-response-format-element % format-params))
         (apply vector))
    (keyword-response-format-element format format-params)))

(defn print-response [response]
  (println "CLJS-AJAX response:" response))

(def default-handler
  "This gets called if you forget to attach a handler to an easy 
  API function." 
  (atom print-response))

(defn print-error-response [response]
  #? (:clj  (println "CLJS-AJAX ERROR:" response)
      :cljs (cond (exists? js/console) (.error js/console response)
                  (exists? js/window)  (.alert js/window (str response))
                  :else                (println "CLJS-AJAX ERROR:" response))))

(def default-error-handler
  "This will be called when errors occur if you don't supply
   an error handler to the easy API functions. If you don't
   want it writing errors to the console (or worse, flashing up
   alerts), make sure you always handle errors."
  (atom print-error-response))

(defn transform-handler
  "Converts easy API handlers to a `ajax-request` handler"
  [{:keys [handler error-handler finally]}]
  (let [h (or handler @default-handler)
        e (or error-handler @default-error-handler)]
    (fn easy-handler [[ok result]]
      ((if ok h e) result)
      (when (fn? finally)
        (finally)))))

(defn transform-opts [{:keys [method format response-format
                              params body]
                       :as opts}]
  "Note that if you call GET, POST et al, this function gets
   called and will include Transit code in your JS.
   If you don't want this to happen, use ajax-request directly
   (and use advanced optimisation)."
  (let [needs-format (and (nil? body) (not= method "GET"))
        rf (if (or format needs-format)
             (keyword-request-format format opts))]
    (assoc opts
      :handler (transform-handler opts)
      :format rf
      :response-format (keyword-response-format response-format opts))))

(defn easy-ajax-request [uri method opts]
  (-> opts
      (assoc :uri uri
             :method method)
      transform-opts
      simple/ajax-request))
