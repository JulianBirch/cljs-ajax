(defproject cljs-ajax "0.1.0"
  :description "A simple Ajax library for ClojureScript"
  :dependencies [[org.clojure/clojure "1.5.1"]]
  :plugins [[lein-cljsbuild "0.3.0"]
            [lein-clojars "0.9.1"]]
  :hooks [leiningen.cljsbuild]
  :cljsbuild {:builds [{:source-paths ["src-cljs"]
                        :jar true
                        :compiler {:libs ["goog/net/xhrio.js" "goog/uri/uri.js"]
                                   :pretty-print true
                                   :output-dir ".cljsbuild/ajax"
                                   :output-to "public/ajax.js"}}
                       {:source-paths ["test-cljs"]
                        :compiler  {:libs ["goog/net/xhrio.js" "goog/uri/uri.js"]
                                    :pretty-print true
                                    :optimizations :none
                                    :output-dir "public/build_no_opt"
                                    :output-to "public/test_no_opt.js"}}
                       {:source-paths ["test-cljs"]
                        :compiler  {:libs ["goog/net/xhrio.js" "goog/uri/uri.js"]
                                    :optimizations :whitespace
                                    :pretty-print true
                                    :output-dir ".cljsbuild/whitespace"
                                    :output-to "public/test_whitespace.js"}}
                       {:source-paths ["test-cljs"]
                        :compiler  {:libs ["goog/net/xhrio.js" "goog/uri/uri.js"]
                                    :optimizations :simple
                                    :pretty-print true
                                    :output-dir ".cljsbuild/simple"
                                    :output-to "public/test_simple.js"}}
                       {:source-paths ["test-cljs"]
                        :compiler  {:libs ["goog/net/xhrio.js" "goog/uri/uri.js"]
                                    :optimizations :advanced
                                    :pretty-print true
                                    :output-dir ".cljsbuild/advanced"
                                    :output-to "public/test_advanced.js"}}]}
  :repositories {"sonatype-staging"
                 "https://oss.sonatype.org/content/groups/staging/"})
