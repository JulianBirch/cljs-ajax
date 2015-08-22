(ns ajax.edn
  (:require [ajax.core :refer [map->ResponseFormat]]
            [ajax.protocols :refer [-body]]
            #?@ (:cljs [[cljs.reader :as edn]]
                       :clj [[clojure.edn :as edn]
                             [clojure.java.io :refer [reader]]]))
  #? (:clj (:import (java.io
                     ByteArrayInputStream OutputStreamWriter
                     PushbackReader InputStreamReader))))

(defn edn-read [xhrio]
  #? (:cljs (-> xhrio -body edn/read-string)
      :clj (-> xhrio
               -body
               (InputStreamReader. "UTF-8")
               PushbackReader.
               edn/read)))

;;; ByteArrayInputStream. (-body xhrio)
(defn edn-response-format
  ([] (map->ResponseFormat {:read edn-read
                            :description "EDN"
                            :content-type ["application/edn"]}))
  ([_] (edn-response-format)))

#? (:clj (defn edn-write
           [writer params]
           (binding [*out* (OutputStreamWriter. writer "UTF-8")]
             (pr params)
             (flush))))

(defn edn-request-format
  ([] {:write #? (:cljs pr-str
                  :clj edn-write)
       :content-type ["application/edn"]})
  ([_] (edn-request-format)))
