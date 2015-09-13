(defproject cljs-ajax "0.5.0-SNAPSHOT"
  :description "A simple Ajax library for ClojureScript"
  :url "https://github.com/JulianBirch/cljs-ajax"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [com.cemerick/clojurescript.test "0.3.3"
                  :scope "test"]
                 [org.clojure/clojurescript "1.7.107"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                 [com.cognitect/transit-cljs "0.8.220"]
                 [net.colourcoding/poppea "0.2.0"]
                 [org.apache.httpcomponents/httpcore "4.4.1"]
                 [org.apache.httpcomponents/httpclient "4.5"]
                 [org.apache.httpcomponents/httpmime "4.5"]
                 [org.apache.httpcomponents/httpasyncclient "4.1"]
                 [cheshire "5.5.0"]]
  :plugins [[lein-cljsbuild "1.0.6"]
            [com.cemerick/clojurescript.test "0.3.3"]]
  :hooks [leiningen.cljsbuild]
  :global-vars { *warn-on-reflection* true }
  :profiles
  {:dev {:source-paths ["dev", "browser-test"]
         :dependencies [[ring-server "0.4.0"]
                        [fogus/ring-edn "0.3.0"]
                        [ring/ring-json "0.3.1"]
                        [ring-transit "0.1.3"]
                        [org.clojure/tools.namespace "0.2.10"]]}}
  :cljsbuild
  {:builds
   {:dev  {:source-paths ["src"]
           :compiler {:output-to "target/main.js"
                      :output-dir "target"
                      ; :source-map "target/main.js.map"
                      :optimizations :whitespace
                      :pretty-print true}}
    :test {:source-paths ["src" "test"]
           :incremental? true
           :compiler {:output-to "target-test/unit-test.js"
                      :output-dir "target-test"
                      ; :source-map "target-test/unit-test.js.map"
                      :optimizations :whitespace
                      :pretty-print true}}
    :int {:source-paths ["src" "browser-test"]
          :incremental? true
          :compiler {:output-to "target-int/integration.js"
                     :output-dir "target-int"
                     ; :source-map "target-int/integration.js.map"
                     :optimizations :whitespace
                     :pretty-print true}}}
   :test-commands {"unit-tests"
                   ["xvfb-run" "-a" "slimerjs" :runner
                    "window.literal_js_was_evaluated=true"
                    "target-test/unit-test.js"]}})
