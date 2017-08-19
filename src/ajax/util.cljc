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

