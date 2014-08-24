(ns test.browser
  (:require
   [cemerick.cljs.test]
   [ajax.core :refer [abort ajax-request
                      url-request-format
                      edn-response-format
                      edn-request-format
                      raw-response-format
                      transit-request-format
                      transit-response-format
                      GET POST]])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(.log js/console "Test Results:")

(defn handle-response [res]
  (.log js/console (pr-str res)))

(defn request
  ([data timeout]
     (ajax-request {:uri "/ajax"
                    :method "POST"
                    :params data
                    :format (edn-request-format)
                    :response-format (edn-response-format)
                    :handler handle-response
                    :timeout timeout}))
  ([data] (request data 5000)))

(let [form-data (doto
                    (js/FormData.)
                  (.append "id" "10")
                  (.append "timeout" "0")
                  (.append "input" "Hello form-data POST"))]
  (POST "/ajax-form-data" {:params form-data
                           :response-format (raw-response-format)
                           :handler handle-response
                           :timeout 100}))

(request {:id 3 :timeout 0 :input "Hello"})

(POST "/ajax" {:params {:id 4 :timeout 0 :input "Hello POST"}
               :format (edn-request-format)
               :response-format (edn-response-format)
               :handler handle-response
               :timeout 10000})

(POST "/ajax" {:params {:id 8 :timeout 0 :input "Hello POST TRANSIT"}
               :format (transit-request-format {})
               :response-format (edn-response-format)
               :handler handle-response
               :timeout 10000})

(POST "/ajax-transit" {:params {:id 13 :timeout 0 :input "Hello POST TRANSIT RESPONSE"}
               :format (transit-request-format {})
               :response-format (transit-response-format {})
               :handler handle-response
               :timeout 10000})

(GET "/ajax-url" {:params {:id 5 :timeout 0 :input "Hello GET"}
                  :response-format (edn-response-format)
                  :handler handle-response
                  :timeout 10000})
;;; GET not found

(request {:id 7 :timeout 5000 :input "Should Timeout"} 100)

(abort (request {:id 11 :timeout 5000 :input "Should Abort"} 1000))

(POST "/ajax-url" {:params {:id 9 :timeout 0 :input "Hello form POST"}
               :format (url-request-format)
               :response-format (raw-response-format)
               :handler handle-response
               :timeout 100})
