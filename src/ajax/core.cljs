(ns ajax.core
  (:require [goog.net.XhrIo :as xhr]
            [goog.Uri :as uri]
            [goog.Uri.QueryData :as query-data]
            [goog.events :as events]
            [goog.structs :as structs]
            [cljs.reader :as reader]))

(defn success? [status]
  (some #{status} [200 201 202 204 205 206]))

(defn base-handler [& [format handler error-handler]]
  (fn [response]
    (let [target (.-target response)
          status (.getStatus target)]
      (if (success? status)
        (if handler
          (handler (condp = (or format :edn)
                     :json (js->clj (.getResponseJson target))
                     :edn (reader/read-string (.getResponseText target))
                     (throw (js/Error. (str "unrecognized format: " format))))))
        (if error-handler
          (error-handler {:status status
                          :status-text (.getStatusText target)}))))))

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

(defn ajax-request [uri method {:keys [format handler error-handler params]}]
  (let [req              (new goog.net.XhrIo)
        response-handler (base-handler format handler error-handler)]
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

