(ns ajax.util
  "Short utility functions. A lot of these only exist because the 
   cross platform implementation is annoying."
   (:require [ajax.protocols :as pr]))

(defn throw-error [args]
  "Throws an error."
  (throw (#?(:clj Exception. :cljs js/Error.)
           (str args))))

(defn get-content-type ^String [response]
  (or (pr/-get-response-header response "Content-Type") ""))
