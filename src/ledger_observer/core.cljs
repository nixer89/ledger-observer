(ns ledger-observer.core
  (:require [active.clojure.cljs.record :as rec :include-macros true]
            [active.clojure.lens :as lens]
            cljsjs.jquery
            [reacl2.core :as reacl :include-macros true]
            [reacl2.dom :as dom :include-macros true]
            [quick-type.core :as qt :include-macros true]
            [active.clojure.sum-type :as st :include-macros true]
            [ledger-observer.visualization.render :as renderer]
            [ledger-observer.socket :as socket]
            [ledger-observer.mouse :as global-mouse]
            [ledger-observer.menu :as menu]
            [ledger-observer.info :as info]
            [ledger-observer.delayed :as delayed]
            [ledger-observer.mailbox :as mailbox]
            [ledger-observer.components.search :as search]
            [ledger-observer.components.search.data :as search-data]
            [ledger-observer.components.settings :as settings]
            [ledger-observer.components.heartbeat :as heartbeat]
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


(qt/def-record app-state
  [render-interaction-mailbox
   render-tx-mailbox
   filter-mailboxes
   ledger-mailbox
   connection
   search-state
   inspector-state
   tx-stats
   settings
   info])

(def inspector-txs-lens
  (lens/>> app-state-inspector-state inspector/state-txs))

(defn maybe-clicked-address [app-state]
  (-> app-state
    app-state-search-state
    search-data/state-result
    search-data/show-result))


(defn maybe-hovered-address [app-state]
  (-> app-state
    app-state-search-state
    search-data/state-highlighted
    search-data/show-highlighted))


(defn set-hovered-address [app-state address]
  (lens/overhaul
    app-state
    app-state-search-state
    #(search-data/highlight-address % address)))

(defn unset-hovered-address [app-state]
  (lens/overhaul
    app-state
    app-state-search-state
    #(search-data/highlight-address % nil)))


(defn set-clicked-address [app-state address]
  (-> app-state
   (lens/overhaul
     app-state-search-state
     #(search-data/set-result % address))
   (inspector-txs-lens [])))


(defn unset-clicked-address [app-state]
  (lens/overhaul
    app-state
    app-state-search-state
    #(search-data/clear-result %)))




(qt/def-type color-node-action-t
  [(highlight-node-action [address])
   (unhighlight-node-action [])
   (select-node-action [address])
   (unselect-node-action [address])])



(defn maybe-make-hovered-action [search-state-old search-state-new]
  (let [highlighted-new (search-data/state-highlighted search-state-new)
        highlighted-old (search-data/state-highlighted search-state-old)]
    (if (= highlighted-new highlighted-old)
      []
      (st/match search-data/highlighted-t highlighted-new
        search-data/none-highlighted? [:action (make-unhighlight-node-action)]
        (search-data/make-highlighted address) [:action (make-highlight-node-action address)]))))



(defn maybe-make-selected-action [search-state-old search-state-new]
  (let [selected-new (search-data/state-result search-state-new)
        selected-old (search-data/state-result search-state-old)]
    (if (= selected-new selected-old)
      []
      (st/match search-data/result-t selected-new

        search-data/no-result?
        [:action (make-unselect-node-action (search-data/result-address selected-old))]

        (search-data/make-result address)
        [:action (make-select-node-action address)
         :action (make-unhighlight-node-action)]))))



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

(qt/def-record fetch-ledger-number-action [receiver])


(rec/define-record-type NewTxsMessage
  (make-new-txs-message txs address) new-txs-message?
  [txs new-txs-message-txs
   address new-txs-message-address])



(rec/define-record-type IncreaseTxMessage
  (make-increase-tx-message n) increase-tx-message?
  [n increase-tx-message-n])


(rec/define-record-type TriggerTxsFetchMessage
  (make-trigger-txs-fetch-message) trigger-txs-fetch-message? [])

(qt/def-record trigger-fetch-ledger-number-message [])


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




(defn new-transaction-event->tx [event address]
  (inspector/make-tx
    (data/new-transaction-event-tid event)
    (data/new-transaction-event-from event)
    (data/new-transaction-event-targets event)
    (data/new-transaction-event-type event)
    (if (= address (data/new-transaction-event-from event)) "Issuer" "Attendee")
    (data/new-transaction-event-success? event)))


(rec/define-record-type TickMeEverySecondAction
  (make-tick-me-every-second-action receiver) tick-me-every-second-action?
  [receiver tick-me-every-second-action-receiver])


(rec/define-record-type TickMessage
  (make-tick-message) tick-message? [])

(defn app-state->fetch-filter-action [app-state reacl-component]
  (make-fetch-filtered-tx-action reacl-component
    (-> app-state app-state-filter-mailboxes data/filter-mailboxes-app-mailbox)
    (-> app-state maybe-clicked-address)))


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

(def visible? (lens/>> app-state-settings settings/settings-interface-visible?))
(defn maybe-invisible [m app-state]
  (if (visible? app-state)
    (update m :class #(str % " animated-show"))
    (update m :class #(str % " animated-hide"))))

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

          filter-mailboxes (data/make-filter-mailboxes
                             (mailbox/make-mailbox) (mailbox/make-mailbox) (mailbox/make-mailbox))]

      (global-mouse/init!)

      (reacl/return
        :app-state (make-app-state render-interaction-mailbox render-tx-mailbox filter-mailboxes
                     (mailbox/make-mailbox)
                     (make-not-connected-mode) search-data/initial-public-state
                     inspector/initial-state
                     counter/initial-state
                     settings/initial-state
                     info/nop)
        :action (make-tick-me-every-second-action this)
        :action (bithomp/make-fetch-users-info-action this)
        :action (make-fetch-ledger-number-action this)
        :action (make-create-socket-action this))))


  render

  (let [info (app-state-info app-state)]

    (dom/div

      (dom/div (-> {:class "logo-bottom-right"}
                 (maybe-invisible app-state)))

      #_(menu/menu (reacl/opt :reaction (reacl/reaction this #(make-show-info-message %)))
        (app-state-info app-state))
      (dom/div (-> {:class "right-menu"}
                 (maybe-invisible app-state))
       (dom/ul
         (dom/li
           {:style {:padding-right "10px"}
            :onclick #(reacl/send-message! this (make-show-info-message info/about))
            :class "menu-image"}
           (dom/img {:src "images/info.png" :style {:width "23px" :height "23px"}}))
         (dom/li
           {:class "menu-image github-icon"}
           (dom/a {:href "https://github.com/nixer89/ledger-observer" :target "_blank"}
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


      (dom/div (-> {:class "sidebar" :style {:will-change "content"}}
                 (maybe-invisible app-state))

       (search/search-field
         (reacl/opt :reaction (reacl/reaction this #(make-update-search-state-message %)))
         (app-state-search-state app-state))

       (when-let [?clicked-address (maybe-clicked-address app-state)]
         (dom/keyed ?clicked-address
           (dom/span
             (inspector/inspector-menu
               (reacl/opt :reaction reacl/no-reaction)
               nil
               ?clicked-address
               #(reacl/send-message! this (make-unset-address-message)))
             (inspector/inspector
               (reacl/opt :reaction reacl/no-reaction)
               (app-state-inspector-state app-state)))))

       (settings/panel
         (reacl/opt
           :reduce-action (fn [_ action] (reacl/return :action action))
           :embed-app-state (fn [as settings] (app-state-settings as settings)))
         (app-state-settings app-state))

       (dom/div (-> {:style {:position "absolute"
                             :width    "100%"
                             :bottom   "0"}}
                  (maybe-invisible app-state))
         (cond
           (not-connected-mode? (app-state-connection app-state))
           (dom/div
             {:class "connecting-label fade-in"}
             "Connecting to wss://xrplcluster.com")

           (app-state-tx-stats app-state)
           (counter/counter (reacl/opt :reaction reacl/no-reaction) (app-state-tx-stats app-state) this)))
       )





      (dom/div {:id          "renderer-container"
                :onmousemove (fn [^js/MouseEvent ev] (mouse/update-render-mouse-position! ev))
                :onclick     (fn [_] (send-renderer! app-state (mouse/make-clicked-message)))})

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

      (data/update-ledger-number-message? msg)
      (let [ledger-number   (data/update-ledger-number-message-ledger-number msg)
            next-app-state (lens/overhaul
                             app-state
                             (lens/>> app-state-tx-stats counter/counter-state-ledgers)
                             #(heartbeat/next-heartbeat-state % ledger-number))]
        (println msg)
        (reacl/return :app-state next-app-state))

      (new-txs-message? msg)
      (reacl/return :app-state
        (let [?address (maybe-clicked-address app-state)]
          (if (= ?address (new-txs-message-address msg))
            (lens/overhaul app-state inspector-txs-lens
              (fn [txs] (concat (map #(new-transaction-event->tx % (new-txs-message-address msg))
                                  (remove nil? (new-txs-message-txs msg))) txs)))
            app-state)))

      (trigger-fetch-ledger-number-message? msg)
      (reacl/return :action (make-fetch-ledger-number-action this))

      (trigger-txs-fetch-message? msg)
      (if (maybe-clicked-address app-state)
        (reacl/return :action (app-state->fetch-filter-action app-state this))
        (reacl/return))

      (unset-address-message? msg)
      (reacl/return
        :app-state (-> app-state
                     (unset-clicked-address)
                     (unset-hovered-address))
        :action (make-unselect-node-action (maybe-clicked-address app-state)))


      (set-address-message? msg)
      (let [address  (set-address-message-address msg)]
        (reacl/return
          :app-state (set-clicked-address app-state address)
          :action (app-state->fetch-filter-action app-state this)
          :action (make-unselect-node-action (maybe-clicked-address app-state))
          :action (make-select-node-action address)))

      (increase-tx-message? msg)
      (let [old-tx (app-state-tx-stats app-state)]
        (reacl/return :app-state
          (app-state-tx-stats app-state (counter/counter-state-tx old-tx (increase-tx-message-n msg)))))

      (tick-message? msg)
      (let [old-tx (app-state-tx-stats app-state)]
        (reacl/return :app-state
          (app-state-tx-stats app-state (lens/overhaul old-tx counter/counter-state-seconds inc))))

      (update-search-state-message? msg)
      (let [search-state (update-search-state-message-state msg)
            current-search-state (app-state-search-state app-state)
            ;; check if new clicked action
            ?selected-action (maybe-make-selected-action current-search-state search-state)
            ;; check if new hovered action
            ?highlighted-action (maybe-make-hovered-action current-search-state search-state)
            ?actions (concat ?selected-action ?highlighted-action
                       [:action (app-state->fetch-filter-action app-state this)])
            new-app-state (-> (if (not-empty ?selected-action)
                                (inspector-txs-lens app-state [])
                                app-state)
                            (app-state-search-state search-state))]
        (if (empty? ?actions)
          (reacl/return :app-state new-app-state)
          (apply reacl/return :app-state (app-state-search-state app-state search-state) ?actions)))

      (hover-address-message? msg)
      (reacl/return :app-state
        (set-hovered-address app-state (hover-address-message-address msg)))

      (show-info-message? msg)
      (reacl/return :app-state
        (app-state-info app-state (show-info-message-info msg)))

      (hide-info-message? msg)
      (reacl/return :app-state
        (app-state-info app-state info/nop))
      )))





(defn handle-actions [app-state action]

  (cond

    (settings/settings-action-t? action)
    (settings/handle-action action (app-state-render-interaction-mailbox app-state))

    (create-socket-action? action)
    (do (create-ripple-socket! (create-socket-action-handler action))
        (reacl/return))

    (setup-socket&receive-action? action)
    (let [render-tx-mailbox (app-state-render-tx-mailbox app-state)
          socket-mailbox    (socket-mailbox-lens app-state)
          app-mailbox       (-> app-state app-state-filter-mailboxes data/filter-mailboxes-app-mailbox)
          ledger-mailbox    (app-state-ledger-mailbox app-state)
          socket            (setup-socket&receive-action-socket action)]
      (socket/setup-ripple-socket! socket render-tx-mailbox socket-mailbox app-mailbox ledger-mailbox)
      (reacl/return
        :app-state (app-state-connection app-state (make-default-mode))))

    (fetch-filtered-tx-action? action)
    (let [^js/Object receiver (fetch-filtered-tx-action-receiver action)
          mailbox             (fetch-filtered-tx-action-mailbox action)
          msgs                (mailbox/receive-n mailbox 10)
          txs                 (map second msgs)
          ;; FIXME: This must be done properly later.
          ;; There can be many filter addresses later if various filter addresses
          ;; were set. Now we only accept one node in clicked state, so all addresses
          ;; must be equal.
          addr                (first (first (map first msgs)))]
      (js/window.setTimeout
        #(when (.isMounted receiver)
           (reacl/send-message! receiver (make-trigger-txs-fetch-message))) 200)
      (if addr
        (reacl/return :message [receiver (make-new-txs-message txs addr)])
        (reacl/return)))

    (fetch-ledger-number-action? action)
    (let [^js/Object receiver (fetch-ledger-number-action-receiver action)
          mailbox             (app-state-ledger-mailbox app-state)
          msg                (first (mailbox/receive-n mailbox 10))
          ;; FIXME: This must be done properly later.
          ;; There can be many filter addresses later if various filter addresses
          ;; were set. Now we only accept one node in clicked state, so all addresses
          ;; must be equal.
          ]
      (js/window.setTimeout
        #(when (.isMounted receiver)
           (reacl/send-message! receiver (make-trigger-fetch-ledger-number-message))) 200)
      (if msg
        (reacl/return :message [receiver (data/make-update-ledger-number-message
                                           (data/update-ledger-number-event-ledger-number msg))])
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

    (search-data/search-action? action)
    (let [callback (search-data/search-action-callback action)
          address  (search-data/search-action-addresses action)]
      (send-renderer! app-state (graph-layout/make-search-address-message address callback))
      (reacl/return))



    (color-node-action-t? action)
    (st/match color-node-action-t action

      (make-unselect-node-action addr)
      (do (send-renderer! app-state (ai/make-unclick-address-message))
          (remove-filter! app-state addr)
          (reacl/return))

      (make-select-node-action addr)
      (do (send-renderer! app-state (ai/make-click-address-message addr))
          (put-filter! app-state addr)
          (reacl/return))

      (make-highlight-node-action addr)
      (do (send-renderer! app-state (ai/make-hovered-address-message addr))
          (reacl/return))

      unhighlight-node-action?
      (do (send-renderer! app-state (ai/make-unhovered-address-message))
          (reacl/return)))


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
    (do (delayed/handle-delayed action)
        (reacl/return))


    ))


(reacl/render-component (.getElementById ^js/Document js/document "renderer") app
                        (reacl/opt :reduce-action handle-actions)
                        (make-app-state nil nil nil (mailbox/make-mailbox) (make-default-mode) search-data/initial-public-state inspector/initial-state nil settings/initial-state info/nop))
