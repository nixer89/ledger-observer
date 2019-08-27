(ns ledger-observer.components.settings
 (:require [active.clojure.cljs.record :as rec :include-macros true]
           [active.clojure.sum-type :as st :include-macros true]
           [active.clojure.lens :as lens]

           [ledger-observer.visualization.app-interaction :as visualization-data]

           [ledger-observer.mailbox :as mailbox]
           [reacl2.core :as reacl :include-macros true]
           [quick-type.core :as qt :include-macros true]
           [reacl2.dom :as dom :include-macros true]))

(qt/def-record settings
  [auto-rotate?
   interface-visible?])

(def initial-state (make-settings true true))

(qt/def-type settings-action-t
  [(set-auto-rotate-action)
   (unset-auto-rotate-action)
   (set-interface-visible-action)
   (unset-interface-visible-action)])

(def the-set-auto-rotate-action (make-set-auto-rotate-action))
(def the-unset-auto-rotate-action (make-unset-auto-rotate-action))

(def the-set-interface-visible-action (make-set-interface-visible-action))
(def the-unset-interface-visible-action (make-unset-interface-visible-action))

(qt/def-type settings-message-t
  [(toggle-auto-rotate-message)
   (toggle-interface-visible-message)])

(def the-toggle-auto-rotate-message (make-toggle-auto-rotate-message))
(def the-toggle-interface-visible-message (make-toggle-interface-visible-message))

(qt/def-type checkbox-message-t
  [(toggle-checkbox-message)])

(def the-toggle-checkbox-message (make-toggle-checkbox-message))
(def always-visible {:style {:visibility "visible"
                             :opacity 1.0}})

(defn maybe-invisible [m visible?]
  (if visible?
    (update m :class #(str % " animated-show"))
    (update m :class #(str % " animated-hide"))))


(defn class-merge [c1 c2]
  (update (merge c2 c1) :class #(str % " " (:class c2))))

(reacl/defclass checkbox this state [label-active label-inactive & [opts]]

  render
  (let [toggle-fn (fn [_] (reacl/send-message! this the-toggle-checkbox-message))]
    (dom/div (class-merge {:class "checkbox"} opts)
      (dom/label {:class "switch"}
        (dom/input {:type    "checkbox"
                    :checked state
                    :onclick toggle-fn})

       (dom/span {:class "slider round"}))
     (dom/span {:onclick toggle-fn}
       (if state
         label-active
         label-inactive
         ))))

  handle-message
  (fn [msg]
    (st/match checkbox-message-t msg

      toggle-checkbox-message?
      (reacl/return :app-state (not state)))))


(reacl/defclass panel this settings []

  render
  (dom/div
    {:class "settings"}
    (dom/ul
      (dom/li
       (checkbox
         (reacl/opt :reaction (reacl/reaction this (constantly the-toggle-auto-rotate-message)))
         (settings-auto-rotate? settings)
         "Auto rotation enabled"
         "Auto rotation disabled"
         (maybe-invisible {} (settings-interface-visible? settings))
         ))
      (dom/li
        (checkbox
          (reacl/opt :reaction (reacl/reaction this (constantly the-toggle-interface-visible-message)))
          (settings-interface-visible? settings)
          "Interface visible"
          "Interface invisible"
          always-visible
          ))
      ))


  handle-message
  (fn [msg]
    (st/match settings-message-t msg

      toggle-auto-rotate-message?
      (reacl/return
        :app-state (lens/overhaul settings settings-auto-rotate? not)
        :action (if (settings-auto-rotate? settings)
                  the-unset-auto-rotate-action
                  the-set-auto-rotate-action
                  ))

      toggle-interface-visible-message?
      (reacl/return
        :app-state (lens/overhaul settings settings-interface-visible? not)
        :action (if (settings-interface-visible? settings)
                  the-unset-interface-visible-action
                  the-set-interface-visible-action
                  )))))



(defn handle-action [action renderer-mailbox]

  (st/match settings-action-t action

    set-interface-visible-action?
    (do
      (println "set visible")
      (reacl/return))

    unset-interface-visible-action?
    (do
      (println "set invisible")
      (reacl/return))

    unset-auto-rotate-action?
    (do (mailbox/send! renderer-mailbox visualization-data/the-unset-auto-rotate-message)
        (reacl/return))

    set-auto-rotate-action?
    (do (mailbox/send! renderer-mailbox visualization-data/the-set-auto-rotate-message)
        (reacl/return))))
