(ns ledger-observer.visualization.animations.data
  (:require
   [active.clojure.cljs.record :as rec :include-macros true]
   [quick-type.core :as qt :include-macros true]
   [ledger-observer.visualization.animations.payment :as payment]
   [active.clojure.lens :as lens :include-macros true]))


(qt/def-type link-animation-t
  [payment/payment-t])


(qt/def-record animation-state
  [link-animations
   node-animations])


(defn add-link-animation [viz-state animation]
  (lens/overhaul
    viz-state
    animation-state-link-animations
    (fn [old] (cons animation old))))

(defn remove-link-animation [viz-state animation]
  (lens/overhaul
    viz-state
    animation-state-link-animations
    (fn [old] (remove #{animation} old))))

(defn put-link-animations [viz-state animations]
  (animation-state-link-animations viz-state animations))

(defn clear-link-animations [viz-state]
  (put-link-animations viz-state []))

(defn add-node-animation [viz-state animation]
  (lens/overhaul
    viz-state
    animation-state-node-animations
    (fn [old] (cons animation old))))

(defn remove-node-animation [viz-state animation]
  (lens/overhaul
    viz-state
    animation-state-node-animations
    (fn [old] (remove #{animation} old))))


(defn put-node-animations [viz-state animations]
  (animation-state-node-animations viz-state animations))

(defn clear-node-animations [viz-state]
  (put-node-animations viz-state []))


(def init (make-animation-state [] []))
