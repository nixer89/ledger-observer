(ns ledger-observer.components.search
  (:require [active.clojure.cljs.record :as rec :include-macros true]
            [active.clojure.sum-type :as st :include-macros true]
            [active.clojure.lens :as lens]
            [ledger-observer.bithomp-userinfo :as bithomp]
            [ledger-observer.components.scrollpane :as scrollpane]
            [ledger-observer.components.search.data :as d]
            [reacl2.core :as reacl :include-macros true]
            [quick-type.core :as qt :include-macros true]
            [reacl2.dom :as dom :include-macros true]))


(defn make-result-callback [ref id]
  (fn [res]
    (reacl/send-message! ref
      (if (not-empty res)
       (d/make-result-success-message id res)
       (d/make-result-failure-message id)))))


(defn not-min-length? [content] (>= 2 (count content)))

(defn ripple-address? [input]
  (boolean (re-find #"r[0-9a-zA-Z]{24,34}" input)))



(defn handle-typed-message [receiver public-state local-state content]
  (apply reacl/return
    :app-state d/initial-public-state

    (let [id (str (gensym))
          callbacks (make-result-callback receiver id)]
      (cond

        (empty? content)
        [:local-state d/initial-state]

        (not-min-length? content)
        [:local-state (-> local-state
                        (d/with-content content)
                        (d/with-min-character-error))]

        (ripple-address? content)
        [:action (d/make-search-action [content] callbacks)
         :local-state (-> local-state
                        (d/with-content content)
                        (d/with-waiting id))]

        :default
        (let [addresses (bithomp/addresses-by-name content)]
          (if (not-empty addresses)
            [:local-state (-> local-state
                            (d/with-content content)
                            (d/with-waiting id))
             :action (d/make-search-action addresses callbacks)]
            [:local-state
             (-> local-state
               (d/with-content content)
               (d/with-address-not-found-error))]))))))



(defn handle-callback-message [local-state msg]

  (st/match d/callback-message-t msg

    (d/make-result-failure-message id)
    (if (d/waiting-with-id? local-state id)
      (reacl/return :local-state (d/with-address-not-found-error local-state))
      (reacl/return))

    (d/make-result-success-message id results)
    (do
      (if (d/waiting-with-id? local-state id)
        (reacl/return :local-state (d/with-results local-state results))
        (reacl/return)))))


(defn handle-interaction-message [receiver public-state local-state msg]

  (st/match d/interaction-message-t msg

    (d/make-highlight-address-message address)
    (reacl/return :app-state (d/highlight-address public-state address))

    (d/make-unhighlight-address-message address)
    (reacl/return :app-state (d/unhighlight-address public-state address))

    d/clear-result-message?
    (reacl/return
      :local-state d/initial-state
      :app-state (d/clear-result public-state))

    (d/make-set-result-message address)
    (reacl/return
      :app-state (d/set-result public-state address)
      :local-state d/initial-state)

    (d/make-typed-message content)
    (handle-typed-message receiver public-state local-state content)))


(defn result-entry [name result]
  (dom/div {:class "result-entry"}
    (dom/div {:class "result-entry-details"}
      (dom/div {:class "result-entry-name"} name)
      (dom/div {:class "result-entry-info"} result))
    (dom/img {:src "images/done.png"})))


(reacl/defclass results this result-entries [parent]
  render
  (dom/ul
    (map-indexed
     (fn [idx result]
       (dom/keyed (str idx)
         (let [send-parent! (fn [m] #(reacl/send-message! parent m))
               onclick-handler (send-parent! (d/make-set-result-message result))
               onover-handler  (send-parent! (d/make-highlight-address-message result))
               onout-handler   (send-parent! (d/make-unhighlight-address-message result))]
           (dom/li {:onclick      onclick-handler
                    :onmouseenter onover-handler
                    :onmouseleave onout-handler}
             (if-let [name (bithomp/get-name result)]
               (result-entry name result)
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
          {:onclick #(reacl/send-message! this (d/make-clear-result-message))}
          "Clear search")))))



(reacl/defclass search-field this app-state []

  refs [field]

  local-state [local-state d/initial-state]

  render
  (let [?highlighted         (d/show-highlighted (d/state-highlighted app-state))
        ?result              (d/show-result (d/state-result app-state))
        ?local-state-content (d/local-state-content local-state)
        string-content       (or ?highlighted ?local-state-content  ?result "")
        content              (bithomp/get-name string-content)
        searching?           (d/is-waiting? local-state)
        status               (d/local-state-status local-state)]

    (dom/div {:class "search-field fade-in"}

      (dom/input
        {:placeholder "Search for address or name"
         :onchange    #(reacl/send-message! this (d/make-typed-message (.-value (reacl/get-dom field))))
         :ref         field
         :value       content})


      (dom/div {:class "search-state"}
        (cond
          ?result
          (dom/img {:src "images/done.png"})

          ?highlighted
          (dom/img {:src "images/visibility.png"})

          (d/local-state-content local-state)
          (dom/img {:src "images/search.png"})))


      (st/match d/status-t status

        (d/make-error error-msg)
        (show-error this error-msg)

        (d/make-success address)
        (dom/div {:class "search-dropdown fade-in"}
          (scrollpane/pane false
            {:color    "white"    :height "350px"
             :position "absolute" :top    0 :left 0}
            results
            address
            this))

        d/idle? nil
        d/waiting? nil)))



  handle-message
  (fn [msg]
    (st/match d/message-t msg

      d/interaction-message-t?
      (handle-interaction-message this app-state local-state msg)

      d/callback-message-t?
      (handle-callback-message local-state msg)
)))
