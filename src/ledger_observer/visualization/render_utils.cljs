(ns ledger-observer.visualization.render-utils
  (:require
   [active.clojure.record :as rec :include-macros true]
   [active.clojure.lens :as lens :include-macros true]
   [ledger-observer.visualization.data :as data]
   [ledger-observer.visualization.app-interaction :as app-interaction]))



(def render-state-marked-tx-state-lens
  (lens/>>
    data/render-state-interaction-state
    app-interaction/interaction-state-tx-marked))

(def render-state-node-marked-by-app-lens
  (lens/>>
    data/render-state-interaction-state
    app-interaction/interaction-state-node-marked))


(def render-state-node-marked-lens
  (lens/>>
   data/render-state-interaction-state
   app-interaction/interaction-state-node-marked))

(def render-state-previous-node-marked-lens
  (lens/>>
    data/render-state-interaction-state
    app-interaction/interaction-state-previous-node-marked))

(def render-state-node-selected-lens
  (lens/>>
    data/render-state-interaction-state
    app-interaction/interaction-state-node-selected))

(def render-state-previous-node-selected-lens
  (lens/>>
    data/render-state-interaction-state
    app-interaction/interaction-state-previous-node-selected))

(def render-state-from-app-mailbox-lens
  (lens/>>
    data/render-state-interaction-state
    app-interaction/interaction-state-from-app-mailbox))

(def render-state-tx-stats-state-lens
  (lens/>>
    data/render-state-interaction-state
    app-interaction/interaction-state-tx-stats))


(def tx-counter-lens
  (lens/>>
    render-state-tx-stats-state-lens
    app-interaction/tx-stats-state-counter))


(def tx-stats-updated-lens
  (lens/>>
    render-state-tx-stats-state-lens
    app-interaction/tx-stats-state-last-updated))

(def render-state-interaction-needs-update-lens
  (lens/>>
    data/render-state-interaction-state
    app-interaction/interaction-state-needs-update?)
  )


(def max-count 80)
(defn inc-count [v] (min max-count (+ 5 v)))
(defn dec-count [v] (max 0 (dec v)))


(defn increase-frame-counter [v]
  (if (< 10000 v) 0 (inc v)))

