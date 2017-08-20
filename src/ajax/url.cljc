(ns ajax.url

"At first blush, it's pretty bizarre that an entire file is devoted to one  
 function, namely params-to-str, which just takes a map and converts it to
 a querystring. However, it turns out that people sometimes want to encode
 fairly complex maps and the behaviour in the presence of vectors/arrays
 is controversial.

 The basic question is: what {:a [1 2]} be encoded as? The correct answer
 as far as ring is concerned is a=1&a=2. This is also true of most Java
 implementations, ASP.NET, Angular, Haskell and even old-school ASP. This 
 is called vec-strategy :java in the code. Rails and PHP, however, 
 prefer a[]=1&a[]=2, which has an obvious implementation in a dynamic 
 language. This is called vec-strategy :rails. Finally, there's what 
 cljs-ajax (mistakenly) did between versions 0.4.0 and 0.6.x: 
 a[0]=1&a[2]=1, which is called vec-strategy :indexed. This is retained 
 mostly for people who need to keep compatibility with the previous behaviour.

 None of these are the \"correct answer\": the HTTP standards are
 silent on the subject, so you're left with what your server accepts, and
 different servers have different conventions. Worse, if you send the
 wrong convention it gets misinterpreted. Send strategy :rails to a :java
 server and you get { \"a[]\" [1 2]}. Worse, send strategy :java to a :rails
 server and you get { \"a\" 2 }. So it's important to know what your server's
 convention is.

 The situation for maps is simpler, pretty much everyone encodes
 {:a {:b 1}} as \"a[b]=1\". That is, assuming they process it at all.
 The HTTP spec is similarly silent on this and your server may get your
 language's equivalent of { \"a[b]\" 1 }. In cases like this, you have two
 choices 1) write your own server-side decoder or 2) don't ever send
 nested maps.

 If you ever wanted to consider exactly how bad the effect of supporting
 a wide range of use cases, consider that this was the original code:

 (defn params-to-str [params]
    (if params
        (-> params      
            clj->js
            structs/Map.
            query-data/createFromMap
            .toString)))

 This code remains completely correct for at least 90% of actual users
 of cljs-ajax. Now we have ~50 SLOCs achieving much the same result.
"

#?@ (:clj  ((:require 
             [poppea :as p]
             [ajax.util :as u]
             [clojure.string :as str]))
     :cljs ((:require 
             [clojure.string :as str]
             [ajax.util :as u])
             (:require-macros [poppea :as p]))))


(defn- key-encode [key]
  (if (keyword? key) (name key) key))

(def ^:private value-encode ; why doesn't def- exist?
    #? (:clj (fn value-encode [u] (java.net.URLEncoder/encode (str u) "UTF-8"))
        :cljs js/encodeURIComponent))

(defn- key-value-pair-to-str [[k v]] 
       (str (key-encode k) "=" (value-encode v)))

(p/defn-curried- vec-key-transform-fn [vec-key-encode k v]
    [(vec-key-encode k) v])

(defn- to-vec-key-transform [vec-strategy]
    (let [vec-key-encode (case (or vec-strategy :java)
                               :java (fn [k] nil) ; no subscript
                               :rails (fn [k] "") ; [] subscript
                               :indexed identity)] ; [1] subscript
        (vec-key-transform-fn vec-key-encode)))


(p/defn-curried- param-to-key-value-pairs [vec-key-transform prefix [key value]]
    "Takes a parameter and turns it into a sequence of key-value pairs suitable
     for passing to `key-value-pair-to-str`. Since we can have nested maps and
     vectors, we need a vec-key-transform function and the current query key
     prefix as well as the key and value to be analysed. Ultimately, this 
     function walks the structure and flattens it." 
    (let [k1 (key-encode key)
          new-key (if prefix 
                      (if key 
                          (str prefix "[" k1 "]")
                          prefix)
                      k1)
          recurse (param-to-key-value-pairs vec-key-transform new-key)]
        (cond 
            (string? value) ; string is sequential so we have to handle it separately
            [[new-key value]]  ; ("a" 1) should be ["a" 1]

            (keyword? value)
            [[new-key (name value)]] ; (:a 1) should be ["a" 1]

            (map? value)
            (mapcat recurse (seq value)) ; {:b {:a 1}} should be ["b[a]" 1]

            (sequential? value) ; behaviour depends on vec-key-transform
            (->> (seq value)
                 (map-indexed vec-key-transform)
                 (mapcat recurse))

            :else [[new-key value]])))

(p/defn-curried params-to-str [vec-strategy params]
    "vec-strategy is one of :rails (a[]=3&a[]=4)
                            :java (a=3&a=4) (this is the correct behaviour and the default)
                            :indexed (a[3]=1&a[4]=1)
     params is an arbitrary clojure map"
    (->> [nil params]
         (param-to-key-value-pairs (to-vec-key-transform vec-strategy) nil)
         (map key-value-pair-to-str)
         (str/join "&")))

(defn url-request-format
  "The request format for simple POST and GET."
  ([] (url-request-format {})) 
  ([{:keys [vec-strategy]}]
   {:write (u/to-utf8-writer (params-to-str vec-strategy))
    :content-type "application/x-www-form-urlencoded; charset=utf-8"}))
