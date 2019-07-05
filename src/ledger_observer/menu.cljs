(ns ledger-observer.menu
  (:require [active.clojure.record :as rec :include-macros true]
            [active.clojure.lens :as lens]
            [ledger-observer.mouse :as global-mouse]
            [ledger-observer.info :as info]
            [ledger-observer.delayed :as delayed]
            cljsjs.jquery
            [reacl2.core :as reacl :include-macros true]
            [reacl2.dom :as dom :include-macros true]))


(rec/define-record-type ShowMenuMessage (make-show-menu-message) show-menu-message? [])
(rec/define-record-type TriggerHideMenuMessage (make-trigger-hide-menu-message ev) trigger-hide-menu-message?
  [ev trigger-hide-menu-message-ev])
(rec/define-record-type HideMenuMessage (make-hide-menu-message) hide-menu-message? [])

(def show-menu-message (make-show-menu-message))

(rec/define-record-type ShowAboutMessage (make-show-about-message) show-about-message? [])
(rec/define-record-type ShowSettingsMessage (make-show-settings-message) show-settings-message? [])

(def show-settings-message (make-show-settings-message))
(def show-about-message (make-show-about-message))

(def menu-id (str (gensym)))

(def menu-ids
  {:menu-about (str (gensym))
   :menu-settings (str (gensym))})


(reacl/defclass menu-entries t [this]

  component-did-mount
  (fn []
    (.slideDown (js/jQuery (str "#" menu-id)) "fast")
    (reacl/return))

  render
  (dom/div {:class "menu"
            :id menu-id}
    (dom/ul
      (dom/li
        {:onclick #(reacl/send-message! this show-about-message)
         :id      (:menu-about menu-ids)}
        (dom/i {:class "material-icons"} "info") "About")
      (dom/li
        {:onclick #(reacl/send-message! this show-settings-message)
         :id      (:menu-settings menu-ids)}
        (dom/i {:class "material-icons"} "code") "Code on Github"))))


(reacl/defclass menu this selected []

  local-state [show-menu? false]

  render
  (dom/div {:class "menu-container"}
    (dom/i {:class "material-icons"
            :onclick #(reacl/send-message! this (make-show-menu-message))} "menu")

    (when show-menu?
      (menu-entries this)))


  handle-message
  (fn [msg]
    (cond

      (show-settings-message? msg)
      (reacl/return
        :app-state info/settings
        :local-state false
        :action (global-mouse/make-deregister-mouse-handlers-action ::menu))

      (show-about-message? msg)
      (reacl/return
        :app-state info/about
        :local-state false
        :action (global-mouse/make-deregister-mouse-handlers-action ::menu))

      (show-menu-message? msg)
      (do
       (reacl/return
         :local-state true
         :action (global-mouse/make-register-mouse-handlers-action
                   ::menu
                   (fn []) (fn [])
                   (fn [_ _ target] (reacl/send-message! this (make-trigger-hide-menu-message target))))))


      (hide-menu-message? msg)
      (reacl/return
        :local-state false
        :action (global-mouse/make-deregister-mouse-handlers-action ::menu))


      (trigger-hide-menu-message? msg)
      (if (some #{(trigger-hide-menu-message-ev msg)} (vals menu-ids))
        (reacl/return)
        (reacl/return
          :action (delayed/make-delayed-action
                    #(reacl/send-message! this (make-hide-menu-message)) 300)
          :action (delayed/make-delayed-action
                    #(.slideUp (js/jQuery (str "#" menu-id)) 200)
                    0))))))
