(ns browser
  (:require
   ; [cemerick.cljs.test]
   [ajax.protocols :refer [-body]]
   [ajax.core :refer [abort ajax-request
                      url-request-format
                      raw-response-format
                      transit-request-format
                      transit-response-format
                      GET POST]]
   [ajax.edn :refer [edn-request-format
                     edn-response-format]]
   [clojure.string :as s]))

#? (:cljs (enable-console-print!))

(println "Test Results:")

(defn handle-response [res]
  (println "Response")
  (println (pr-str res)))

(defn handle-error [error]
  (println "Error" error))

(defn handle-progress [e]
  (println (str "Progress (" (.-loaded e) "/" (.-total e) ")")))

(defn blob-response-handler
  [[status res]]
  (println (pr-str "status should be true:" status
                   "res should get a blob: " (type res)
                   "blob type should be application/png:" (.-type res))))

(defn fix-uri [uri]
  #?(:cljs uri
     :clj (if (.startsWith uri "http://")
            uri
            (str "http://localhost:3000" uri))))

(defn log-id-handler [message]
  (fn [handler]
    (fn handle [response]
      (println message)
      (handler response))))

(defn log-id [opts]
  (-> opts
      (update :handler (log-id-handler (str "Response id " (-> opts :params :id))))
      (update :error-handler (log-id-handler (str "Error Response id " (-> opts :params :id))))))

#? (:cljs (do
            (defn modern-params [{:keys [id] :as params}]
              (assoc params :id (+ id 100)))

            (defn modern-opts [{:keys [params] :as opts}]
              (assoc opts
                :params (modern-params params)
                :api (js/XMLHttpRequest. )))

            (defn get-opts [opts] [(log-id opts)
                                   (log-id (modern-opts opts))]))
    :clj (defn get-opts [{:keys [uri] :as opts}]
           [(log-id (if uri
                      (assoc opts :uri (fix-uri uri))
                      opts))]))

(defn request-opts [data timeout]
  {:uri "/ajax"
   :method "POST"
   :params data
   :format (edn-request-format)
   :response-format (edn-response-format)
   :handler handle-response
   :error-handler handle-error
   :timeout timeout})

(defn request
  ([data timeout]
     (doseq [p (get-opts (request-opts data timeout))]
       (ajax-request p)))
  ([data] (request data 5000)))

(defn POST2 [uri opts]
  (doseq [p (get-opts opts)]
    (POST (fix-uri uri) p)))

#? (:cljs
    (do
      (let [form-data (doto
                          (js/FormData.)
                        (.append "id" "10")
                        (.append "timeout" "0")
                        (.append "input" "Hello form-data POST"))]
        (POST "/ajax-form-data" {:body form-data
                                 :response-format (raw-response-format)
                                 :handler handle-response
                                 :error-handler handle-error
                                 :timeout 100}))
      (let [form-data (doto
                          (js/FormData.)
                        (.append "id" "110")
                        (.append "timeout" "0")
                        (.append "input" "Hello form-data POST"))]
        (POST "/ajax-form-data" {:body form-data
                                 :response-format (raw-response-format)
                                 :handler handle-response
                                 :error-handler handle-error
                                 :timeout 100
                                 :api (js/XMLHttpRequest.)}))))

(defn run-browser-tests []
  (request {:id 3 :timeout 0 :input "Hello"})

  (POST2 "/ajax" {:params {:id 4 :timeout 0 :input "Hello POST"}
                  :format (edn-request-format)
                  :response-format (edn-response-format)
                  :handler handle-response
                  :error-handler handle-error
                  :timeout 10000})

  (POST2 "/ajax" {:params {:id 8 :timeout 0 :input "Hello POST TRANSIT"}
                  :format (transit-request-format {})
                  :response-format (edn-response-format)
                  :handler handle-response
                  :error-handler handle-error
                  :timeout 10000})

  (POST2 "/ajax-transit" {:params {:id 13 :timeout 0 :input "Hello POST TRANSIT RESPONSE"}
                          :format (transit-request-format {})
                          :response-format (transit-response-format {})
                          :handler handle-response
                          :error-handler handle-error
                          :timeout 10000})
  (println "Keep going 0")
  (let [get-params {:params {:id 5 :timeout 0 :input "Hello GET"}
                    :response-format (edn-response-format)
                    :handler handle-response
                    :timeout 10000}]
    (doseq [p (get-opts get-params)]
      (GET (fix-uri "/ajax-url") p)))

;;; GET not found

  (request {:id 7 :timeout 5000 :input "Should Timeout"} 100)
  (println "Keep going 1")
  (doseq [p (get-opts (request-opts {:id 11 :timeout 5000 :input "Should Abort"} 1000))]
    (abort (ajax-request p)))

  (POST2 "http://localhost:9797/no-server"
         {:params {:id 14 :input "Fail to connect"}
          :format (url-request-format)
          :response-format (raw-response-format)
          :handler handle-response
          :error-handler handle-error
          :timeout 100})
  (println "Keep going 2")
  (POST2 "/ajax-url" {:params {:id 9 :timeout 0 :input "Hello form POST"}
                      :format (url-request-format)
                      :response-format (raw-response-format)
                      :handler handle-response
                      :error-handler handle-error
                      :timeout 100})

  (POST2 "/ajax-transit" {:params {:id 15 :timeout 0 :input "Can use multiple response formats"}
                          :format (transit-request-format {})
                          :response-format [:transit :json]
                          :handler handle-response
                          :error-handler handle-error
                          :timeout 10000})

  #? (:cljs (ajax-request {:uri "/ajax-form-data-png"
                           :method "POST"
                           :body (doto
                                     (js/FormData.)
                                   (.append "id" "19")
                                   (.append "timeout" "0")
                                   (.append "input" "Hello form-data POST"))
                           :api (js/XMLHttpRequest.)
                           :handler blob-response-handler
                           :error-handler handle-error
                           :response-format {:content-type "image/png"
                                             :type :blob
                                             :description "PNG file"
                                             :read -body}}))

  #? (:cljs (ajax-request {:uri "/ajax-form-data-png"
                           :method "POST"
                           :body (doto
                                   (js/FormData.)
                                   (.append "id" "19")
                                   (.append "timeout" "0")
                                   (.append "input" "Hello form-data POST"))
                           :handler blob-response-handler
                           :error-handler handle-error
                           :progress-handler handle-progress
                           :response-format {:content-type "image/png"
                                             :type :blob
                                             :description "PNG file"
                                             :read -body}})))
#? (:cljs (run-browser-tests))
