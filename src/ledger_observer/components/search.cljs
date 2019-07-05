(ns ledger-observer.components.search
  (:require [active.clojure.record :as rec :include-macros true]
            [active.clojure.lens :as lens]
            [ledger-observer.bithomp-userinfo :as bithomp]
            [ledger-observer.components.scrollpane :as scrollpane]
            [reacl2.core :as reacl :include-macros true]
            [reacl2.dom :as dom :include-macros true]))

(rec/define-record-type Waiting
  (make-waiting id) waiting?
  [id waiting-id])

(rec/define-record-type Error
  (make-error id msg) error?
  [id error-id
   msg error-msg])

(rec/define-record-type Success
  (make-success id address) success?
  [id success-id
   address success-address])

(rec/define-record-type EmptyState (make-empty-state) empty-state? [])


(rec/define-record-type SearchFieldState
  (make-search-field-state content state) search-field-state?
  [content search-field-state-content
   state search-field-state-state])


(rec/define-record-type SearchResultSuccessMessage
  (make-search-result-success-message id address) search-result-success-message?
  [id search-result-success-message-id
   address search-result-success-message-address])

(rec/define-record-type SearchResultFailureMessage
  (make-search-result-failure-message id) search-result-failure-message?
  [id search-result-failure-message-id])

(rec/define-record-type SearchAction
  (make-search-action addresses callback) search-action?
  [addresses search-action-addresses
   callback search-action-callback])

(rec/define-record-type ChangedMessage
  (make-changed-message content) changed-message?
  [content changed-message-content])

(rec/define-record-type ActiveSearchResultMessage
  (make-activate-search-result-message address) activate-search-result-message?
  [address activate-search-result-message-address])

(rec/define-record-type MarkAddressMessage
  (make-mark-address-message address) mark-address-message?
  [address mark-address-message-address])

(rec/define-record-type UnmarkAddressMessage
  (make-unmark-address-message address) unmark-address-message?
  [address unmark-address-message-address])

(rec/define-record-type SubmittedMessage (make-submitted-message) submitted-message? [])

(rec/define-record-type ClickAddressAction
  (make-click-address-action address) click-address-action?
  [address click-address-action-address])

(rec/define-record-type HoveredAdressAction
  (make-hovered-address-action address) hovered-address-action?
  [address hovered-address-action-address])

(rec/define-record-type UnoveredAdressAction (make-unhovered-address-action) unhovered-address-action? [])

(rec/define-record-type ResetMessage (make-reset-message) reset-message? [])
(rec/define-record-type UnblurMessage (make-unblur-message) unblur-message? [])

(def initial-state (make-search-field-state nil (make-empty-state)))

(defn is-empty-state? [search-field-state]
  (empty-state? (search-field-state-state search-field-state)))

(defn make-result-callback [ref id]
  (fn [res]
    (reacl/send-message! ref
      (if (not-empty res)
       (make-search-result-success-message id res)
       (make-search-result-failure-message id)))))


