(ns test.core
  (:require-macros [cemerick.cljs.test :refer (is deftest with-test run-tests testing)])
  (:require [ajax.core :refer [get-default-format]]
            [cemerick.cljs.test :as t]))

(deftype FakeTarget [content-type]
  Object
  (getResponseHeader [this header] content-type))

(deftest default-format
  (let [t (FakeTarget. "application/edn; charset blah blah")
        f (get-default-format t)]
    (is (= (:content-type f) "application/edn"))))
