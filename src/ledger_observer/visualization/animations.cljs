(ns ledger-observer.visualization.animations
  (:require [active.clojure.cljs.record :as rec :include-macros true]
            [active.clojure.sum-type :as st]
            [ledger-observer.visualization.data :as data]
            [ledger-observer.visualization.animations.data :as an]
            [ledger-observer.visualization.animations.payment :as payment]
            [quick-type.core :as qt :include-macros true]))



(qt/def-record no-animation [])
(def no-animation-inst (make-no-animation))


(qt/def-record linear-animation [state pace repeating?])
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



(defn normalize [t]
  (/ (- 100 t) 100))


(defn done? [animation]
  (neg? (payment/payment-temperature animation)))


(defn animate-1 [scene animation]
  (st/match an/link-animation-t animation
    payment/payment-t?
    (payment/animate-payment scene animation)))

(defn animate [animation-state ^js/Object scene]
  (let [link-animations (an/animation-state-link-animations animation-state)
        node-animations (an/animation-state-node-animations animation-state)]
    (an/put-link-animations
     animation-state
     (remove nil? (mapv (partial animate-1 scene) link-animations)))))


(defn add-payment-link-animation [animation-state graph-ref direction]
  (an/add-link-animation
   animation-state
   (payment/init-payment graph-ref direction)))

(def init an/init)
