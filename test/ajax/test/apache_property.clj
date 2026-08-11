(ns ajax.test.apache-property
  "Property based tests for the Apache HttpClient 5 implementation.

   The pure properties cover the request/response adaptation layer. The
   round trip properties post to a local server and compare what comes
   back, so every request body type is encoded, sent, echoed and decoded."
  (:require [ajax.apache :as apache]
            [ajax.core :as ajax]
            [ajax.protocols :as pr]
            [ajax.ring :as ring]
            [clojure.string :as string]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [org.httpkit.server :refer [run-server]])
  (:import [java.io ByteArrayInputStream File InputStream]
           [java.net SocketTimeoutException URI]
           [java.util Arrays]
           [org.apache.hc.client5.http ConnectTimeoutException]
           [org.apache.hc.client5.http.async.methods SimpleHttpResponse]
           [org.apache.hc.core5.http ContentType]
           [org.apache.hc.core5.util Timeout]))

(def ^:dynamic *trials*
  "Number of test.check trials. The mutation testing harness lowers this,
   since it runs the whole suite once per mutant."
  50)

(defn trials [] *trials*)

;;; Test server

(defn- header-echo [headers]
  (into {} (filter (fn [[k _]] (.startsWith ^String k "x-echo-")) headers)))

(defn- query-param [query-string k]
  (some (fn [pair]
          (let [[pk pv] (.split ^String pair "=" 2)]
            (when (= pk k) pv)))
        (.split ^String (or query-string "") "&")))

(defn- handler [{:keys [uri body headers query-string request-method]}]
  (if (not= :post request-method)
    ;; Every route below is a POST, so this also pins that the request
    ;; method actually reaches the server.
    {:status 405 :body "method not allowed"}
    (case uri
      "/echo" {:status 200
               :headers {"Content-Type" "application/octet-stream"}
               :body (if body (.readAllBytes ^InputStream body) (byte-array 0))}
      "/headers" {:status 200
                  :headers (assoc (header-echo headers)
                                  "Content-Type" "text/plain")
                  :body ""}
      "/status" {:status (Integer/parseInt (or (query-param query-string "code") "200"))
                 :headers {"Content-Type" "text/plain"}
                 :body "status"}
      {:status 404 :body "not found"})))

(def ^:private server (atom nil))
(def ^:private port (atom nil))

(defn- with-server [f]
  (let [free-port (with-open [s (java.net.ServerSocket. 0)] (.getLocalPort s))]
    (reset! port free-port)
    (reset! server (run-server handler {:port free-port :max-body (* 16 1024 1024)}))
    (try
      (f)
      (finally
        (@server :timeout 100)
        (reset! server nil)))))

(use-fixtures :once with-server)

(defn- lower-case-keys [headers]
  (into {} (map (fn [[k v]] [(string/lower-case k) v])) headers))

(defn- endpoint [path]
  (str "http://localhost:" @port path))

(defn- request!
  "Runs a cljs-ajax request and returns [ok? response], blocking on the
   handler rather than on the returned future."
  [url opts]
  (let [p (promise)]
    (ajax/POST url (merge {:handler (fn [r] (deliver p [true r]))
                           :error-handler (fn [r] (deliver p [false r]))}
                          opts))
    (deref p 20000 [::timed-out nil])))

(defn- post-bytes!
  "POSTs a body of any type ajax.apache supports and returns the echoed bytes."
  [body]
  (let [[ok? response] (request! (endpoint "/echo")
                                 {:body body
                                  :response-format (ajax/raw-response-format)})]
    (when-not (true? ok?)
      (throw (ex-info "Echo request failed" {:response response})))
    (if response (.readAllBytes ^InputStream response) (byte-array 0))))

;;; Generators

(def gen-bytes
  (gen/fmap byte-array (gen/vector (gen/choose -128 127) 0 4096)))

