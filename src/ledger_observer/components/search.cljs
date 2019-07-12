(ns ledger-observer.components.search
  (:require [active.clojure.cljs.record :as rec :include-macros true]
            [active.clojure.sum-type :as st :include-macros true]
            [active.clojure.lens :as lens]
            [ledger-observer.bithomp-userinfo :as bithomp]
            [ledger-observer.components.scrollpane :as scrollpane]
            [reacl2.core :as reacl :include-macros true]
            [quick-type.core :as qt :include-macros true]
            [reacl2.dom :as dom :include-macros true]))



;; ==========================================================
;;                       DATA SHAPES
;; ==========================================================

;; Represents the status of the search field. A status is
;; a status is one of the following:

;; * waiting: waiting for the results of a search request with id `id`
;; * error: received and error for search request with id `id` with message `msg`
;; * success: a request with `id` found `address` 
;; * idle: nothings on.

(qt/def-type status-t
  [
   (waiting [id])
   (error [msg])
   (success [address])
   (idle [])])

;; constant for idle
(def idle-inst (make-idle))


;; Represents the state of a search field. Consists of content,
;; which is typed content and a status of type status-t

(qt/def-record search-field-state [content status])


(qt/def-type result-t
  [(no-result [])
   (search-result [result])])

(def no-result-inst (make-no-result))

(qt/def-type highlighted-t
  [(none-highlighted [])
   (highlighted [address])])


(def none-highlighted-inst (make-none-highlighted))

(qt/def-record search-field-public-state [highlighted result])


(defn highlight-address [public-state address]
  (search-field-public-state-highlighted public-state (make-highlighted address)))

(defn unhighlight-address [public-state address]
  (search-field-public-state-highlighted public-state none-highlighted-inst))

(defn clear-result [public-state]
  (search-field-public-state-result public-state no-result-inst))

(defn set-result [public-state address]
  (search-field-public-state-result public-state (make-search-result address)))

(qt/def-type callback-message
  [(search-result-failure-message [id])
   (search-result-success-message [id address])])

(qt/def-type interaction-message-t
  [(set-result-message [address])
   (highlight-address-message [address])
   (unhighlight-address-message [address])
   (clear-result-message [])
   (typed-message [content])])

(qt/def-type message-t
  [interaction-message-t
   callback-message])

(qt/def-type action-t
  [(search-action [addresses callback])])


(def initial-public-state (make-search-field-public-state none-highlighted-inst no-result-inst))
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


(def address-not-found-error
  (make-error "Address or name not found!"))

(def min-character-error
  (make-error "Please enter at least two characters"))

(defn not-min-length? [content] (>= 2 (count content)))

(defn ripple-address? [input]
  (boolean (re-find #"r[0-9a-zA-Z]{24,34}" input)))




(defn handle-interaction-message [this public-state local-state msg]

  (st/match interaction-message-t msg

    (make-highlight-address-message address)
    (reacl/return :app-state (highlight-address public-state address))

    (make-unhighlight-address-message address)
    (reacl/return :app-state (unhighlight-address public-state address))

    clear-result-message?
    (reacl/return
      :local-state initial-state
      :app-state (clear-result public-state))

    (make-set-result-message address)
    (reacl/return
      :app-state (set-result public-state address)
      :local-state initial-state)

    (make-typed-message content)
    (apply reacl/return
      :app-state initial-public-state
      (let [id (str (gensym))]
        (cond

          (empty? content)
          [:local-state initial-state]

          (not-min-length? content)
          [:local-state (-> local-state
                          (search-field-state-content content)
                          (search-field-state-status min-character-error))]

          (ripple-address? content)
          [:action (make-search-action [content] (make-result-callback this id))
           :local-state (-> local-state
                          (search-field-state-content content)
                          (search-field-state-status (make-waiting id)))]

          :default
          (let [addresses (bithomp/addresses-by-name content)]
            (if (not-empty addresses)
              [:local-state (-> local-state
                              (search-field-state-content content)
                              (search-field-state-status (make-waiting id)))
               :action (make-search-action addresses (make-result-callback this id))]
              [:local-state
               (-> local-state
                 (search-field-state-content content)
                 (search-field-state-status address-not-found-error))])))))

    :default "12"
    ))


(reacl/defclass results this result-entries [parent]
  render
  (dom/ul
    (map-indexed
     (fn [idx result]
       (dom/keyed (str idx)
         (let [send-parent! (fn [m] #(reacl/send-message! parent m))
               onclick-handler (send-parent! (make-set-result-message result))
               onover-handler  (send-parent! (make-highlight-address-message result))
               onout-handler   (send-parent! (make-unhighlight-address-message result))]
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
          {:onclick #(reacl/send-message! this (make-clear-result-message))}
          "Clear search")))))



(reacl/defclass search-field this app-state []

  refs [field]

  local-state [local-state initial-state]

  render
  (let [?highlighted (st/match highlighted-t (search-field-public-state-highlighted app-state)
                   none-highlighted? nil
                   (make-highlighted addr) addr)
        ?result (st/match result-t (search-field-public-state-result app-state)
                  no-result? nil
                  (make-search-result addr) addr)
        content    (bithomp/get-name
                    (or ?highlighted
                      (search-field-state-content local-state)
                      ?result
                      ""))
        searching? (waiting? (search-field-state-status local-state))
        status (search-field-state-status local-state)]

    (dom/div {:class "search-field fade-in"}

      (dom/input
        {:placeholder "Search for address or name"
         :id          "sf"
         :onchange    #(reacl/send-message! this
                         (make-typed-message (.-value (reacl/get-dom field))))
         :ref         field
         :value       content})


      (dom/div {:class "search-state"}
        (cond
          ?result
          (dom/img {:src "images/done.png"})

          ?highlighted
          (dom/img {:src "images/visibility.png"})

          (search-field-state-content local-state)
          (dom/img {:src "images/search.png"})
          ))


      (st/match status-t status

        (make-error error-msg)
        (show-error this error-msg)

        (make-success address)
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
    (st/match message-t msg

      interaction-message-t?
      (handle-interaction-message this app-state local-state msg)

      (make-search-result-failure-message id)
      (if (waiting-with-id? local-state id)
        (reacl/return :local-state (search-field-state-status local-state address-not-found-error))
        (reacl/return))

      (make-search-result-success-message id results)
      (do
        (if (waiting-with-id? local-state id)
          (reacl/return :local-state (search-field-state-status local-state (make-success results)))
          (reacl/return))))))
