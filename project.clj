(defproject cljs-ajax "0.5.1"
  :min-lein-version "2.5.2" ;;; lower can't run tests in cljc
  :description "A simple Ajax library for ClojureScript"
  :url "https://github.com/JulianBirch/cljs-ajax"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[cheshire "5.5.0"]
                 [com.cemerick/clojurescript.test "0.3.3" :scope "test"]
                 [com.cognitect/transit-clj "0.8.281"]
                 [com.cognitect/transit-cljs "0.8.225"]
                 [net.colourcoding/poppea "0.2.1"]
                 [org.apache.httpcomponents/httpasyncclient "4.1"]
                 [org.apache.httpcomponents/httpcore "4.4.3"]
                 [org.clojure/clojure "1.7.0"]
                 [org.clojure/clojurescript "1.7.122"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]]
  :plugins [[lein-cljsbuild "1.0.6"]
            [com.cemerick/clojurescript.test "0.3.3"]]
  :hooks [leiningen.cljsbuild]
  :global-vars { *warn-on-reflection* true }
  :clean-targets ^{:protect false} ["target" "target-int" "target-test"]
  :profiles
  {:dev {:source-paths ["dev", "browser-test"]
         :test-paths ["test"]
         :dependencies [[clj-time "0.11.0"]
                        [compojure "1.4.0"]
                        [fogus/ring-edn "0.3.0"]
                        [http-kit "2.1.19"]
                        [org.clojure/tools.namespace "0.2.10"]
                        [ring-server "0.4.0"]
                        [ring-transit "0.1.3"]
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
                      :optimizations :whitespace
                      :pretty-print true}}
    :int {:source-paths ["src" "browser-test"]
          :incremental? true
          :compiler {:output-to "target-int/integration.js"
                     :output-dir "target-int"
                     ;;; :source-map "target-int/integration.js.map"
                     :warnings {:single-segment-namespace false}
                     :optimizations :whitespace
                     :pretty-print true}}}
   :test-commands {"unit-tests"
                   ["xvfb-run" "-a" "slimerjs" :runner
                    "window.literal_js_was_evaluated=true"
                    "target-test/unit-test.js"]}})
