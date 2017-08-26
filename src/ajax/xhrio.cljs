(ns ajax.xhrio
  (:require goog.net.EventType
            goog.net.ErrorCode
            [goog.net.XhrIo :as xhr]
            [goog.net.XhrManager :as xhrm]
            [goog.Uri :as uri]
            [goog.json :as goog-json]
            [goog.events :as events]
            [ajax.protocols :refer [AjaxImpl AjaxRequest
                                    AjaxResponse Interceptor]]))

(extend-type goog.net.XhrIo
  AjaxImpl
  (-js-ajax-request
    [this
     {:keys [uri method body headers timeout with-credentials
             response-format progress-handler]
      :or {with-credentials false
           timeout 0}}
     handler]
    (when-let [response-type (:type response-format)]
      (.setResponseType this (name response-type)))
    (when (fn? progress-handler)
      (doto this
        (.setProgressEventsEnabled true)
        (events/listen goog.net.EventType.UPLOAD_PROGRESS progress-handler)))
    (doto this
      (events/listen goog.net.EventType/COMPLETE
                     #(handler (.-target %)))
      (.setTimeoutInterval timeout)
      (.setWithCredentials with-credentials)
      (.send uri method body (clj->js headers))))
  AjaxRequest
  (-abort [this] (.abort this goog.net.ErrorCode/ABORT))
  AjaxResponse
  (-body [this] (.getResponse this))
  (-status [this] (.getStatus this))
  (-status-text [this] (.getStatusText this))
  (-get-all-headers [this]
    (js->clj (.getResponseHeaders this)))
  (-get-response-header [this header]
    (.getResponseHeader this header))
  (-was-aborted [this]
    (= (.getLastErrorCode this) goog.net.ErrorCode/ABORT)))

(extend-type goog.net.XhrManager
  AjaxImpl
  (-js-ajax-request
    [this {:keys [uri method body headers
                  id timeout priority max-retries]
           :or {timeout 0}}
     handler]
    (.send this id uri method body (clj->js headers)
           priority handler max-retries)))
