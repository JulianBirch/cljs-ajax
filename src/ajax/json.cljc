(ns ajax.json
  (:require [ajax.interceptors :refer 
                [map->ResponseFormat]]
            [ajax.protocols :refer
                [-body -process-request -process-response -abort -status
                -get-response-header -status-text -js-ajax-request
                -was-aborted]]
            #?@ (:clj  [[cheshire.core :as c]
                        [clojure.java.io :as io]]))
  #? (:clj (:import [java.io OutputStreamWriter ByteArrayOutputStream
                InputStreamReader Closeable OutputStream
                InputStream])))

;;; NB If you're looking to use the google closure JSON implementation,
;;; You'll need ajax.goog-json instead

#? (:clj (defn write-json-cheshire [stream data]
           (c/generate-stream data (io/writer stream))))

#? (:cljs (defn write-json-native [data]
            (.stringify js/JSON (clj->js data))))

#? (:clj (defn read-json-cheshire [raw keywords? text]
           ; NB Raw is ignored since it makes no sense in this context
           (c/parse-stream (io/reader text) keywords?)))

#? (:cljs (defn read-json-native [raw keywords? text]
               (let [result-raw (.parse js/JSON text)]
                    (if raw
                        result-raw
                        (js->clj result-raw :keywordize-keys keywords?)))))

(defn make-json-request-format [write-json]
  (fn json-request-format []
      {:write write-json
       :content-type "application/json"}))

#? (:clj (defn strip-prefix
           ^InputStream [^String prefix ^InputStream text]
           (if prefix
             (let [utf8 (.getBytes prefix "UTF-8")]
               (loop [i 0]
                 (if (and (< i (alength utf8))
                          (= (aget utf8 i) (.read text)))
                   (recur (inc i))
                   text)))
             text))
     :cljs (defn strip-prefix [^String prefix text]
             (if (and prefix (= 0 (.indexOf text prefix)))
               (.substring text (.-length prefix))
               text)))

(defn make-json-response-format [read-json]
  "Create a json request format given `read-json` function."
  (fn json-response-format
    ([] (json-response-format {}))
    ([{:keys [prefix keywords? raw]}]
       (map->ResponseFormat
        {:read (fn json-read-response-format [xhrio] 
            (read-json keywords? 
                       raw
                       (strip-prefix prefix (-body xhrio))))
         :description (str "JSON"
                         (if prefix (str " prefix '" prefix "'"))
                         (if keywords? " keywordize"))
         :content-type ["application/json"]}))))

(def json-response-format
  "Returns a JSON response format using the native JSON 
   implementation. Options include
   :keywords? Returns the keys as keywords
   :prefix A prefix that needs to be stripped off.  This is to
   combat JSON hijacking.  If you're using JSON with GET request,
   you should think about using this.
   http://stackoverflow.com/questions/2669690/why-does-google-prepend-while1-to-their-json-responses
   http://haacked.com/archive/2009/06/24/json-hijacking.aspx"
    (make-json-response-format 
        #? (:clj read-json-cheshire :cljs read-json-native)))

(def json-request-format 
    (make-json-request-format 
        #? (:clj write-json-cheshire :cljs write-json-native)))
