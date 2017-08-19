(ns ajax.interceptors
  "This file contains the standard interceptors used by cljs-ajax to implement
   most of the 'magic'. There are four of them: 
     
     * ProcessGet, which handles the fact that GETs do not have bodies and so need treating separately.
     * ApplyRequestFormat, which takes the request format key and applies it to the params key.
     * ResponseFormat, which is a parameterised interceptor dynamically added to the interceptor list. Note that the response format routines return one of these.
     * DirectSubmission, which spots that you're using a type that doesn't need format processing and sends it immediately.
   
   There are no functions forming part of the public API in this file, so
   unless you're working on, studying or debugging cljs-ajax, you're 
   probably in the wrong place."
    (:require [clojure.string :as str]
              [ajax.util :as u]
              [ajax.url :as url]
              [ajax.protocols :refer
                  [-body -process-request -process-response -abort -status
                   -get-response-header -status-text -js-ajax-request
                   -was-aborted
                   #?@ (:cljs [AjaxImpl AjaxRequest AjaxResponse
                               Interceptor Response])]]
              #? (:clj [poppea :as p]))
    #? (:clj
        (:import [ajax.protocols AjaxImpl AjaxRequest
                  AjaxResponse Interceptor Response]
                 [java.io OutputStreamWriter ByteArrayOutputStream
                  InputStreamReader Closeable OutputStream
                  InputStream])
        :cljs
        (:require-macros [ajax.macros :as m]
                         [poppea :as p])))

;;; Utility

(defrecord StandardInterceptor [name request response]
  Interceptor
  (-process-request [{:keys [request]} opts]
    (request opts))
  (-process-response [{:keys [response]} xhrio]
    (response xhrio)))

(defn to-interceptor [m]
  "Utility function. If you want to create your own interceptor
   quickly, this will do the job. Note you don't need to specify
   both methods. (Or indeed either, but it won't do much under
   those circumstances.)"
  (map->StandardInterceptor (merge
                             {:request identity :response identity}
                             m)))


;;; Response Format record

(defn- success? [status]
  (some #{status} [200 201 202 204 205 206]))

#? (:clj (defn exception-message [^Exception e] (.getMessage e))
    :cljs (defn exception-message [e] (.-message e)))

(defn- exception-response [e status {:keys [description]} xhrio]
  (let [response {:status status
                  :failure :error
                  :response nil}
        status-text (str (exception-message e)
                         "  Format should have been "
                         description)
        parse-error (assoc response
                      :status-text status-text
                      :failure :parse
                      :original-text (-body xhrio))]
    (if (success? status)
      parse-error
      (assoc response
        :status-text (-status-text xhrio)
        :parse-error parse-error))))

(defn fail [status status-text failure & params]
  (let [response {:status status
                  :status-text status-text
                  :failure failure}]
    [false (reduce conj
                   response
                   (map vec (partition 2 params)))]))

(defn content-type-to-request-header [content-type]
  (->> (if (string? content-type)
         [content-type]
         content-type)
       (str/join ", ")))

;;; The ResponseFormat interceptor is one of the core pieces of functionality in
;;; cljs-ajax. It is an interceptor that applies the response format first
;;; to the Accept part of the request, and later to the response to interpret 
;;; the result.
;;; 
;;; Note that the "response format" functions all return ResponseFormat returns.
(defrecord ResponseFormat [read description content-type]
  Interceptor
  (-process-request [{:keys [content-type]} request]
    "Sets the headers on the request"
    (update request
            :headers
            #(merge {"Accept" (content-type-to-request-header content-type)}
                    (or % {}))))
  (-process-response [{:keys [read] :as format} xhrio]
    "Transforms the raw response (an implementation of AjaxResponse)"
    (try
      (let [status #? (:clj (long (-status xhrio))
                       :cljs (-status xhrio))
            fail (partial fail status)]
        (case status
          0 (if (instance? Response xhrio)
              [false xhrio]
              (fail "Request failed." :failed))
          -1 (if (-was-aborted xhrio)
               (fail "Request aborted by client." :aborted)
               (fail "Request timed out." :timeout))
          204 [true nil]       ; 204 and 205 should have empty responses
          205 [true nil]
          (try
            (let [response (read xhrio)]
              (if (success? status)
                [true response]
                (fail (-status-text xhrio) :error :response response)))
            (catch #? (:clj Exception :cljs js/Object) e
                   [false (exception-response e status format xhrio)]))))
      (catch #? (:clj Exception :cljs js/Object) e
                                        ; These errors should never happen
             (let [message #? (:clj (.getMessage e)
                               :cljs (.-message e))]
               (fail 0 message :exception :exception e))))))


