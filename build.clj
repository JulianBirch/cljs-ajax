(ns build
  (:require [clojure.tools.build.api :as b]))

(def project {:lib 'cljs-ajax/cljs-ajax
              :version "0.8.4"
              :description "A simple Ajax library for ClojureScript"
              :url "https://github.com/JulianBirch/cljs-ajax"
              :license {:name "Eclipse Public License"
                        :url "http://www.eclipse.org/legal/epl-v10.html"}})

(def lib (:lib project))
(def version (:version project))
(def class-dir "target/classes")
(def basis (b/create-basis {:project "deps.edn"}))
(def jar-file (format "target/%s-%s.jar" (name lib) version))

(defn clean [_]
  (b/delete {:path "target"})
  (b/delete {:path "target-test"})
  (b/delete {:path "target-test-node"})
  (b/delete {:path "target-int"}))

(defn jar [_]
  (clean nil)
  (b/copy-dir {:src-dirs ["src"]
               :target-dir class-dir})
  (b/write-pom {:class-dir class-dir
                :lib lib
                :version version
                :basis basis
                :pom-data [[:description (:description project)]
                           [:url (:url project)]
                           [:licenses
                            [:license
                             [:name (get-in project [:license :name])]
                             [:url (get-in project [:license :url])]]]]
                :src-dirs ["src"]})
  (b/jar {:class-dir class-dir
          :jar-file jar-file}))

(defn install [_]
  (jar nil)
  (b/install {:class-dir class-dir
              :lib lib
              :version version
              :basis basis
              :jar-file jar-file}))
