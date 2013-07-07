(ns ajax.core
  (:require [goog.net.XhrIo :as xhr]
            [goog.Uri :as uri]
            [goog.Uri.QueryData :as query-data]
            [goog.events :as events]
            [goog.structs :as structs]
            [cljs.reader :as reader]))

(defn success? [status]
  (some #{status} [200 201 202 204 205 206]))

(defn parse-response [target format keywordize-keys]
  (condp = (or format :edn)
    :json (js->clj (.getResponseJson target) :keywordize-keys keywordize-keys)
    :edn (reader/read-string (.getResponseText target))
    (throw (js/Error. (str "unrecognized format: " format)))))

(defn exception-response [e status format target]
  (let [response {:status status
                  :response nil}
        status-text (str (.-message e)
                         "  Format should have been "
                         (or format :edn)
                         (if format
                           "."
                           " (default)."))
        parse-error (assoc response
                      :status-text status-text
                      :is-parse-error true
                      :original-text (.getResponseText target))]
    (if (success? status)
      parse-error
      (assoc response
        :status-text (.getStatusText target)
        :parse-error parse-error))))

(defn base-handler [& [format handler error-handler keywordize-keys]]
  (fn [response]
    (try
      (let [target (.-target response)
            status (.getStatus target)]
        (try
          (let [response (parse-response target format keywordize-keys)]
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
                          :status-text (.getStatusText target)
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

(defn ajax-request [uri method {:keys [format keywordize-keys handler error-handler params]}]
  (let [req              (new goog.net.XhrIo)
        response-handler (base-handler format handler error-handler keywordize-keys)]
    (events/listen req goog.net.EventType/COMPLETE response-handler)
    (.send req uri method (params-to-str params))))

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
