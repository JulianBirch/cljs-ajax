(ns ajax.apache
  (:require [ajax.protocols :refer [map->Response
              AjaxImpl AjaxRequest AjaxResponse]]
            [clojure.string :as s])
  (:import [clojure.lang IDeref IBlockingDeref IPending]
           [org.apache.http HttpResponse]
           [org.apache.http.entity ByteArrayEntity StringEntity
            FileEntity InputStreamEntity]
           [org.apache.http.client.methods HttpRequestBase
            HttpEntityEnclosingRequestBase]
           [org.apache.http.client.config RequestConfig]
           [org.apache.http.concurrent FutureCallback]
           [org.apache.http.impl.nio.client HttpAsyncClients]
           [java.lang Exception]
           [java.util.concurrent Future]
           [java.net URI SocketTimeoutException]
           [java.io File InputStream Closeable]))

;;; Chunks of this code liberally ripped off dakrone/clj-http
;;; Although that uses the synchronous API

(def array-of-bytes-type (Class/forName "[B"))

(defn- to-entity [b]
  "This function means you can just hand cljs-ajax a byte
   array, string, normal Java file or input stream and it
   will automatically work with the Apache implementation."
  (condp instance? b
    array-of-bytes-type (ByteArrayEntity. b)
    String (StringEntity. ^String b "UTF-8")
    File (FileEntity. b)
    InputStream (InputStreamEntity. b)
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

;;; Takes an HttpResponse and exposes the interface needed
;;; by cljs-ajax interceptors (including response formats).

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
  "Life's to short to use all of the apache types for
   the different HTTP methods when you can just wrap a
   string in the appropriate base class."
  ^HttpEntityEnclosingRequestBase [method]
  (proxy [HttpEntityEnclosingRequestBase] []
    (getMethod [] method)))

(defn cancel [handler]
  "This method ensures that the behaviour of the wrapped
   Apache classes matches the behaviour the javascript version,
   including the negative status number."
  (handler
   (map->Response {:status -1
                   :status-text "Cancelled"
                   :headers {}
                   :was-aborted true})))

(defn fail [handler ^Exception ex]
  "XMLHttpRequest reports a status of -1 for timeouts, so
   we do the same."
  (let [status (if (instance? SocketTimeoutException ex) -1 0)]
    (handler
     (map->Response {:status status
                     :status-text (.getMessage ex)
                     :headers {}
                     :exception ex
                     :was-aborted false}))))

(defn create-handler [handler]
  "Takes a cljs-ajax style handler method and converts it
   to a FutureCallback suitable for use the Apache API."
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

;;; This is the only thing exposed by the apache.clj file:
;;;   a class that wraps the Apache async API to the cljs-ajax
;;;   API. Note that it's completely stateless: all of the relevant
;;;   objects are created each time."


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
