(ns ajax.test.integration
  (:require
   [ajax.core :as ajax]
   [ajax.edn :refer [edn-request-format edn-response-format]]
   [ajax.protocols :refer [-body]]
   #?@(:clj [[ajax.test.integration-support :as integration-support]
             [clojure.test :refer :all]]
       :cljs [[cljs.test :refer-macros [deftest is async]]])))

#?(:clj
   (defn integration-base-url []
     integration-support/base-url)
   :cljs
   (defn integration-base-url []
     (some-> js/globalThis .-cljsAjaxIntegrationBaseUrl)))

(defn endpoint [path]
  (str (or (integration-base-url) "") path))

(defn assert-success-response [actual expected]
  (is (= expected actual)))

#?(:cljs
   (defn run-integration-test [done f]
     (if-let [url (integration-base-url)]
       (f url)
       (done))))

#?(:cljs
   (defn cljs-request-opts [opts]
     (cond-> opts
       (exists? js/process)
       (assoc :api (js/XMLHttpRequest.)))))

#?(:cljs
   (defn node-runtime? []
     (exists? js/process)))

#?(:clj
   (deftest endpoint-uses-base-url
     (with-redefs [integration-base-url (fn [] "http://example.test")]
       (is (= "http://example.test/foo" (endpoint "/foo"))))))

#?(:clj
   (defn start-request [request-fn]
     (let [result (promise)
           request (request-fn #(deliver result [:success %])
                               #(deliver result [:error %]))]
       {:request request
        :result result})))

#?(:clj
   (defn await-result [result]
     (deref result 5000 [:timeout nil])))

#?(:clj
   (deftest edn-post
     (let [{:keys [result]} (start-request
                             (fn [handler error-handler]
                               (ajax/POST (endpoint "/ajax")
                                          {:params {:id 4
                                                    :timeout 0
                                                    :input "Hello POST"}
                                           :format (edn-request-format)
                                           :response-format (edn-response-format)
                                           :handler handler
                                           :error-handler error-handler})))]
       (is (= [:success {:id 4 :output "INPUT:  Hello POST"}]
              (await-result result)))))
   :cljs
   (deftest edn-post
     (async done
       (run-integration-test
        done
        (fn [_base-url]
          (ajax/POST (endpoint "/ajax")
                     (cljs-request-opts
                      {:params {:id 4
                                :timeout 0
                                :input "Hello POST"}
                       :format (edn-request-format)
                       :response-format (edn-response-format)
                       :handler (fn [response]
                                  (assert-success-response response
                                                           {:id 4
                                                            :output "INPUT:  Hello POST"})
                                  (done))
                       :error-handler (fn [error]
                                        (is false (pr-str error))
                                        (done))})))))))

#?(:clj
   (deftest edn-get
     (let [{:keys [result]} (start-request
                             (fn [handler error-handler]
                               (ajax/GET (endpoint "/ajax-url")
                                         {:params {:id 5
                                                   :timeout 0
                                                   :input "Hello GET"}
                                          :response-format (edn-response-format)
                                          :handler handler
                                          :error-handler error-handler})))]
       (is (= [:success {:id 5 :output "INPUT:  Hello GET"}]
              (await-result result)))))
   :cljs
   (deftest edn-get
     (async done
       (run-integration-test
        done
        (fn [_base-url]
          (ajax/GET (endpoint "/ajax-url")
                    (cljs-request-opts
                     {:params {:id 5
                               :timeout 0
                               :input "Hello GET"}
                      :response-format (edn-response-format)
                      :handler (fn [response]
                                 (assert-success-response response
                                                          {:id 5
                                                           :output "INPUT:  Hello GET"})
                                 (done))
                      :error-handler (fn [error]
                                       (is false (pr-str error))
                                       (done))})))))))

