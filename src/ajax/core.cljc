(ns ajax.core
  (:require [clojure.string :as str]
            [ajax.url :as url]
            [ajax.json :as json]
            [ajax.transit :as transit]
            [ajax.util :as u]
            [ajax.interceptors :refer 
             [map->ResponseFormat request-interceptors 
              get-response-format]]
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
      (:import [java.io OutputStreamWriter ByteArrayOutputStream
                InputStreamReader Closeable OutputStream
                InputStream]
               [java.lang String]
               [java.util Scanner]
               [ajax.apache Connection]
               [ajax.protocols AjaxImpl AjaxRequest
                AjaxResponse Interceptor Response])
      :cljs
      (:require-macros [ajax.macros :as m]
                       [poppea :as p])))

;;; NB As a matter of policy, this file shouldn't reference any
;;; google closure files. That functionality should be off in
;;; specific namespaces and exposed here in a platform indepdent 
;;; way

;;; Ideally this would be true of all functionality, but it's
;;; an ongoing project.

(defn process-response [response interceptor]
  "-process-response with the arguments flipped for use in reduce"
  (-process-response interceptor response))

(defn process-request [request interceptor]
  "-process-request with the arguments flipped for use in reduce"
  (-process-request interceptor request))

(defrecord StandardInterceptor [name request response]
  Interceptor
  (-process-request [{:keys [request]} opts]
    (request opts))
  (-process-response [{:keys [response]} xhrio]
    (response xhrio)))

(defn to-interceptor [m]
  (map->StandardInterceptor (merge
                             {:request identity :response identity}
                             m)))

(defn get-content-type ^String [response]
  (or (-get-response-header response "Content-Type") ""))

(defn abort ([this] (-abort this)))

;;; Request Format Record

(defn to-utf8-writer [to-str]
  #? (:cljs to-str
      :clj (fn write-utf8 [stream params]
             (doto (OutputStreamWriter. stream)
               (.write ^String (to-str params))
               (.flush)))))

;;; Standard Formats

(def json-request-format json/json-request-format)
(def json-response-format json/json-response-format)

(def transit-request-format transit/transit-request-format)
(def transit-response-format transit/transit-response-format)

(defn url-request-format
  ([] (url-request-format {})) 
  ([{:keys [vec-strategy]}]
   {:write (to-utf8-writer (url/params-to-str vec-strategy))
    :content-type "application/x-www-form-urlencoded; charset=utf-8"}))

(defn raw-response-format
  ([] (map->ResponseFormat {:read -body
                            :description #? (:cljs "raw text"
                                             :clj "raw binary")
                            :content-type ["*/*"]}))
  ([_] (raw-response-format)))

(defn text-request-format []
  {:write (to-utf8-writer identity)
   :content-type "text/plain; charset=utf-8"})

#? (:clj
    ;;; http://stackoverflow.com/questions/309424/read-convert-an-inputstream-to-a-string
    (do
      (defn response-to-string [response]
        (let [s (doto (Scanner. ^InputStream (-body response)
                                "UTF-8")
                  (.useDelimiter "\\A"))]
          (if (.hasNext s) (.next s) "")))

      (defn text-response-format
        ([] (map->ResponseFormat {:read response-to-string
                                  :description "raw text"
                                  :content-type ["*/*"]}))
        ([_] (text-response-format))))
    :cljs
    (def text-response-format raw-response-format))

;;; strip prefix for CLJ

;;; Detection and Accept Code

(def default-formats
  [["application/transit+json" transit-response-format]
   ["application/transit+transit" transit-response-format]
   ["application/json" json-response-format]
   ["text/plain" text-response-format]
   ["text/html" text-response-format]
   ["*/*" raw-response-format]])

(p/defn-curried get-format [request format-entry]
  (cond
   (or (nil? format-entry) (map? format-entry))
   format-entry

   (vector? format-entry)
   (get-format request (second format-entry))

   ;;; Must be a format generating function
   :else (format-entry request)))

