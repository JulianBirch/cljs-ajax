(ns ajax.util
  "Short utility functions. A lot of these only exist because the 
   cross platform implementation is annoying."
   (:require [ajax.protocols :as pr])
   #? (:clj
       (:import [java.io OutputStreamWriter]
                [java.lang String])))

(defn throw-error [args]
  "Throws an error."
  (throw (#?(:clj Exception. :cljs js/Error.)
           (str args))))

(defn get-content-type ^String [response]
  (or (pr/-get-response-header response "Content-Type") ""))

(defn to-utf8-writer [to-str]
  "Takes a function that converts to a string and transforms it
   into a function that converts to an object that will write
   UTF-8 to the wire. Note that this is the identity function
   for JavaScript because the underlying implementations take
   a string."
  #? (:cljs to-str
      :clj (fn write-utf8 [stream params]
             (doto (OutputStreamWriter. stream)
               (.write ^String (to-str params))
               (.flush)))))

(def successful-response-codes-set
  "A set of successful response types derived from `goog.net.HttpStatus.isSuccess`."
  ;; Factoid: Closure considers some 2XX status codes to *not* be successful, namely
  ;; 205 Reset Content, 207 Multi Status & the unspecified 208+ range
  #{200    ;; Ok
    201    ;; Created
    202    ;; Accepted
    204    ;; No Content
    206    ;; Partial Content
    304    ;; Not Modified
    ;; See https://github.com/google/closure-library/blob/f999480c4005641d284b86d82d0d5d0f05f3ffc8/closure/goog/net/httpstatus.js#L89-L94
    1223}) ;; QUIRK_IE_NO_CONTENT

(defn success? [status]
  "Indicates whether an HTTP status code is considered successful."
  (contains? successful-response-codes-set
             status))
