(ns ajax.test.integration-support
  (:import (java.io ByteArrayOutputStream)
           (java.net ServerSocket))
  (:require [cognitect.transit :as t]
            [hiccup.core :refer [html]]
            [ring.middleware.edn :as rme]
            [ring.middleware.file :as rmf]
            [ring.middleware.multipart-params :as rmmp]
            [ring.middleware.params :as params]
            [ring.middleware.transit :as tr]
            [ring.server.standalone :as rsa]
            [ring.util.response :as rur]))

(defonce ^:private port
  (with-open [socket-server (ServerSocket. 0)]
    (.getLocalPort socket-server)))

(def base-url (str "http://localhost:" port))

(defn- edn-response [data & [status]]
  {:status (or status 200)
   :headers {"Content-Type" "application/edn"}
   :body (pr-str data)})

(defn- write-transit [x]
  (let [baos (ByteArrayOutputStream.)
        w    (t/writer baos :json)
        _    (t/write w x)
        ret  (.toString baos)]
    (.reset baos)
    ret))

(defn- transit-response [response]
  {:status 200
   :headers {"Content-Type" "application/transit+json; charset=utf-8"}
   :body (write-transit response)})

(defn- png-response [_response]
  {:status 200
   :headers {"Content-Type" "image/png"
             "Content-Disposition" "inline; filename=\"foo.png\""}
   :body "im not even a real png!"})

(defn- ajax-handler
  ([{{:keys [id timeout input output]} :params :as request}]
   (ajax-handler id timeout input output))
  ([id timeout input]
   (ajax-handler id timeout input nil))
  ([id timeout input output]
   (let [slept? (try
                  (when timeout
                    (Thread/sleep timeout))
                  true
                  (catch InterruptedException _
                    false))]
     (if (and slept? id)
       (or ((or output edn-response)
            {:id id :output (str "INPUT:  " input)})
           (rur/not-found ""))
       (rur/not-found "")))))

(defn- ajax-uri-handler [{{:strs [id timeout input]} :params}]
  (ajax-handler (read-string id) (read-string timeout) input))

(defn- ajax-form-data-handler [request]
  (ajax-uri-handler request))

(defn- sc-handler [{:keys [uri] :as request}]
  (case uri
    "/" {:status 200
         :body (html
                [:h1 "Ajax Tester"]
                [:script {:src "/integration.js" :type "text/javascript"}])}
    "/ajax" (ajax-handler request)
    "/ajax-transit" (ajax-handler
                     (update-in request [:params]
                                #(assoc % :output transit-response)))
    "/ajax-url" (ajax-uri-handler request)
    "/ajax-form-data" (ajax-form-data-handler request)
    "/ajax-form-data-png" (png-response request)
    "/favicon.ico" (rur/not-found "")
    (rur/not-found "")))

(def app
  (-> sc-handler
      rme/wrap-edn-params
      params/wrap-params
      rmmp/wrap-multipart-params
      tr/wrap-transit-params))

(defonce ^:private target-int-dir
  (doto (java.io.File. "target-int")
    (.mkdirs)))

(def server-app
  (rmf/wrap-file app (.getPath target-int-dir)))

(defn start-server []
  (rsa/serve server-app {:port port}))

(defn stop-server [server]
  (when server
    (.stop server)))
