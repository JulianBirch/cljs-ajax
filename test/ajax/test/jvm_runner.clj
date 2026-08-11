(ns ajax.test.jvm-runner
  (:require [clojure.test :as t]))

(def test-namespaces
  ['ajax.test.apache-property
   'ajax.test.core
   'ajax.test.server-interaction
   'ajax.test.url])

(defn run-tests [_]
  (doseq [test-namespace test-namespaces]
    (require test-namespace))
  (let [{:keys [fail error]} (apply t/run-tests test-namespaces)]
    (when (pos? (+ fail error))
      (throw (ex-info "JVM tests failed"
                      {:fail fail
                       :error error})))))
