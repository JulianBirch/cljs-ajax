(ns ajax.test.url
  (:require
   #? (:cljs [cljs.test]
       :clj [clojure.test :refer :all])
    [ajax.url :refer [params-to-str]])
   #? (:cljs (:require-macros [cljs.test :refer [deftest testing is]])))

(deftest simple-params-to-str
  (doseq [vec-strategy [:java :rails :indexed]]
    (is (= "a=0" (params-to-str vec-strategy {:a 0})))
    (is (= "a=b" (params-to-str vec-strategy {:a :b})))
    (is (= "foo/bar=john%2Fdoe" (params-to-str vec-strategy {:foo/bar :john/doe})))
    (is (= "c[d]=3&c[e]=4" (params-to-str vec-strategy {:c {:d 3 :e 4}})))
    (is (= "d=5" (params-to-str vec-strategy {"d" 5})))
    (is (= "a=b%2Bc" (params-to-str vec-strategy {:a "b+c"})))))

(deftest params-to-str-indexed
  (is (= "b[0]=1&b[1]=2" (params-to-str :indexed {:b [1 2]})))
  (is (= "a=0&b[0]=1&b[1]=2&c[d]=3&c[e]=4&f=5&g[0]=1"
         (params-to-str :indexed {:a 0
                                  :b [1 2]
                                  :c {:d 3 :e 4}
                                  "f" 5
                                  :g #{1}})))
  (is (= "foo/bar=10&john/doe[0]=20&john/doe[1]=30"
         (params-to-str :indexed {:foo/bar 10 :john/doe [20 30]}))))

(deftest params-to-str-java
  (is (= "b=1&b=2" (params-to-str :java {:b [1 2]})))
  (is (= "a=0&b=1&b=2&c[d]=3&c[e]=4&f=5&g=1"
         (params-to-str :java {:a 0
                               :b [1 2]
                               :c {:d 3 :e 4}
                               "f" 5
                               :g #{1}})))
  (is (= "foo/bar=10&john/doe=20&john/doe=30"
         (params-to-str :java {:foo/bar 10 :john/doe [20 30]}))))

(deftest params-to-str-rails
  (is (= "b[]=1&b[]=2" (params-to-str :rails {:b [1 2]})))
  (is (= "a=0&b[]=1&b[]=2&c[d]=3&c[e]=4&f=5&g[]=1"
         (params-to-str :rails {:a 0
                                :b [1 2]
                                :c {:d 3 :e 4}
                                "f" 5
                                :g #{1}})))
  (is (= "foo/bar=10&john/doe[]=20&john/doe[]=30"
         (params-to-str :rails {:foo/bar 10 :john/doe [20 30]}))))
