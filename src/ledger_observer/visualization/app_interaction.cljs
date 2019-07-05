(ns ledger-observer.visualization.app-interaction
  (:require [ledger-observer.visualization.data :as data]
            [ledger-observer.mailbox :as mailbox]
            [ledger-observer.visualization.animations :as animation]
            [active.clojure.record :as rec :include-macros true]))

(rec/define-record-type Nop (make-nop) nop? [])
(def nop (make-nop))

(rec/define-record-type ClickAddressMessage
  (make-click-address-message address) click-address-message?
  [address click-address-message-address])

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




(rec/define-record-type NoNodeSelected (make-no-node-selected) no-node-selected? [])
(def no-node-selected (make-no-node-selected))

(rec/define-record-type NodeSelectedByMouseClick
  (make-node-selected-by-mouse-click index animation ) node-selected-by-mouse-click?
  [index node-selected-by-mouse-click-index
   animation node-selected-by-mouse-click-animation])


(defn selected-node-index [s]
  (cond
    (node-selected-by-mouse-click? s)
    (node-selected-by-mouse-click-index s)))

(defn selected-node-animation [m]
  (cond
    (node-selected-by-mouse-click? m)
    (node-selected-by-mouse-click-animation m)))

(rec/define-record-type TxStatsState
  (make-tx-stats counter last-updated) tx-stats-state?
  [counter tx-stats-state-counter
   last-updated tx-stats-state-last-updated])

(rec/define-record-type InteractionState
  (make-interaction-state from-app-mailbox node-marked previous-node-marked
    node-selected previous-node-selected tx-marked tx-stats needs-update?)
  interaction-state?
  [from-app-mailbox interaction-state-from-app-mailbox
   node-marked interaction-state-node-marked
   previous-node-marked interaction-state-previous-node-marked
   node-selected interaction-state-node-selected
   previous-node-selected interaction-state-previous-node-selected
   tx-marked interaction-state-tx-marked
   tx-stats interaction-state-tx-stats
   needs-update? interaction-state-needs-update?
   ])


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
    no-node-selected
    no-node-selected
    no-tx-marked
    (make-tx-stats 0 0)
    true))

(defn marked-node-put-animation [node-marked animation]
  (cond
    (node-marked-by-app? node-marked)
    (node-marked-by-app-animation node-marked animation)

    (node-marked-by-mouse-hover? node-marked)
    (node-marked-by-mouse-hover-animation node-marked animation)))

(defn selected-node-put-animation [node-selected animation]
  (cond
    (node-selected-by-mouse-click? node-selected)
    (node-selected-by-mouse-click-animation node-selected animation)))


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
