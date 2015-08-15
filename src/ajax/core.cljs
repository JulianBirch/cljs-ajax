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
            [clojure.string :as str]
            [cognitect.transit :as t])
  (:require-macros [ajax.macros :as m]
                   [poppea :as p]))

(defprotocol AjaxImpl
  "An abstraction for a javascript class that implements
   Ajax calls."
  (-js-ajax-request [this request handler]
    "Makes an actual ajax request.  All parameters except opts
     are in JS format.  Should return an AjaxRequest."))

(defprotocol AjaxRequest
  "An abstraction for a running ajax request."
  (-abort [this]
    "Aborts a running ajax request, if possible."))

(defprotocol AjaxResponse
  "An abstraction for an ajax response."
  (-status [this]
    "Returns the HTTP Status of the response as an integer.")
  (-status-text [this]
    "Returns the HTTP Status Text of the response as a string.")
  (-body [this]
    "Returns the response body as a string or as type specified in response-format
    such as a blob or arraybuffer.")
  (-get-response-header [this header]
    "Gets the specified response header (specified by a string) as a string.")
  (-was-aborted [this]
    "Was the response aborted."))

(defprotocol Interceptor
  "An abstraction for something that processes requests and responses."
  (-process-request [this request]
    "Transforms the opts")
  (-process-response [this response]
    "Transforms the raw response (an implementation of AjaxResponse)"))

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

(extend-type goog.net.XhrIo
  AjaxImpl
  (-js-ajax-request
    [this
     {:keys [uri method body headers timeout with-credentials]
      :or {with-credentials false
           timeout 0}}
     handler]
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
    [this
     {:keys [uri method body headers timeout with-credentials
             response-format]
           :or {with-credentials false
                timeout 0}}
     handler]
    (set! (.-withCredentials this) with-credentials)
    (set! (.-onreadystatechange this) #(when (= :response-ready (ready-state %)) (handler this)))
    (.open this method uri true)
    (set! (.-timeout this) timeout)
    ;;; IE8 needs timeout to be set between open and send
    ;;; https://msdn.microsoft.com/en-us/library/cc304105(v=vs.85).aspx
    (when-let [response-type (:type response-format)]
      (set! (.-responseType this) (name response-type)))
    (doseq [[k v] headers]
      (.setRequestHeader this k v))
    (.send this (or body ""))
    this)
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
    [this {:keys [uri method body headers
                  id timeout priority max-retries]
           :or {timeout 0}}
     handler]
    (.send this id uri method body (clj->js headers)
           priority handler max-retries)))

(defn abort ([this] (-abort this)))

(defn success? [status]
  (some #{status} [200 201 202 204 205 206]))

;;; Response Format record

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

(defrecord ResponseFormat [read description content-type]
  Interceptor
  (-process-request [{:keys [content-type]} request]
    "Sets the headers on the request"
    (update request
            :headers
            #(merge {"Accept" content-type}
                    (or % {}))))
  (-process-response [{:keys [read] :as format} xhrio]
    "Transforms the raw response (an implementation of AjaxResponse)"
    (try
      (let [status (-status xhrio)
            fail (partial fail status)]
        (case status
          -1 (if (-was-aborted xhrio)
               (fail "Request aborted by client." :aborted)
               (fail "Request timed out." :timeout))
          204 [true nil]       ; 204 and 205 should have empty responses
          205 [true nil]
          (try
            (let [response (read xhrio)]
              (if (success? status)
                [true response]
                (fail (-status-text xhrio) :error :response response)))
            (catch js/Object e
              [false (exception-response e status format xhrio)]))))
      (catch js/Object e
                                        ; These errors should never happen
        (fail 0 (.-message e) :exception :exception e)))))

;;; Request Format Record

(defn params-to-str-old [params]
  (if params
    (-> params
        clj->js
        structs/Map.
        query-data/createFromMap
        .toString)))

(declare param-to-str)

(p/defn-curried vec-param-to-str [prefix key value]
  (param-to-str prefix [key value]))

(p/defn-curried param-to-str [prefix [key value]]
  (let [k1 (if (keyword? key) (name key) key)
        new-key (if prefix (str prefix "[" k1 "]") k1)]
    (cond (string? value)
          [[new-key value]]

          (map? value)
          (mapcat (param-to-str new-key) (seq value))

          (sequential? value)
          (apply concat (map-indexed (vec-param-to-str new-key)
                                     (seq value)))

          :else [[new-key value]])))

(defn params-to-str [params]
  (->> (seq params)
       (mapcat (param-to-str nil))
       (map (fn [[k v]] (str k "=" v)))
       (str/join "&")))

(defn uri-with-params [uri params]
  (if params
    (str uri
         (if (re-find #"\?" uri) "&" "?") ; add & if uri contains ?
         (params-to-str params))
    uri))

(defn get-request-format [format]
  (cond
   (map? format) format
   (ifn? format) {:write format :content-type "text/plain"}
   :else {}))

(defrecord ProcessGet []
  Interceptor
  (-process-request [_ {:keys [method] :as request}]
    (if (= method "GET")
      (reduced (update request :uri
                       #(uri-with-params % (:params request))))
      request))
  (-process-response [_ response] response))

(defrecord DirectSubmission []
  Interceptor
  (-process-request [_ {:keys [body params] :as request}]
    (if body (reduced request) request))
  (-process-response [_ response] response))

(defrecord ApplyRequestFormat []
  Interceptor
  (-process-request
    [_ {:keys [uri method format params headers] :as request}]
    (let [{:keys [write content-type]} (get-request-format format)
          body (if-not (nil? write) (write params)
                       (throw (js/Error. (str "unrecognized request format: " format))))
          headers (or headers {})]
      (assoc request
        :body body
        :headers (if content-type
                   (assoc headers "Content-Type"
                          (str content-type "; charset=utf-8"))
                   headers))))
  (-process-response [_ xhrio] xhrio))

;;; Standard Formats



(p/defn-curried transit-write
  [writer params]
  (t/write writer params))

(defn transit-request-format
  ([] (transit-request-format {}))
  ([{:keys [type writer] :as request}]
     (let [writer (or writer (t/writer (or type :json) request))]
       {:write (transit-write writer)
        :content-type "application/transit+json"})))

(p/defn-curried transit-read [reader raw xhrio]
  (let [text (-body xhrio)
        data (t/read reader text)]
    (if raw data (js->clj data))))

(defn transit-response-format
  ([] (transit-response-format {}))
  ([{:keys [type reader raw] :as request}]
   (let [reader (or reader (t/reader (or type :json) request))]
     (map->ResponseFormat {:read (transit-read reader raw)
                           :description "Transit"
                           :content-type "application/transit+json"}))))

(defn url-request-format []
  {:write params-to-str
   :content-type "application/x-www-form-urlencoded"})

(defn raw-response-format
  ([] (map->ResponseFormat {:read -body :description "raw text" :content-type "*/*"}))
  ([_] (raw-response-format)))

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
     (map->ResponseFormat
      {:read (json-read prefix raw keywords?)
       :description (str "JSON"
                         (if prefix (str " prefix '" prefix "'"))
                         (if keywords? " keywordize"))
       :content-type "application/json"})))

;;; Detection and Accept Code

(def default-formats
  [json-response-format
   transit-response-format
   ["text/plain" raw-response-format]
   ["text/html" raw-response-format]
   raw-response-format])

(p/defn-curried get-format [request format-entry]
  (cond
   (or (nil? format-entry) (map? format-entry))
   format-entry

   (vector? format-entry)
   (get-format request (second format-entry))

   ;;; Must be a format generating function
   :else (format-entry request)))

(p/defn-curried accept-entry [request format-entry]
  (or (if (vector? format-entry)
        (first format-entry)
        (:content-type (get-format request format-entry)))
      "*/*"))

(p/defn-curried detect-content-type
  [content-type request format-entry]
  (let [accept (accept-entry request format-entry)]
    (or (= accept "*/*")
        (>= (.indexOf content-type accept) 0))))

(defn get-default-format
  [xhrio {:keys [response-format] :as request}]
  (let [f (detect-content-type
           (or (-get-response-header xhrio "Content-Type") "")
           request)]
    (->> response-format
         (filter f)
         first
         (get-format request))))

(p/defn-curried detect-response-format-read
  [request xhrio]
  ((:read (get-default-format xhrio request)) xhrio))

(defn accept-header [{:keys [response-format] :as request}]
  (if (vector? response-format)
    (str/join ", " (map (accept-entry request) response-format))
    (accept-entry request response-format)))

(defn detect-response-format
  ([] (detect-response-format {:response-format default-formats}))
  ([opts]
     (let [accept (accept-header opts)]
       (map->ResponseFormat
        {:read (detect-response-format-read opts)
         :format (str "(from " accept ")")
         :content-type accept}))))

;;; AJAX calls

(defn get-response-format [{:keys [response-format] :as opts}]
  (cond
   (instance? ResponseFormat response-format) response-format
   (vector? response-format) (detect-response-format opts)
   (map? response-format) (map->ResponseFormat response-format)
   (ifn? response-format)
   (map->ResponseFormat {:read response-format
                         :description "custom"
                         :content-type "*/*"})
   :else (throw (js/Error. (str "unrecognized response format: " response-format)))))

(defn no-format [xhrio]
  (throw (js/Error. "No response format was supplied.")))

(defn normalize-method [method]
  (if (keyword? method)
    (str/upper-case (name method))
    method))

(p/defn-curried js-handler [handler interceptors response]
  (let [process (fn process [response interceptor]
            (-process-response interceptor response))
        response (reduce process response interceptors)]
    (handler response)))

(defn base-handler [interceptors {:keys [handler]}]
  (if handler
    (js-handler handler interceptors)
    (throw (js/Error. "No ajax handler provided."))))

(def request-interceptors [(ProcessGet.) (DirectSubmission.) (ApplyRequestFormat.)])

(def default-interceptors (atom []))

(defn normalize-request [request]
  (let [response-format (get-response-format request)]
    (-> request
        (update :method normalize-method)
        (update :interceptors
                #(concat [response-format]
                         (or % @default-interceptors)
                         request-interceptors)))))

(defn raw-ajax-request [{:keys [interceptors] :as request}]
  (let [request (reduce process-request request interceptors)
        handler (base-handler (reverse interceptors) request)
        api (or (:api request) (new goog.net.XhrIo))]
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
           :raw (url-request-format)
           :url (url-request-format)
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
           :raw (raw-response-format)
           :detect (detect-response-format)
           nil)))

(defn keyword-response-format [format format-params]
  (if (vector? format)
    (->> format
         (map #(keyword-response-format-element % format-params))
         (apply vector))
    (keyword-response-format-element format format-params)))

(p/defn-curried transform-handler
  [{:keys [handler error-handler finally]} [ok result]]
  (if-let [h (if ok handler error-handler)]
    (h result))
  (when (fn? finally)
    (finally)))

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
