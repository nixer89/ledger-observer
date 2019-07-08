(ns ledger-observer.components.search
  (:require [active.clojure.cljs.record :as rec :include-macros true]
            [active.clojure.sum-type :as st :include-macros true]
            [active.clojure.lens :as lens]
            [ledger-observer.bithomp-userinfo :as bithomp]
            [ledger-observer.components.scrollpane :as scrollpane]
            [reacl2.core :as reacl :include-macros true]
            [quick-type.core :as qt :include-macros true]
            [reacl2.dom :as dom :include-macros true]))

(qt/def-type status
  [(waiting [id])
   (error [id msg])
   (success [id address])
   (idle [])])

(def idle-inst (make-idle))

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

(qt/def-type message
  [interaction-message
   callback-message])

(qt/def-type action
  [(search-action [addresses callback])
   (click-address-action [address])
   (hovered-address-action [address])
   (unhovered-address-action [])])


(def initial-state (make-search-field-state nil (make-idle)))

(defn has-idle-status? [search-field-state]
  (idle? (search-field-state-status search-field-state)))

(defn make-result-callback [ref id]
  (fn [res]
    (reacl/send-message! ref
      (if (not-empty res)
       (make-search-result-success-message id res)
       (make-search-result-failure-message id)))))


(defn waiting-with-id? [search-field-state id]
  (let [?waiting (search-field-state-status search-field-state)]
    (and (waiting? ?waiting) (= (waiting-id ?waiting) id))))


(defn address-not-found-error [id]
  (make-error id "Address or name not found!"))

(def min-character-error
  (make-error nil "Please enter at least two characters"))

(defn not-min-length? [content] (>= 2 (count content)))

(defn ripple-address? [input]
  (boolean (re-find #"r[0-9a-zA-Z]{24,34}" input)))




(defn handle-interaction-message [this local-state msg]

  (st/match interaction-message msg

    (make-mark-address-message address)
    (reacl/return :action (make-hovered-address-action address))

    unmark-address-message?
    (reacl/return :action (make-unhovered-address-action))

    unblur-message?
    (reacl/return :app-state initial-state)

    (make-activate-search-result-message address)
    (reacl/return
      :action (make-click-address-action address)
      :action (make-unhovered-address-action)
      :app-state initial-state)

    (make-typed-message content)
    (let [id (str (gensym))]
      (cond

        (empty? content)
        (reacl/return
          :app-state initial-state
          :action (make-unhovered-address-action))

        (not-min-length? content)
        (reacl/return
          :app-state (-> local-state
                       (search-field-state-content content)
                       (search-field-state-status min-character-error))
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
              :action (make-unhovered-address-action)
              :action (make-search-action addresses (make-result-callback this id)))
            (reacl/return :app-state
              (-> local-state
                (search-field-state-content content)
                (search-field-state-status address-not-found-error)))))))
    )
  )


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

(defn show-error [this error-msg]
  (dom/div {:class "search-dropdown fade-in"}
    (dom/div {:class "error-info"}
      error-msg
      (dom/br)
      (dom/div {:class "error-info-back"}
        (dom/img {:src "images/clear.png"})
        (dom/span
          {:onclick #(reacl/send-message! this (make-unblur-message))}
          "Clear search")))))


(reacl/defclass search-field this local-state [hovered clicked]

  refs [field]

  render
  (let [?content     (search-field-state-content local-state)
        content      (bithomp/get-name (or hovered ?content clicked ""))
        clicked?     (and clicked (not (or hovered ?content)))
        searching?   (not (or clicked hovered (not ?content)))
        status-field (search-field-state-status local-state)]

    (dom/div {:class "search-field fade-in"}

      (dom/input
        {:placeholder "Search for address or name"
         :id          "sf"
         :onchange    #(reacl/send-message! this
                         (make-typed-message (.-value (reacl/get-dom field))))
         :onblur      #(js/window.setTimeout
                         (fn [] (reacl/send-message! this (make-unblur-message))) 100)
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


      (st/match status status-field

        (make-error _ error-msg)
        (show-error this error-msg)

        (make-success _ address)
        (dom/div {:class "search-dropdown fade-in"}
          (scrollpane/pane
            false
            {:color    "white"    :height "350px"
             :position "absolute" :top    0 :left 0}
            results
            address
            this))

        idle? nil
        waiting? nil
        )))


  handle-message
  (fn [msg]
    (st/match message msg

      interaction-message?
      (handle-interaction-message this local-state msg)

      (make-search-result-failure-message id)
      (if (waiting-with-id? local-state id)
        (reacl/return
          :app-state (search-field-state-status local-state (address-not-found-error id))
          :action (make-unhovered-address-action))
        (reacl/return))

      (make-search-result-success-message id results)
      (if (waiting-with-id? local-state id)
        (reacl/return :app-state
          (search-field-state-status local-state (make-success id results)))
        (reacl/return :app-state local-state))



    )
  ))
