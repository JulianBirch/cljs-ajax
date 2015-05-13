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
                      GET POST -body]])
  (:require-macros [cljs.core.async.macros :refer [go]]))


(.log js/console "Test Results:")

(defn handle-response [res]
  (.log js/console (pr-str res)))

(defn modern-params [{:keys [id] :as params}]
  (assoc params :id (+ id 100)))

(defn modern-opts [{:keys [params] :as opts}]
  (assoc opts
    :params (modern-params params)
    :api (js/XMLHttpRequest. )))

(defn request-opts [data timeout]
  {:uri "/ajax"
   :method "POST"
   :params data
   :format (edn-request-format)
   :response-format (edn-response-format)
   :handler handle-response
   :timeout timeout})

(defn request
  ([data timeout]
     (let [p (request-opts data timeout)]
       (ajax-request p)
       (ajax-request (modern-opts p))))
  ([data] (request data 5000)))

(defn POST2 [uri opts]
  (POST uri opts)
  (POST uri (modern-opts opts)))

(let [form-data (doto
                    (js/FormData.)
                  (.append "id" "10")
                  (.append "timeout" "0")
                  (.append "input" "Hello form-data POST"))]
  (POST "/ajax-form-data" {:params form-data
                           :response-format (raw-response-format)
                           :handler handle-response
                           :timeout 100}))
(let [form-data (doto
                    (js/FormData.)
                  (.append "id" "110")
                  (.append "timeout" "0")
                  (.append "input" "Hello form-data POST"))]
  (POST "/ajax-form-data" {:params form-data
                           :response-format (raw-response-format)
                           :handler handle-response
                           :timeout 100
                           :api (js/XMLHttpRequest.)}))

(request {:id 3 :timeout 0 :input "Hello"})

(POST2 "/ajax" {:params {:id 4 :timeout 0 :input "Hello POST"}
               :format (edn-request-format)
               :response-format (edn-response-format)
               :handler handle-response
               :timeout 10000})

(POST2 "/ajax" {:params {:id 8 :timeout 0 :input "Hello POST TRANSIT"}
               :format (transit-request-format {})
               :response-format (edn-response-format)
               :handler handle-response
               :timeout 10000})

(POST2 "/ajax-transit" {:params {:id 13 :timeout 0 :input "Hello POST TRANSIT RESPONSE"}
               :format (transit-request-format {})
               :response-format (transit-response-format {})
               :handler handle-response
               :timeout 10000})

(let [get-params {:params {:id 5 :timeout 0 :input "Hello GET"}
                  :response-format (edn-response-format)
                  :handler handle-response
                  :timeout 10000}]
  (GET "/ajax-url" get-params)
  (GET "/ajax-url" (modern-opts get-params)))

;;; GET not found

(request {:id 7 :timeout 5000 :input "Should Timeout"} 100)

(abort (ajax-request (request-opts {:id 11 :timeout 5000 :input "Should Abort"} 1000)))
(abort (ajax-request (modern-opts (request-opts {:id 111 :timeout 5000 :input "Should Abort"} 1000))))

(POST2 "/ajax-url" {:params {:id 9 :timeout 0 :input "Hello form POST"}
               :format (url-request-format)
               :response-format (raw-response-format)
               :handler handle-response
               :timeout 100})

(POST2 "/ajax-transit" {:params {:id 15 :timeout 0 :input "Can use multiple response formats"}
               :format (transit-request-format {})
               :response-format [:transit :edn]
               :handler handle-response
               :timeout 10000})

(defn blob-response-handler
  [[status res]]
  (.log js/console (pr-str "status should be true:" status 
                           "res should get a blob: " (type res) 
                           "blob type should be application/png:" (.-type res))))

(ajax-request {:uri "/ajax-form-data-png"
               :method "POST"
               :params (doto
                         (js/FormData.)
                         (.append "id" "10")
                         (.append "timeout" "0")
                         (.append "input" "Hello form-data POST"))
               :api (js/XMLHttpRequest.)
               :handler blob-response-handler
               :response-format {:content-type "image/png"
                                 :type :blob
                                 :description "PNG file"
                                 :read -body}})
