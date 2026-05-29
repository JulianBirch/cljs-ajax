(ns ajax.test.integration-jvm-runner
  (:require [ajax.test.integration-support :as integration-support]
            [clojure.test :as t]))

(def test-namespaces
  ['ajax.test.integration])

(defn run-tests [_]
  (let [server (integration-support/start-server)]
    (try
      (doseq [test-namespace test-namespaces]
        (require test-namespace))
      (let [{:keys [fail error]} (apply t/run-tests test-namespaces)]
        (when (pos? (+ fail error))
          (throw (ex-info "JVM integration tests failed"
                          {:fail fail
                           :error error}))))
      (finally
        (integration-support/stop-server server)))))
