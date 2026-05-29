(ns ajax.test.server-interaction
  (:require [ajax.core :as ajax]
            [ajax.test.integration-support :as integration-support]
            [clojure.core.async :refer [go >! <!! chan]]
            [clojure.test :refer :all]
            [compojure.core :as compojure]
            [compojure.handler :refer [site]]
            [clojure.java.io :as io]
            [org.httpkit.client :as httpkit]
            [org.httpkit.server :refer [run-server]]))

(compojure/defroutes app
  (compojure/GET "/" [] "It is dangerous to go alone! Take this!")
  (compojure/POST "/echo" {body :body} (slurp body)))

;; Get a random free port
(defonce port
  (with-open [socket-server (new java.net.ServerSocket 0)]
    (.getLocalPort socket-server)))

(deftest get-root
  (let [url (str "http://0.0.0.0:" port "/")]
    (testing "httpkit can GET /"
      (let [{:keys [:status :body] :as resp} @(httpkit/get url)]
        (is (= "It is dangerous to go alone! Take this!" body))
        (is (= 200 status))))
    (testing "ajax can GET / (call-back wraps core-async)"
      (let [comm (chan)
            _ (ajax/GET url {:handler #(go (>! comm %))
                             :error-handler #(go (>! comm %))})
            out (<!! comm)]
        (is (= "It is dangerous to go alone! Take this!" out))))))

(deftest post-echo
  (let [url (str "http://0.0.0.0:" port "/echo")]
    (testing "httpkit can POST to /echo and it echoes back"
      (let [payload (name (gensym))
            {:keys [:status :body] :as resp} @(httpkit/post url {:body payload})]
        (is (= payload body))
        (is (= 200 status))))
    (testing "ajax can POST to /echo and it echos back (call-back wraps core-async)"
      (let [payload (name (gensym))
            comm (chan)
            _ (ajax/POST url {:handler #(go (>! comm %))
                              :error-handler #(go (>! comm %))
                              :format :text
                              :params payload})
            out (<!! comm)]
        (is (= payload out))))
    (testing "ajax can POST a string in the :body tag"
      (let [payload (name (gensym))
            comm (chan)
            _ (ajax/POST url {:handler #(go (>! comm %))
                              :error-handler #(go (>! comm %))
                              :body payload})
            out (<!! comm)]
        (is (= payload out))))))

(deftest integration-support-routes
  (testing "the extracted support app still serves the integration contract"
    (let [resp (integration-support/app {:request-method :get
                                         :uri "/ajax"
                                         :params {:id 1
                                                  :timeout 0
                                                  :input "Hello"}})]
      (is (= 200 (:status resp)))
      (is (= (pr-str {:id 1 :output "INPUT:  Hello"}) (:body resp)))))
  (is (re-matches #"^http://localhost:\d+$" integration-support/base-url)))

(deftest integration-support-server-lifecycle
  (let [integration-js (io/file "target-int" "integration.js")
        _ (.mkdirs (.getParentFile integration-js))]
    (spit integration-js "console.log('integration support');")
    (let [server (integration-support/start-server)]
      (try
        (testing "the extracted lifecycle serves the static integration bundle"
          (let [{:keys [:status :body]} @(httpkit/get (str integration-support/base-url "/integration.js"))]
            (is (= 200 status))
            (is (= "console.log('integration support');" (slurp body)))))
        (testing "the extracted lifecycle preserves key integration routes"
          (let [ajax-resp @(httpkit/post (str integration-support/base-url "/ajax")
                                         {:headers {"Content-Type" "application/edn"}
                                          :body (pr-str {:id 4
                                                         :timeout 0
                                                         :input "Hello POST"})})
                ajax-url-resp @(httpkit/get (str integration-support/base-url "/ajax-url")
                                            {:query-params {:id "12"
                                                            :timeout "0"
                                                            :input "Hello GET"}})
                form-data-resp @(httpkit/post (str integration-support/base-url "/ajax-form-data")
                                              {:multipart [{:name "id" :content "18"}
                                                           {:name "timeout" :content "0"}
                                                           {:name "input" :content "Hello form-data"}]})
                transit-resp @(httpkit/post (str integration-support/base-url "/ajax-transit")
                                            {:headers {"Content-Type" "application/edn"}
                                             :body (pr-str {:id 16
                                                            :timeout 0
                                                            :input "Hello Transit"})})
                png-resp @(httpkit/post (str integration-support/base-url "/ajax-form-data-png"))]
            (is (= 200 (:status ajax-resp)))
            (is (= (pr-str {:id 4 :output "INPUT:  Hello POST"})
                   (slurp (:body ajax-resp))))
            (is (= 200 (:status ajax-url-resp)))
            (is (= (pr-str {:id 12 :output "INPUT:  Hello GET"})
                   (slurp (:body ajax-url-resp))))
            (is (= 200 (:status form-data-resp)))
            (is (= (pr-str {:id 18 :output "INPUT:  Hello form-data"})
                   (slurp (:body form-data-resp))))
            (is (= 200 (:status transit-resp)))
            (is (.contains (str (:body transit-resp)) "INPUT:  Hello Transit"))
            (is (= 200 (:status png-resp)))
            (is (= "im not even a real png!" (slurp (:body png-resp))))))
        (finally
          (integration-support/stop-server server)
          (.delete integration-js))))))

(comment (run-tests))

(defn setup-server [f]
  (let [server (-> app site (run-server {:port port}))]
    (try
      (f)
      (finally
        (server)))))

(use-fixtures :once setup-server)
