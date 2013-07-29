(defproject cljs-ajax "0.1.4"
  :clojurescript? true
  :description "A simple Ajax library for ClojureScript"
  :url "https://github.com/yogthos/cljs-ajax"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.5.1"]]
  :plugins [[lein-cljsbuild "0.3.2"]]
  :hooks [leiningen.cljsbuild]
  :profiles
  {:dev {:dependencies
         [[com.cemerick/clojurescript.test "0.0.4"]]}}
  :cljsbuild
    {:builds
     {:dev  {:source-paths ["src"]
             :compiler {:output-to "target/main.js"
                        :optimizations :whitespace
                        :pretty-print true}}
      :test {:source-paths ["src" "test"]
             :incremental? true
             :compiler {:output-to "target/unit-test.js"
                        :optimizations :whitespace
                        :pretty-print true}}}
     :test-commands {"unit-tests"
                     ["runners/phantomjs.js" "target/unit-test.js"]}})
