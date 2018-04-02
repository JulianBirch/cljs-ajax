(ns ajax.transit
    (:require [cognitect.transit :as t]
              [ajax.interceptors :as i]
              [ajax.protocols :as pr]
              [ajax.util :as u]
              #? (:clj  [poppea :as p]))
    #? (:cljs (:require-macros [poppea :as p])))

(defn transit-type [{:keys [type]}]
  (or type #? (:cljs :json :clj :msgpack)))

#? (:cljs (defn transit-write-fn
            [type request]
            (let [writer (or (:writer request)
                             (t/writer type request))]
              (fn transit-write-params [params]
                (t/write writer params))))
    :clj (p/defn-curried transit-write-fn
           [type request stream params]
           (let [writer (t/writer stream type request)]
             (t/write writer params))))

(defn transit-request-format
  "Returns a Transit request format.

   Options include:
   :writer Explicit Transit writer. If not supplied one will be created using the other options.
           The default type is :json for ClojureScript and :msgpack for Clojure.
   :type Override the default transit type with value :json, :json-verbose or :msgpack
   :handlers Custom Transit handlers (refer to Transit documentation)"
  ([] (transit-request-format {}))
  ([request]
     (let [type (transit-type request)
           mime-type (if (or (= type :json)
                             (= type :json-verbose)) "json" "msgpack")]
       {:write (transit-write-fn type request)
        :content-type (str "application/transit+" mime-type)})))

#? (:cljs (defn transit-read-fn [request]
            (let [reader (or (:reader request)
                             (t/reader :json request))]
              (fn transit-read-response [response]
                (t/read reader (pr/-body response)))))
    :clj (p/defn-curried transit-read-fn [request response]
           (let [content-type (u/get-content-type response)
                 type (if (.contains content-type "msgpack")
                        :msgpack :json)
                 stream (pr/-body response)
                 reader (t/reader stream type request)]
             (t/read reader))))

(defn transit-response-format
  "Returns a Transit request format.

   Options include:
   :reader (CLJS only) Explicit Transit reader. If not supplied one will be created using the other options.
   :handlers Custom Transit handlers (refer to Transit documentation)"
  ([] (transit-response-format {}))
  ([request]
     (transit-response-format (transit-type request) request))
  ([type request]
     (i/map->ResponseFormat
      {:read (transit-read-fn request)
       :description "Transit"
       :content-type
       #? (:cljs ["application/transit+json"]
           :clj ["application/transit+msgpack"
                 "application/transit+json"])})))
