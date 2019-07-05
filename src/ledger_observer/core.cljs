(ns ledger-observer.core
  (:require [active.clojure.record :as rec :include-macros true]
            [active.clojure.lens :as lens]
            cljsjs.jquery
            [reacl2.core :as reacl :include-macros true]
            [reacl2.dom :as dom :include-macros true]
            [ledger-observer.visualization.render :as renderer]
            [ledger-observer.socket :as socket]
            [ledger-observer.mouse :as global-mouse]
            [ledger-observer.menu :as menu]
            [ledger-observer.info :as info]
            [ledger-observer.delayed :as delayed]
            [ledger-observer.mailbox :as mailbox]
            [ledger-observer.components.search :as search]
            [ledger-observer.components.counter :as counter]
            [ledger-observer.components.inspector :as inspector]
            [ledger-observer.bithomp-userinfo :as bithomp]
            [ledger-observer.visualization.data :as data]
            [ledger-observer.visualization.render-utils :as render-utils]
            [ledger-observer.visualization.mouse :as mouse]
            [ledger-observer.visualization.app-interaction :as ai]
            [ledger-observer.visualization.graph-layout :as graph-layout]))


(enable-console-print!)
(set! *warn-on-infer* true)

;; app state of render registry
(rec/define-record-type AppState
  (make-app-state render-interaction-mailbox render-tx-mailbox filter-mailboxes
    connection hovered-address clicked-address search-state tx-stats info) app-state?
  [render-interaction-mailbox app-state-render-interaction-mailbox
   render-tx-mailbox app-state-render-tx-mailbox
   filter-mailboxes app-state-filter-mailboxes
   connection app-state-connection
   hovered-address app-state-hovered-address
   clicked-address app-state-clicked-address
   search-state app-state-search-state
   tx-stats app-state-tx-stats
   info app-state-info])

;; messages
(rec/define-record-type SetAddressMessage
  (make-set-address-message address) set-address-message?
  [address set-address-message-address])

(rec/define-record-type UnsetAddressMessage
  (make-unset-address-message) unset-address-message?
  [])

(rec/define-record-type HoverAddressMessage
  (make-hover-address-message address) hover-address-message?
  [address hover-address-message-address])


(rec/define-record-type FetchFilteredTxAction
  (make-fetch-filtered-tx-action receiver channel address) fetch-filtered-tx-action?
  [receiver fetch-filtered-tx-action-receiver
   channel fetch-filtered-tx-action-mailbox
   address fetch-filtered-tx-action-address])


(rec/define-record-type NewTxsMessage
  (make-new-txs-message txs address) new-txs-message?
  [txs new-txs-message-txs
   address new-txs-message-address])



(rec/define-record-type IncreaseTxMessage
  (make-increase-tx-message n) increase-tx-message?
  [n increase-tx-message-n])


(rec/define-record-type TriggerTxsFetchMessage
  (make-trigger-txs-fetch-message) trigger-txs-fetch-message? [])



(defn initialize-renderer! [callbacks]
  (let [render-state      (renderer/init-visualization-state)
        render-tx-mailbox (data/render-state-new-tx-mailbox render-state)]
    (renderer/kickoff-visualization! render-state callbacks)
    render-state))



(def socket-mailbox-lens
  (lens/>> app-state-filter-mailboxes data/filter-mailboxes-socket-mailbox))

(defn put-filter! [app-state address]
  (let [socket-mailbox (socket-mailbox-lens app-state)]
    (mailbox/send! socket-mailbox (socket/make-set-filter-address address))))

(defn remove-filter! [app-state address]
  (let [socket-mailbox (socket-mailbox-lens app-state)]
    (mailbox/send! socket-mailbox (socket/make-unset-filter-address address))))


(defn send-renderer! [app-state msg]
  (mailbox/send! (app-state-render-interaction-mailbox app-state) msg))

(defn unset-clicked! [app-state]
  (send-renderer! app-state (mouse/make-unclick-message)))

;; local states of render registry
(rec/define-record-type ClickedAddress
  (make-clicked-address address txs) clicked-address?
  [address clicked-address-address
   txs clicked-address-txs])

(defn maybe-clicked-address [app-state]
  (when-let [clicked (app-state-clicked-address app-state)]
    (clicked-address-address clicked)))


(rec/define-record-type DefaultMode (make-default-mode) default-mode? [])

(rec/define-record-type NotConnectedMode (make-not-connected-mode) not-connected-mode? [])