#?(:clj
   (deftest transit-request-encoding
     (let [{:keys [result]} (start-request
                             (fn [handler error-handler]
                               (ajax/POST (endpoint "/ajax")
                                          {:params {:id 8
                                                    :timeout 0
                                                    :input "Hello Transit Request"}
                                           :format (ajax/transit-request-format {})
                                           :response-format (edn-response-format)
                                           :handler handler
                                           :error-handler error-handler})))]
       (is (= [:success {:id 8 :output "INPUT:  Hello Transit Request"}]
              (await-result result)))))
   :cljs
   (deftest transit-request-encoding
     (async done
       (run-integration-test
        done
        (fn [_base-url]
          (ajax/POST (endpoint "/ajax")
                     (cljs-request-opts
                      {:params {:id 8
                                :timeout 0
                                :input "Hello Transit Request"}
                       :format (ajax/transit-request-format {})
                       :response-format (edn-response-format)
                       :handler (fn [response]
                                  (assert-success-response response
                                                           {:id 8
                                                            :output "INPUT:  Hello Transit Request"})
                                  (done))
                       :error-handler (fn [error]
                                        (is false (pr-str error))
                                        (done))})))))))

#?(:clj
   (deftest transit-response-decoding
     (let [{:keys [result]} (start-request
                             (fn [handler error-handler]
                               (ajax/POST (endpoint "/ajax-transit")
                                          {:params {:id 13
                                                    :timeout 0
                                                    :input "Hello Transit Response"}
                                           :format (ajax/transit-request-format {})
                                           :response-format (ajax/transit-response-format {})
                                           :handler handler
                                           :error-handler error-handler})))]
       (is (= [:success {:id 13 :output "INPUT:  Hello Transit Response"}]
              (await-result result)))))
   :cljs
   (deftest transit-response-decoding
     (async done
       (run-integration-test
        done
        (fn [_base-url]
          (ajax/POST (endpoint "/ajax-transit")
                     (cljs-request-opts
                      {:params {:id 13
                                :timeout 0
                                :input "Hello Transit Response"}
                       :format (ajax/transit-request-format {})
                       :response-format (ajax/transit-response-format {})
                       :handler (fn [response]
                                  (assert-success-response response
                                                           {:id 13
                                                            :output "INPUT:  Hello Transit Response"})
                                  (done))
                       :error-handler (fn [error]
                                        (is false (pr-str error))
                                        (done))})))))))

#?(:clj
   (deftest urlencoded-form-submission
     (let [{:keys [result]} (start-request
                             (fn [handler error-handler]
                               (ajax/POST (endpoint "/ajax-url")
                                           {:params {:id 9
                                                     :timeout 0
                                                     :input "Hello Form"}
                                            :format (ajax/url-request-format)
                                           :response-format (assoc (ajax/raw-response-format)
                                                                   :read (fn [response]
                                                                           (slurp (-body response))))
                                           :handler handler
                                           :error-handler error-handler})))]
       (is (= [:success (pr-str {:id 9 :output "INPUT:  Hello Form"})]
              (await-result result)))))
   :cljs
   (deftest urlencoded-form-submission
     (async done
       (run-integration-test
        done
        (fn [_base-url]
          (ajax/POST (endpoint "/ajax-url")
                     (cljs-request-opts
                      {:params {:id 9
                                :timeout 0
                                :input "Hello Form"}
                       :format (ajax/url-request-format)
                       :response-format (ajax/raw-response-format)
                       :handler (fn [response]
                                  (assert-success-response response
                                                           (pr-str {:id 9
                                                                    :output "INPUT:  Hello Form"}))
                                  (done))
                       :error-handler (fn [error]
                                        (is false (pr-str error))
                                        (done))})))))))

