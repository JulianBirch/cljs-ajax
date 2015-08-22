(ns ajax.apache
  (:require [ajax.protocols :refer [map->Response]]
            [clojure.string :as s])
  (:import [org.apache.http HttpResponse]
           [org.apache.http.entity ByteArrayEntity]
           [org.apache.http.client.methods HttpRequestBase
            HttpEntityEnclosingRequestBase]
           [org.apache.http.client.config RequestConfig]
           [org.apache.http.concurrent FutureCallback]
           [org.apache.http.impl.nio.client HttpAsyncClients]
           [ajax.protocols AjaxImpl AjaxRequest AjaxResponse
            Interceptor Response]
           [java.lang Exception]
           [java.util.concurrent Future]
           [java.net URI SocketTimeoutException]))

;;; Chunks of this code liberally ripped off dakrone/clj-http
;;; Although that uses the synchronous API

(def array-of-bytes-type (Class/forName "[B"))

(defn to-entity [b]
  (if (instance? array-of-bytes-type b)
    (ByteArrayEntity. b)
    b))

(defn to-uri [u]
  (if (instance? URI u)
    u
    (URI. (s/replace u " " "%20"))))

(defrecord HttpResponseWrapper [^HttpResponse response]
  AjaxResponse
  (-body [this]
    (let [^HttpResponse response (:response this)]
      (.getContent (.getEntity response))))
  (-status [this]
    (let [^HttpResponse response (:response this)]
      (-> response .getStatusLine .getStatusCode)))
  (-status-text [this]
    (let [^HttpResponse response (:response this)]
      (-> response .getStatusLine .getReasonPhrase)))
  (-get-response-header [this header]
    (let [^HttpResponse response (:response this)]
      (.getValue (.getFirstHeader response header))))
  (-was-aborted [this] false))

(defn create-request [method]
  (proxy [HttpEntityEnclosingRequestBase] []
    (getMethod [] method)))

(defn cancel [handler]
  (handler
   (map->Response {:status -1
                   :status-text "Cancelled"
                   :headers {}
                   :was-aborted true})))

(defn fail [handler ^Exception ex]
  (let [status (if (instance? SocketTimeoutException ex) -1 0)]
;;; XMLHttpRequest reports a status of -1 for timeouts, so
;;; we do the same
    (handler
     (map->Response {:status status
                     :status-text (.getMessage ex)
                     :headers {}
                     :exception ex
                     :was-aborted false}))))

(defn create-handler [handler]
  (proxy [FutureCallback] []
    (cancelled []
      (cancel handler))
    (completed [^HttpResponse response]
      (handler (HttpResponseWrapper. response)))
    (failed [^Exception ex]
      (fail handler ex))))

(defrecord RunningRequest [future]
  AjaxRequest
  (-abort [this]
    (let [^Future future (:future this)]
      (.cancel future true))))

(defn create-request-config ^RequestConfig
  [{:keys [timeout socket-timeout]}]
  (let [builder (RequestConfig/custom)]
    (if timeout
      (.setConnectTimeout builder timeout))
    (if-let [st (or socket-timeout timeout)]
      (.setSocketTimeout builder st))
    (.build builder)))

(defrecord Connection []
  AjaxImpl
  (-js-ajax-request
    [this {:keys [uri method body headers] :as opts} handler]
    (try
      (let [request (doto (create-request method)
                      (.setURI (to-uri uri))
                      (.setEntity (to-entity body)))
            request-config (create-request-config opts)
            builder (doto (HttpAsyncClients/custom)
                      (.setDefaultRequestConfig request-config))
            client (doto (.build builder)
                     (.start))
            h (create-handler handler)]
        (doseq [x headers]
          (let [[h v] x]
            (.addHeader request h v)))
        (RunningRequest. (.execute client request h)))
      (catch Exception ex (fail handler ex)))))
