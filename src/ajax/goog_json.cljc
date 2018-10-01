(ns ajax.goog-json
  (:require [ajax.json :as json]
            [ajax.util :as u]
            #?@ (:cljs [[goog.json :as goog-json]
                        [goog.json.Serializer]
                        [cognitect.transit :as transit]
                        [clojure.walk :as walk]])))

#? (:cljs (defn write-json-google [data]
            (.serialize (goog.json.Serializer.) (clj->js data))))

#? (:cljs (defn read-json-google [raw keywords? text]
            (let [json (goog-json/parse text)]
              (if raw
                json
                (let [edn (transit/read (transit/reader :json) text) ]
                  (if keywords?
                    (walk/keywordize-keys edn)
                    edn))))))

(def goog-json-response-format
  "Returns a JSON response format using the native JSON
   implementation. Options include
   :keywords? Returns the keys as keywords
   :prefix A prefix that needs to be stripped off.  This is to
   combat JSON hijacking.  If you're using JSON with GET request,
   you should think about using this.
   http://stackoverflow.com/questions/2669690/why-does-google-prepend-while1-to-their-json-responses
   http://haacked.com/archive/2009/06/24/json-hijacking.aspx"
  (json/make-json-response-format
   #? (:clj json/read-json-cheshire :cljs read-json-google)))

(def goog-json-request-format
  (json/make-json-request-format
   #? (:clj json/write-json-cheshire :cljs write-json-google)))
