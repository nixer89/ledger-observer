(ns ledger-observer.components.search
  (:require [active.clojure.cljs.record :as rec :include-macros true]
            [active.clojure.lens :as lens]
            [ledger-observer.bithomp-userinfo :as bithomp]
            [ledger-observer.components.scrollpane :as scrollpane]
            [reacl2.core :as reacl :include-macros true]
            [quick-type.core :as qt :include-macros true]
            [reacl2.dom :as dom :include-macros true]))

(qt/def-type status [(waiting [id])
                     (error [id msg])
                     (success [id address])
                     (empty-state [])])

(qt/def-record search-field-state [content status])

(qt/def-type callback-message
  [(search-result-failure-message [id])
   (search-result-success-message [id address])])


(qt/def-type interaction-message
  [(activate-search-result-message [address])
   (mark-address-message [address])
   (unmark-address-message [address])
   (unblur-message [])
   (typed-message [content])])


(qt/def-type action
  [(search-action [addresses callback])
   (click-address-action [address])
   (hovered-address-action [address])
   (unhovered-address-action [])])


(def initial-state (make-search-field-state nil (make-empty-state)))

(defn is-empty-state? [search-field-state]
  (empty-state? (search-field-state-status search-field-state)))

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
                (dom/img {:src "images/done.png"}))
               (dom/div {:class "result-entry"} "Inspect address"))))))
     result-entries)))


(reacl/defclass search-field this local-state [hovered clicked]

  refs [field]

  render
  (let [?content (search-field-state-content local-state)
        content (bithomp/get-name (or hovered ?content clicked ""))
        clicked? (and clicked (not (or hovered ?content)))
        searching? (not (or clicked hovered (not ?content)))
        state   (search-field-state-status local-state)]

    (dom/div {:class "search-field fade-in"}
      (dom/input
        {:placeholder "Search for address or name"
        :id          "sf"
        :onchange    #(reacl/send-message! this (make-typed-message (.-value (reacl/get-dom field))))
        :onblur      #(js/window.setTimeout (fn [] (reacl/send-message! this (make-unblur-message))) 100)
        :ref         field
        :value       content})

      (dom/div {:class "search-state"}
        (cond
          clicked?
          (dom/img {:src "images/done.png"})

          searching?
          (dom/img {:src "images/search.png"})

          hovered
          (dom/img {:src "images/visibility.png"})))

      (when (error? state)
        (dom/div {:class "search-dropdown fade-in"}
          (dom/div {:class "error-info"}
            (error-msg state)
            (dom/br)
            (dom/div {:class "error-info-back"}
              (dom/img {:src "images/clear.png"})
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

      (typed-message? msg)
      (let [id (str (gensym))
            content (typed-message-content msg)]
        (cond

          (zero? (count content))
          (reacl/return
            :app-state (-> local-state
                           (search-field-state-content content)
                           (search-field-state-status (make-empty-state)))
            :action (make-unhovered-address-action))

          (>= 2 (count content))
          (reacl/return
            :app-state (-> local-state
                           (search-field-state-content content)
                           (search-field-state-status (make-error nil "Please enter at least two characters")))
            :action (make-unhovered-address-action))

          (ripple-address? content)
          (reacl/return
            :action (make-search-action [content] (make-result-callback this id))
            :action (make-unhovered-address-action)
            :app-state (-> local-state
                           (search-field-state-content content)
                           (search-field-state-status (make-waiting id))))

          :default
          (let [addresses (bithomp/addresses-by-name content)]
            (if (not-empty addresses)
              (reacl/return :app-state
                (-> local-state
                  (search-field-state-content content)
                  (search-field-state-status (make-waiting id)))
                :action (make-search-action addresses (make-result-callback this id))
                :action (make-unhovered-address-action))
              (reacl/return :app-state
                (-> local-state
                  (search-field-state-content content)
                  (search-field-state-status (make-error nil "Address or name not found!"))))))))



      (search-result-failure-message? msg)
      (reacl/return :app-state
        (let [state (search-field-state-status local-state)]
          (if (and (waiting? state)
                (= (search-result-failure-message-id msg) (waiting-id state)))
            (search-field-state-status local-state (make-error (waiting-id state) "Address or name not found!"))
            local-state))
        :action (make-unhovered-address-action))

      (search-result-success-message? msg)
      (let [state (search-field-state-status local-state)
            msg-id (search-result-success-message-id msg)
            msg-res (search-result-success-message-address msg)]
        (if (and (waiting? state) (= (waiting-id state)))
          (reacl/return :app-state (search-field-state-status local-state (make-success msg-id msg-res)))
          (reacl/return :app-state local-state)))

      (activate-search-result-message? msg)
      (reacl/return
        :action (make-click-address-action (activate-search-result-message-address msg))
        :action (make-unhovered-address-action)
        :app-state (-> local-state
                       (search-field-state-status (make-empty-state))
                       (search-field-state-content nil)))

      (mark-address-message? msg)
      (reacl/return :action (make-hovered-address-action (mark-address-message-address msg)))

      (unmark-address-message? msg)
      (reacl/return :action (make-unhovered-address-action))

      (unblur-message? msg)
      (reacl/return :app-state
        (-> local-state
          (search-field-state-content nil)
          (search-field-state-status (make-empty-state))))
    )
  ))
