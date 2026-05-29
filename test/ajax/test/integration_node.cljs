(ns ajax.test.integration-node
  (:require [cljs.test :refer-macros [deftest is]]))

(set! (.-XMLHttpRequest js/globalThis)
      (or (.-XMLHttpRequest js/globalThis)
          (some-> js/require
                  (#(% "xmlhttprequest"))
                  .-XMLHttpRequest)))

(set! (.-cljsAjaxIntegrationBaseUrl js/globalThis)
      (or (some-> js/process .-env .-CLJS_AJAX_INTEGRATION_BASE_URL)
          (.-cljsAjaxIntegrationBaseUrl js/globalThis)))

(deftest integration-base-url-configured
  (is (string? (.-cljsAjaxIntegrationBaseUrl js/globalThis))))

(deftest xml-http-request-configured
  (is (some? (.-XMLHttpRequest js/globalThis))))
