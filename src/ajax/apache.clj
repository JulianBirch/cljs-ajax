(ns ajax.apache
  (:require [ajax.protocols :refer [map->Response
              AjaxImpl AjaxRequest AjaxResponse]]
            [clojure.java.io :as io]
            [clojure.string :as s])
  (:import [clojure.lang IDeref IBlockingDeref IPending]
           [org.apache.hc.client5.http ConnectTimeoutException]
           [org.apache.hc.client5.http.async.methods SimpleHttpResponse
            SimpleResponseConsumer]
           [org.apache.hc.client5.http.config ConnectionConfig RequestConfig]
           [org.apache.hc.client5.http.cookie StandardCookieSpec]
           [org.apache.hc.client5.http.impl.async CloseableHttpAsyncClient
            HttpAsyncClients]
           [org.apache.hc.client5.http.impl.nio
            PoolingAsyncClientConnectionManagerBuilder]
           [org.apache.hc.core5.concurrent FutureCallback]
           [org.apache.hc.core5.http ContentType Header]
           [org.apache.hc.core5.http.nio AsyncEntityProducer
            AsyncRequestProducer]
           [org.apache.hc.core5.http.nio.entity AsyncEntityProducers]
           [org.apache.hc.core5.http.nio.support AsyncRequestBuilder]
           [org.apache.hc.core5.http.nio.support.classic
            AbstractClassicEntityProducer]
           [org.apache.hc.core5.util Timeout]
           [java.lang Exception]
           [java.util.concurrent Future]
           [java.net URI SocketTimeoutException]
           [java.io ByteArrayInputStream File InputStream OutputStream
            Closeable]))

;;; Chunks of this code liberally ripped off dakrone/clj-http
;;; Although that uses the synchronous API
;;; Note that the only thing exposed by this rather complicated
;;; piece of code is `new-api` at the bottom.

(def array-of-bytes-type (Class/forName "[B"))

(def ^:private ^ContentType text-content-type
  (ContentType/create "text/plain" "UTF-8"))

(defn- stream-entity-producer
  "HttpClient 5 has no entity producer for an InputStream, so we use
   the classic (blocking) producer, which pumps the stream on a
   separate thread rather than buffering it in memory."
  ^AsyncEntityProducer [^InputStream in]
  (proxy [AbstractClassicEntityProducer] [8192 nil clojure.lang.Agent/soloExecutor]
    (produceData [_content-type ^OutputStream out]
      (io/copy in out))))

(defn- to-entity-producer
  "This function means you can just hand cljs-ajax a byte
   array, string, normal Java file or input stream and it
   will automatically work with the Apache implementation."
  ^AsyncEntityProducer [b]
  (condp instance? b
    array-of-bytes-type (AsyncEntityProducers/create ^bytes b nil)
    String (AsyncEntityProducers/create ^String b text-content-type)
    File (AsyncEntityProducers/create ^File b nil)
    InputStream (stream-entity-producer b)
    b))

(defn- to-uri [u]
  (if (instance? URI u)
    u
    (URI. (s/replace u " " "%20"))))

;;; This is a nice demonstration of how protocols don't
;;; in fact solve the expression problem. Various apache
;;; methods return HttpResponse classes, but it is not
;;; guaranteed what concrete class is returned nor that
;;; it is stable between minor version numbers.

;;; So, you end up doing what you'd normally do in Java,
;;; write an adapter class.

;;; Takes a SimpleHttpResponse and exposes the interface needed
;;; by cljs-ajax interceptors (including response formats).

(defrecord HttpResponseWrapper [^SimpleHttpResponse response]
  AjaxResponse
  (-body [this]
    (let [^SimpleHttpResponse response (:response this)]
      (some-> (.getBodyBytes response) (ByteArrayInputStream.))))
  (-status [this]
    (let [^SimpleHttpResponse response (:response this)]
      (.getCode response)))
  (-status-text [this]
    (let [^SimpleHttpResponse response (:response this)]
      (.getReasonPhrase response)))
  (-get-all-headers [this]
    (let [^SimpleHttpResponse response (:response this)]
      (reduce (fn [headers ^Header header]
                (assoc headers (.getName header) (.getValue header)))
              {}
              (.getHeaders response))))
  (-get-response-header [this header]
    (let [^SimpleHttpResponse response (:response this)]
      (.getValue (.getFirstHeader response ^String header))))
  (-was-aborted [this] false))

(defn- create-request
  "Builds the request producer the Apache async API executes.
   `AsyncRequestBuilder` takes the method as a string, so all
   HTTP methods are supported without a class per method."
  ^AsyncRequestProducer [{:keys [uri method body headers]}]
  (let [builder (AsyncRequestBuilder/create method)]
    (.setUri builder ^URI (to-uri uri))
    (when-let [entity (to-entity-producer body)]
      (.setEntity builder ^AsyncEntityProducer entity))
    (doseq [x headers]
      (let [[h v] x]
        (.addHeader builder ^String h ^String v)))
    (.build builder)))

(defn- cancel
  "This method ensures that the behaviour of the wrapped
   Apache classes matches the behaviour the javascript version,
   including the negative status number."
  [handler]
  (handler
   (map->Response {:status -1
                   :status-text "Cancelled"
                   :headers {}
                   :was-aborted true})))

