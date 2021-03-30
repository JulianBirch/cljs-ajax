(ns ajax.formats
    "This file contains the base formats: raw, text and detect.
     url, json and transit are found in their own files."
    (:require [ajax.interceptors :as i]
              [ajax.util :as u]
              [ajax.protocols :as pr])
    #? (:clj (:import [java.io InputStream]
                      [java.util Scanner])))

(defn raw-response-format
  "This will literally return whatever the underlying implementation
   considers has been sent. Obviously, this is highly implementation
   dependent, gives different results depending on your platform but
   is nonetheless really rather useful."
  ([] (i/map->ResponseFormat {:read pr/-body
                            :description #? (:cljs "raw text"
                                             :clj "raw binary")
                            :content-type ["*/*"]}))
  ([_] (raw-response-format)))

(defn text-request-format []
  {:write (u/to-utf8-writer identity)
   :content-type "text/plain; charset=utf-8"})

#? (:clj
    ;;; http://stackoverflow.com/questions/309424/read-convert-an-inputstream-to-a-string
    (do
      (defn response-to-string
        "Interprets the response as text (a string). Isn't likely 
         to give you a good outcome if the response wasn't text."
        [response]
        (let [s (doto (Scanner. ^InputStream (pr/-body response)
                                "UTF-8")
                  (.useDelimiter "\\A"))]
          (if (.hasNext s) (.next s) "")))

      (defn text-response-format
        ([] (i/map->ResponseFormat {:read response-to-string
                                  :description "raw text"
                                  :content-type ["*/*"]}))
        ([_] (text-response-format))))
    :cljs
    ;;; For CLJS, there's no distinction betweeen raw and text
    ;;; format, because it's a string in the API anyway.
    (def text-response-format raw-response-format))

;;; Detect Response Format

(defn get-format 
  "Converts one of a number of types to a response format.
   Note that it processes `[text format]` the same as `format`,
   which makes it easier to work with detection vectors such as
   `default-formats`.
   
   It also supports providing formats as raw functions. I don't 
   know if anyone has ever used this."
  [request format-entry]
  (cond
   (or (nil? format-entry) (map? format-entry))
   format-entry

   (vector? format-entry)
   (get-format request (second format-entry))

   ;;; Must be a format generating function
   :else (format-entry request)))

(defn get-accept-entries [request format-entry]
  (let [fe (if (vector? format-entry)
             (first format-entry)
             (:content-type (get-format request format-entry)))]
    (cond (nil? fe) ["*/*"]
          (string? fe) [fe]
          :else fe)))

(defn content-type-matches
  [^String content-type ^String accept]
  (or (= accept "*/*")
      (>= (.indexOf content-type accept) 0)))

(defn detect-content-type
  [content-type request format-entry]
  (let [accept (get-accept-entries request format-entry)]
    (some #(content-type-matches content-type %) accept)))

(defn get-default-format
  [response {:keys [response-format] :as request}]
  (let [content-type (u/get-content-type response)]
    (letfn [(accepted-format?
              [format-entry]
              (detect-content-type content-type request format-entry))]
      (->> response-format
           (filter accepted-format?)
           first
           (get-format request)))))

(defn detect-response-format-read
  [request]
  (fn detect-response-format [response]
    (let [format (get-default-format response request)]
      ((:read format) response))))

(defn accept-header [{:keys [response-format] :as request}]
  (let [formats (if (vector? response-format) response-format [response-format])]
    (mapcat #(get-accept-entries request %) formats)))

(defn detect-response-format 
   "NB This version of the response format doesn't have a zero
     arity version. This is because it would involve pulling
     in every dependency. Instead, core.cljc adds it in."
  [opts]
    (let [accept (accept-header opts)]
      (i/map->ResponseFormat
      {:read (detect-response-format-read opts)
        :format (str "(from " accept ")")
        :content-type accept})))
