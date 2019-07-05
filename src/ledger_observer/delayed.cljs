(ns ledger-observer.delayed
  (:require [active.clojure.record :as rec :include-macros true]
            [active.clojure.lens :as lens]))



(rec/define-record-type DelayedAction
  (make-delayed-action callback delay) delayed-action?
  [callback delayed-action-callback
   delay delayed-action-delay])


(defn handle [action]
  (let [callback  (delayed-action-callback action)
        delay        (delayed-action-delay action)]
    (js/window.setTimeout callback delay)))

