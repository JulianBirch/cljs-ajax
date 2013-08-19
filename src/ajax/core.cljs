(ns ajax.core
  (:require [goog.net.XhrIo :as xhr]
            [goog.Uri :as uri]
            [goog.Uri.QueryData :as query-data]
            [goog.json.Serializer]
            [goog.events :as events]
            [goog.structs :as structs]
            [cljs.reader :as reader]
            [clojure.string :as str]))

(defn success? [status]
  (some #{status} [200 201 202 204 205 206]))

(defn read-edn [target]
  (reader/read-string (.getResponseText target)))

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

(defn read-text [target]
  (.getResponseText target))

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
   you should use this.
   http://stackoverflow.com/questions/2669690/why-does-google-prepend-while1-to-their-json-responses
   http://haacked.com/archive/2009/06/24/json-hijacking.aspx"
  ([] (json-response-format {}))
  ([{:keys [prefix keywords?]}]
     {:read (fn read-json [target]
              (let [json (.getResponseJson target prefix)]
                (js->clj json :keywordize-keys keywords?)))
      :description (str "JSON"
                        (if prefix (str " prefix '" prefix "'"))
                        (if keywords? " keywordize"))}))

(defn get-default-format [target]
  (let [ct (.getResponseHeader target "Content-Type")
        format (if (and ct (>= (.indexOf ct "json") 0))
                (json-response-format)
                (edn-response-format))]
    (update-in format [:description] #(str % " (default)"))))

(defn use-content-type [format]
  (dissoc format :write))

(defn codec [request-format
             {:keys [read description] :as response-format}]
  (assoc request-format
    :read read
    :description description))

(defn get-format [format]
  (cond
   (map? format) format
   (ifn? format) (codec (url-request-format)
                        {:read format :description "custom"})
   :else (throw (js/Error. (str "unrecognized format: " format)))))

(defn exception-response [e status {:keys [description]} target]
  (let [response {:status status
                  :response nil}
        status-text (str (.-message e)
                         "  Format should have been "
                         description)
        parse-error (assoc response
                      :status-text status-text
                      :is-parse-error true
                      :original-text (.getResponseText target))]
    (if (success? status)
      parse-error
      (assoc response
        :status-text (.getStatusText target)
        :parse-error parse-error))))

(defn interpret-response [format response get-default-format]
  (try
    (let [target (.-target response)
          status (.getStatus target)
          format (if (:read format)
                   format
                   (get-default-format target))
          parse  (:read format)]
      (try
        (let [response (parse target)]
          (if (success? status)
            [true response]
            [false {:status status
                    :status-text (.getStatusText target)
                    :response response}]))
        (catch js/Object e
          [false (exception-response e status format target)])))
    (catch js/Object e               ; These errors should never happen
      [false {:status 0
              :status-text (.-message e)
              :response nil}])))

(defn no-format [target]
  (throw (js/Error. (str "Format cannot be used to read result,  "))))

(defn uri-with-params [uri params]
  (if params
    (str uri "?" (params-to-str params))
    uri))

(defn process-inputs [uri method
                      {:keys [write content-type] :as format}
                      {:keys [params headers]}]
  (if (= method "GET")
    [(uri-with-params uri params) nil headers]
    (let [{:keys [write content-type]} format body (write params)
          content-type (if content-type
                         {"Content-Type" content-type})
          headers (merge (or headers {}) content-type)] [uri body
          headers])))

(defn normalize-method [method]
  (if (keyword? method)
    (str/upper-case (name method))
    method))

(defn js-ajax-request [uri method body headers timeout handler]
  (doto (new goog.net.XhrIo)
    (events/listen goog.net.EventType/COMPLETE handler)
    (.send uri method body headers timeout)))

(defn ajax-request
  ([uri method {:keys [handler] :as opts} js-ajax-request]
     (let [format (get-format (:format opts))
           method (normalize-method method)
           [uri body headers]
           (process-inputs uri method format opts)]
       (js-ajax-request uri method body
                        (clj->js headers) (:timeout opts)
                        handler)))
  ([uri method opts]
     (ajax-request uri method opts js-ajax-request)))

(defn json-format [format-params]
  (codec (json-request-format)
                 (json-response-format format-params)))

(defn edn-format []
  (codec (edn-request-format) (edn-response-format)))

(defn raw-format []
  (codec (url-request-format) (raw-response-format)))

(defn keyword-format [format format-params]
  (case format
    :json (json-format format-params)
    :edn (edn-format)
    :raw (raw-format)
    :url (url-request-format)
    (throw
     (js/Error. (str "unrecognized request format: " format)))))

(defn base-handler [format {:keys [handler error-handler]}]
  (fn [response]
    (let [[ok result]
          (interpret-response format response get-default-format)
          h (if ok handler error-handler)]
      (if h (h result)))))

(defn enhance-opts [{:keys [format] :as opts}]
  "Note that if you call GET and POST, this function gets called and
   will include JSON and EDN code in your JS.  If you don't want
   this to happen, use ajax-request directly."
  (let [format (cond (nil? format) (url-request-format)
                             (keyword? format)
                             (keyword-format format opts)
                             :else nil)]
    (assoc opts
      :handler (base-handler format opts)
      :format format)))

(defn GET
  "accepts the URI and an optional map of options, options include:
  :handler - the handler function for successful operation
             should accept a single parameter which is the deserialized
             response
  :error-handler - the handler function for errors, should accept a map
                   with keys :status and :status-text
  :format - the format for the response
  :params - a map of parameters that will be sent with the request"
  [uri & [opts]]
  (ajax-request uri "GET" (enhance-opts opts)))

(defn POST
  "accepts the URI and an optional map of options, options include:
  :handler - the handler function for successful operation
             should accept a single parameter which is the deserialized
             response
  :error-handler - the handler function for errors, should accept a map
                   with keys :status and :status-text
  :format - the format for the response
  :params - a map of parameters that will be sent with the request"
  [uri & [opts]]
  (ajax-request uri "POST" (enhance-opts opts)))
