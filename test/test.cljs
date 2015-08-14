(ns test.core
  (:require
   [cemerick.cljs.test]
   [ajax.core :refer [get-default-format
                      normalize-method
                      normalize-request
                      to-interceptor
                      ajax-request
                      url-request-format
                      edn-response-format
                      edn-request-format
                      raw-response-format
                      json-response-format
                      transit-response-format
                      transit-request-format
                      keyword-request-format
                      keyword-response-format
                      detect-response-format
                      accept-entry
                      accept-header
                      default-formats
                      submittable?
                      process-request
                      process-response
                      transform-opts
                      ResponseFormat
                      get-response-format
                      params-to-str
                      POST GET]])
  (:require-macros [cemerick.cljs.test :refer (is deftest with-test run-tests testing)]))

(deftest complex-params-to-str
  (is (= "a=0") (params-to-str {:a 0}))
  (is (= "b[0]=1&b[0]=2") (params-to-str {:b [1 2]}))
  (is (= "c[d]=3&c[e]=4") (params-to-str {:c {:d 3 :e 4}}))
  (is (= "f=5") (params-to-str {"d" 5}))
  (is (= "a=0&b[0]=1&b[1]=2&c[d]=3&c[e]=4&f=5"
         (params-to-str {:a 0
                         :b [1 2]
                         :c {:d 3 :e 4}
                         "f" 5}))))

(deftest normalize
  (is (= "GET" (normalize-method :get)))
  (is (= "POST" (normalize-method "POST"))))

(deftype FakeXhrIo [content-type response status]
  ajax.core/AjaxImpl
  (-js-ajax-request [this _ h]
    ; (.log js/Console (str "-js-ajax-request " argument))
    (h this))
  ajax.core/AjaxResponse
  (-get-response-header [this header] content-type)
  (-status [_] status)
  (-body [_] response))

(deftest test-get-default-format
  (letfn [(make-format [content-type]
            (get-default-format (FakeXhrIo. content-type nil nil)
                                {:response-format default-formats}))
          (detects [{:keys [from format]}] (is (= (:description (make-format from)) format)))]
    (detects {:format "EDN"      :from "application/edn;..."})
    (detects {:format "JSON"     :from "application/json;..."})
    (detects {:format "raw text" :from "text/plain;..."})
    (detects {:format "raw text" :from "text/html;..."})
    (detects {:format "Transit"  :from "application/transit+json;xxx"})
    (detects {:format "raw text" :from "application/xml;..."})))

(defn multi-content-type [input]
  (let [a (keyword-response-format input {})
        a2 (detect-response-format {:response-format a})]
    (:content-type a2)))

(deftest keywords
  (is (= (:content-type (keyword-response-format :transit {}))
         (:content-type (transit-response-format {}))))
  (is (= (:content-type (keyword-request-format :transit {}))
         (:content-type (transit-request-format))))
  (is (vector? (keyword-response-format [:json :transit] {})))
  (is (map? (first (keyword-response-format [:json :transit] {}))))
  (is (= "application/json" (accept-header {:response-format [(json-response-format {})]})))
  (is (= (multi-content-type [:json :transit])
         "application/json, application/transit+json"))
  (is (= (multi-content-type [:json ["text/plain" :raw]])
         "application/json, text/plain")))

;;; Somewhat ugly that this isn't exactly the same code as runs
;;; in ajax-request
(defn process-inputs [request]
  (let [{:keys [interceptors] :as request}
        (normalize-request request)]
    (reduce process-request request interceptors)))

(deftest test-process-inputs-as-json
  (let [{:keys [uri body headers]}
        (process-inputs {:params {:a 3 :b "hello"}
                         :headers nil
                         :uri "/test"
                         :method "POST"
                         :format (edn-request-format)
                         :response-format (edn-response-format)})]
    (is (= uri "/test"))
    (is (= body "{:a 3, :b \"hello\"}"))
    (is (= headers {"Content-Type" "application/edn; charset=utf-8"
                    "Accept" "application/edn"}))))

(deftest can-add-to-query-string
  (let [{:keys [uri]}
        (process-inputs {:params {:a 3 :b "hello"}
                         :headers nil
                         :uri "/test?extra=true"
                         :method "GET"
                         :response-format (edn-response-format)})]
    (is (= uri "/test?extra=true&a=3&b=hello"))))

