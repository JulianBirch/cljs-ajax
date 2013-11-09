(defproject cljs-ajax "0.2.2"
  :clojurescript? true
  :description "A simple Ajax library for ClojureScript"
  :url "https://github.com/yogthos/cljs-ajax"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [com.cemerick/clojurescript.test "0.1.0"
                  :scope "test"]]
  :plugins [[lein-cljsbuild "0.3.2"]]
  :hooks [leiningen.cljsbuild]
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
                     ["phantomjs" :runner "target/unit-test.js"]}})
