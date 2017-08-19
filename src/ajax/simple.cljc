(ns ajax.simple
    (:require [clojure.string :as str]
              [ajax.protocols :as pr]
              [ajax.interceptors :as i]
              [ajax.formats :as f]
              [ajax.util :as u]
              #? (:clj [ajax.apache :as a])
              #? (:clj [poppea :as p]
                  :cljs [goog.net.XhrIo :as xhr]))
    #? (:clj (:import [java.io Closeable])
        :cljs (:require-macros [poppea :as p])))

(defn normalize-method [method]
  (if (keyword? method)
    (str/upper-case (name method))
    method))

(defn process-response [response interceptor]
  (pr/-process-response interceptor response))

(p/defn-curried js-handler [handler interceptors response]
  (let [processed (reduce process-response response interceptors)]
    ;;; This requires a bit of explanation: if we return a closeable,
    ;;; it should be wrapping the original response, so we _don't_
    ;;; close the original response stream
    ;;; If you're writing a weird interceptor that doesn't do this,
    ;;; remember to close the original stream yourself
    #? (:clj (if (and response
                      (instance? Closeable (second processed)))
               (.close ^Closeable (pr/-body response))))
    (handler processed)))

(defn base-handler [interceptors {:keys [handler]}]
  (if handler
    (js-handler handler interceptors)
    (u/throw-error "No ajax handler provided.")))

(def default-interceptors (atom []))

(defn normalize-request [request]
  (let [response-format (i/get-response-format f/detect-response-format request)]
    (-> request
        (update :method normalize-method)
        (update :interceptors
                #(concat [response-format]
                         (or % @default-interceptors)
                         i/request-interceptors)))))

(defn new-default-api []
  #? (:clj  (a/new-api)
      :cljs (new goog.net.XhrIo)))

(defn process-request [request interceptor]
  "-process-request with the arguments flipped for use in reduce"
  (pr/-process-request interceptor request))

(defn raw-ajax-request [{:keys [interceptors] :as request}]
  "The main request function."
  (let [request (reduce process-request request interceptors)
        ;;; Pass the request through the interceptors
        handler (base-handler (reverse interceptors) request)
        ;;; Set up a handler that passes it back through
        api (or (:api request) (new-default-api))]
    (pr/-js-ajax-request api request handler)))

(defn ajax-request [request]
  (-> request normalize-request raw-ajax-request))
