(ns cljs-ajax.test.browser
  (:require
   [ajax.core :refer [ajax-request edn-format]]))

(defn handle-response [res]
  (.log js/console (pr-str res)))

(defn request
  ([data timeout]
      (ajax-request "/ajax" "POST" {:params data
                                    :format (edn-format)
                                    :handler handle-response
                                    :timeout timeout}))
  ([data] (request data 5000)))

(request {:id 3 :timeout 0 :input "Hello"})

(request {:id 7 :timeout 5000 :input "Should Timeout"} 100)
