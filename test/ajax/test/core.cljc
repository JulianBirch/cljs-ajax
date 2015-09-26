(ns ajax.test.core
  (:require
   #? (:cljs [cemerick.cljs.test]
             :clj [clojure.test :refer :all])
   [ajax.protocols :refer [-body]]
   [ajax.core :refer [get-default-format
                      normalize-method
                      normalize-request
                      to-interceptor
                      ajax-request
                      url-request-format
                      raw-response-format
                      text-response-format
                      json-response-format
                      json-request-format
                      transit-response-format
                      transit-request-format
                      keyword-request-format
                      keyword-response-format
                      detect-response-format
                      accept-header
                      default-formats
                      process-request
                      process-response
                      transform-opts
                      get-response-format
                      params-to-str
                      apply-request-format
                      json-read
                      POST GET
                      #?@ (:cljs [ResponseFormat] :clj [])]]
   [ajax.edn :refer [edn-request-format edn-response-format]])
   #? (:cljs (:require-macros
              [cemerick.cljs.test :refer
               (is deftest with-test run-tests testing)])
       :clj (:import [ajax.core ResponseFormat]
                     [java.lang String]
                     [java.io ByteArrayInputStream])))

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

(defrecord FakeXhrIo [content-type response status]
  ajax.protocols/AjaxImpl
  (-js-ajax-request [this _ h]
    (h this))
  ajax.protocols/AjaxResponse
  (-get-response-header [this header] content-type)
  (-status [_] status)
  (-body [_] #? (:cljs response
                 :clj (if response
                          (ByteArrayInputStream.
                           (if (string? response)
                             (.getBytes ^String response)
                             response))))))

(deftest test-get-default-format
  (letfn [(make-format [content-type]
            (get-default-format (FakeXhrIo. content-type nil nil)
                                {:response-format default-formats}))
          (detects [{:keys [from format]}] (is format (= (:description (make-format from)))))]
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
         #? (:clj "application/json, application/transit+msgpack, application/transit+json"
             :cljs "application/json, application/transit+json")))
  (is (= (multi-content-type [:json ["text/plain" :raw]])
         "application/json, text/plain")))

;;; Somewhat ugly that this isn't exactly the same code as runs
;;; in ajax-request
(defn process-inputs [request]
  (let [{:keys [interceptors] :as request}
        (normalize-request request)]
    (reduce process-request request interceptors)))

(defn as-string [body]
  #? (:cljs body
      :clj (String. ^bytes body "UTF-8")) )

(deftest test-process-inputs-as-json
  (let [{:keys [uri body headers]}
        (process-inputs {:params {:a 3 :b "hello"}
                         :headers nil
                         :uri "/test"
                         :method "POST"
                         :format (edn-request-format)
                         :response-format (edn-response-format)})]
    (is (= uri "/test"))
    (is (= (as-string body) "{:a 3, :b \"hello\"}"))
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

(deftest process-inputs-as-edn
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

(deftest process-inputs-as-raw
  (let [{:keys [uri body headers]}
        (process-inputs {:params {:a 3 :b "hello"}
                         :headers nil
                         :uri "/test"
                         :method "POST"
                         :format (url-request-format)
                         :response-format (json-response-format)})]
    (is (= uri "/test"))
    (is (= (as-string body) "a=3&b=hello"))
    (is (= headers {"Content-Type"
                    "application/x-www-form-urlencoded; charset=utf-8"
                    "Accept" "application/json"}))))

#? (:cljs (deftest body-is-passed-through
            (let [result (process-inputs {:body (js/FormData.)
                                           :response-format (json-response-format)})]
              (is (instance? js/FormData (:body result))))))

(defn fake-from-request [{:keys [params format]}]
  (let [{:keys [content-type write]} format]
    (FakeXhrIo. content-type (apply-request-format write params) 200)))

(defn round-trip-test [request-format
                  {:keys [read] :as response-format} request]
  (let [response (fake-from-request {:params request
                                     :format request-format})]
    (is (= request (read response)))))

(deftest transit-round-trip
  (round-trip-test (transit-request-format {})
                   (transit-response-format {})
                   {:id 3 :content "Hello"}))

(deftest edn-round-trip
  (round-trip-test (edn-request-format {})
                   (edn-response-format {})
                   {:id 3 :content "Hello"}))

(def ^String simple-response-text "[\"^ \",\"~:a\",1]")

(def simple-response-body
  #? (:cljs simple-response-text
      :clj (.getBytes simple-response-text "UTF8")))

