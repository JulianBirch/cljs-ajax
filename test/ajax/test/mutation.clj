(ns ajax.test.mutation
  "Mutation testing for the Apache HttpClient 5 implementation.

   Each operator applies one defect to `src/ajax/apache.clj`, the namespace
   is reloaded from the mutated source, and the property suite is run
   against it. A mutant the suite fails to notice is a survivor: it marks
   behaviour no test asserts.

   Run with `clojure -X:mutation`."
  (:require [ajax.test.apache-property :as property]
            [clojure.java.io :as io]
            [clojure.string :as string]
            [clojure.test :as t])
  (:import [java.io PushbackReader StringWriter]))

(def ^:private source-path "src/ajax/apache.clj")

(def ^:private suite ['ajax.test.apache-property])

(def ^:private mutation-trials
  "Trials per property while mutation testing. The whole suite runs once
   per mutant, so this trades breadth of sampling for the number of
   defects we can try."
  10)

;;; Metadata preserving walk. `clojure.walk` drops metadata on
;;; collections, which would strip the type hints on argument vectors.

(defn- walk-form [f form]
  (f (cond
       (list? form) (with-meta (apply list (map (partial walk-form f) form))
                      (meta form))
       (instance? clojure.lang.IMapEntry form)
       (clojure.lang.MapEntry/create (walk-form f (key form))
                                     (walk-form f (val form)))

       (seq? form) (with-meta (doall (map (partial walk-form f) form))
                     (meta form))
       (record? form) form
       (coll? form) (with-meta (into (empty form) (map (partial walk-form f) form))
                      (meta form))
       :else form)))

(defn- occurrences [forms match?]
  (let [found (atom 0)]
    (walk-form (fn [form] (when (match? form) (swap! found inc)) form) forms)
    @found))

(defn- mutate [forms match? replace-fn n]
  (let [seen (atom -1)]
    (walk-form (fn [form]
                 (if (and (match? form) (= n (swap! seen inc)))
                   (replace-fn form)
                   form))
               forms)))

;;; Operators

(defn- literal [x] (fn [form] (= form x)))

(defn- call-to [sym]
  (fn [form] (and (seq? form) (= sym (first form)))))

(defn- always [x] (fn [_] x))

(def operators
  [{:id :positive-timeout-check
    :doc "pos? -> neg?: treats every positive timeout as no timeout"
    :match (literal 'pos?) :replace (always 'neg?)}
   {:id :timeout-fallback
    :doc "or -> and: drops the socket-timeout/timeout fallback and the
          timeout exception union"
    :match (literal 'or) :replace (always 'and)}
   {:id :no-timeout-constant
    :doc "Timeout/DISABLED -> 1ms: turns 'no timeout' into an instant one"
    :match (literal 'Timeout/DISABLED)
    :replace (always '(Timeout/ofMilliseconds 1))}
   {:id :connect-timeout-setter
    :doc ".setConnectTimeout -> .setSocketTimeout: leaves the connect
          timeout at the HttpClient default"
    :match (literal '.setConnectTimeout) :replace (always '.setSocketTimeout)}
   {:id :nil-safe-body
    :doc "some-> -> ->: stops guarding against an absent response body"
    :match (literal 'some->) :replace (always '->)}
   {:id :error-status
    :doc "-1 -> 1: loses the negative status that marks timeouts and aborts"
    :match (literal -1) :replace (always 1)}
   {:id :aborted-flag
    :doc "flips :was-aborted on the synthesised responses"
    :match #(and (map? %) (contains? % :was-aborted))
    :replace #(update % :was-aborted not)}
   {:id :strict-cookie-spec
    :doc "STRICT -> RELAXED: silently downgrades :standard-strict"
    :match (literal 'StandardCookieSpec/STRICT)
    :replace (always 'StandardCookieSpec/RELAXED)}
   {:id :ignore-cookie-spec
    :doc "IGNORE -> RELAXED: silently enables cookies for :none"
    :match (literal 'StandardCookieSpec/IGNORE)
    :replace (always 'StandardCookieSpec/RELAXED)}
   {:id :body-charset
    :doc "UTF-8 -> US-ASCII: mangles non-ascii string bodies"
    :match (literal "UTF-8") :replace (always "US-ASCII")}
   {:id :status-accessor
    :doc ".getCode -> .getVersion: reports something other than the status"
    :match (literal '.getCode) :replace (always '.getVersion)}
   {:id :header-name-accessor
    :doc ".getName -> .getValue: keys the header map by value"
    :match (literal '.getName) :replace (always '.getValue)}
   {:id :request-method
    :doc "sends every request as a GET"
    :match (call-to 'AsyncRequestBuilder/create)
    :replace (always '(AsyncRequestBuilder/create "GET"))}
   {:id :request-uri
    :doc "never sets the request URI"
    :match (call-to '.setUri) :replace (always nil)}
   {:id :request-body
    :doc "never attaches the request entity"
    :match (call-to '.setEntity) :replace (always nil)}
   {:id :request-headers
    :doc "never adds the request headers"
    :match (call-to '.addHeader) :replace (always nil)}])

;;; Running

(defn- read-forms [path]
  (with-open [reader (PushbackReader. (io/reader path))]
    (doall (take-while #(not= ::eof %)
                       (repeatedly #(read {:eof ::eof} reader))))))

(defn- load-forms! [forms]
  (binding [*ns* *ns*]
    (doseq [form forms]
      (eval form))))

(defn- restore! []
  (require 'ajax.apache :reload))

(defn- suite-passes?
  "Runs the property suite quietly. Any failure, error or thrown
   exception counts as the mutant being killed."
  []
  (let [out (StringWriter.)]
    (try
      (binding [t/*test-out* out
                *out* out
                *err* out
                property/*trials* mutation-trials]
        (let [{:keys [fail error]} (apply t/run-tests suite)]
          (zero? (+ fail error))))
      (catch Throwable _ false))))

(defn- mutants [forms]
  (for [{:keys [id doc match replace]} operators
        n (range (occurrences forms match))]
    {:operator id
     :doc doc
     :occurrence n
     :forms (mutate forms match replace n)}))

(defn- kills? [{:keys [forms]}]
  (try
    (load-forms! forms)
    (not (suite-passes?))
    (catch Throwable _
      ;; A mutant that will not compile is not a useful signal, but it is
      ;; not a survivor either.
      true)
    (finally (restore!))))

(defn- report [{:keys [total killed survivors]}]
  (println)
  (println (format "Mutation score: %d/%d killed (%.0f%%)"
                   killed total (* 100.0 (/ (double killed) (max total 1)))))
  (when (seq survivors)
    (println)
    (println "Survivors, behaviour no test asserts:")
    (doseq [{:keys [operator occurrence doc]} survivors]
      (println (format "  %s #%d  %s"
                       (name operator) occurrence
                       (string/replace (str doc) #"\s+" " "))))))

(defn run-mutation-tests
  "Entry point for `clojure -X:mutation`. Throws if any mutant survives."
  [_]
  (require 'ajax.apache)
  (let [forms (read-forms source-path)
        candidates (mutants forms)]
    (println (format "Running %d mutants of %s against %s"
                     (count candidates) source-path (string/join ", " suite)))
    (when-not (suite-passes?)
      (throw (ex-info "The property suite fails before any mutation" {})))
    (let [results (doall (map (fn [mutant]
                                (print ".") (flush)
                                (assoc mutant :killed (kills? mutant)))
                              candidates))
          survivors (remove :killed results)
          summary {:total (count results)
                   :killed (count (filter :killed results))
                   :survivors (map #(select-keys % [:operator :occurrence :doc])
                                   survivors)}]
      (report summary)
      (when (seq survivors)
        (throw (ex-info "Mutants survived the test suite"
                        (select-keys summary [:total :killed :survivors]))))
      summary)))