(p/defn-curried get-accept-entries [request format-entry]
  (let [fe (if (vector? format-entry)
             (first format-entry)
             (:content-type (get-format request format-entry)))]
    (cond (nil? fe) ["*/*"]
          (string? fe) [fe]
          :else fe)))

(p/defn-curried content-type-matches
  [^String content-type ^String accept]
  (or (= accept "*/*")
      (>= (.indexOf content-type accept) 0)))

(p/defn-curried detect-content-type
  [content-type request format-entry]
  (let [accept (get-accept-entries request format-entry)]
    (some (content-type-matches content-type) accept)))

(defn get-default-format
  [response {:keys [response-format] :as request}]
  (let [f (detect-content-type (get-content-type response) request)]
    (->> response-format
         (filter f)
         first
         (get-format request))))

(p/defn-curried detect-response-format-read
  [request response]
  (let [format (get-default-format response request)]
    ((:read format) response)))

(defn accept-header [{:keys [response-format] :as request}]
  (if (vector? response-format)
    (mapcat (get-accept-entries request) response-format)
    (get-accept-entries request response-format)))

(defn detect-response-format
  ([] (detect-response-format {:response-format default-formats}))
  ([opts]
     (let [accept (accept-header opts)]
       (map->ResponseFormat
        {:read (detect-response-format-read opts)
         :format (str "(from " accept ")")
         :content-type accept}))))

;;; AJAX calls

(defn normalize-method [method]
  (if (keyword? method)
    (str/upper-case (name method))
    method))

(p/defn-curried js-handler [handler interceptors response]
  (let [process (fn process [response interceptor]
            (-process-response interceptor response))
        processed (reduce process response interceptors)]
    ;;; This requires a bit of explanation: if we return a closeable,
    ;;; it should be wrapping the original response, so we _don't_
    ;;; close the original response stream
    ;;; If you're writing a weird interceptor that doesn't do this,
    ;;; remember to close the original stream yourself
    #? (:clj (if (and response
                      (instance? Closeable (second processed)))
               (.close ^Closeable (-body response))))
    (handler processed)))

(defn base-handler [interceptors {:keys [handler]}]
  (if handler
    (js-handler handler interceptors)
    (u/throw-error "No ajax handler provided.")))

(def default-interceptors (atom []))

(defn normalize-request [request]
  (let [response-format (get-response-format detect-response-format request)]
    (-> request
        (update :method normalize-method)
        (update :interceptors
                #(concat [response-format]
                         (or % @default-interceptors)
                         request-interceptors)))))

(defn new-default-api []
  #? (:clj  (ajax.apache/Connection.)
      :cljs (new goog.net.XhrIo)))

(defn raw-ajax-request [{:keys [interceptors] :as request}]
  (let [request (reduce process-request request interceptors)
        handler (base-handler (reverse interceptors) request)
        api (or (:api request) (new-default-api))]
    (-js-ajax-request api request handler)))

(defn ajax-request [request]
  (-> request normalize-request raw-ajax-request))

;;; "Easy" API beyond this point

(defn keyword-request-format [format format-params]
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

(defn keyword-response-format-element [format format-params]
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
  (if (vector? format)
    (->> format
         (map #(keyword-response-format-element % format-params))
         (apply vector))
    (keyword-response-format-element format format-params)))

(defn print-response [response]
  (println "CLJS-AJAX response:" response))

(def default-handler (atom print-response))

(defn print-error-response [response]
  #? (:clj  (println "CLJS-AJAX ERROR:" response)
      :cljs (cond (exists? js/console) (.error js/console response)
                  (exists? js/window)  (.alert js/window (str response))
                  :else                (println "CLJS-AJAX ERROR:" response))))

(def default-error-handler
  (atom print-error-response))

(defn transform-handler
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
   called and will include JSON code in your JS.
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
