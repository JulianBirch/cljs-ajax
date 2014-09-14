(ns ajax.core
  (:require goog.net.EventType
            goog.net.ErrorCode
            [goog.net.XhrIo :as xhr]
            [goog.net.XhrManager :as xhrm]
            [goog.Uri :as uri]
            [goog.Uri.QueryData :as query-data]
            [goog.json.Serializer]
            [goog.events :as events]
            [goog.structs :as structs]
            [cljs.reader :as reader]
            [clojure.string :as str]
            [cognitect.transit :as t])
  (:require-macros [ajax.macros :as m]
                   [poppea :as p]))

(defprotocol AjaxImpl
  "An abstraction for a javascript class that implements
   Ajax calls."
  (-js-ajax-request [this uri method body headers handler opts]
    "Makes an actual ajax request.  All parameters except opts
     are in JS format.  Should return an AjaxRequest."))

(defprotocol AjaxRequest
  "An abstraction for a running ajax request."
  (-abort [this error-code]
    "Aborts a running ajax request, if possible."))

(defprotocol DirectlySubmittable
  "A marker interface for types that can be directly sent to XhrIo")

(extend-type js/String DirectlySubmittable)
(extend-type js/FormData DirectlySubmittable)

(extend-type nil
  AjaxImpl
  (-js-ajax-request
    [this uri method body headers handler {:keys [timeout]}]
    (doto (new goog.net.XhrIo)
      (events/listen goog.net.EventType/COMPLETE handler)
      (.setTimeoutInterval (or timeout 0))
      (.send uri method body headers))))

(extend-type goog.net.XhrIo
  AjaxRequest
  (-abort [this error-code]
    (.abort this error-code)))

(extend-type goog.net.XhrManager
  AjaxImpl
  (-js-ajax-request
    [this uri method body headers handler
     {:keys [id timeout priority max-retries]}]
    (.send this id uri method body headers
           priority handler max-retries)))

(defn abort
  ([this] (-abort this goog.net.ErrorCode/ABORT)))

(defn success? [status]
  (some #{status} [200 201 202 204 205 206]))

(defn read-edn [xhrio]
  (reader/read-string (.getResponseText xhrio)))

; This code would be a heck of a lot shorter if ClojureScript
; had macros.  As it is, a macro doesn't justify the extra build
; complication
(defn edn-response-format
  ([] {:read read-edn :description "EDN"})
  ([opts] (edn-response-format)))

(defn edn-request-format []
  {:write pr-str
   :content-type "application/edn"})

(def transit-content-type "application/transit+json; charset=utf-8")

(p/defn-curried transit-write
  [writer params]
  (t/write writer params))

(defn transit-request-format
  ([] (transit-request-format {}))
  ([{:keys [type writer] :as opts}]
     (let [writer (or writer (t/writer (or type :json) opts))]
       {:write (transit-write writer)
        :content-type transit-content-type})))

(p/defn-curried transit-read [reader raw xhrio]
  (let [text (.getResponseText xhrio)
        data (t/read reader text)]
    (if raw data (js->clj data))))

(defn transit-response-format
  ([] (transit-response-format {}))
  ([{:keys [type reader raw] :as opts}]
   (let [reader (or reader (t/reader (or reader :json) opts))]
     {:read (transit-read reader raw)
      :description "Transit"})))

(defn params-to-str [params]
  (if params
    (-> params
        clj->js
        structs/Map.
        query-data/createFromMap
        .toString)))

(defn read-text [xhrio]
  (.getResponseText xhrio))

(defn url-request-format []
  {:write params-to-str
   :content-type "application/x-www-form-urlencoded"})

(defn raw-response-format
  ([] {:read read-text :description "raw text"})
  ([opts] (raw-response-format)))

(defn write-json [data]
  (.serialize (goog.json.Serializer.) (clj->js data)))

(defn json-request-format []
  {:write write-json
   :content-type "application/json"})

(p/defn-curried json-read [prefix raw keywords? xhrio]
  (let [json (.getResponseJson xhrio prefix)]
    (if raw
      json
      (js->clj json :keywordize-keys keywords?))))

(defn json-response-format
  "Returns a JSON response format.  Options include
   :keywords? Returns the keys as keywords
   :prefix A prefix that needs to be stripped off.  This is to
   combat JSON hijacking.  If you're using JSON with GET request,
   you should think about using this.
   http://stackoverflow.com/questions/2669690/why-does-google-prepend-while1-to-their-json-responses
   http://haacked.com/archive/2009/06/24/json-hijacking.aspx"
  ([] (json-response-format {}))
  ([{:keys [prefix keywords? raw]}]
     {:read (json-read prefix raw keywords?)
      :description (str "JSON"
                        (if prefix (str " prefix '" prefix "'"))
                        (if keywords? " keywordize"))}))

(defn get-response-format [format]
  (cond
   (map? format) format
   (ifn? format) {:read format :description "custom"}
   :else (throw (js/Error. (str "unrecognized response format: " format)))))

(defn exception-response [e status {:keys [description]} xhrio]
  (let [response {:status status
                  :failure :error
                  :response nil}
        status-text (str (.-message e)
                         "  Format should have been "
                         description)
        parse-error (assoc response
                      :status-text status-text
                      :failure :parse
                      :original-text (.getResponseText xhrio))]
    (if (success? status)
      parse-error
      (assoc response
        :status-text (.getStatusText xhrio)
        :parse-error parse-error))))

(defn fail [status status-text failure & params]
  (let [response {:status status
                  :status-text status-text
                  :failure failure}]
    [false (reduce conj
                   response
                   (map vec (partition 2 params)))]))

(defn interpret-response [{:keys [read] :as format} response]
  (try
    (let [xhrio (.-target response)
          status (.getStatus xhrio)
          fail (partial fail status)]
      (if (= -1 status)
        (if (= (.getLastErrorCode xhrio) goog.net.ErrorCode/ABORT)
          (fail "Request aborted by client." :aborted)
          (fail "Request timed out." :timeout))
        (try
          (let [response (read xhrio)]
            (if (success? status)
              [true response]
              (fail (.getStatusText xhrio) :error :response response)))
          (catch js/Object e
            [false (exception-response e status format xhrio)]))))
    (catch js/Object e
      ; These errors should never happen
      (fail 0 (.-message e) :exception :exception e))))

(defn no-format [xhrio]
  (throw (js/Error. "No response format was supplied.")))

(defn uri-with-params [uri params]
  (if params
    (str uri "?" (params-to-str params))
    uri))

(defn get-request-format [format]
  (cond
   (map? format) format
   (ifn? format) {:write format :content-type "text/plain"}
   :else nil))

(defn normalize-method [method]
  (if (keyword? method)
    (str/upper-case (name method))
    method))

(defn process-inputs [{:keys [uri method format params headers]}]
  (if (= (normalize-method method) "GET")
    [(uri-with-params uri params) nil headers]
    (let [{:keys [write content-type]}
          (get-request-format format)
          body (cond
                (not (nil? write)) (write params)
                (satisfies? DirectlySubmittable params) params
                :else (throw (js/Error. (str "unrecognized request format: " format))))
          content-type (if content-type
                         {"Content-Type" content-type})
          headers (merge (or headers {}) content-type)]
      [uri body headers])))

(p/defn-curried js-handler [response-format handler xhrio]
  (let [response
        (interpret-response response-format xhrio)]
    (handler response)))

(defn base-handler [response-format {:keys [handler]}]
  (if handler
    (js-handler response-format handler)
    (throw (js/Error. "No ajax handler provided."))))

(defn ajax-request
  [{:keys [uri method response-format manager] :as opts}]
  (let [response-format (get-response-format response-format)
        method (normalize-method method)
        [uri body headers] (process-inputs opts)
        handler (base-handler response-format opts)]
    (-js-ajax-request manager uri method body
                      (clj->js headers) handler opts)))

; "Easy" API beyond this point

(def default-formats
  [["application/json" json-response-format]
   ["application/edn" edn-response-format]
   ["text/plain" raw-response-format]
   ["text/html" raw-response-format]
   ["application/transit+json" transit-response-format]
   [nil raw-response-format]])

(p/defn-curried detect-content-type [content-type [substring]]
  (or (nil? substring)
      (>= (.indexOf content-type substring) 0)))

(defn get-default-format [xhrio {:keys [defaults] :as opts}]
  (let [f (detect-content-type
           (or (.getResponseHeader xhrio "Content-Type") ""))]
    ((->> defaults
          (filter f)
          first
          second) opts)))

(p/defn-curried detect-response-format-read
  [opts xhrio]
  ((:read (get-default-format xhrio opts)) xhrio))

(defn detect-response-format
  ([] (detect-response-format {:defaults default-formats}))
  ([opts]
     {:read (detect-response-format-read opts)
      :format "(from content type)"}))

(defn keyword-request-format [format format-params]
  (cond
   (map? format) format
   (fn? format) {:write format}
   (nil? format) (transit-request-format format-params)
   :else (case format
           :transit (transit-request-format format-params)
           :json (json-request-format)
           :edn (edn-request-format)
           :raw (url-request-format)
           :url (url-request-format)
           nil)))

(defn keyword-response-format [format format-params]
  (cond
   (map? format) format
   (fn? format) {:read format :description "custom"}
   (nil? format) (detect-response-format)
   :else (case format
           :transit (transit-response-format format-params)
           :json (json-response-format format-params)
           :edn (edn-response-format)
           :raw (raw-response-format)
           :detect (detect-response-format)
           nil)))

(defn transform-handler [{:keys [handler error-handler finally]}]
  (fn easy-handler [[ok result]]
    (if-let [h (if ok handler error-handler)]
      (h result))
    (when (fn? finally)
      (finally))))

(defn transform-opts [{:keys [method format response-format params]
                       :as opts}]
  "Note that if you call GET, POST et al, this function gets
   called and will include JSON and EDN code in your JS.
   If you don't want this to happen, use ajax-request directly
   (and use advanced optimisation)."
  (let [needs-format
        (not (or (satisfies? DirectlySubmittable params)
                 (= method "GET")))
        rf (if (or format needs-format)
             (keyword-request-format format opts))]
    (assoc opts
      :handler (transform-handler opts)
      :format (or rf (if needs-format
                       (throw (js/Error.
                               (str "unrecognized request format: '"
                                    (or format "NIL") "'")))))
      :response-format (keyword-response-format response-format opts))))

(defn easy-ajax-request [uri method opts]
  (-> opts
      ajax.core/transform-opts
      (assoc :uri uri
             :method method)
      ajax.core/ajax-request))

(m/easy-api GET)
(m/easy-api HEAD)
(m/easy-api POST)
(m/easy-api PUT)
(m/easy-api DELETE)
(m/easy-api OPTIONS)
(m/easy-api TRACE)