(defn- timeout? [ex]
  (or (instance? SocketTimeoutException ex)
      (instance? ConnectTimeoutException ex)))

(defn- fail [handler ^Exception ex]
  "XMLHttpRequest reports a status of -1 for timeouts, so
   we do the same."
  (let [status (if (timeout? ex) -1 0)]
    (handler
     (map->Response {:status status
                     :status-text (.getMessage ex)
                     :headers {}
                     :exception ex
                     :was-aborted false}))))

(defn- create-handler
   "Takes a cljs-ajax style handler method and converts it
   to a FutureCallback suitable for use the Apache API."
  [handler]
  (reify
    FutureCallback
    (cancelled [_]
      (cancel handler))
    (completed [_ response]
      (handler (HttpResponseWrapper. response)))
    (failed [_ ex]
      (fail handler ex))))

(defmulti get-cookie-policy
  "Method to retrieve the cookie policy that should be used for the request.
   This is a multimethod that may be extended to return your own cookie policy.
   Dispatches based on the `:cookie-policy` key in the request map."
  (fn get-cookie-dispatch [request] (:cookie-policy request)))

(defmethod get-cookie-policy :none none-cookie-policy
  [_] StandardCookieSpec/IGNORE)
(defmethod get-cookie-policy :default default-cookie-policy
  [_] StandardCookieSpec/RELAXED)
(defmethod get-cookie-policy :netscape netscape-cookie-policy
  [_] StandardCookieSpec/RELAXED)
(defmethod get-cookie-policy :standard standard-cookie-policy
  [_] StandardCookieSpec/RELAXED)
(defmethod get-cookie-policy :standard-strict standard-strict-cookie-policy
  [_] StandardCookieSpec/STRICT)

(defn- to-timeout
  "Zero or absent means no timeout, matching XMLHttpRequest."
  ^Timeout [ms]
  (if (and ms (pos? ms))
    (Timeout/ofMilliseconds (long ms))
    Timeout/DISABLED))

(defn- create-connection-config
  ^ConnectionConfig [{:keys [timeout socket-timeout]}]
  (-> (ConnectionConfig/custom)
      (.setConnectTimeout (to-timeout timeout))
      (.setSocketTimeout (to-timeout (or socket-timeout timeout)))
      (.build)))

(defn- create-request-config
  ^RequestConfig [{:keys [cookie-policy] :as req}]
  (let [builder (RequestConfig/custom)]
    (if cookie-policy
      (.setCookieSpec builder ^String (get-cookie-policy req)))
    (.build builder)))

(defn- create-client
  "The connection manager owns the connect and socket timeouts in
   HttpClient 5; it is not shared, so closing the client closes it."
  ^CloseableHttpAsyncClient [opts]
  (let [connection-manager (-> (PoolingAsyncClientConnectionManagerBuilder/create)
                               (.setDefaultConnectionConfig
                                (create-connection-config opts))
                               (.build))]
    (-> (HttpAsyncClients/custom)
        (.setConnectionManager connection-manager)
        (.setDefaultRequestConfig (create-request-config opts))
        (.build))))

(defn- to-clojure-future
  "Converts a normal Java future to one similar to the one generated
   by `clojure.core/future`. Operationally, this is used to wrap the
   result of the Apache API into something that can be returned by
   `ajax-request`. Note that there's no guarantee anyone will ever dereference
   it (but they might). Also, since it's returned by `ajax-request`,
   it needs to support `abort`."
  [^Future f ^Closeable client]
  ;;; We wrap the original future and closeable in a second layer
  ;;; to guarantee that we don't leak memory. This deeply clever
  ;;; solution is by https://github.com/divs1210
  (let [^Future f* (future
                     (try
                       (.get f)
                       (finally (.close client))))
        cancel* (fn [interrupt?]
                  (try
                    (.cancel f interrupt?)
                    (.cancel f* interrupt?)
                    (finally (.close client))))]
    (reify
      IDeref
      (deref [_] (deref f*))
      IBlockingDeref
      (deref [_ timeout-ms timeout-val]
        (deref f* timeout-ms timeout-val))
      IPending
      (isRealized [_] (.isDone f*))
      Future
      (get [_] (.get f*))
      (get [_ timeout unit]
        (.get f* timeout unit))
      (isCancelled [_] (.isCancelled f*))
      (isDone [_] (.isDone f*))
      (cancel [_ interrupt?]
        (cancel* interrupt?))
      AjaxRequest
      (-abort [_]
        (cancel* true)))))

(defrecord Connection []
  AjaxImpl
  (-js-ajax-request
    [this opts handler]
    (try
      (let [request (create-request opts)
            ^CloseableHttpAsyncClient client (create-client opts)
            ^FutureCallback h (create-handler handler)]
        (.start client)
        (to-clojure-future
         (.execute client request (SimpleResponseConsumer/create) h)
         client))
      (catch Exception ex (fail handler ex)))))

(defn new-api
  "This is the only thing exposed by the apache.clj file:
   a factory function that returns a class that wraps the
   Apache async API to the cljs-ajax API.
   Note that it's completely stateless: all of the relevant
   implementation objects are created each time."
  []
  (Connection.))
