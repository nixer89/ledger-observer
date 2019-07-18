(ns ledger-observer.visualization.data.animations
  (:require
   [active.clojure.cljs.record :as rec :include-macros true]
   [quick-type.core :as qt :include-macros true]
   [active.clojure.lens :as lens :include-macros true]))



(qt/def-type link-animation-t
  [(payment-animation [initialized? temperature animation-ref link-ref direction])])


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
