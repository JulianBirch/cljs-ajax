(defproject cljs-ajax "0.3.11"
  :clojurescript? true
  :description "A simple Ajax library for ClojureScript"
  :url "https://github.com/JulianBirch/cljs-ajax"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [com.cemerick/clojurescript.test "0.3.3"
                  :scope "test"]
                 [org.clojure/clojurescript "0.0-2850"]
                 [org.clojure/core.async "0.1.303.0-886421-alpha"]
                 [com.cognitect/transit-cljs "0.8.215"]
                 [net.colourcoding/poppea "0.2.0"]]
  :plugins [[lein-cljsbuild "1.0.0-SNAPSHOT"]
            [com.cemerick/clojurescript.test "0.3.3"]]
  :hooks [leiningen.cljsbuild]
  :profiles
  {:dev {:source-paths ["dev"]
         :dependencies [[ring-server "0.3.1"]
                        [fogus/ring-edn "0.2.0"]
                        [ring/ring-json "0.3.1"]
                        [ring-transit "0.1.3"]
                        [org.clojure/tools.namespace "0.2.8"]]}}
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
                     :source-map "target-int/integration.js.map"
                     :optimizations :whitespace
                     :pretty-print true}}}
   :test-commands {"unit-tests"
                   ["phantomjs" :runner "target-test/unit-test.js"]}})
