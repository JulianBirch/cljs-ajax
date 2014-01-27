(ns test.browser
  (:require
   [ajax.core :refer [abort ajax-request edn-format GET POST]]
   [ajax.async :as a])
  (:require-macros [cljs.core.async.macros :refer [go]]))

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

(POST "/ajax" {:params {:id 4 :timeout 0 :input "Hello POST"}
               :format (edn-format)
               :handler handle-response
               :timeout 10000})

(GET "/ajax" {:params {:id 5 :timeout 0 :input "Hello GET"}
              :format (edn-format)
              :handler handle-response
              :timeout 10000})
;;; GET not found

(request {:id 7 :timeout 5000 :input "Should Timeout"} 100)

(abort (request {:id 7 :timeout 5000 :input "Should Abort"} 1000))

#_(let [opts {:format (edn-format)
            :params {:id 3 :input "Async Hello" :timeout 500}}
      [c r] (a/ajax-request "/ajax" "POST" opts)]
  (go
   (.log js/console "Pre block")
   (.log js/console (pr-str (<! c)))
   (.log js/console "Unblock")))