;;; ApplyRequestFormat is a stateless interceptor that applies the rules
;;; for the request formats, reading from the request and transforming it
;;; as appropriate. It does not affect the result in any way
;;;
;;; Contrast with ResponseFormat, that has to change the request to add
;;; the Accept header, and then transforms the response to interpret the result.
(defn ^:internal get-request-format [format]
  "Internal function. Takes whatever was provided as :request-format and 
   converts it to a true request format. In practice, this just means it will 
   interpret functions as formats and not change maps. Note that it throws an
   exception when passed a keyword, because they should already have been 
   transformed to maps."
  (cond
   (map? format) format
   (keyword? format) (u/throw-error ["keywords are not allowed as request formats in ajax calls: " format])
   (ifn? format) {:write format :content-type "text/plain"}
   :else {}))

(defn apply-request-format [write params]
  #? (:cljs (write params)
      :clj (let [stream (ByteArrayOutputStream.)]
             (write stream params)
             (.toByteArray stream))))

(defrecord ApplyRequestFormat []
  Interceptor
  (-process-request
    [_ {:keys [uri method format params headers] :as request}]
    (let [{:keys [write content-type]} (get-request-format format)
          body (if-not (nil? write)
                 (apply-request-format write params)
                 (u/throw-error ["unrecognized request format: "
                               format]))
          headers (or headers {})]
      (assoc request
        :body body
        :headers (if content-type
                   (assoc headers "Content-Type"
                          (content-type-to-request-header
                           content-type))
                   headers))))
  (-process-response [_ xhrio] xhrio))

(p/defn-curried ^:internal uri-with-params [{:keys [vec-strategy params]} uri]
  "Internal function. Takes a uri and appends the interpretation of the query string to it
   matching the behaviour of `url-request-format`."
  (if params
    (str uri
         (if (re-find #"\?" uri) "&" "?") ; add & if uri contains ?
         (url/params-to-str vec-strategy params))
    uri))

;;; ProcessGet is one of the standard interceptors
;;; Its function is to rewrite the uri of GET requests,
;;; since there's no other way to transmit params data
;;; in a GET.
(defrecord ProcessGet []
  Interceptor
  (-process-request [_ {:keys [method] :as request}]
    (if (= method "GET")
      (reduced (update request :uri
                       (uri-with-params request)))
      request))
  (-process-response [_ response] response))

;;; DirectSubmission is one of the default interceptors.
;;; Its function is to spot when :body key is present
;;; When it is present, it prevents all other processing
;;; of the request, which stops you doing stupid things
;;; like applying a transit format to a FormData.
(defrecord DirectSubmission []
  Interceptor
  (-process-request [_ {:keys [body] :as request}]
    (if (nil? body) request (reduced request)))
  (-process-response [_ response] response))

;;; The standard interceptors for processing a request.
(def request-interceptors 
  [(ProcessGet.) (DirectSubmission.) (ApplyRequestFormat.)])

;;; It seems rubbish making a function of this, but the namespace noise
;;; caused by importing the actual type across boundaries is significant
;;; in a cljc environment
(defn is-response-format? [response-format]
  (instance? ResponseFormat response-format))

;;; interpret-vector should be the implementation of detect-response-format
(defn get-response-format [interpret-vector {:keys [response-format] :as opts}]
  (cond
   (is-response-format? response-format) response-format
   (vector? response-format) (interpret-vector opts)
   (map? response-format) (map->ResponseFormat response-format)
   (keyword? response-format) (u/throw-error ["keywords are not allowed as response formats in ajax calls: " response-format])
   (ifn? response-format)
   (map->ResponseFormat {:read response-format
                         :description "custom"
                         :content-type "*/*"})
   :else (u/throw-error ["unrecognized response format: "
                       response-format])))

