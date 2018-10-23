(ns ajax.test.core
  (:require
   #? (:cljs [cljs.test]
       :clj [clojure.test :refer :all])
   [ajax.protocols :refer [-body]]
   [ajax.formats :as f]
   [ajax.easy :as easy]
   [ajax.simple :as simple]
   [ajax.interceptors :refer [get-request-format
                              apply-request-format
                              get-response-format
                              is-response-format?]]
   [ajax.core :refer [to-interceptor
                      ajax-request
                      url-request-format
                      raw-response-format
                      text-response-format
                      json-response-format
                      json-request-format
                      transit-response-format
                      transit-request-format
                      detect-response-format
                      default-formats
                      POST GET]]
   [ajax.json :as json]
   [ajax.edn :refer [edn-request-format edn-response-format]]
   [ajax.ring :refer [ring-response-format]])
   #? (:cljs (:require-macros [cljs.test :refer [deftest testing is]])
       :clj (:import [ajax.interceptors ResponseFormat]
                     [java.lang String]
                     [java.io ByteArrayInputStream])))

(deftest normalize
  (is (= "GET" (simple/normalize-method :get)))
  (is (= "POST" (simple/normalize-method "POST"))))

(defrecord FakeXhrIo [content-type response status]
  ajax.protocols/AjaxImpl
  (-js-ajax-request [this _ h]
    (h this))
  ajax.protocols/AjaxResponse
  (-get-all-headers [this] {"Content-Type" content-type})
  (-get-response-header [this header] content-type)
  (-status [_] status)
  (-status-text [_] "Test Status")
  (-body [_] #? (:cljs response
                 :clj (if response
                          (ByteArrayInputStream.
                           (if (string? response)
                             (.getBytes ^String response)
                             response))))))

(deftest test-get-default-format
  (letfn [(make-format [content-type]
            (f/get-default-format (FakeXhrIo. content-type nil nil)
                                {:response-format @default-formats}))
          (detects [{:keys [from format]}] (is format (= (:description (make-format from)))))]
    (detects {:format "JSON"     :from "application/json;..."})
    (detects {:format "raw text" :from "text/plain;..."})
    (detects {:format "raw text" :from "text/html;..."})
    (detects {:format "Transit"  :from "application/transit+json;xxx"})
    (detects {:format "raw text" :from "application/xml;..."})))

(defn multi-content-type [input]
  (let [a (easy/keyword-response-format input {})
        a2 (detect-response-format {:response-format a})]
    (:content-type a2)))

(deftest keywords
  (is (= (:content-type (easy/keyword-response-format :ring {}))
         (:content-type (ring-response-format))))
  (is (= (:content-type (easy/keyword-response-format :transit {}))
         (:content-type (transit-response-format {}))))
  (is (= (:content-type (easy/keyword-request-format :transit {}))
         (:content-type (transit-request-format))))
  (is (= "application/transit+json" (:content-type (transit-request-format {:type :json-verbose}))))
  (is (vector? (easy/keyword-response-format [:json :transit] {})))
  (is (map? (first (easy/keyword-response-format [:json :transit] {}))))
  (is (= ["application/json"] (f/accept-header {:response-format [(json-response-format {})]})))
  (is (= #? (:clj ["application/json" "application/transit+msgpack" "application/transit+json"]
             :cljs ["application/json" "application/transit+json"])
         (multi-content-type [:json :transit])))
  (is (= ["application/json" "text/plain"]
         (multi-content-type [:json ["text/plain" :raw]]))))

;;; Somewhat ugly that this isn't exactly the same code as runs
;;; in ajax-request
(defn process-inputs [request]
  (let [{:keys [interceptors] :as request}
        (simple/normalize-request request)]
    (reduce simple/process-request request interceptors)))

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
    (is (= "/test" uri))
    (is (= "{:a 3, :b \"hello\"}" (as-string body)))
    (is (= {"Content-Type" "application/edn"
            "Accept" "application/edn"}
           headers))))

(deftest regression-no-formats
  (let [{:keys [headers]}
        (process-inputs {:params {:a 3 :b "hello"}
                         :headers nil
                         :uri "/test"
                         :method "POST"
                         :format (easy/keyword-request-format nil {})
                         :response-format (easy/keyword-response-format nil {})})]
    (is (= "application/transit+json, application/transit+transit, application/json, text/plain, text/html, */*" (get headers "Accept")))))

; NB This also tests that the vec-strategy has reverted to :java
(deftest can-add-to-query-string
  (let [{:keys [uri]}
        (process-inputs {:params {:a [3 4] :b "hello"}
                         :headers nil
                         :uri "/test?extra=true"
                         :method "GET"
                         :response-format (edn-response-format)})]
    (is (= "/test?extra=true&a=3&a=4&b=hello" uri))))

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
    (is (= "/test?extra=true&a=3&b=hello&c=world" uri))))

