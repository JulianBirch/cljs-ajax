(ns user
  (:require [ajax.test.integration-support :as integration-support]
            [clojure.tools.namespace.repl
             :refer (refresh refresh-all)]
            [ring.server.standalone :as rsa]))

(def system nil)

(defn sc-system [] nil)

(defn sc-start [config]
  (rsa/serve integration-support/server-app))

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
