(ns test.core
  (:require-macros [cemerick.cljs.test :refer (is deftest with-test run-tests testing)])
  (:require
   [cemerick.cljs.test :as t]
   [ajax.core :refer [get-default-format
                      normalize-method process-inputs
                      edn-format json-format]]))

(deftype FakeTarget [content-type]
  Object
  (getResponseHeader [this header] content-type))

(deftest default-format
  (let [t (FakeTarget. "application/edn; charset blah blah")
        f (get-default-format t)]
    (is (= (:description f) "EDN (default)"))))

(deftest normalize
  (is (= "GET" (normalize-method :get)))
  (is (= "POST" (normalize-method "POST"))))

(deftest test-process-inputs
  (let [[uri payload headers]
        (process-inputs "/test" "GET" (edn-format)
                        {:params {:a 3 :b "hello"}
                         :headers nil})]
    (is (= uri "/test?a=3&b=hello"))
    (is (nil? payload))
    (is (nil? headers)))
  (let [[uri payload headers]
        (process-inputs "/test" "POST" (edn-format)
                        {:params {:a 3 :b "hello"}
                         :headers nil})]
    (is (= uri "/test"))
    (is (= payload "{:a 3, :b \"hello\"}"))
    (is (= headers {"Content-Type" "application/edn"})))
  (let [[uri payload headers]
        (process-inputs "/test" "POST" (json-format)
                        {:params {:a 3 :b "hello"}
                         :headers nil})]
    (is (= uri "/test"))
    (is (= payload "{\"a\":3,\"b\":\"hello\"}"))
    (is (= headers {"Content-Type" "application/json"}))))
