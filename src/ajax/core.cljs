(ns ajax.core
  (:require [goog.net.XhrIo :as xhr]
            [goog.Uri :as uri]
            [goog.Uri.QueryData :as query-data]
            [goog.json.Serializer]
            [goog.events :as events]
            [goog.structs :as structs]
            [cljs.reader :as reader]))

(defn success? [status]
  (some #{status} [200 201 202 204 205 206]))

(defn read-edn [target]
  (reader/read-string (.getResponseText target)))

(defn edn-format []
  {:read read-edn
   :description "EDN"
   :write pr-str
   :content-type "text/edn"})

(defn params-to-str [params]
  (if params
    (-> params
        clj->js
        structs/Map.
        query-data/createFromMap
        .toString)))

(defn raw-format []
  {:read (fn read-text [target] (.getResponseText target))
   :description "raw text"
   :write params-to-str
   :content-type "application/x-www-form-urlencoded"})

(defn write-json [data]
  (.log js/console "WRITE JSON" data)
  (.serialize (goog.json.Serializer.) (clj->js data)))

(defn json-format [{:keys [prefix keywordize-keys]}]
  {:read (fn read-json [target]
           (let [json (.getResponseJson target prefix)]
             (js->clj json :keywordize-keys keywordize-keys)))
   :description (str "JSON"
                     (if prefix (str " prefix '" prefix "'"))
                     (if keywordize-keys " keywordize"))
   :write write-json
   :content-type "application/json"})

(defn get-default-format [target]
  (let [ct (.getResponseHeader target "Content-Type")
        format (if (and ct (.indexOf ct "json"))
                (json-format {})
                (edn-format))]
    (update-in format [:description] #(str % " (default)"))))

(defn keyword-format [format format-params]
  (case format
    :json (json-format format-params)
    :edn (edn-format)
    :raw (raw-format)
    (throw (js/Error. (str "unrecognized format: " format)))))

(defn get-format [{:keys [format] :as format-params}]
  (cond
   (keyword? format) (keyword-format format format-params)
   (nil? format) nil ; i.e. use get-default-format later
   (map? format) format
   (ifn? format) {:read format :description "custom"}
   :else (throw (js/Error. (str "unrecognized format: " format)))))

(defn exception-response [e status format target]
  (let [response {:status status
                  :response nil}
        status-text (str (.-message e)
                         "  Format should have been "
                         (:description format))
        parse-error (assoc response
                      :status-text status-text
                      :is-parse-error true
                      :original-text (.getResponseText target))]
    (if (success? status)
      parse-error
      (assoc response
        :status-text (.getStatusText target)
        :parse-error parse-error))))

(defn interpret-response [format response]
  (try
    (let [target (.-target response)
          status (.getStatus target)
          format (or format (get-default-format target))
          parse  (get format :read)]
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

(defn base-handler [format {:keys [handler error-handler]}]
  (fn [response]
    (let [[ok result] (interpret-response format response)
          h (if ok handler error-handler)]
      (if h (h result)))))

(defn uri-with-params [uri params]
  (if params
    (str uri "?" (params-to-str params))
    uri))

(defn payload-and-headers [format {:keys [params body headers data]}]
  (let [payload (or body
                    (if-let [write (:write format)] (write data))
                    (params-to-str params))
        content-type (if (and (nil? body) data)
                       (if-let [ct (:content-type format)]
                         {"Content-Type" ct}))
        headers (merge (or headers {}) content-type)]
    [payload headers]))

(defn ajax-request [uri method opts]
  (let [req (new goog.net.XhrIo)
        format (get-format opts)
        response-handler (base-handler format opts)
        [payload headers] (payload-and-headers format opts)]
    (events/listen req goog.net.EventType/COMPLETE response-handler)
    (.send req uri method payload
           (clj->js headers) (:timeout opts))))

(defn GET
  "accepts the URI and an optional map of options, options include:
  :handler - the handler function for successful operation
             should accept a single parameter which is the deserialized
             response
  :error-handler - the handler function for errors, should accept a map
                   with keys :status and :status-text
  :format - the format for the response :edn or :json defaults to :edn
  :params - a map of parameters that will be sent with the request"
  [uri & [opts]]
  (ajax-request (uri-with-params uri (:params opts))
                "GET"
                (dissoc opts :params)))

(defn POST
  "accepts the URI and an optional map of options, options include:
  :handler - the handler function for successful operation
             should accept a single parameter which is the deserialized
             response
  :error-handler - the handler function for errors, should accept a map
                   with keys :status and :status-text
  :format - the format for the response :edn or :json defaults to :edn
  :params - a map of parameters that will be sent with the request"
  [uri & [opts]]
  (ajax-request uri "POST" opts))
