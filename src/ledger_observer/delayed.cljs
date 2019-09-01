(ns ledger-observer.delayed
  (:require [active.clojure.cljs.record :as rec :include-macros true]
            [quick-type.core :as qt :include-macros true]
            [active.clojure.lens :as lens]))


(qt/def-record periodic-action [callback period handler])
(qt/def-record delayed-action [callback delay])

(defn handle-delayed [action]
  (let [callback  (delayed-action-callback action)
        delay        (delayed-action-delay action)]
    (js/window.setTimeout callback delay)))


(defn handle-periodic [action handler]
  (let [callback (periodic-action-callback action)]
    (callback)))

