(ns ajax.core
  (:require [goog.net.XhrIo :as xhr]
            [goog.Uri :as uri]
            [goog.Uri.QueryData :as query-data]
            [goog.events :as events]
            [goog.structs :as structs]
            [cljs.reader :as reader]))

(defn success? [status]
  (some #{status} [200 201 202 204 205 206]))

(def edn-format
  {:read (fn read-edn [target]
           (reader/read-string (.getResponseText target)))
   :description "EDN"})

(def raw-format
  {:read (fn read-text [target] (.getResponseText target))
   :description "raw text"})

(defn get-json-format [{:keys [prefix keywordize-keys]}]
  {:read (fn read-json [target]
           (let [json (if prefix
                        (.getResponseJson target prefix)
                        (.getResponseJson target))]
             (js->clj json :keywordize-keys keywordize-keys)))
   :description (str "JSON"
                     (if prefix (str " prefix '" prefix "'"))
                     (if keywordize-keys " keywordize"))})

(defn get-default-format [target]
  (let [ct (.getResponseHeader target "Content-Type")
        format (if (and ct (.indexOf ct "json"))
                (get-json-format {:keywordize-keys true})
                :else edn-format)]
    (update-in format [:description] #(str % " (default)"))))

(defn get-format [{:keys [format] :as format-params}]
  (cond
   (nil? format) nil ; i.e. use get-default-format later
   (map? format) format
   (ifn? format) {:read format :description "custom"}
   (= format :json) (get-json-format format-params)
   (= format :edn) edn-format
   (= format :raw raw-format)
   (throw (js/Error. (str "unrecognized format: " format)))))

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

(defn base-handler [{:keys [format handler error-handler]}]
  (fn [response]
    (try
      (let [target (.-target response)
            status (.getStatus target)
            format (or format (get-default-format target))
            parse  (get format :read)]
        (try
          (let [response (parse target)]
            (if (success? status)
              (if handler
                (handler response))
              (if error-handler
                (error-handler {:status status
                                :status-text (.getStatusText target)
                                :response response}))))
          (catch js/Object e
            (if error-handler
              (error-handler
               (exception-response e status format target))))))
      (catch js/Object e            ; These errors should never happen
        (if error-handler
          (error-handler {:status 0
                          :status-text (.-message e)
                          :response nil}))))))

(defn params-to-str [params]
  (if params
    (-> params
        clj->js
        structs/Map.
        query-data/createFromMap
        .toString)))

(defn uri-with-params [uri params]
  (if params
    (str uri "?" (params-to-str params))
    uri))

(defn ajax-request [uri method
                    {:keys [params body headers] :as opts}]
  (let [req              (new goog.net.XhrIo)
        opts             (assoc opts :format (get-format opts))
        response-handler (base-handler opts)]
    (events/listen req goog.net.EventType/COMPLETE response-handler)
    (.send req uri method
           (or body (params-to-str params))
           (if headers (clj->js headers)))))

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
