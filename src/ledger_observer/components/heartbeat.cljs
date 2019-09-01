(ns ledger-observer.components.heartbeat
  (:require [active.clojure.cljs.record :as rec :include-macros true]
            [active.clojure.sum-type :as st :include-macros true]
            [active.clojure.lens :as lens]
            [reacl2.core :as reacl :include-macros true]
            [quick-type.core :as qt :include-macros true]
            [reacl2.dom :as dom :include-macros true]))


(qt/def-type state-t
  [(empty-state)
   (heartbeat-state [counter ledger-number])])


(def the-empty-state (make-empty-state))
(def initial-state the-empty-state)

(defn format [num]
  (if (>= num 1000)
    (str (/ (Math/round (/ num 100)) 10) " k")
    num))

(reacl/defclass heartbeat this [state]

  render
  (dom/div
    (dom/div {:class "counter-component"
              }
      (dom/div {:class "counter-number"}
        (format (counter state)))
      (dom/keyed (counter state)
       (dom/div
         {:class "counter-text"
          :style {:position "relative"}}
         (dom/div {:class "hide-under-pulse"} "#Ledgers")
         (dom/div {:class "pulse"})))
      )))



(defn next-heartbeat-state [state ledger-number]
  (st/match state-t state
    empty-state?
    (make-heartbeat-state 1 ledger-number)

    (make-heartbeat-state count current-ledger-number)
    (cond
      (= current-ledger-number ledger-number)
      state

      (= (inc current-ledger-number) ledger-number)
      (make-heartbeat-state (inc count) ledger-number)

      :default
      (do
        (println (str "Unexpected ledger number. Old: " current-ledger-number ", New: " ledger-number))
        (make-heartbeat-state count ledger-number)))))


(defn counter [state]
  (st/match state-t state
    empty-state? 0

    (make-heartbeat-state counter _)
    counter
    )
  )
