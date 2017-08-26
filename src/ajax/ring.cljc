(ns ajax.ring
  (:require [ajax.interceptors :refer [map->ResponseFormat]]
            [ajax.protocols :refer [-status -get-all-headers -body]]
            [ajax.formats :as f]))

(defn make-ring-read [body-read]
  (fn ring-read [response]
    {:status (-status response)
     :headers (-get-all-headers response)
     :body (body-read response)}))

(defn ring-response-format
  "Returns a Ring-compatible response map.

   Optionally can be passed a :format option. This should be another
   response-format map. If format is provided it will be used to
   specify the content-type, and the read method will be used to
   populate the :body key in the response map."
  ([] (ring-response-format {:format (f/raw-response-format)}))
  ([{{:keys [read description content-type]} :format}]
   (map->ResponseFormat {:read (make-ring-read read)
                         :description (str "ring/" description)
                         :content-type content-type})))
