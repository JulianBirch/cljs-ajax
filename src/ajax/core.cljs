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
            [clojure.string :as str])
  (:require-macros [ajax.macros :as m]))

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
(defn edn-response-format [] {:read read-edn :description "EDN"})
(defn edn-request-format []
  {:write pr-str
   :content-type "application/edn"})

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

(defn raw-response-format []
  {:read read-text
   :description "raw text"})

(defn write-json [data]
  (.serialize (goog.json.Serializer.) (clj->js data)))

(defn json-request-format []
  {:write write-json
   :content-type "application/json"})

(defn json-response-format
  "Returns a JSON response format.  Options include
   :keywords? Returns the keys as keywords
   :prefix A prefix that needs to be stripped off.  This is to
   combat JSON hijacking.  If you're using JSON with GET request,
   you should think about using this.
   http://stackoverflow.com/questions/2669690/why-does-google-prepend-while1-to-their-json-responses
   http://haacked.com/archive/2009/06/24/json-hijacking.aspx"
  ([{:keys [prefix keywords?]}]
     {:read (fn read-json [xhrio]
              (let [json (.getResponseJson xhrio prefix)]
                (js->clj json :keywordize-keys keywords?)))
      :description (str "JSON"
                        (if prefix (str " prefix '" prefix "'"))
                        (if keywords? " keywordize"))}))

(defn get-default-format [xhrio]
  (let [ct (or (.getResponseHeader xhrio "Content-Type") "")]
    (let [detect (fn detect [s] (>= (.indexOf ct s) 0))
          format (cond
                  (detect "application/json") (json-response-format {})
                  (detect "application/edn") (edn-response-format)
                  (detect "text/plain") (raw-response-format)
                  (detect "text/html") (raw-response-format)
                  :else (edn-response-format))]
      (update-in format [:description] #(str % " (default)")))))

(defn get-response-format [format]
  (cond
   (map? format) format
   (ifn? format) {:read format :description "custom"}
   :else (throw (js/Error. (str "unrecognized response format: " format)))))

(defn exception-response [e status {:keys [description]} xhrio]
  (let [response {:status status
                  :response nil}
        status-text (str (.-message e)
                         "  Format should have been "
                         description)
        parse-error (assoc response
                      :status-text status-text
                      :is-parse-error true
                      :original-text (.getResponseText xhrio))]
    (if (success? status)
      parse-error
      (assoc response
        :status-text (.getStatusText xhrio)
        :parse-error parse-error))))

(defn fail [status status-text keyword value]
  [false {:status status
          :status-text status-text
          keyword value}])

(defn interpret-response [format response get-default-format]
  (try
    #_(.log js/console response)
    (let [xhrio (.-target response)
          status (.getStatus xhrio)
          fail (fn fail [status-text keyword value]
                 {:status status
                  :status-text status-text
                  keyword value})]
      (if (= -1 status)
        (if (= (.getLastErrorCode xhrio) goog.net.ErrorCode/ABORT)
          (fail "Request aborted by client." :aborted? true)
          (fail "Request timed out." :timeout? true))
        (let [format (if (:read format)
                       format
                       (get-default-format xhrio))
              parse  (:read format)]
          (try
            (let [response (parse xhrio)]
              (if (success? status)
                [true response]
                (fail (.getStatusText xhrio) :response response)))
            (catch js/Object e
              [false (exception-response e status format xhrio)])))))
    (catch js/Object e                ; These errors should never happen
      [false {:status 0
              :status-text (.-message e)
              :response nil}])))

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

(defn base-handler [response-format
                    {:keys [handler get-default-format]}]
  (if handler
    (fn [xhrio]
      (let [response
            (interpret-response response-format xhrio
                                (or get-default-format no-format))]
        #_(.log js/console (str "RES:  " response))
        (handler response)))
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

(defn keyword-request-format [format format-params]
  (cond
   (map? format) format
   (ifn? format) {:write format}
   :else (case format
           :json (json-request-format)
           :edn (edn-request-format)
           :raw (url-request-format)
           :url (url-request-format)
           nil)))

(defn keyword-response-format [format format-params]
  (cond
   (map? format) format
   (ifn? format) {:read format :description "custom"}
   :else (case format
           :json (json-response-format format-params)
           :edn (edn-response-format)
           :raw (raw-response-format)
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
  (let [rf (keyword-request-format format opts)
        needs-format
        (not (or (satisfies? DirectlySubmittable params)
                 (= method "GET")))]
    (assoc opts
      :handler (transform-handler opts)
      :format (or rf (if needs-format
                       (js/Error. (str "unrecognized request format: '"
                                       (or format "NIL") "'"))))
      :response-format (keyword-response-format response-format opts)
      :get-default-format get-default-format)))

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