(def simple-reply
  (FakeXhrIo.
   "application/transit+json; charset blah blah"
   simple-response-body 200))

(defn expect-simple-reply [r value]
  (is (vector? r))
  (is (first r) "Request should have been successful.")
  (is (= value (second r))))

(deftest correct-handler
  (let [r (atom nil)]
    ;; Rolled usage of ajax-request
    (ajax-request {:handler #(reset! r %)
                   :format (url-request-format)
                   :response-format (text-response-format)
                   :api simple-reply})
    (expect-simple-reply @r simple-response-text)))

(deftest unrolled-arguments
  (let [r (atom nil)]
    ;; Alternative usage with unrolled arguments.
    (POST nil
          :handler #(reset! r %)
          :format :url
          :response-format (text-response-format)
          :api simple-reply)
    (is (= simple-response-text @r))))

(deftest format-detection
  (let [r (atom nil)]
    (POST nil {:handler #(reset! r %)
               :format :url
               :api simple-reply})
    (is (= {:a 1} @r) "Format detection didn't work")))

#? (:cljs
    (deftest js-types
      (POST nil {:body (js/FormData.)
                 :api simple-reply})))

(deftest through-run
  ;;; Test format detection runs all the way through
  ;;; These are basically "don't crash" tests
  (GET "/" {:params {:a 3}
            :api simple-reply})
  (GET "/" {:params {:a 3}
            :api simple-reply}
       :response-format [:json :text])
  (GET "/" {:params {:a 3}
            :api simple-reply}
       :response-format [:json ["text/plain" :raw]]))

(deftest no-content
  (let [r1 (atom "whatever")
        r2 (atom "whatever")]
    (POST "/" {:handler #(reset! r1 %)
               :error-handler #(reset! r1 %)
               :response-format (json-response-format)
               :api (FakeXhrIo. "application/json; charset blah blah" "" 204)})
    (is (= nil @r1))
    (POST "/" {:handler #(reset! r2 %)
               :error-handler #(reset! r2 %)
               :response-format (json-response-format)
               :api (FakeXhrIo. "application/json; charset blah blah" "{\"a\":\"b\"}" 200)})
    (is (= {"a" "b"} @r2))))

(deftest format-interpretation
  (is (map? (keyword-response-format {} {}))))

(deftest composite-format
  (let [request (-> {:format :raw
                     :response-format [:transit :json]}
                    transform-opts)
        response (FakeXhrIo. "application/json; charset blah blah" "{\"a\":\"b\"}" 200)]
    (is (instance? ResponseFormat (get-response-format request)))
    (let [format (get-default-format simple-reply request)]
      (is format))))

(deftest json-parsing
  (let [response (FakeXhrIo. "application/json; charset blah blah" "while(1);{\"a\":\"b\"}" 200)]
    (is (= {"a" "b"} (json-read "while(1);" false false response)))
    (is (= {:a "b"} (json-read "while(1);" false true response)))))

#_ (deftest empty-response
  (let [r1 (atom "whatever")
        response (FakeXhrIo. nil nil 200)]
    (ajax-request {:uri "/"
                   :handler #(reset! r1 %)
                   :error-handler #(reset! r1 %)
                   :format (json-request-format)
                   :response-format (json-response-format)
                   :api response
                   :method "DELETE"})))
