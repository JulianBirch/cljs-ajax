(ns ajax.test.integration-browser
  (:require [ajax.core :as ajax]
            [ajax.protocols :refer [-body]]
            [cljs.test :refer-macros [deftest is async]]))

(set! (.-cljsAjaxIntegrationBaseUrl js/globalThis)
      (or (some-> js/globalThis .-location .-origin)
          (.-cljsAjaxIntegrationBaseUrl js/globalThis)))

(defn integration-base-url []
  (.-cljsAjaxIntegrationBaseUrl js/globalThis))

(defn endpoint [path]
  (str (integration-base-url) path))

(defn integration-form-data [id input]
  (doto (js/FormData.)
    (.append "id" (str id))
    (.append "timeout" "0")
    (.append "input" input)))

(deftest integration-base-url-configured
  (is (string? (integration-base-url))))

(deftest form-data-round-trip
  (async done
    (ajax/POST (endpoint "/ajax-form-data")
               {:body (integration-form-data 10 "Hello form-data POST")
                :response-format (ajax/raw-response-format)
                :handler (fn [response]
                           (is (= (pr-str {:id 10
                                           :output "INPUT:  Hello form-data POST"})
                                  response))
                           (done))
                :error-handler (fn [error]
                                 (is false (pr-str error))
                                 (done))})))

(deftest explicit-xml-http-request-round-trip
  (async done
    (ajax/POST (endpoint "/ajax-form-data")
               {:body (integration-form-data 110 "Hello explicit xhr")
                :api (js/XMLHttpRequest.)
                :response-format (ajax/raw-response-format)
                :handler (fn [response]
                           (is (= (pr-str {:id 110
                                           :output "INPUT:  Hello explicit xhr"})
                                  response))
                           (done))
                :error-handler (fn [error]
                                 (is false (pr-str error))
                                 (done))})))

(deftest blob-response-handling
  (async done
    (ajax/ajax-request
     {:uri (endpoint "/ajax-form-data-png")
      :method "POST"
      :body (integration-form-data 19 "Hello form-data POST")
      :response-format {:content-type "image/png"
                        :type :blob
                        :description "PNG file"
                        :read -body}
      :handler (fn [[ok response]]
                 (is ok)
                 (is (instance? js/Blob response))
                 (is (= "image/png" (.-type response)))
                 (done))})))

(deftest progress-handler-invoked
  (async done
    (let [progress-called? (atom false)]
      (ajax/ajax-request
       {:uri (endpoint "/ajax-form-data-png")
        :method "POST"
        :body (integration-form-data 20 "Hello progress")
        :progress-handler (fn [_]
                            (reset! progress-called? true))
        :response-format {:content-type "image/png"
                          :type :blob
                          :description "PNG file"
                          :read -body}
        :handler (fn [[ok response]]
                   (is ok)
                   (is (instance? js/Blob response))
                   (is @progress-called?)
                   (done))
        :error-handler (fn [error]
                         (is false (pr-str error))
                         (done))}))))