(deftest use-interceptor
  (let [interceptor (to-interceptor
                     {:request #(assoc-in % [:params :c] "world")})
        {:keys [uri]}
        (process-inputs {:params {:a 3 :b "hello"}
                         :headers nil
                         :uri "/test?extra=true"
                         :method "GET"
                         :response-format (edn-response-format)
                         :interceptors [interceptor]})]
    (is (= uri "/test?extra=true&a=3&b=hello&c=world"))))

(deftest test-process-inputs-as-edn
  (let [{:keys [uri body headers]}
        (process-inputs {:params {:a 3 :b "hello"}
                         :headers nil
                         :uri "/test"
                         :method "GET"
                         :format (edn-request-format)
                         :response-format (edn-response-format)})]
    (is (= uri "/test?a=3&b=hello"))
    (is (nil? body))
    (is (= {"Accept" "application/edn"} headers))))

(deftest test-process-inputs-as-raw
  (let [{:keys [uri body headers]}
        (process-inputs {:params {:a 3 :b "hello"}
                         :headers nil
                         :uri "/test"
                         :method "POST"
                         :format (url-request-format)
                         :response-format (json-response-format)})]
    (is (= uri "/test"))
    (is (= body "a=3&b=hello"))
    (is (= headers {"Content-Type"
                    "application/x-www-form-urlencoded; charset=utf-8"
                    "Accept" "application/json"}))))

(defn fake-from-request [{:keys [params format]}]
  (let [{:keys [content-type write]} format]
    (FakeXhrIo. content-type (write params) 200)))

(defn round-trip-test [request-format
                  {:keys [read] :as response-format} request]
  (let [response (fake-from-request {:params request
                                     :format request-format})]
    (is (= request (read response)))))

(deftest transit-round-trip
  (round-trip-test (transit-request-format {})
                   (transit-response-format {})
                   {:id 3 :content "Hello"}))

(def simple-reply
  (FakeXhrIo.
   "application/edn; charset blah blah"
   "{:a 1}" 200))

(defn expect-simple-reply [r value]
  (is (vector? r))
  (is (first r) "Request should have been successful.")
  (is (= value (second r))))

(deftest submittable
  (is (submittable? (js/FormData.)))
  (is (submittable? ""))
  (is (not (submittable? {}))))

(deftest correct-handler
  (let [r (atom nil)]
    ;; Rolled usage of ajax-request
    (ajax-request {:handler #(reset! r %)
                   :format (url-request-format)
                   :response-format (raw-response-format)
                   :api simple-reply})
    (expect-simple-reply @r "{:a 1}")))

(deftest unrolled-arguments
  (let [r (atom nil)]
    ;; Alternative usage with unrolled arguments.
    (POST nil
          :handler #(reset! r %)
          :format :url
          :response-format (raw-response-format)
          :api simple-reply)
    (is (= "{:a 1}" @r))))

(deftest format-detection
  (let [r (atom nil)]
    (POST nil {:handler #(reset! r %)
               :format :url
               :api simple-reply})
    (is (= {:a 1} @r) "Format detection didn't work")))

(deftest through-run
         ; Test format detection runs all the way through
         ; These are basically "don't crash" tests
  (POST nil {:params (js/FormData.)
             :api simple-reply})
  (GET "/" {:params {:a 3}
            :api simple-reply})
  (GET "/" {:params {:a 3}
            :api simple-reply}
       :response-format [:json :raw])
  (GET "/" {:params {:a 3}
            :api simple-reply}
       :response-format [:json ["text/plain" :raw]]))

(deftest no-content
  (let [r1 (atom "whatever")
        r2 (atom "whatever")]
    (POST "/" {:handler #(reset! r1 %)
               :response-format (json-response-format)
               :api (FakeXhrIo. "application/json; charset blah blah" "" 204)})
    (is (= nil @r1))
    (POST "/" {:handler #(reset! r2 %)
               :response-format (json-response-format)
               :api (FakeXhrIo. "application/json; charset blah blah" "{\"a\":\"b\"}" 200)})
    (is (= {"a" "b"} @r2))))

(deftest format-interpretation
  (is (map? (keyword-response-format {} {}))))

(deftest composite-format
  (let [request (-> {:format :raw
                     :response-format [:transit :edn]}
                    transform-opts
                    get-response-format)]
    (is (instance? ResponseFormat request))))
