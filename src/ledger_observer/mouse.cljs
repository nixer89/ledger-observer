(ns ledger-observer.mouse
  (:require [active.clojure.cljs.record :as rec :include-macros true]))

(defn $ [a] (js/jQuery a))

(rec/define-record-type MouseHandlerRegistryEntry
  (make-mouse-handler-registry-entry on-move on-down on-release) mouse-handler-registry-entry?
  [on-move mouse-handler-registry-entry-on-move
   on-down mouse-handler-registry-entry-on-down
   on-release mouse-handler-registry-entry-on-release])



(rec/define-record-type RegisterMouseHandlersAction
  (make-register-mouse-handlers-action id on-move on-down on-release) register-mouse-handlers-action?
  [id register-mouse-handlers-action-id
   on-move register-mouse-handlers-action-on-move
   on-down register-mouse-handlers-action-on-down
   on-release register-mouse-handlers-action-on-release])


(rec/define-record-type DeregisterMouseHandlersAction
  (make-deregister-mouse-handlers-action id) deregister-mouse-handlers-action?
  [id deregister-mouse-handlers-action-id])


(def global-registry (atom {}))

(defn register! [id on-move on-down on-release]
  (swap! global-registry assoc id (make-mouse-handler-registry-entry on-move on-down on-release)))

(defn deregister! [id]
  (swap! global-registry dissoc id))

(defn exec [accessor e]
  (run!
    (fn [handler-entry]
      (when-let [handler (accessor handler-entry)]
        (handler (.-pageX e) (.-pageY e) (.-id (.-target  e)))))
    (vals @global-registry)))

(defn on-move [e] (exec mouse-handler-registry-entry-on-move e))
(defn on-down [e] (exec mouse-handler-registry-entry-on-down e))
(defn on-release [e] (exec mouse-handler-registry-entry-on-release e))

(defn init! []
  (.mousemove ($ "body") (fn [e] (on-move e)))
  (.mousedown ($ "body") (fn [e] (on-down e)))
  (.mouseup   ($ "body") (fn [e] (on-release e))))