(def gen-utf8-string
  "Strings restricted to non-surrogate code points, so a UTF-8 round trip
   is lossless rather than the generator emitting unpaired surrogates."
  (gen/fmap (partial apply str)
            (gen/vector (gen/fmap char
                                  (gen/one-of [(gen/choose 32 126)
                                               (gen/choose 161 591)
                                               (gen/choose 0x4E00 0x4EFF)]))
                        0 200)))

(def gen-header-name
  (gen/fmap #(str "x-echo-" (apply str %))
            (gen/vector (gen/elements (seq "abcdefghijklmnopqrstuvwxyz0123456789")) 1 12)))

(def gen-header-value
  ;; Header values are trimmed in transit, so leading and trailing
  ;; whitespace is not something a client can round trip.
  (gen/such-that seq
                 (gen/fmap (comp string/trim (partial apply str))
                           (gen/vector (gen/elements (seq "abcdefghijklmnopqrstuvwxyz0123456789 .-_")) 1 30))
                 100))

(def gen-timeout-ms (gen/choose 1 3600000))

(def cookie-policies [:none :default :netscape :standard :standard-strict])

;;; Timeout mapping

(defspec positive-timeouts-survive-the-conversion (trials)
  (prop/for-all [ms gen-timeout-ms]
    (= (long ms) (.toMilliseconds ^Timeout (#'apache/to-timeout ms)))))

(defspec absent-or-zero-timeout-means-no-timeout (trials)
  (prop/for-all [ms (gen/one-of [(gen/return nil)
                                 (gen/return 0)
                                 (gen/choose -1000 0)])]
    (.isDisabled ^Timeout (#'apache/to-timeout ms))))

(defspec timeout-configures-both-connect-and-socket (trials)
  (prop/for-all [ms gen-timeout-ms]
    (let [config (#'apache/create-connection-config {:timeout ms})]
      (and (= (long ms) (.toMilliseconds (.getConnectTimeout config)))
           (= (long ms) (.toMilliseconds (.getSocketTimeout config)))))))

(defspec socket-timeout-overrides-timeout (trials)
  (prop/for-all [ms gen-timeout-ms
                 socket-ms gen-timeout-ms]
    (let [config (#'apache/create-connection-config {:timeout ms
                                                     :socket-timeout socket-ms})]
      (and (= (long ms) (.toMilliseconds (.getConnectTimeout config)))
           (= (long socket-ms) (.toMilliseconds (.getSocketTimeout config)))))))

;;; Cookie policies

(defspec cookie-policy-is-set-exactly-when-requested (trials)
  (prop/for-all [policy (gen/elements (cons nil cookie-policies))]
    (let [spec (.getCookieSpec (#'apache/create-request-config
                                {:cookie-policy policy}))]
      (if policy
        (string? spec)
        (nil? spec)))))

(deftest cookie-policies-map-to-httpclient-5-specs
  (is (= "ignore" (apache/get-cookie-policy {:cookie-policy :none})))
  (is (= "strict" (apache/get-cookie-policy {:cookie-policy :standard-strict})))
  (doseq [policy [:default :netscape :standard]]
    (testing (str policy " maps to the RFC 6265 relaxed spec")
      (is (= "relaxed" (apache/get-cookie-policy {:cookie-policy policy}))))))

;;; Response adaptation

(defn- simple-response [code headers body]
  (let [response (SimpleHttpResponse/create (int code))]
    (doseq [[k v] headers]
      (.addHeader response ^String k ^String v))
    (when body
      (.setBody response ^bytes body ContentType/APPLICATION_OCTET_STREAM))
    response))

(defspec wrapper-mirrors-the-underlying-response (trials)
  (prop/for-all [code (gen/choose 100 599)
                 headers (gen/map gen-header-name gen-header-value)
                 body (gen/one-of [(gen/return nil) gen-bytes])]
    (let [wrapper (apache/->HttpResponseWrapper (simple-response code headers body))]
      (and (= code (pr/-status wrapper))
           (= headers (select-keys (pr/-get-all-headers wrapper) (keys headers)))
           (every? #(= (get headers %) (pr/-get-response-header wrapper %))
                   (keys headers))
           (false? (pr/-was-aborted wrapper))
           (if body
             (Arrays/equals ^bytes body
                            (.readAllBytes ^InputStream (pr/-body wrapper)))
             (nil? (pr/-body wrapper)))))))

;;; Failure adaptation

(defn- captured-response [f]
  (let [captured (atom nil)]
    (f (fn [response] (reset! captured response)))
    @captured))

(defspec timeouts-report-a-status-of-minus-one (trials)
  (prop/for-all [message (gen/such-that seq gen-header-value)
                 timeout-class (gen/elements [:socket :connect])]
    (let [ex (case timeout-class
               :socket (SocketTimeoutException. message)
               :connect (ConnectTimeoutException. message))
          response (captured-response #(#'apache/fail % ex))]
      (and (= -1 (:status response))
           (= message (:status-text response))
           (false? (:was-aborted response))
           (identical? ex (:exception response))))))

(defspec other-failures-report-a-status-of-zero (trials)
  (prop/for-all [message (gen/such-that seq gen-header-value)]
    (let [response (captured-response
                    #(#'apache/fail % (java.io.IOException. ^String message)))]
      (and (= 0 (:status response))
           (false? (:was-aborted response))))))

(deftest cancellation-looks-like-an-aborted-xhr
  (let [response (captured-response #(#'apache/cancel %))]
    (is (= -1 (:status response)))
    (is (= "Cancelled" (:status-text response)))
    (is (true? (:was-aborted response)))))

;;; URI handling

(defspec spaces-in-uris-are-escaped (trials)
  (prop/for-all [path (gen/vector (gen/elements ["a" "b" " " "c"]) 1 10)]
    (let [uri (str "http://example.com/" (apply str path))]
      (= (URI. (string/replace uri " " "%20"))
         (#'apache/to-uri uri)))))

;;; Round trips against a real server

(defspec byte-array-bodies-round-trip (trials)
  (prop/for-all [body gen-bytes]
    (Arrays/equals ^bytes body ^bytes (post-bytes! body))))

(defspec string-bodies-round-trip-as-utf-8 (trials)
  (prop/for-all [body gen-utf8-string]
    (Arrays/equals ^bytes (.getBytes body "UTF-8") ^bytes (post-bytes! body))))

(defspec input-stream-bodies-round-trip (trials)
  (prop/for-all [body gen-bytes]
    (Arrays/equals ^bytes body ^bytes (post-bytes! (ByteArrayInputStream. body)))))

(defspec file-bodies-round-trip (trials)
  (prop/for-all [body gen-bytes]
    (let [file (doto (File/createTempFile "cljs-ajax-property" ".bin")
                 (.deleteOnExit))]
      (try
        (with-open [out (java.io.FileOutputStream. file)]
          (.write out ^bytes body))
        (Arrays/equals ^bytes body ^bytes (post-bytes! file))
        (finally (.delete file))))))

(defspec request-headers-reach-the-server-and-come-back (trials)
  (prop/for-all [headers (gen/map gen-header-name gen-header-value {:min-elements 1})]
    (let [[ok? response] (request! (endpoint "/headers")
                                   {:headers headers
                                    :body ""
                                    :response-format (ring/ring-response-format)})]
      (and (true? ok?)
           (= 200 (:status response))
           ;; HTTP header names are case insensitive and servers are free
           ;; to send back whatever casing they like.
           (= headers (select-keys (lower-case-keys (:headers response))
                                   (keys headers)))))))

(defspec status-codes-are-reported-verbatim (trials)
  (prop/for-all [code (gen/elements [200 201 204 400 404 418 500 503])]
    (let [[ok? response] (request! (str (endpoint "/status") "?code=" code)
                                   {:body ""
                                    :response-format (ajax/raw-response-format)})]
      (if (< code 400)
        (true? ok?)
        (and (false? ok?) (= code (:status response)))))))
