(ns user
  (:import (java.io ByteArrayOutputStream))
  (:require [ring.server.standalone :as rsa]
            [ring.middleware.params :as params]
            [ring.middleware.edn :as rme]
            [ring.middleware.file :as rmf]
            [ring.middleware.multipart-params :as rmmp]
            [ring.middleware.transit :as tr]
            [cognitect.transit :as t]
            [clojure.tools.namespace.repl
             :refer (refresh refresh-all)]
            [ring.util.response :as rur]
            [hiccup.core :refer :all]))

(defn edn-response [data & [status]]
  {:status (or status 200)
   :headers {"Content-Type" "application/edn"}
   :body (pr-str data)})

(def system nil)

(defn write-transit [x]
  (let [baos (ByteArrayOutputStream.)
        w    (t/writer baos :json)
        _    (t/write w x)
        ret  (.toString baos)]
    (.reset baos)
    ret))

(defn transit-response [response]
  {:status 200
   :headers {"Content-Type" "application/transit+json; charset=utf-8"}
   :body (write-transit response)})

(defn png-response [response]
  {:status 200
   :headers {"Content-Type" "image/png"
             "Content-Disposition" "inline; filename=\"foo.png\""}
   :body "im not even a real png!"})

(defn ajax-handler
  ([{{:keys [id timeout input output]} :params :as x}]
     (println x)
     (ajax-handler id timeout input output))
  ([id timeout input]
     (ajax-handler id timeout input nil))
  ([id timeout input output]
     (when timeout
       (println "Timeout " timeout)
       (Thread/sleep timeout))
     (if id
       (doto
           ((or output edn-response)
            {:id id :output (str "INPUT:  " input)})
         println)
       (rur/not-found ""))))

(defn ajax-uri-handler [{{:strs [id timeout input]} :params}]
  (ajax-handler (read-string id) (read-string timeout) input))

(defn ajax-form-data-handler [request]
  (ajax-uri-handler request))

(defn sc-handler [{:keys [uri] :as request}]
  (case uri
    "/" {:status 200
         :body (html
                [:h1 "Ajax Tester"]
                [:script {:src "/integration.js" :type "text/javascript"}])}
    ;;; "/js/unit-test.js" (rur/file-response "target/unit-test.js")
    "/ajax" (ajax-handler request)
    "/ajax-transit" (ajax-handler
                     (update-in request [:params]
                                #(assoc % :output transit-response)))
    "/ajax-url" (ajax-uri-handler request)
    "/ajax-form-data" (ajax-form-data-handler request)
    "/ajax-form-data-png" (png-response request)
    "/favicon.ico" (rur/not-found "")))

(defn sc-system [] nil)

(defn sc-start [config]
  (-> sc-handler
      (rmf/wrap-file "target-int")
      rme/wrap-edn-params
      params/wrap-params
      rmmp/wrap-multipart-params
      tr/wrap-transit-params
      rsa/serve))

(defn init
  "Constructs the current development system."
  []
  (alter-var-root #'system
    (constantly (sc-system))))

(defn start
  "Starts the current development system."
  []
  (alter-var-root #'system sc-start))

(defn stop
  "Shuts down and destroys the current development system."
  []
  (alter-var-root #'system
                  (fn [s] (when s (.stop s)))))

(defn go
  "Initializes the current development system and starts it running."
  []
  (init)
  (start))

(defn reset []
  (stop)
  (refresh :after 'user/go))
