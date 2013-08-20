(ns test.core
  (:require-macros [cemerick.cljs.test :refer (is deftest with-test run-tests testing)])
  (:require
   [cemerick.cljs.test :as t]
   [ajax.core.AjaxImpl :as ai]
   [ajax.core :refer [get-default-format
                      normalize-method process-inputs
                      edn-format json-format raw-format
                      ajax-request]]))

(deftest normalize
  (is (= "GET" (normalize-method :get)))
  (is (= "POST" (normalize-method "POST"))))

(deftype FakeXhrIo [content-type response status]
  ajax.core.AjaxImpl
  (-js-ajax-request [this _ _ _ _ h _]
    (h (clj->js {:target this})))
  Object
  (getResponseHeader [this header] content-type)
  (getStatus [_] status)
  (getResponseText [_] response))


(deftest default-format
  (let [t (FakeXhrIo. "application/edn; charset blah blah" nil nil)
        f (get-default-format t)]
    (is (= (:description f) "EDN (default)"))))

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
        (process-inputs "/test" "POST" (json-format {})
                        {:params {:a 3 :b "hello"}
                         :headers nil})]
    (is (= uri "/test"))
    (is (= payload "{\"a\":3,\"b\":\"hello\"}"))
    (is (= headers {"Content-Type" "application/json"}))))

(deftest correct-handler
  (let [x (FakeXhrIo. "application/edn; charset blah blah"
                      "Reply" 200)
        r (ajax-request nil nil {:handler identity
                                 :format (raw-format)} x)]
    (is (vector? r))
    (is (first r) "Request should have been successful.")
    (is (= "Reply" (second r)))))
