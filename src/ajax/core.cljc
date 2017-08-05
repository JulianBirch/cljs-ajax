(ns ajax.core
  (:require [clojure.string :as str]
            [ajax.url :as url]
            [ajax.json :as json]
            [ajax.transit :as transit]
            [ajax.formats :as f]
            [ajax.util :as u]
            [ajax.interceptors :as i]
            [ajax.simple :as simple]
            [ajax.protocols :refer
             [-body -process-request -process-response -abort -status
              -get-response-header -status-text -js-ajax-request
              -was-aborted
              #?@ (:cljs [AjaxImpl AjaxRequest AjaxResponse
                          Interceptor Response])]]
            #?@ (:clj  [[ajax.macros :as m]
                        [poppea :as p]
                        [cheshire.core :as c]
                        [ajax.apache]
                        [clojure.java.io :as io]]
                 :cljs [[ajax.xhrio]
                        [ajax.xml-http-request]]))
  #? (:clj
      (:import [java.lang String]
               [ajax.apache Connection]
               [java.io Closeable])
      :cljs
      (:require-macros [ajax.macros :as m]
                       [poppea :as p])))

;;; NB As a matter of policy, this file shouldn't reference any
;;; google closure files. That functionality should be off in
;;; specific namespaces and exposed here in a platform indepdent 
;;; way

;;; Ideally this would be true of all functionality, but it's
;;; an ongoing project.

(def to-interceptor i/to-interceptor)

(defn abort [this]
  "Call this on the result of `ajax-request` to cancel the request." 
  (-abort this))

;;; Standard Formats

(def json-request-format json/json-request-format)
(def json-response-format json/json-response-format)

(def transit-request-format transit/transit-request-format)
(def transit-response-format transit/transit-response-format)

(def url-request-format url/url-request-format)

(def text-request-format f/text-request-format)
(def text-response-format f/text-response-format)
; There's no raw-request-format because it's handled by the DirectSubmission code
(def raw-response-format f/raw-response-format)

;;; Detection and Accept Code

(def default-formats
  [["application/transit+json" transit-response-format]
   ["application/transit+transit" transit-response-format]
   ["application/json" json-response-format]
   ["text/plain" text-response-format]
   ["text/html" text-response-format]
   ["*/*" raw-response-format]])

(defn detect-response-format
  ([] (f/detect-response-format {:response-format default-formats}))
  ([opts] (f/detect-response-format opts)))

(def ajax-request simple/ajax-request)

;;; "Easy" API beyond this point

(defn keyword-request-format [format format-params]
  "Converts an easy API request format specifier to an `ajax-request`
  request format specifier."
  (cond
   (map? format) format
   (fn? format) {:write format}
   (nil? format) (transit-request-format format-params)
   :else (case format
           :transit (transit-request-format format-params)
           :json (json-request-format)
           :text (text-request-format)
           :raw (url-request-format format-params)
           :url (url-request-format format-params)
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
           :transit (transit-response-format format-params)
           :json (json-response-format format-params)
           :text (text-response-format)
           :raw (raw-response-format)
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
      ajax.core/transform-opts
      ajax.core/ajax-request))

(m/easy-api GET)
(m/easy-api HEAD)
(m/easy-api POST)
(m/easy-api PUT)
(m/easy-api DELETE)
(m/easy-api OPTIONS)
(m/easy-api TRACE)
(m/easy-api PATCH)
(m/easy-api PURGE)
