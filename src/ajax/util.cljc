(ns ajax.util
  "Short utility functions. A lot of these only exist because the 
   cross platform implementation is annoying.")

(defn throw-error [args]
  "Throws an error."
  (throw (#?(:clj Exception. :cljs js/Error.)
           (str args))))
