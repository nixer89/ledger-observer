(ns ledger-observer.visualization.app-interaction
  (:require [ledger-observer.visualization.data :as data]
            [ledger-observer.mailbox :as mailbox]
            [quick-type.core :as qt :include-macros true]
            [active.clojure.sum-type :as st :include-macros true]
            [active.clojure.lens :as lens]
            [ledger-observer.visualization.animations :as animation]
            [active.clojure.cljs.record :as rec :include-macros true]))

(rec/define-record-type Nop (make-nop) nop? [])
(def nop (make-nop))

(rec/define-record-type ClickAddressMessage
  (make-click-address-message address) click-address-message?
  [address click-address-message-address])

(rec/define-record-type UnclickAddressMessage
  (make-unclick-address-message) unclick-address-message?
  [])

(rec/define-record-type HoveredAddressMessage
  (make-hovered-address-message address) hovered-address-message?
  [address hovered-address-message-address])

(rec/define-record-type UnhoveredAddressMessage
  (make-unhovered-address-message) unhovered-address-message? [])

(rec/define-record-type AppMarkTransactionMessage
  (make-app-mark-transaction-message from targets) app-mark-transaction-message?
  [from app-mark-transaction-message-from
   targets app-mark-transaction-message-targets])

(rec/define-record-type AppUnmarkTransactionMessage
  (make-app-unmark-transaction-message from targets) app-unmark-transaction-message?
  [from app-unmark-transaction-message-from
   targets app-unmark-transaction-message-targets])



(rec/define-record-type NoNodeMarked (make-no-node-marked) no-node-marked? [])
(def no-node-marked (make-no-node-marked))

(rec/define-record-type NodeMarkedByMouseHover
  (make-node-marked-by-mouse-hover index neighbours neighbour-links animation) node-marked-by-mouse-hover?
  [index node-marked-by-mouse-hover-index
   neighbours node-marked-by-mouse-hover-neighbours
   neighbour-links node-marked-by-mouse-hover-neighbour-links
   animation node-marked-by-mouse-hover-animation])

(rec/define-record-type NodeMarkedByApp
  (make-node-marked-by-app index animation ) node-marked-by-app?
  [index node-marked-by-app-index
   animation node-marked-by-app-animation])

(defn marked-node-index [m]
  (cond
    (node-marked-by-mouse-hover? m)
    (node-marked-by-mouse-hover-index m)

    (node-marked-by-app? m)
    (node-marked-by-app-index m)))


(defn marked-node-animation [m]
  (cond
    (node-marked-by-mouse-hover? m)
    (node-marked-by-mouse-hover-animation m)

    (node-marked-by-app? m)
    (node-marked-by-app-animation m)))



(rec/define-record-type NoTxMarked (make-no-tx-marked) no-tx-marked? [])
(def no-tx-marked (make-no-tx-marked))

(rec/define-record-type TxMarked
  (make-tx-marked from targets) tx-marked?
  [from tx-marked-from
   targets tx-marked-targets])



(qt/def-type node-selected-t
  [(no-node-selected [])
   (node-selected-by-mouse-click [index animation])
   (node-selected-by-app [index animation])])

(def no-node-selected-inst (make-no-node-selected))

(defn selected-node-index [s]
  (st/match node-selected-t s
    (make-node-selected-by-mouse-click index _) index
    (make-node-selected-by-app index _) index
    no-node-selected? nil))

(defn selected-node-animation [m]
  (st/match node-selected-t m
    (make-node-selected-by-mouse-click _ animation) animation
    (make-node-selected-by-app _ animation) animation
    no-node-selected? nil))


(rec/define-record-type TxStatsState
  (make-tx-stats counter last-updated) tx-stats-state?
  [counter tx-stats-state-counter
   last-updated tx-stats-state-last-updated])

(rec/define-record-type InteractionState
  (make-interaction-state from-app-mailbox node-marked previous-node-marked
    node-selected previous-node-selected tx-marked tx-stats needs-update?
    auto-rotation?)
  interaction-state?
  [from-app-mailbox interaction-state-from-app-mailbox
   node-marked interaction-state-node-marked
   previous-node-marked interaction-state-previous-node-marked
   node-selected interaction-state-node-selected
   previous-node-selected interaction-state-previous-node-selected
   tx-marked interaction-state-tx-marked
   tx-stats interaction-state-tx-stats
   needs-update? interaction-state-needs-update?
   auto-rotation? interaction-state-auto-rotation?])


