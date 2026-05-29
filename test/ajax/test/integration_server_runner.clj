(ns ajax.test.integration-server-runner
  (:require [ajax.test.integration-support :as integration-support]
            [clojure.test :as t]))

(def integration-namespaces
  ['ajax.test.integration])

(defn run-jvm-integration! []
  (doseq [test-namespace integration-namespaces]
    (require test-namespace))
  (let [{:keys [fail error]} (apply t/run-tests integration-namespaces)]
    (when (pos? (+ fail error))
      (throw (ex-info "JVM integration tests failed"
                      {:fail fail
                       :error error})))))

(defn run-command! [env command]
  (let [builder (doto (ProcessBuilder. ^java.util.List command)
                  (.directory (java.io.File. "."))
                  (.inheritIO))
        process-env (.environment builder)]
    (.putAll process-env env)
    (let [process (.start builder)
          exit (.waitFor process)]
      (when-not (zero? exit)
        (throw (ex-info "Integration command failed"
                        {:command command
                         :exit exit}))))))

(defn -main [& _]
  (let [server (integration-support/start-server)
        env (assoc (into {} (System/getenv))
                   "CLJS_AJAX_INTEGRATION_BASE_URL"
                   integration-support/base-url)]
    (try
      (run-jvm-integration!)
      (run-command! env ["npm" "run" "test:integration:node"])
      (run-command! env ["npm" "run" "test:integration:browser"])
      (finally
        (integration-support/stop-server server)))))
