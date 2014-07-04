(ns test.core
  (:require
   [cemerick.cljs.test]
   ajax.core/AjaxImpl
   [ajax.core :refer [get-default-format
                      normalize-method process-inputs
                      ajax-request
                      url-request-format
                      edn-response-format
                      edn-request-format
                      raw-response-format
                      keyword-request-format
                      keyword-response-format
                      interpret-response
                      POST]])
  (:require-macros [cemerick.cljs.test :refer (is deftest with-test run-tests testing)]))

(deftest normalize
  (is (= "GET" (normalize-method :get)))
  (is (= "POST" (normalize-method "POST"))))

(deftype FakeXhrIo [content-type response status]
  ajax.core/AjaxImpl
  (-js-ajax-request [this _ _ _ _ h _]
    (h (clj->js {:target this})))
  Object
  (getResponseHeader [this header] content-type)
  (getStatus [_] status)
  (getResponseText [_] response))

(deftest test-get-default-format
  (letfn [(make-format [content-type]
            (get-default-format (FakeXhrIo. content-type nil nil)))
          (detects [{:keys [from format]}] (is (= (:description (make-format from)) format)) )]
    (detects {:format "EDN (default)"      :from "application/edn;..."})
    (detects {:format "JSON (default)"     :from "application/json;..."})
    (detects {:format "raw text (default)" :from "text/plain;..."})
    (detects {:format "raw text (default)" :from "text/html;..."})
    ;;TODO: change default to raw on next major version
    (detects {:format "EDN (default)" :from "application/xml;..."})))

(deftest test-process-inputs-as-json
  (let [[uri payload headers]
        (process-inputs "/test" "POST" (edn-request-format)
                        {:params {:a 3 :b "hello"}
                         :headers nil})]
    (is (= uri "/test"))
    (is (= payload "{:a 3, :b \"hello\"}"))
    (is (= headers {"Content-Type" "application/edn"}))))

(deftest test-process-inputs-as-edn
  (let [[uri payload headers]
        (process-inputs "/test" "GET" (edn-request-format)
                        {:params {:a 3 :b "hello"}
                         :headers nil})]
    (is (= uri "/test?a=3&b=hello"))
    (is (nil? payload))
    (is (nil? headers))))

(deftest test-process-inputs-as-raw
  (let [[uri payload headers]
        (process-inputs "/test" "POST" (url-request-format)
                        {:params {:a 3 :b "hello"}
                         :headers nil})]
    (is (= uri "/test"))
    (is (= payload "a=3&b=hello"))
    (is (= headers {"Content-Type" "application/x-www-form-urlencoded"}))))

(def simple-reply
  (FakeXhrIo.
   "application/edn; charset blah blah"
   "Reply" 200))

(defn expect-simple-reply [r]
  (is (vector? r))
  (is (first r) "Request should have been successful.")
  (is (= "Reply" (second r))))

(deftest correct-handler
  (let [r1 (atom nil)
        r2 (atom nil)]
    ;; Rolled usage of ajax-request
    (ajax-request {:handler #(reset! r1 %)
                   :format (url-request-format)
                   :response-format (raw-response-format)
                   :manager simple-reply})
    (expect-simple-reply @r1)
    ;; Alternative usage with unrolled arguments.
    (POST nil
          :handler #(reset! r2 %)
          :format (url-request-format)
          :response-format (raw-response-format)
          :manager simple-reply)
    (is (= "Reply" @r2))))

(deftest format-interpretation
  (is (map? (keyword-response-format {} {}))))
