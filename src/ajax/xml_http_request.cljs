(ns ajax.xml-http-request
  (:require [ajax.protocols :refer [AjaxImpl AjaxRequest
                                    AjaxResponse Interceptor]]))

(defn ready-state [e]
  ({0 :not-initialized
    1 :connection-established
    2 :request-received
    3 :processing-request
    4 :response-ready} (.-readyState (.-target e))))

(def xmlhttprequest
  (if (= cljs.core/*target* "nodejs")
    (let [xmlhttprequest (.-XMLHttpRequest (js/require "@pupeno/xmlhttprequest"))]
      (goog.object/set js/global "XMLHttpRequest" xmlhttprequest)
      xmlhttprequest)
    (.-XMLHttpRequest js/window)))

(extend-type xmlhttprequest
  AjaxImpl
  (-js-ajax-request
    [this
     {:keys [uri method body headers timeout with-credentials
             response-format]
      :or {with-credentials false
           timeout 0}}
     handler]
    (set! (.-withCredentials this) with-credentials)
    (set! (.-onreadystatechange this)
          #(when (= :response-ready (ready-state %))
             (handler this)))
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
