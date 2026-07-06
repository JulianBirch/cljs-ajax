(ns ajax.test.compiled-output
  (:require [cljs.test :refer-macros [deftest is]]))

(def fs (js/require "fs"))
(def path (js/require "path"))

(deftest xml-http-request-does-not-emit-static-xmlhttprequest-require
  (let [compiled-js (.readFileSync fs
                                   (.join path
                                          js/__dirname
                                          "../.shadow-cljs/builds/test-node/dev/out/cljs-runtime/ajax.xml_http_request.js")
                                   "utf8")]
    (is (not (re-find #"(?m)[^\w.]require[^\w.]" compiled-js)))))
