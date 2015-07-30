(ns test.core
  (:require
    [cemerick.cljs.test]
    [ajax.core :refer [get-default-format
                       normalize-method process-inputs
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
                       interpret-response
                       default-formats
                       submittable?
                       default-interceptors
                       add-interceptor
                       POST GET]]
    [cljs.reader :as reader])
  (:require-macros [cemerick.cljs.test :refer (is deftest with-test run-tests testing)]))

(deftest normalize
  (is (= "GET" (normalize-method :get)))
  (is (= "POST" (normalize-method "POST"))))

(deftype FakeXhrIo [content-type response status]
  ajax.core/AjaxImpl
  (-js-ajax-request [this _ _ _ _ h _]
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

(deftest test-process-inputs-as-json
  (let [[uri payload headers]
        (process-inputs {:params {:a 3 :b "hello"}
                         :headers nil
                         :uri "/test"
                         :method "POST"
                         :format (edn-request-format)}
                        (edn-response-format))]
    (is (= uri "/test"))
    (is (= payload "{:a 3, :b \"hello\"}"))
    (is (= headers {"Content-Type" "application/edn; charset=utf-8"
                    "Accept" "application/edn"}))))

(deftest test-process-inputs-as-edn
  (let [[uri payload headers]
        (process-inputs {:params {:a 3 :b "hello"}
                         :headers nil
                         :uri "/test"
                         :method "GET"
                         :format (edn-request-format)}
                        (edn-response-format))]
    (is (= uri "/test?a=3&b=hello"))
    (is (nil? payload))
    (is (= {"Accept" "application/edn"} headers))))

(deftest test-process-inputs-as-raw
  (let [[uri payload headers]
        (process-inputs {:params {:a 3 :b "hello"}
                         :headers nil
                         :uri "/test"
                         :method "POST"
                         :format (url-request-format)}
                        (json-response-format))]
    (is (= uri "/test"))
    (is (= payload "a=3&b=hello"))
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
  (let [r1 (atom nil)
        r2 (atom nil)
        r3 (atom nil)]
    ;; Rolled usage of ajax-request
    (ajax-request {:handler #(reset! r1 %)
                   :format (url-request-format)
                   :response-format (raw-response-format)
                   :api simple-reply})
    (expect-simple-reply @r1 "{:a 1}")
    ;; Alternative usage with unrolled arguments.
    (POST nil
          :handler #(reset! r2 %)
          :format :url
          :response-format (raw-response-format)
          :api simple-reply)
    (is (= "{:a 1}" @r2))
    ;; Test format detection runs all the way through
    (POST nil {:handler #(reset! r3 %)
               :format :url
               :api simple-reply})
    (is (= {:a 1} @r3) "Format detection didn't work")
    (POST nil {:params (js/FormData.)
               :api simple-reply})
    (GET "/" {:params {:a 3}
              :api simple-reply}
    (GET "/" {:params {:a 3}
              :api simple-reply}
         :response-format [:json :raw])
    (GET "/" {:params {:a 3}
              :api simple-reply}
              :response-format [:json ["text/plain" :raw]]))))

(deftest format-interpretation
  (is (map? (keyword-response-format {} {}))))


;; INTERCEPTORS

(def request-only-interceptor
  {:request (fn [[uri method format params headers]]
              [uri
               method
               format
               (assoc params :test-req-interceptor true)
               headers])})

(def response-only-interceptor
  {:response (fn [xhrio]
               (let [resp-body (-> (.-response xhrio)
                                   (reader/read-string)
                                   (assoc :test-resp-interceptor true)
                                   (str))]
                 (set! (.-response xhrio) resp-body))
               xhrio)})

(def request-and-response-interceptor
  {:request (fn [[uri method format params headers]]
              [uri
               method
               format
               (assoc params :test-req-resp-interceptor true)
               headers])
   :response (fn [xhrio]
               (let [resp-body (-> (.-response xhrio)
                                   (reader/read-string)
                                   (assoc :test-req-resp-interceptor true)
                                   (str))]
                 (set! (.-response xhrio) resp-body))
               xhrio)})

(deftest test-request-interceptors
  (add-interceptor request-only-interceptor)
  (add-interceptor response-only-interceptor)
  (add-interceptor request-and-response-interceptor)

  ;; test global interceptors
  (let [[uri payload headers]
        (process-inputs {:params {:a 1}
                         :headers nil
                         :uri "/test"
                         :method "GET"
                         :format (edn-request-format)}
                        (edn-response-format))]
    (is (= uri "/test?a=1&test-req-interceptor=true&test-req-resp-interceptor=true")))

  ;; test interceptor overrides
  (let [[uri payload headers]
        (process-inputs {:params {:a 1}
                         :headers nil
                         :uri "/test"
                         :method "GET"
                         :interceptors [request-only-interceptor]
                         :format (edn-request-format)}
                        (edn-response-format))]
    (is (= uri "/test?a=1&test-req-interceptor=true")))

  (reset! default-interceptors ()))

(deftest test-response-interceptors
  (add-interceptor request-only-interceptor)
  (add-interceptor response-only-interceptor)
  (add-interceptor request-and-response-interceptor)

  (let [r1 (atom nil)
        r2 (atom nil)]

    ;; test global interceptors
    (ajax-request {:handler #(reset! r1 %)
                   :format (url-request-format)
                   :response-format (raw-response-format)
                   :api (FakeXhrIo.
                          "application/edn; charset blah blah"
                          "{:a 1}" 200)})

    (let [resp-body (reader/read-string (second @r1))]
      (is (= (:a resp-body) 1))
      (is (:test-resp-interceptor resp-body))
      (is (:test-req-resp-interceptor resp-body)))

    ;; test interceptor overrides
    (ajax-request {:handler #(reset! r2 %)
                   :format (url-request-format)
                   :response-format (raw-response-format)
                   :interceptors [response-only-interceptor]
                   :api (FakeXhrIo.
                          "application/edn; charset blah blah"
                          "{:a 1}" 200)})

    (let [resp-body (reader/read-string (second @r2))]
      (is (= (:a resp-body) 1))
      (is (:test-resp-interceptor resp-body))
      (is (not (:test-req-resp-interceptor resp-body))))

    (reset! default-interceptors ())))
