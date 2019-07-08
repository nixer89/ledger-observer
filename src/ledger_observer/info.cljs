(ns ledger-observer.info
  (:require [active.clojure.cljs.record :as rec :include-macros true]
            [active.clojure.lens :as lens]
            [ledger-observer.mouse :as global-mouse]
            [ledger-observer.delayed :as delayed]
            [ledger-observer.components.inspector :as inspector]
            cljsjs.jquery
            [reacl2.core :as reacl :include-macros true]
            [reacl2.dom :as dom :include-macros true]))


(rec/define-record-type Nop (make-nop) nop? [])
(rec/define-record-type About (make-about) about? [])
(rec/define-record-type Settings (make-settings) settings? [])

(rec/define-record-type TriggerHideInfoContainerMessage
  (make-trigger-hide-info-container-message target) trigger-hide-info-container-message?
  [target trigger-hide-info-container-message-target])

(rec/define-record-type HideInfoContainerMessage
  (make-hide-info-container-message) hide-info-container-message? [])

(rec/define-record-type HideInfoContainerAction
  (make-hide-info-container-action) hide-info-container-action? [])

(def hide-info-container-action (make-hide-info-container-action))

(def about (make-about))
(def settings (make-settings))
(def nop (make-nop))


(def info-container-id (str (gensym)))

(reacl/defclass info-container this [& content]

  component-will-unmount
  (fn []
    (reacl/return :action (global-mouse/make-deregister-mouse-handlers-action ::info-container)))


  component-did-mount
  (fn []
    (.fadeIn (js/jQuery (str "#" info-container-id)) 500)
    (reacl/return :action
     (global-mouse/make-register-mouse-handlers-action
       ::info-container
       (fn []) (fn [])
       (fn [_ _ target] (reacl/send-message! this (make-trigger-hide-info-container-message target))))))

  render
  (apply dom/div {:class "info-container"
                  :id    info-container-id}
    (dom/div {:class "info-container-content"}
     (dom/div {:class "close"
               :onclick #(reacl/send-message! this (make-trigger-hide-info-container-message nil))}
       (dom/img {:src "images/clear.png" :width 20 :height 20})))
    content)

  handle-message
  (fn [msg]
    (cond

      (trigger-hide-info-container-message? msg)
      (if-not (= (trigger-hide-info-container-message-target msg) info-container-id)
        (reacl/return
          :action (delayed/make-delayed-action #(reacl/send-message! this (make-hide-info-container-message))  500)
          :action (delayed/make-delayed-action
                    #(.fadeOut (js/jQuery (str "#" info-container-id)) 200) 200))
        (reacl/return))

      (hide-info-container-message? msg)
      (reacl/return :action hide-info-container-action))))


(reacl/defclass settings-content this []
  render
  (info-container "Hello world"))


(defn badge [t]
  (dom/div
    {:class "badge"
     :style {:background-color (inspector/type->rgb-string t)}}
    t))

(reacl/defclass about-content this []
  render
  (info-container
    (dom/h1 "About")
    (dom/p "Ledger Observer is a real-time visualization of the XRP-Ledger (XRPL).
It visualizes transactions as they appear on the ledger using a graph structure.")

      (dom/h3 "Nodes are addresses")
      (dom/p "Each node in the graph represents an address, that was part in an transaction while you visited the site.")

      (dom/h3 "Links are transactions")
      (dom/p "Each link between nodes (addresses) visualizes that a transaction occured between them. A transaction is animated, afterwards, the link is kept. There are differnet types of transaction on the ledger, visualized with the following colors:")

      (badge "OfferCreate")
      (badge "OfferCancel")
      (badge "Payment")
      (dom/p )
      (badge "Escrow related")
      (badge "Others")

      (dom/p "A transaction may flash in a dark red without glowing (getting white). This means a transaction wasn't successfull.")
    ))


(defn show-info [info]
  (cond

    (settings? info)
    (settings-content)

    (about? info)
    (about-content)))
