(ns ajax.edn
  (:require [ajax.core :refer [map->ResponseFormat
                               -body]]
            [cljs.reader :as reader]))

(defn read-edn [xhrio]
  (reader/read-string (-body xhrio)))

(defn edn-response-format
  ([] (map->ResponseFormat {:read read-edn
                            :description "EDN"
                            :content-type "application/edn"}))
  ([_] (edn-response-format)))

(defn edn-request-format []
  {:write pr-str
   :content-type "application/edn"})