(deftest process-inputs-as-edn
  (let [{:keys [uri body headers]}
        (process-inputs {:params {:a 3 :b "hello"}
                         :headers nil
                         :uri "/test"
                         :method "GET"
                         :format (edn-request-format)
                         :response-format (edn-response-format)})]
    (is (= "/test?a=3&b=hello" uri))
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
    (is (= "/test" uri))
    (is (= "a=3&b=hello" (as-string body)))
    (is (= {"Content-Type"
            "application/x-www-form-urlencoded; charset=utf-8"
            "Accept" "application/json"}
           headers))))

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

(deftest not-modified
  "If the response to a GET request is of status 304 Not Modified it should be successful"
  (let [r1 (atom nil)
        r2 (atom nil)
        not-modified-response (FakeXhrIo. "application/transit+json; charset blah blah"
                                          "Not Modified Test Response" 304)
        see-other-response    (FakeXhrIo. "application/transit+json; charset blah blah"
                                          "See Other Test Response" 303)]
    (testing "successful response"
      (ajax-request {:uri "/"
                     :handler #(reset! r1 %)
                     :format (url-request-format)
                     :response-format (text-response-format)
                     :api not-modified-response})
      (expect-simple-reply @r1 "Not Modified Test Response"))
    (testing "failure response"
      (ajax-request {:uri "/"
                     :handler #(reset! r2 %)
                     :format (url-request-format)
                     :response-format (text-response-format)
                     :api see-other-response})
      (is @r2 "There should be a non-nil response")
      (is (not (first @r2)) "The response should be a failure"))))

(deftest format-interpretation
  (is (map? (easy/keyword-response-format {} {}))))

(deftest composite-format
  (let [request (-> {:format :raw
                     :response-format [:transit :json]}
                    easy/transform-opts)
        response (FakeXhrIo. "application/json; charset blah blah" "{\"a\":\"b\"}" 200)]
    (is (is-response-format? (get-response-format detect-response-format request)))
    (let [format (f/get-default-format simple-reply request)]
      (is format))))

(deftest response-format-kw
  (is (thrown-with-msg?
      #?(:clj Exception :cljs js/Error)
      #"keywords are not allowed as response formats in ajax calls:"
      (get-response-format detect-response-format {:response-format :json}))))

(deftest request-format-kw
  (is (thrown-with-msg? #?(:clj Exception :cljs js/Error) #"keywords are not allowed as request formats in ajax calls:" (get-request-format :json))))

(deftest json-parsing
  (let [response (FakeXhrIo. "application/json; charset blah blah" "while(1);{\"a\":\"b\"}" 200)
        json-read (fn [prefix keywords? raw]
                    (let [opts {:prefix prefix :keywords? keywords? :raw raw}]
                      ((:read (json-response-format opts)) response)))]
    (is (= {"a" "b"} (json-read "while(1);" false false)))
    (is (= {:a "b"} (json-read "while(1);" true false)))
    #? (:cljs (is (= {"a" "b"} (js->clj (json-read "while(1);" false true))))
        :clj (is (= {"a" "b"} (json-read "while(1);" false true))))))

(deftest ring-format
  (let [response (FakeXhrIo. "text/plain" "BODY" 200)
        test-format {:format {:read :content
                              :content-type ["whatever/you-want"]}}]
    (is (= ["*/*"]
           (:content-type (ring-response-format))))
    (is (= ["whatever/you-want"]
           (:content-type (ring-response-format test-format))))

    (let [read-fn (:read (ring-response-format {:format (text-response-format)}))]
      (is (= {:status 200
              :headers {"Content-Type" "text/plain"}
              :body "BODY"}
             (read-fn response))))))

(deftest url-params-test
  "Sending a delete request with URL params should populate the URL with
  the appropriate query string. Body should have null string"
  (let [{:keys [uri body]}
        (process-inputs {:url-params {:a 3 :b "hello"}
                         :headers nil
                         :uri "/test"
                         :method "DELETE"
                         :format (json-request-format)
                         :response-format (json-response-format)})]
    (is (= "/test?a=3&b=hello" uri))
    (is (= (as-string body) "null"))))

(deftest get-priority-test
  "If a GET request is given both params and url-params, url-params takes priority."
  (let [{:keys [uri]}
        (process-inputs {:url-params {:a 7 :b "bye"}
                         :params {:a 3 :b "hello"}
                         :headers nil
                         :uri "/test"
                         :method "GET"
                         :format (json-request-format)
                         :response-format (edn-response-format)})]
    (is (= "/test?a=7&b=bye" uri))))

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
