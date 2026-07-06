(ns ajax.edn
  (:require [ajax.interceptors :refer [map->ResponseFormat]]
            [ajax.protocols :refer [-body empty-response]]
            #?@ (:cljs [[cljs.reader :as edn]]
                 :clj [[clojure.edn :as edn]
                       [clojure.java.io :refer [reader]]]))
  #? (:clj (:import (java.io
                     ByteArrayInputStream OutputStreamWriter
                     PushbackReader InputStreamReader
                     InputStream OutputStream))))

(defn edn-read [xhrio]
  #? (:cljs (let [body (-body xhrio)]
              (if (empty? body)
                empty-response
                (edn/read-string body)))
      :clj (let [body (-body xhrio)]
             (if (nil? body)
               empty-response
               (let [reader (-> ^InputStream body
                                (InputStreamReader. "UTF-8")
                                PushbackReader.)
                     first-char (.read reader)]
                 (if (= -1 first-char)
                   empty-response
                   (do
                     (.unread reader first-char)
                     (edn/read reader))))))))

(defn edn-response-format
  ([] (map->ResponseFormat {:read edn-read
                            :description "EDN"
                            :content-type ["application/edn"]}))
  ([_] (edn-response-format)))

#? (:clj (defn edn-write
           [^OutputStream writer params]
           (binding [*out* (OutputStreamWriter. writer "UTF-8")]
             (pr params)
             (flush))))

(defn edn-request-format
  ([] {:write #? (:cljs pr-str
                  :clj edn-write)
       :content-type ["application/edn"]})
  ([_] (edn-request-format)))