(rec/define-record-type SocketConnectedMessage
  (make-socket-connected-message socket) socket-connected-message?
  [socket socket-connected-message-socket])

(rec/define-record-type SocketClosedMessage
  (make-socket-closed-message error) socket-closed-message?
  [error socket-closed-message-error])

(rec/define-record-type SetupSocket&ReceiveAction
  (make-setup-socket&receive-action socket) setup-socket&receive-action?
  [socket setup-socket&receive-action-socket])

(rec/define-record-type CreateSocketAction
  (make-create-socket-action handler) create-socket-action?
  [handler create-socket-action-handler])

(rec/define-record-type SetUsersInfoMessage
  (make-set-users-info-message users-info) set-users-info-message?
  [users-info set-users-info-message-users-info])

(rec/define-record-type UpdateSearchStateMessage
  (make-update-search-state-message state) update-search-state-message?
  [state update-search-state-message-state])


(defn handle-unset-address-msg [app-state]
  (let [clicked (app-state-clicked-address app-state)
        ?current-address (clicked-address-address clicked)]
    (unset-clicked! app-state)
    (remove-filter! app-state ?current-address)
    (app-state-clicked-address app-state nil)))


(defn handle-set-address-msg [app-state msg]
  (let [address          (set-address-message-address msg)
        ?current-address (maybe-clicked-address app-state)]
    ;; if there is a current address, unset it in filter-set of tx ripple socket
    (if (= address ?current-address)
      app-state
      (do
        (remove-filter! app-state ?current-address)
        (put-filter! app-state address)
        (app-state-clicked-address app-state (make-clicked-address address []))))))


(defn new-transaction-event->tx [event address]
  (inspector/make-tx
    (data/new-transaction-event-tid event)
    (data/new-transaction-event-from event)
    (data/new-transaction-event-targets event)
    (data/new-transaction-event-type event)
    (if (= address (data/new-transaction-event-from event)) "Issuer" "Attendee")
    (data/new-transaction-event-success? event)))


(def clicked-address-mode-txs-lens
  (lens/>> app-state-clicked-address clicked-address-txs))

(rec/define-record-type TickMeEverySecondAction
  (make-tick-me-every-second-action receiver) tick-me-every-second-action?
  [receiver tick-me-every-second-action-receiver])


(rec/define-record-type TickMessage
  (make-tick-message) tick-message? [])

(defn app-state->fetch-filter-action [app-state reacl-component]
  (make-fetch-filtered-tx-action reacl-component
    (-> app-state app-state-filter-mailboxes data/filter-mailboxes-app-mailbox)
    (-> app-state app-state-clicked-address clicked-address-address)))


(defn create-ripple-socket! [ref]
  (let [socket-open-callback  #(reacl/send-message! ref (make-socket-connected-message %))
        socket-close-callback #(reacl/send-message! ref (make-socket-closed-message %))]
    (socket/create-ripple-socket! socket-open-callback socket-close-callback)))




(rec/define-record-type ShowInfoMessage
  (make-show-info-message info) show-info-message?
  [info show-info-message-info])

(rec/define-record-type HideInfoMessage
  (make-hide-info-message) hide-info-message? [])

#_(defn baseline-grid
  
  []
  (apply dom/div {:style
                  {
                   :position "absolute"
                   :top      0
                   :left     0
                   :width    "100%"
                   :height   "100%"
                   }}

    (mapcat (fn [_]
              [ (dom/div {:style {:width            "100%"
                                  :height           "4px"
                                  :background-color "red"
                                  :opacity          0.2}})
               (dom/div {:style {:width  "100%"
                                 :height "8px"}})]
              ) (range 0 130))))

