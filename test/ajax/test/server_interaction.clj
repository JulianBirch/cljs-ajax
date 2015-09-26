(ns ajax.test.server-interaction
  (:require [ajax.core :as ajax]
            [clojure.core.async :refer [go >! <!! chan]]
            [clojure.test :refer :all]
            [compojure.core :as compojure]
            [compojure.handler :refer [site]]
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
        (is (= payload out))))))

(comment (run-tests))

(defn setup-server [f]
  (let [server (-> app site (run-server {:port port}))]
    (try
      (f)
      (finally
        (server)))))

(use-fixtures :once setup-server)