(defn ripple-address? [input]
  (boolean (re-find #"r[0-9a-zA-Z]{24,34}" input)))

(reacl/defclass results this result-entries [parent]
  render
  (dom/ul
    (map-indexed
     (fn [idx result]
       (dom/keyed (str idx)
         (let [onclick-handler #(reacl/send-message! parent (make-activate-search-result-message result))
               onover-handler  #(reacl/send-message! parent (make-mark-address-message result))
               onout-handler   #(reacl/send-message! parent (make-unmark-address-message result))]
           (dom/li {:onclick      onclick-handler
                    :onmouseenter onover-handler
                    :onmouseleave onout-handler}
             (if-let [name (bithomp/get-name result)]
               (dom/div {:class "result-entry"}
                (dom/div {:class "result-entry-details"}
                  (dom/div {:class "result-entry-name"} name)
                  (dom/div {:class "result-entry-info"} result))
                (dom/i {:class "material-icons"} "done"))
               (dom/div {:class "result-entry"} "Inspect address"))))))
     result-entries)))


(reacl/defclass search-field this local-state [hovered clicked]

  refs [field]

  render
  (let [?content (search-field-state-content local-state)
        content (bithomp/get-name (or hovered ?content clicked ""))
        clicked? (and clicked (not (or hovered ?content)))
        searching? (not (or clicked hovered (not ?content)))
        state   (search-field-state-state local-state)]

    (dom/div {:class "search-field fade-in"}
      (dom/input
        {:placeholder "Search for address or name"
        :id          "sf"
        :onchange    #(reacl/send-message! this (make-changed-message (.-value (reacl/get-dom field))))
        :onblur      #(js/window.setTimeout (fn [] (reacl/send-message! this (make-unblur-message))) 100)
        :ref         field
        :value       content})

      (dom/div {:class "search-state"}
        (cond
          clicked?
          (dom/i {:class "material-icons"} "done")

          searching?
          (dom/i {:class "material-icons"} "search")

          hovered
          (dom/i {:class "material-icons"} "visibility")))

      (when (error? state)
        (dom/div {:class "search-dropdown fade-in"}
          (dom/div {:class "error-info"}
            (error-msg state)
            (dom/br)
            (dom/div {:class "error-info-back"}
              (dom/i {:class "material-icons"} "clear")
              (dom/span
                {:onclick #(reacl/send-message! this (make-unblur-message))}
                "Clear search")))))

      (when (success? state)
       (dom/div {:class "search-dropdown fade-in"}
         (scrollpane/pane
           false
           {:color    "white"    :height "350px"
            :position "absolute" :top    0 :left 0}
           results
           (success-address state)
           this)))))

  handle-message
  (fn [msg]
    (cond

      (reset-message? msg)
      (reacl/return :app-state
        (-> local-state
          (search-field-state-state (make-empty-state))))

      (changed-message? msg)
      (let [id (str (gensym))
            content (changed-message-content msg)]
        (cond

          (zero? (count content))
          (reacl/return
            :app-state (-> local-state
                           (search-field-state-content content)
                           (search-field-state-state (make-empty-state)))
            :action (make-unhovered-address-action))

          (>= 2 (count content))
          (reacl/return
            :app-state (-> local-state
                           (search-field-state-content content)
                           (search-field-state-state (make-error nil "Please enter at least two characters")))
            :action (make-unhovered-address-action))

          (ripple-address? content)
          (reacl/return
            :action (make-search-action [content] (make-result-callback this id))
            :action (make-unhovered-address-action)
            :app-state (-> local-state
                           (search-field-state-content content)
                           (search-field-state-state (make-waiting id))))

          :default
          (let [addresses (bithomp/addresses-by-name content)]
            (if (not-empty addresses)
              (reacl/return :app-state
                (-> local-state
                  (search-field-state-content content)
                  (search-field-state-state (make-waiting id)))
                :action (make-search-action addresses (make-result-callback this id))
                :action (make-unhovered-address-action))
              (reacl/return :app-state
                (-> local-state
                  (search-field-state-content content)
                  (search-field-state-state (make-error nil "Address or name not found!"))))))))



      (search-result-failure-message? msg)
      (reacl/return :app-state
        (let [state (search-field-state-state local-state)]
          (if (and (waiting? state)
                (= (search-result-failure-message-id msg) (waiting-id state)))
            (search-field-state-state local-state (make-error (waiting-id state) "Address or name not found!"))
            local-state))
        :action (make-unhovered-address-action))

      (search-result-success-message? msg)
      (let [state (search-field-state-state local-state)
            msg-id (search-result-success-message-id msg)
            msg-res (search-result-success-message-address msg)]
        (if (and (waiting? state) (= (waiting-id state)))
          (reacl/return :app-state (search-field-state-state local-state (make-success msg-id msg-res)))
          (reacl/return :app-state local-state)))

      (activate-search-result-message? msg)
      (reacl/return
        :action (make-click-address-action (activate-search-result-message-address msg))
        :action (make-unhovered-address-action)
        :app-state (-> local-state
                       (search-field-state-state (make-empty-state))
                       (search-field-state-content nil)))

      (mark-address-message? msg)
      (reacl/return :action (make-hovered-address-action (mark-address-message-address msg)))

      (unmark-address-message? msg)
      (reacl/return :action (make-unhovered-address-action))

      (unblur-message? msg)
      (reacl/return :app-state
        (-> local-state
          (search-field-state-content nil)
          (search-field-state-state (make-empty-state))))
    )
  ))