(reacl/defclass app this app-state [parent]

  component-did-mount
  (fn [_]
    (let [set-address                #(reacl/send-message! this (make-set-address-message %))
          hover-address              #(reacl/send-message! this (make-hover-address-message %))
          set-counter                #(reacl/send-message! this (make-increase-tx-message %))
          callbacks                  (data/make-gui-component-callbacks
                                       set-counter set-address hover-address)
          renderer-state             (initialize-renderer! callbacks)
          render-interaction-mailbox (render-utils/render-state-from-app-mailbox-lens renderer-state)
          render-tx-mailbox          (data/render-state-new-tx-mailbox renderer-state)

          filter-mailboxes (data/make-filter-mailboxes (mailbox/make-mailbox) (mailbox/make-mailbox))]

      (global-mouse/init!)

      (reacl/return
        :app-state (make-app-state render-interaction-mailbox render-tx-mailbox filter-mailboxes
                     (make-not-connected-mode) nil nil search/initial-state (counter/make-counter-state 0 0)
                     info/nop)
        :action (make-tick-me-every-second-action this)
        :action (bithomp/make-fetch-users-info-action this)
        :action (make-create-socket-action this))))


  render

  (let [?clicked-address (maybe-clicked-address app-state)
        ?hovered-address (app-state-hovered-address app-state)
        info (app-state-info app-state)]

    (dom/div


      (dom/div {:class "logo-bottom-right"})

      #_(menu/menu (reacl/opt :reaction (reacl/reaction this #(make-show-info-message %)))
        (app-state-info app-state))
      (dom/div {:class "right-menu"}
       (dom/ul
         (dom/li
           {:style {:padding-right "10px"}
            :onclick #(reacl/send-message! this (make-show-info-message info/about))
            :class "menu-image"}
           (dom/img {:src "images/info.png" :style {:width "23px" :height "23px"}}))
         (dom/li
           {:class "menu-image"}
           (dom/a {:href "https://github.com/smoes/ledger-observer" :target "_blank"}
             (dom/img {:src "images/github.png" :style {:width "20px" :height "20px" :margin-top "1px"}}

              )))
         ))

      (let [opt (reacl/opt :reduce-action
                  (fn [_ action]
                    (cond
                      (info/hide-info-container-action? action)
                      (reacl/return :message [this (make-hide-info-message)])

                      :default
                      (reacl/return :action action))))]
       (cond
         (info/settings? info)
         (info/settings-content opt)

         (info/about? info)
         (info/about-content opt)))


      (dom/div {:class "sidebar" :style {:will-change "content"}}

       (search/search-field
         (reacl/opt :reaction (reacl/reaction this #(make-update-search-state-message %)))
         (app-state-search-state app-state) ?hovered-address ?clicked-address)

       (when ?clicked-address
         (when (search/is-empty-state? (app-state-search-state app-state))
          (dom/keyed ?clicked-address
            (dom/span
              (inspector/inspector-menu ?clicked-address
                #(reacl/send-message! this (make-unset-address-message)))
              (inspector/inspector (clicked-address-mode-txs-lens app-state)))))))



      (dom/div {:style {:position         "absolute"
                        :width            "100%"
                        :bottom           "40px"}}
        (cond
          (not-connected-mode? (app-state-connection app-state))
          (dom/div
            {:class "connecting-label fade-in"}
            "Connecting to wss://s1.ripple.com")

          (app-state-tx-stats app-state)
          (counter/counter (app-state-tx-stats app-state) this)))

      (dom/div {:id          "renderer-container"
                :onmousemove (fn [^js/MouseEvent ev] (mouse/update-render-mouse-position! ev))
                :onclick     (fn [_] (send-renderer! app-state (mouse/make-clicked-message)))})


      #_(baseline-grid)
      ))



  handle-message
  (fn [msg]
    (cond

      (socket-connected-message? msg)
      (reacl/return :action (make-setup-socket&receive-action
                              (socket-connected-message-socket msg)))

      (socket-closed-message? msg)
      (reacl/return
        :action (make-create-socket-action this)
        :app-state (app-state-connection app-state (make-not-connected-mode)))

      (new-txs-message? msg)
      (reacl/return :app-state
        (let [?address (maybe-clicked-address app-state)]
          (if (= ?address (new-txs-message-address msg))
            (lens/overhaul app-state clicked-address-mode-txs-lens
              (fn [txs] (concat (map #(new-transaction-event->tx % (new-txs-message-address msg))
                                  (remove nil? (new-txs-message-txs msg))) txs)))
            app-state)))


      (trigger-txs-fetch-message? msg)
      (if (maybe-clicked-address app-state)
        (reacl/return :action (app-state->fetch-filter-action app-state this))
        (reacl/return))

      (unset-address-message? msg)
      (reacl/return :app-state (handle-unset-address-msg app-state))

      (set-address-message? msg) ;; with nil it is unset
      (let [new-app-state (handle-set-address-msg app-state msg)]
        (reacl/return
          :app-state new-app-state
          :action (app-state->fetch-filter-action new-app-state this)))

      (increase-tx-message? msg)
      (let [old-tx (app-state-tx-stats app-state)]
        (reacl/return :app-state
          (app-state-tx-stats app-state (counter/counter-state-tx old-tx (increase-tx-message-n msg)))))

      (tick-message? msg)
      (let [old-tx (app-state-tx-stats app-state)]
        (reacl/return :app-state
          (app-state-tx-stats app-state (lens/overhaul old-tx counter/counter-state-seconds inc))))

      (update-search-state-message? msg)
      (reacl/return :app-state
        (app-state-search-state app-state (update-search-state-message-state msg)))

      (hover-address-message? msg)
      (reacl/return :app-state
        (app-state-hovered-address app-state (hover-address-message-address msg)))

      (show-info-message? msg)
      (reacl/return :app-state
        (app-state-info app-state (show-info-message-info msg)))

      (hide-info-message? msg)
      (reacl/return :app-state
        (app-state-info app-state info/nop))
      )))





(defn handle-actions [app-state action]

  (cond

    (create-socket-action? action)
    (do (create-ripple-socket! (create-socket-action-handler action))
        (reacl/return))

    (setup-socket&receive-action? action)
    (let [render-tx-mailbox (app-state-render-tx-mailbox app-state)
          socket-mailbox    (socket-mailbox-lens app-state)
          app-mailbox       (-> app-state app-state-filter-mailboxes data/filter-mailboxes-app-mailbox)
          socket            (setup-socket&receive-action-socket action)]
      (socket/setup-ripple-socket! socket render-tx-mailbox socket-mailbox app-mailbox)
      (reacl/return :app-state (app-state-connection app-state (make-default-mode))))

    (fetch-filtered-tx-action? action)
    (let [^js/Object receiver (fetch-filtered-tx-action-receiver action)
          mailbox  (fetch-filtered-tx-action-mailbox action)
          msgs     (mailbox/receive-n mailbox 10)
          txs      (map second msgs)
          ;; FIXME: This must be done properly later.
          ;; There can be many filter addresses later if various filter addresses
          ;; were set. Now we only accept one node in clicked state, so all addresses
          ;; must be equal.
          addr     (first (first (map first msgs)))]
      (js/window.setTimeout
        #(when (.isMounted receiver)
           (reacl/send-message! receiver (make-trigger-txs-fetch-message))) 200)
      (if addr
        (reacl/return :message [receiver (make-new-txs-message txs addr)])
        (reacl/return)))


    (tick-me-every-second-action? action)
    (do (js/window.setInterval #(reacl/send-message! (tick-me-every-second-action-receiver action)
                                  (make-tick-message)) 1000)
        (reacl/return))

    (global-mouse/register-mouse-handlers-action? action)
    (do (global-mouse/register!
          (global-mouse/register-mouse-handlers-action-id action)
          (global-mouse/register-mouse-handlers-action-on-move action)
          (global-mouse/register-mouse-handlers-action-on-down action)
          (global-mouse/register-mouse-handlers-action-on-release action))
        (reacl/return))

    (global-mouse/deregister-mouse-handlers-action? action)
    (do (global-mouse/deregister! (global-mouse/deregister-mouse-handlers-action-id action))
        (reacl/return))

    (search/search-action? action)
    (let [callback (search/search-action-callback action)
          address  (search/search-action-addresses action)]
      (send-renderer! app-state (graph-layout/make-search-address-message address callback))
      (reacl/return))

    (search/click-address-action? action)
    (let [address (search/click-address-action-address action)]
      (println action)
      (send-renderer! app-state (ai/make-click-address-message address))
      (reacl/return))

    (search/hovered-address-action? action)
    (let [address (search/hovered-address-action-address action)]
      (send-renderer! app-state (ai/make-hovered-address-message address))
      (reacl/return))

    (search/unhovered-address-action? action)
    (do (send-renderer! app-state (ai/make-unhovered-address-message))
        (reacl/return))

    (bithomp/fetch-users-info-action? action)
    (do (bithomp/get-users-info)
        (reacl/return))


    (inspector/mark-tx-action? action)
    (let [from    (inspector/mark-tx-action-from action)
          targets (inspector/mark-tx-action-targets action)]
      (do (send-renderer! app-state (ai/make-app-mark-transaction-message from targets))
          (reacl/return)))

    (inspector/unmark-tx-action? action)
    (let [from    (inspector/unmark-tx-action-from action)
          targets (inspector/unmark-tx-action-targets action)]
      (do (send-renderer! app-state (ai/make-app-unmark-transaction-message from targets))
          (reacl/return)))

    (delayed/delayed-action? action)
    (do (delayed/handle action)
        (reacl/return))))


(reacl/render-component (.getElementById ^js/Document js/document "renderer") app
                        (reacl/opt :reduce-action handle-actions)
                        (make-app-state nil nil nil (make-default-mode) nil nil
                          search/initial-state nil info/nop))
