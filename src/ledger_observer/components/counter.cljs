(ns ledger-observer.components.counter
  (:require [active.clojure.record :as rec :include-macros true]
            [reacl2.core :as reacl :include-macros true]
            [reacl2.dom :as dom :include-macros true]))


(defn round-1 [num]
  (/ (Math/round (* 10 num)) 10))

(rec/define-record-type TickMessage
  (make-tick-message) tick-message?
  [])

(rec/define-record-type CounterState
  (make-counter-state tx seconds) counter-state?
  [tx counter-state-tx
   seconds counter-state-seconds])

(def minute 60)
(def hour (* 60 60))

(def minute-threshold (* 2 minute))
(def hour-threshold (* 2 hour))

(defn secs->unit [secs]
  (cond
    (> secs hour-threshold)
    [(Math/round (/ secs hour)) "H"]

    (> secs minute-threshold)
    [(Math/round (/ secs minute)) "MIN"]

    :default
    [secs "SEC"]))

(defn tx->unit [tx]
  (if (>= tx 1000)
    (str (/ (Math/round (/ tx 100)) 10) " k")
    tx))


(reacl/defclass counter this state [parent]

  render
  (let [secs (counter-state-seconds state)
        [time unit] (secs->unit secs)
        tx   (counter-state-tx state)
        tx-unit (tx->unit tx)]
    (dom/div
      {:class "counter-components fade-in"}
      (dom/span {:style {:position :relative}}
        (dom/div {:class "counter-component"}
          (dom/div {:class "counter-number"}
            time)
          (dom/div {:class "counter-text"}
            unit))
        (dom/div {:class "counter-component"}
          (dom/div {:class "counter-number"} tx-unit)
          (dom/div {:class "counter-text"}
            "#TX"))
        (dom/div {:class "counter-component"}
          (dom/div {:class "counter-number"}
            (if (or
                  (not secs)
                  (not tx)
                  (zero? secs))
              0
              (round-1 (/ tx secs))))
          (dom/div
            {:class "counter-text"}
            "tx/s"))))))