#?(:clj
   (deftest timeout-handling
     (let [{:keys [result]} (start-request
                             (fn [handler error-handler]
                               (ajax/POST (endpoint "/ajax")
                                          {:params {:id 7
                                                    :timeout 2000
                                                    :input "Should Timeout"}
                                           :socket-timeout 1
                                           :format (edn-request-format)
                                           :response-format (edn-response-format)
                                           :handler handler
                                           :error-handler error-handler})))]
       (let [resolved (await-result result)]
         (is (= :error (first resolved)))
         (is (= -1 (get (second resolved) :status)))
         (is (= :timeout (get (second resolved) :failure))))))
   :cljs
   (deftest timeout-handling
     (async done
       (run-integration-test
        done
        (fn [_base-url]
          (if (node-runtime?)
            (done)
            (ajax/POST (endpoint "/ajax")
                       (cljs-request-opts
                        {:params {:id 7
                                  :timeout 2000
                                  :input "Should Timeout"}
                         :timeout 1
                         :format (edn-request-format)
                         :response-format (edn-response-format)
                         :handler (fn [response]
                                    (is false (pr-str response))
                                    (done))
                         :error-handler (fn [error]
                                          (is (= -1 (:status error)))
                                          (is (= :timeout (:failure error)))
                                          (done))}))))))))

#?(:clj
   (deftest abort-handling
     (let [{:keys [request result]} (start-request
                                     (fn [handler error-handler]
                                       (ajax/POST (endpoint "/ajax")
                                                  {:params {:id 11
                                                            :timeout 1000
                                                            :input "Should Abort"}
                                                   :timeout 5000
                                                   :format (edn-request-format)
                                                   :response-format (edn-response-format)
                                                   :handler handler
                                                   :error-handler error-handler})))]
       (Thread/sleep 100)
       (ajax/abort request)
       (let [resolved (await-result result)]
         (is (= :error (first resolved)))
         (is (= -1 (get (second resolved) :status)))
         (is (= :aborted (get (second resolved) :failure))))))
   :cljs
   (deftest abort-handling
     (async done
       (run-integration-test
        done
        (fn [_base-url]
          (if (node-runtime?)
            (done)
            (let [aborted? (atom false)
                  request (ajax/POST
                           (endpoint "/ajax")
                           (cljs-request-opts
                            {:params {:id 11
                                      :timeout 1000
                                      :input "Should Abort"}
                             :timeout 5000
                             :format (edn-request-format)
                             :response-format (edn-response-format)
                             :handler (fn [response]
                                        (is false (pr-str response))
                                        (done))
                             :error-handler (fn [error]
                                              (when @aborted?
                                                (is (= -1 (:status error)))
                                                (is (= :aborted (:failure error)))
                                                (done)))}))]
              (js/setTimeout
               (fn []
                 (reset! aborted? true)
                 (ajax/abort request))
               100))))))))

#?(:clj
   (deftest connection-refused-reporting
     (let [port (with-open [socket (java.net.ServerSocket. 0)]
                  (.getLocalPort socket))
           {:keys [result]} (start-request
                             (fn [handler error-handler]
                               (ajax/POST (str "http://127.0.0.1:" port "/no-server")
                                          {:params {:id 14
                                                    :input "Fail to connect"}
                                           :format (ajax/url-request-format)
                                           :response-format (edn-response-format)
                                           :handler handler
                                           :error-handler error-handler})))]
       (let [resolved (await-result result)]
         (is (= :error (first resolved)))
         (is (= 0 (get (second resolved) :status)))
         (is (re-find #"Connection refused"
                      (get (second resolved) :status-text ""))))))
   :cljs
   (deftest connection-refused-reporting
     (async done
       (run-integration-test
        done
        (fn [_base-url]
          (ajax/POST "http://127.0.0.1:1/no-server"
                     (cljs-request-opts
                      {:params {:id 14
                                :input "Fail to connect"}
                       :format (ajax/url-request-format)
                       :response-format (edn-response-format)
                       :handler (fn [response]
                                  (is false (pr-str response))
                                  (done))
                       :error-handler (fn [error]
                                        (is (= 0 (:status error)))
                                        (is (contains? #{:error :failed}
                                                       (:failure error)))
                                        (done))})))))))