(defn push [state lens1 lens2 new-value]
  (let [old (lens1 state)]
    (-> state (lens2 old) (lens1 new-value))))


(defn mark [interaction-state new-hover]
  (-> (push interaction-state
        interaction-state-node-marked
        interaction-state-previous-node-marked
        new-hover)
    (interaction-state-needs-update? true)))

(defn select [interaction-state new-selected]
  (-> (push interaction-state
        interaction-state-node-selected
        interaction-state-previous-node-selected
        new-selected)
    (interaction-state-needs-update? true)))

(defn marked-index [s]
  (marked-node-index (interaction-state-node-marked s)))

(defn previously-marked-index [s]
  (marked-node-index (interaction-state-previous-node-marked s)))

(defn selected-index [s]
  (selected-node-index (interaction-state-node-selected s)))

(defn previously-selected-index [s]
  (selected-node-index (interaction-state-previous-node-selected s)))

(defn mark-node-by-app [s idx]
  (mark s (make-node-marked-by-app idx (animation/linear 0 2 true))))

(defn unmark-node [s]
  (mark s no-node-marked))



(def initial-state
  (make-interaction-state
    (mailbox/make-mailbox)
    no-node-marked
    no-node-marked
    no-node-selected-inst
    no-node-selected-inst
    no-tx-marked
    (make-tx-stats 0 0)
    true
    true))

(defn marked-node-put-animation [node-marked animation]
  (cond
    (node-marked-by-app? node-marked)
    (node-marked-by-app-animation node-marked animation)

    (node-marked-by-mouse-hover? node-marked)
    (node-marked-by-mouse-hover-animation node-marked animation)))

(defn selected-node-put-animation [node-selected animation]
  (st/match node-selected-t node-selected
    node-selected-by-mouse-click?
    (node-selected-by-mouse-click-animation node-selected animation)

    node-selected-by-app?
    (node-selected-by-app-animation node-selected animation)

    no-node-selected?
    :die))


(defn get-marked-animation-value&step [interaction-state]
  (let [?node-marked (interaction-state-node-marked interaction-state)]
    (cond

      (not (no-node-marked? ?node-marked))
      (let [animation (marked-node-animation ?node-marked)
            stepped   (animation/step animation)
            value     (animation/get-value stepped)]
        [(marked-node-index ?node-marked)
         value
         (interaction-state-node-marked
           interaction-state
           (marked-node-put-animation ?node-marked stepped))])

      :default
      [nil nil interaction-state])))


(defn get-selected-animation-value&step [interaction-state]
  (let [?node-selected (interaction-state-node-selected interaction-state)]
    (cond

      (not (no-node-selected? ?node-selected))
      (let [animation (selected-node-animation ?node-selected)
            stepped   (animation/step animation)
            value     (animation/get-value stepped)]
        [(selected-node-index ?node-selected)
         value
         (interaction-state-node-selected
           interaction-state
           (selected-node-put-animation ?node-selected stepped))])

      :default
      [nil nil interaction-state])))





(qt/def-type auto-rotate-message-t
  [(set-auto-rotate-message)
   (unset-auto-rotate-message)])

(def the-set-auto-rotate-message (make-set-auto-rotate-message))
(def the-unset-auto-rotate-message (make-unset-auto-rotate-message))


(def auto-rotation-lens
  (lens/>> data/render-state-interaction-state interaction-state-auto-rotation?))

(defn update-auto-rotate [render-state flag]
  (let [camera (data/render-state-controls render-state)]
    (set! (.-autoRotate camera) flag)
    (lens/overhaul render-state auto-rotation-lens (constantly flag))))

(defn set-auto-rotate [render-state]
  (update-auto-rotate render-state true))

(defn unset-auto-rotate [render-state]
  (update-auto-rotate render-state false))

(defn handle-auto-rotate-message [render-state msg]
  (st/match auto-rotate-message-t msg

    set-auto-rotate-message?
    (set-auto-rotate render-state)

    unset-auto-rotate-message?
    (unset-auto-rotate render-state)))
