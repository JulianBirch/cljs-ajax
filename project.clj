(defproject cljs-ajax "0.5.8"
  :min-lein-version "2.5.2" ;;; lower can't run tests in cljc
  :description "A simple Ajax library for ClojureScript"
  :url "https://github.com/JulianBirch/cljs-ajax"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[cheshire "5.5.0"]
                 [com.cognitect/transit-clj "0.8.285"]
                 [com.cognitect/transit-cljs "0.8.239"]
                 [net.colourcoding/poppea "0.2.1"]
                 [org.apache.httpcomponents/httpasyncclient "4.1.1"]
                 [org.apache.httpcomponents/httpcore "4.4.4"]
                 [org.clojure/clojure "1.7.0"]
                 [org.clojure/clojurescript "1.7.228" :scope "provided"]
                 [lein-doo "0.1.6" :scope "test"]
                 [org.clojure/core.async "0.2.374" :scope "test"]]
  :plugins [[lein-doo "0.1.6"]
            [lein-cljsbuild "1.1.3"]]
  :hooks [leiningen.cljsbuild]
  :global-vars { *warn-on-reflection* true }
  :clean-targets ^{:protect false} ["target" "target-int" "target-test"]
  :profiles
  {:dev {:source-paths ["dev", "browser-test"]
         :test-paths ["test"]
         :dependencies [[org.clojure/core.async "0.2.374"]
                        [clj-time "0.11.0"]
                        [compojure "1.4.0"]
                        [fogus/ring-edn "0.3.0"]
                        [lein-doo "0.1.6"]
                        [http-kit "2.1.19"]
                        [org.clojure/tools.namespace "0.2.10"]
                        [ring-server "0.4.0"]
                        [ring-transit "0.1.4"]
                        [ring/ring-defaults "0.1.5"]
                        [ring/ring-json "0.4.0"]]}}
  :cljsbuild
  {:builds
   {:dev  {:source-paths ["src"]
           :compiler {:output-to "target/main.js"
                      :output-dir "target"
                      ;;; :source-map "target/main.js.map"
                      :optimizations :whitespace
                      :pretty-print true}}
    :test {:source-paths ["src" "test"]
           :incremental? true
           :compiler {:output-to "target-test/unit-test.js"
                      :output-dir "target-test"
                      ;;; :source-map "target-test/unit-test.js.map"
                      :main ajax.test.runner
                      :optimizations :whitespace
                      :pretty-print true}}
    :int {:source-paths ["src" "browser-test"]
          :incremental? true
          :compiler {:output-to "target-int/integration.js"
                     :output-dir "target-int"
                     ;;; :source-map "target-int/integration.js.map"
                     :warnings {:single-segment-namespace false}
                     :optimizations :whitespace
                     :pretty-print true}}}}
  :aliases {"clj-test"  ["test"]
            "cljs-test" ["doo" "phantom" "test" "once"]
            "run-tests" ["do" "clean," "clj-test," "cljs-test"]})
