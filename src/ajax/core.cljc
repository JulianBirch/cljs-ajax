(ns ajax.core
  "This is the documented API for cljs-ajax. The only functions not
   exposed here that are documented are the deprecated features."
  (:require [clojure.string :as str]
            [ajax.url :as url]
            [ajax.json :as json]
            [ajax.transit :as transit]
            [ajax.ring :as ring]
            [ajax.formats :as f]
            [ajax.util :as u]
            [ajax.interceptors :as i]
            [ajax.simple :as simple]
            [ajax.easy :as easy]
            [ajax.protocols :as pr]
            #?@ (:clj  [[ajax.macros :as m]
                        [ajax.apache]]
                 :cljs [[ajax.xhrio]
                        [ajax.xml-http-request]]))
  #? (:cljs
      (:require-macros [ajax.macros :as m])))

;;; NB As a matter of policy, this file shouldn't reference any
;;; google closure files. That functionality should be off in
;;; specific namespaces and exposed here in a platform indepdent
;;; way

;;; Ideally this would be true of all functionality, but it's
;;; an ongoing project.

(def to-interceptor i/to-interceptor)

(defn abort [this]
  "Call this on the result of `ajax-request` to cancel the request."
  (pr/-abort this))

;;; Standard Formats

(def json-request-format json/json-request-format)
(def json-response-format json/json-response-format)

(def transit-request-format transit/transit-request-format)
(def transit-response-format transit/transit-response-format)

(def ring-response-format ring/ring-response-format)

(def url-request-format url/url-request-format)

(def text-request-format f/text-request-format)
(def text-response-format f/text-response-format)
; There's no raw-request-format because it's handled by the DirectSubmission code
(def raw-response-format f/raw-response-format)

(def success? u/success?)

;;; Here's the entire "simple" API.

(def default-interceptors simple/default-interceptors)

(def ajax-request simple/ajax-request)

;;; "Easy" API beyond this point

(def default-formats easy/default-formats)

;;; NB detect-response-format with no parameters will pull
;;; in every non-deprecated format in the code and so is
;;; considered part of the "easy" API. However,
;;; using your own vector specification works with the
;;; simple API and allows you to control what formats you
;;; want to support.
(def detect-response-format easy/detect-response-format)

(m/easy-api GET)
(m/easy-api HEAD)
(m/easy-api POST)
(m/easy-api PUT)
(m/easy-api DELETE)
(m/easy-api OPTIONS)
(m/easy-api TRACE)
(m/easy-api PATCH)
(m/easy-api PURGE)
