(ns ajax.core
  (:require goog.net.EventType
            goog.net.ErrorCode
            [goog.net.XhrIo :as xhr]
            [goog.net.XhrManager :as xhrm]
            [goog.Uri :as uri]
            [goog.json :as goog-json]
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
  (-abort [this]
    "Aborts a running ajax request, if possible."))

(defprotocol DirectlySubmittable
  "A marker interface for types that can be directly sent to XhrIo.")

(defprotocol AjaxResponse
  "An abstraction for an ajax response."
  (-status [this]
    "Returns the HTTP Status of the response as an integer.")
  (-status-text [this]
    "Returns the HTTP Status Text of the response as a string.")
  (-body [this]
    "Returns the response body as a string.")
  (-get-response-header [this header]
    "Gets the specified response header (specified by a string) as a string.")
  (-was-aborted [this]
    "Was the response aborted."))

(m/register-directly-submittable js/FormData js/ArrayBufferView
                                 js/Blob js/Document)

(defn submittable? [params]
  (or (satisfies? DirectlySubmittable params)
      (string? params)))

(extend-type goog.net.XhrIo
  AjaxImpl
  (-js-ajax-request
    [this uri method body headers handler
     {:keys [timeout with-credentials]
      :or {with-credentials false
           timeout 0}}]
    (doto this
      (events/listen goog.net.EventType/COMPLETE
                     #(handler (.-target %)))
      (.setTimeoutInterval timeout)
      (.setWithCredentials with-credentials)
      (.send uri method body (clj->js headers))))
  AjaxRequest
  (-abort [this] (.abort this goog.net.ErrorCode/ABORT))
  AjaxResponse
  (-body [this] (.getResponseText this))
  (-status [this] (.getStatus this))
  (-status-text [this] (.getStatusText this))
  (-get-response-header [this header]
    (.getResponseHeader this header))
  (-was-aborted [this]
    (= (.getLastErrorCode this) goog.net.ErrorCode/ABORT)))

(defn ready-state
  [e]
  ({0 :not-initialized
    1 :connection-established
    2 :request-received
    3 :processing-request
    4 :response-ready} (.-readyState (.-target e))))

(extend-type js/XMLHttpRequest
  AjaxImpl
  (-js-ajax-request
    [this uri method body headers handler
     {:keys [timeout with-credentials]
      :or {with-credentials false
           timeout 0}}]
    (set! (.-timeout this) timeout)
    (set! (.-withCredentials this) with-credentials)
    (set! (.-onreadystatechange this) #(when (= :response-ready (ready-state %)) (handler this)))
    (doto this
      (.open method uri true)
      (as-> t
            (doseq [[k v] headers]
              (.setRequestHeader t k v)))
      (.send (or body ""))))
  AjaxRequest
  (-abort [this] (.abort this))
  AjaxResponse
  (-body [this] (.-response this))
  (-status [this] (.-status this))
  (-status-text [this] (.-statusText this))
  (-get-response-header [this header]
    (.getResponseHeader this header))
  (-was-aborted [this] (= 0 (.-readyState this))))

(extend-type goog.net.XhrManager
  AjaxImpl
  (-js-ajax-request
    [this uri method body headers handler
     {:keys [id timeout priority max-retries]}]
    (.send this id uri method body (clj->js headers)
           priority handler max-retries)))

(defn abort ([this] (-abort this)))

(defn success? [status]
  (some #{status} [200 201 202 204 205 206]))

;;; Standard Formats

(defn read-edn [xhrio]
  (reader/read-string (-body xhrio)))

(defn edn-response-format
  ([] {:read read-edn
       :description "EDN"
       :content-type "application/edn"})
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
  (let [text (-body xhrio)
        data (t/read reader text)]
    (if raw data (js->clj data))))

(defn transit-response-format
  ([] (transit-response-format {}))
  ([{:keys [type reader raw] :as opts}]
   (let [reader (or reader (t/reader (or type :json) opts))]
     {:read (transit-read reader raw)
      :description "Transit"
      :content-type "application/transit+json"})))

(defn params-to-str [params]
  (if params
    (-> params
        clj->js
        structs/Map.
        query-data/createFromMap
        .toString)))

(defn url-request-format []
  {:write params-to-str
   :content-type "application/x-www-form-urlencoded"})

(defn raw-response-format
  ([] {:read -body :description "raw text" :content-type "*/*"})
  ([opts] (raw-response-format)))

(defn write-json [data]
  (.serialize (goog.json.Serializer.) (clj->js data)))

(defn json-request-format []
  {:write write-json
   :content-type "application/json"})

(p/defn-curried json-read [prefix raw keywords? xhrio]
  (let [text (-body xhrio)
        text (if (and prefix (= 0 (.indexOf text prefix)))
               (.substring text (.length prefix))
               text)
        json (goog-json/parse text)]
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
                        (if keywords? " keywordize"))
      :content-type "application/json"}))

;;; Detection and Accept Code

(def default-formats
  [json-response-format
   edn-response-format
   transit-response-format
   ["text/plain" raw-response-format]
   ["text/html" raw-response-format]
   raw-response-format])

(p/defn-curried get-format [opts format-entry]
  (cond (vector? format-entry) (get-format opts
                                           (second format-entry))
        (map? format-entry) format-entry
        ; Must be a format generating function
        :else (format-entry opts)))

(p/defn-curried accept-entry [opts format-entry]
  (or (if (vector? format-entry)
        (first format-entry)
        (:content-type (get-format opts format-entry)))
      "*/*"))

(p/defn-curried detect-content-type [content-type opts format-entry]
  (let [accept (accept-entry opts format-entry)]
    (or (= accept "*/*")
        (>= (.indexOf content-type accept) 0))))

(defn get-default-format [xhrio {:keys [response-format] :as opts}]
  (let [f (detect-content-type
           (or (-get-response-header xhrio "Content-Type") "")
           opts)]
    (->> response-format
         (filter f)
         first
         (get-format opts))))

(p/defn-curried detect-response-format-read
  [opts xhrio]
  ((:read (get-default-format xhrio opts)) xhrio))

(defn accept-header [{:keys [response-format] :as opts}]
  (if (vector? response-format)
    (str/join ", " (map (accept-entry opts) response-format))
    (accept-entry opts response-format)))

(defn detect-response-format
  ([] (detect-response-format {:response-format default-formats}))
  ([opts]
     (let [accept (accept-header opts)]
       {:read (detect-response-format-read opts)
        :format (str "(from " accept ")")
        :content-type accept})))

;;; AJAX calls

(defn get-response-format [{:keys [response-format] :as opts}]
  (cond
   (vector? response-format) (detect-response-format opts)
   (map? response-format) response-format
   (ifn? response-format) {:read response-format
                           :description "custom"
                           :content-type "*/*"}
   :else (throw (js/Error. (str "unrecognized response format: " response-format)))))

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
                      :original-text (-body xhrio))]
    (if (success? status)
      parse-error
      (assoc response
        :status-text (-status-text xhrio)
        :parse-error parse-error))))

