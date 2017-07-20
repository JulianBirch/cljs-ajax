(ns ajax.apache
  (:require [ajax.protocols :refer [AjaxImpl AjaxRequest AjaxResponse
                                    map->Response]]
            [clojure.string :as s])
  (:import [org.apache.http HttpResponse]
           [org.apache.http.entity ByteArrayEntity StringEntity
            FileEntity InputStreamEntity]
           [org.apache.http.client.methods HttpRequestBase
            HttpEntityEnclosingRequestBase]
           [org.apache.http.client.config RequestConfig]
           [org.apache.http.concurrent FutureCallback]
           [org.apache.http.impl.nio.client HttpAsyncClients]
           [java.util.concurrent Future]
           [java.net URI SocketTimeoutException]
           [java.io File InputStream]))

;;; Chunks of this code liberally ripped off dakrone/clj-http
;;; Although that uses the synchronous API

(def array-of-bytes-type (Class/forName "[B"))

(defn- to-entity [b]
  (condp instance? b
    array-of-bytes-type (ByteArrayEntity. b)
    String (StringEntity. b "UTF-8")
    File (FileEntity. b)
    InputStream (InputStreamEntity. b)
    b))

(defn- to-uri [u]
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

(defn- create-request
  ^HttpEntityEnclosingRequestBase [method]
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
  (reify
    FutureCallback
    (cancelled [_]
      (cancel handler))
    (completed [_ response]
      (handler (HttpResponseWrapper. response)))
    (failed [_ ex]
      (fail handler ex))))

(defn- create-request-config
  ^RequestConfig [{:keys [timeout socket-timeout]}]
  (let [builder (RequestConfig/custom)]
    (if timeout
      (.setConnectTimeout builder timeout))
    (if-let [st (or socket-timeout timeout)]
      (.setSocketTimeout builder st))
    (.build builder)))

(defn- to-clojure-future [^Future future ^java.io.Closeable client]
  "Converts a normal Java future to one similar to the one generated
   by `clojure.core/future`"
  (reify
    clojure.lang.IDeref
    (deref [_]
      (try
        (.get future)
        (finally (.close client))))
    clojure.lang.IBlockingDeref
    (deref [_ timeout-ms timeout-val]
      (try
        (.get future timeout-ms
              java.util.concurrent.TimeUnit/MILLISECONDS)
        (catch java.util.concurrent.TimeoutException e
          timeout-val)
        (finally (.close client))))
    clojure.lang.IPending
    (isRealized [_] (.isDone future))
    java.util.concurrent.Future
    (get [_]
      (try
        (.get future)
        (finally (.close client))))
    (get [_ timeout unit]
      (try
        (.get future timeout unit)
        (finally (.close client))))
    (isCancelled [_] (.isCancelled future))
    (isDone [_] (.isDone future))
    (cancel [_ interrupt?]
      (try
        (.cancel future interrupt?)
        (finally (.close client))))
    AjaxRequest
    (-abort [_]
      (try
        (.cancel future true)
        (finally (.close client))))))

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
        (to-clojure-future (.execute client request h) client))
      (catch Exception ex (fail handler ex)))))
