(ns ajax.async
  (:require [ajax.core :as c]
            [cljs.core.async :refer [chan <! >!]])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]))

(defn ajax-request
  ([uri method opts]
     (ajax-request uri method opts nil))
  ([uri method opts js-ajax]
     (let [data (or (:channel opts) (chan))
           handler #(go (>! data %))
           opts (assoc opts :handler handler)]
       [data (c/ajax-request uri method opts js-ajax)])))