(defn fail [status status-text failure & params]
  (let [response {:status status
                  :status-text status-text
                  :failure failure}]
    [false (reduce conj
                   response
                   (map vec (partition 2 params)))]))

(defn interpret-response [{:keys [read] :as format} xhrio]
  (try
    (let [status (-status xhrio)
          fail (partial fail status)]
      (if (= -1 status)
        (if (-was-aborted xhrio)
          (fail "Request aborted by client." :aborted)
          (fail "Request timed out." :timeout))
        (try
          (let [response (read xhrio)]
            (if (success? status)
              [true response]
              (fail (-status-text xhrio) :error :response response)))
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

(defn process-inputs [{:keys [uri method format params headers]}
                      {:keys [content-type]}]
  (let [headers (merge {"Accept" content-type}
                       (or headers {}))]
    (if (= (normalize-method method) "GET")
      [(uri-with-params uri params) nil headers]
      (let [{:keys [write content-type]}
            (get-request-format format)
            body (cond
                  (not (nil? write)) (write params)
                  (submittable? params) params
                  :else (throw (js/Error. (str "unrecognized request format: " format))))
            content-type (if content-type
                           {"Content-Type" content-type})
            headers (merge headers content-type)]
        [uri body headers]))))

(p/defn-curried js-handler [response-format handler xhrio]
  (let [response
        (interpret-response response-format xhrio)]
    (handler response)))

(defn base-handler [response-format {:keys [handler]}]
  (if handler
    (js-handler response-format handler)
    (throw (js/Error. "No ajax handler provided."))))

(defn ajax-request
  [{:keys [method api] :as opts}]
  (let [response-format (get-response-format opts)
        method (normalize-method method)
        [uri body headers] (process-inputs opts response-format)
        handler (base-handler response-format opts)
        api (or api (new goog.net.XhrIo))]
    (-js-ajax-request api uri method body
                      headers handler opts)))

;;; "Easy" API beyond this point

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

(defn keyword-response-format-2 [format format-params]
  (cond
   (vector? format) [(first format)
                  (keyword-response-format-2 (second format)
                                             format-params)]
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

(defn keyword-response-format [format format-params]
  (if (vector? format)
    (->> format
         (map #(keyword-response-format-2 % format-params))
         (apply vector))
    (keyword-response-format-2 format format-params)))

(p/defn-curried transform-handler
  [{:keys [handler error-handler finally]} [ok result]]
  (if-let [h (if ok handler error-handler)]
    (h result))
  (when (fn? finally)
    (finally)))

(defn transform-opts [{:keys [method format response-format params]
                       :as opts}]
  "Note that if you call GET, POST et al, this function gets
   called and will include JSON and EDN code in your JS.
   If you don't want this to happen, use ajax-request directly
   (and use advanced optimisation)."
  (let [needs-format
        (not (or (submittable? params)
                 (= method "GET")))
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
