(ns ledger-observer.components.inspector
  (:require [active.clojure.cljs.record :as rec :include-macros true]
            [quick-type.core :as qt :include-macros true]
            [cljs.core.async :refer [<! >! go-loop go alt!]]
            [reacl2.core :as reacl :include-macros true]
            [ledger-observer.visualization.data :as data]
            [ledger-observer.bithomp-userinfo :as bithomp]
            [ledger-observer.components.scrollpane :as scrollpane]
            [ledger-observer.visualization.materials :as colors]
            [reacl2.dom :as dom :include-macros true]))


(qt/def-type inspector-state-t
  [(state [txs])
   (none [])])

(def initial-state (make-state []))

(defn type->rgb-string* [a]
  (let [[r g b] (cond
                  (= a "OfferCreate")                      colors/red
                  (= a "OfferCancel")                      colors/dark-red
                  (= a "Payment")                          colors/blue
                  (clojure.string/starts-with? a "Escrow") colors/yellow
                  :default                                 colors/dark-blue)]
    (str "rgb(" r "," g "," b ", 1.0)")))

(def type->rgb-string (memoize type->rgb-string*))



(rec/define-record-type MarkTxMessage
  (make-mark-tx-message tx) mark-tx-message?
  [tx mark-tx-message-tx])

(rec/define-record-type MarkTxAction
  (make-mark-tx-action from targets) mark-tx-action?
  [from mark-tx-action-from
   targets mark-tx-action-targets])

(rec/define-record-type UnmarkTxMessage
  (make-unmark-tx-message tx) unmark-tx-message?
  [tx unmark-tx-message-tx])

(rec/define-record-type UnmarkTxAction
  (make-unmark-tx-action from targets) unmark-tx-action?
  [from unmark-tx-action-from
   targets unmark-tx-action-targets])


(rec/define-record-type Tx
  (make-tx tid from targets type label success?) tx?
  [tid tx-tid
   from tx-from
   targets tx-targets
   type tx-type
   label tx-label
   success? tx-success?])



(reacl/defclass tx this tx-data []

  should-component-update?
  (fn [_] false)

  component-did-mount
  (fn [_]
    (.slideDown (js/jQuery (str "#inspect-" (tx-tid tx-data))))
    (reacl/return))

  render
  (dom/div {:id (str "inspect-" (tx-tid tx-data))
            :onmouseover #(reacl/send-message! this (make-mark-tx-message tx-data))
            :onmouseleave #(reacl/send-message! this (make-unmark-tx-message tx-data))
            :class "inspect-tx-entry"}
    (dom/div
      {:class "inspect-tx-entry-type"
       :style {:border-color (type->rgb-string (tx-type tx-data))}}
      (dom/span (when-not (tx-success? tx-data)
                  {:class "invalid"})
        (tx-type tx-data))
      (when-not (tx-success? tx-data)
        (dom/img {:src "images/warning.png" :class "invalid-icon"})))
    (dom/div {:class "inspect-tx-entry-label"} (tx-label tx-data))
    (dom/div {:class "inspect-tx-entry-hash"}
      (dom/span
        {:style {:white-space "nowrap"}}
        (dom/img {:src "images/launch.png"})
        (dom/a {:href (str "https://bithomp.com/explorer/" (tx-tid tx-data))
                :target "_blank"}
          (tx-tid tx-data))))
    (dom/br {:style {:clear "both"}}))


  handle-message
  (fn [msg]
    (cond
      (mark-tx-message? msg)
      (let [tx (mark-tx-message-tx msg)
            from (tx-from tx)
            targets (tx-targets tx)]
        (reacl/return :action (make-mark-tx-action from targets)))

      (unmark-tx-message? msg)
      (let [tx      (unmark-tx-message-tx msg)
            from    (tx-from tx)
            targets (tx-targets tx)]
        (reacl/return :action (make-unmark-tx-action from targets))))))


(reacl/defclass txs this txs []


  render
  (dom/div
    (map (fn [t] (dom/keyed (tx-tid t) (tx (reacl/opt :reaction reacl/no-reaction)  t))) txs)))


(reacl/defclass inspector-menu this none [address unset-fn!]

  render
  (dom/div
    {:id "inspect-menu"
     :class "fade-in"}
    (dom/ul {:class "item"}
      (dom/li
        (dom/a {:onclick #(unset-fn!) :href "#"}
          (dom/img {:src "images/clear.png"})
          "Clear address"))
      (dom/li
        (dom/a
          {:href (str "https://bithomp.com/explorer/" address)
           :target "_blank"}
          (dom/img {:src "images/launch.png"})
          "Inspect address on bithomp")))))


(reacl/defclass inspector this app-state []

  render
  (if (empty? (state-txs app-state))
    (dom/div {:class "inspect-box"}
      (dom/span {:class "info fade-in"}
        "Waiting for transactions..."))
   (dom/div
     (scrollpane/pane
       false
       {:height "300px"}
       txs
       (reacl/opt :reaction reacl/no-reaction)
       (state-txs app-state)))))

