(ns user
  (:require [ring.server.standalone :as rsa]
            [ring.middleware.edn :as rme]
            [clojure.tools.namespace.repl
             :refer (refresh refresh-all)]
            [ring.util.response :as rur]
            [hiccup.core :refer :all]))

(defn edn-response [data & [status]]
  {:status (or status 200)
   :headers {"Content-Type" "application/edn"}
   :body (pr-str data)})

(def system nil)

(defn ajax-handler [{{:keys [id timeout input]} :params}]
  (when timeout
    (println "Timeout " timeout)
    (Thread/sleep timeout))
  (if id
    (edn-response {:id id :output (apply str (reverse input))})
    (rur/not-found "")))

(defn sc-handler [{:keys [uri] :as request}]
  (println uri)
  (case uri
    "/" {
         :status 200
         :body (html
                [:h1 "Ajax Tester"]
                [:script {:src "/js.js" :type "text/javascript"}])}
    "/js.js" (rur/file-response "target/unit-test.js")
    "/ajax" (ajax-handler request)))

(defn sc-system [] nil)

(defn sc-start [config]
  (rsa/serve (rme/wrap-edn-params sc-handler)))

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
