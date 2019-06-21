(ns ajax.test.runner
  (:require [doo.runner :refer-macros [doo-tests]]
            [ajax.test.core]
            [ajax.test.url]))

(doo-tests 'ajax.test.core
           'ajax.test.url)
