(ns ledger-observer.visualization.animations
  (:require [active.clojure.record :as rec :include-macros true]))



(rec/define-record-type NoAnimation (make-no-animation*) no-animation? [])
(def no-animation (make-no-animation*))


(rec/define-record-type LinearAnimation
  (make-linear-animation state pace repeating?) linear-animation?
  [state linear-animation-state
   pace linear-animation-pace
   repeating? linear-animation-repeating?
   ])

(def linear make-linear-animation)


(defn step-linear-animation [animation]
  (let [next (+ (linear-animation-state animation) (linear-animation-pace animation))
        repeating? (linear-animation-repeating? animation)]
    (linear-animation-state animation
      (cond
        (and (> next 100) repeating?) 0
        (> next 100) 100
        :default next))))


(defn step [animation]
  (cond
    (linear-animation? animation)
    (step-linear-animation animation)

    (no-animation? animation)
    animation
    ))


(defn get-value [animation]
  (cond
    (linear-animation? animation)
    (linear-animation-state animation)

    (no-animation? animation)
    0
    ))
