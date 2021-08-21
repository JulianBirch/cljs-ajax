<<<<<<< HEAD
(defproject cljs-ajax "0.8.3"
=======
(defproject cljs-ajax "0.8.4"
>>>>>>> Bump to 0.8.4
  :min-lein-version "2.5.2" ;;; lower can't run tests in cljc
  :description "A simple Ajax library for ClojureScript"
  :url "https://github.com/JulianBirch/cljs-ajax"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[cheshire "5.10.0"]
                 [com.cognitect/transit-clj "1.0.324"]
                 [com.cognitect/transit-cljs "0.8.264"]
                 [org.apache.httpcomponents/httpasyncclient "4.1.4"]
                 [org.apache.httpcomponents/httpcore "4.4.14"]
                 [org.clojure/clojure "1.10.3"]
                 [org.clojure/clojurescript "1.10.844" :scope "provided"]
                 [lein-doo "0.1.11" :scope "test"]
                 [org.clojure/core.async "1.3.610" :scope "test"]]
  :plugins [[lein-doo "0.1.11"]
            [lein-cljsbuild "1.1.8"]
            [lein-ancient "1.0.0-RC3"]]
  :hooks [leiningen.cljsbuild]
  :global-vars { *warn-on-reflection* true }
  :clean-targets ^{:protect false} ["target" "target-int" "target-test" "target-test-node"]
  :profiles
  {:dev {:source-paths ["dev", "browser-test"]
         :test-paths ["test"]
         :dependencies [[clj-time "0.15.2"]
                        [compojure "1.6.2"]
                        [fogus/ring-edn "0.3.0"]
                        [lein-doo "0.1.11"]
                        [http-kit "2.5.3"]
                        [org.clojure/tools.namespace "1.1.0"]
                        [ring-server "0.5.0"]
                        [ring-transit "0.1.6"]
                        [ring/ring-defaults "0.3.2"]
                        [ring/ring-json "0.5.1"]]}}
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
                      :pretty-print true
                      :process-shim false}}
    :test-node {:source-paths ["src" "test"]
                :incremental? true
                :compiler {:output-to "target-test-node/unit-test.js"
                           :output-dir "target-test-node"
                           :target :nodejs
                      ;;; :source-map "target-test/unit-test.js.map"
                           :main ajax.test.runner
                           ;; :optimizations :whitespace
                           :pretty-print true
                           :process-shim false}}
    :int {:source-paths ["src" "browser-test"]
          :incremental? true
          :compiler {:output-to "target-int/integration.js"
                     :output-dir "target-int"
                     ;;; :source-map "target-int/integration.js.map"
                     :warnings {:single-segment-namespace false}
                     :optimizations :whitespace
                     :pretty-print true}}}}
  :aliases {"clj-test"  ["test"]
            "cljs-test" ["doo" "chrome-headless" "test" "once"]
            "cljs-node-test" ["doo" "node" "test-node" "once"]
            "run-tests" ["do" "clean," "clj-test," "cljs-test"]})
