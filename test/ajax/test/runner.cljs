(ns ajax.test.runner
  (:require [doo.runner :refer-macros [doo-tests]]
            [ajax.test.core]))

(doo-tests 'ajax.test.core)
