(ns ledger-observer.components.search.data
  (:require [active.clojure.cljs.record :as rec :include-macros true]
            [active.clojure.sum-type :as st :include-macros true]
            [active.clojure.lens :as lens]
            [quick-type.core :as qt :include-macros true]))


;; Status Type
;; Represents the status of the search field in local-state. A status is
;; is one of the following:
;; * waiting: waiting for the results of a search request with id `id`
;; * error: received and error for search request with message `msg`
;; * success: a request found `addresses` 
;; * idle: nothings on.

(qt/def-type status-t
  [(waiting [id])
   (error [msg])
   (success [addresses])
   (idle [])])

;; constant for idle
(def idle-inst (make-idle))


;; specific errors

(def address-not-found-error
  (make-error "Address or name not found!"))

(def min-character-error
  (make-error "Please enter at least two characters"))



;; represents the local-state of the component.
;; consists of
;; * content, which holds the user input
;; * status, as described in status-t

(qt/def-record local-state [content status])

(defn has-idle-status?
  "Returns true if local state status is idle"
  [local-state]
  (idle? (local-state-status local-state)))

(defn is-waiting?
  "Returns true if local state status is waiting"
  [local-state]
  (waiting? (local-state-status local-state)))

(defn waiting-with-id?
  "Returns true if local state status is waiting and the id is `id`"
  [local-state id]
  (let [?waiting (local-state-status local-state)]
    (and (waiting? ?waiting) (= (waiting-id ?waiting) id))))

(defn with-min-character-error
  "Sets local-state's status to min-character-error"
  [local-state]
   (local-state-status local-state min-character-error))

(defn with-address-not-found-error
  "Sets local-state's status to address-not-found-error"
  [local-state]
  (local-state-status local-state address-not-found-error))

(defn with-content
  "Sets content in local-state"
  [local-state content]
  (local-state-content local-state content))

(defn with-results
  "Sets results as success in local-state's status"
  [local-state results]
  (local-state-status local-state (make-success results)))

(defn with-waiting
  "Sets waiting status with `id` in local-state's status"
  [local-state id]
  (local-state-status local-state (make-waiting id)))


;; Result Type
;; Represents the result of a successfull search.
;; A result-t is one of the following:
;; * no-result, if no result is chosen
;; * result, containing an `address` which is the new result

(qt/def-type result-t
  [(no-result [])
   (result [address])])

;; constant for no-result
(def no-result-inst (make-no-result))

(defn show-result
  "Renders a result-t"
  [result]
  (st/match result-t result
    no-result? nil
    (make-result address) address))


;; Highlighted Type
;; Represents highlighted addresses
;; A highlighted-t is one of the following:
;; * none-highlighted, if none was highlighted
;; * highlighted, containing the highlighted `address`

(qt/def-type highlighted-t
  [(none-highlighted [])
   (highlighted [address])])

;; constant for none-highlighted
(def none-highlighted-inst (make-none-highlighted))


(defn show-highlighted
  "Renders a highlighted-t"
  [highlighted]
  (st/match highlighted-t highlighted
    none-highlighted? nil
    (make-highlighted address) address))


;; State of the search field component.
;; A state consists of:
;; * highlighted, as described in highlighted-t
;; * result, representing a selected result, either throught direct
;;           user input or selection from the results dropdown
(qt/def-record state [highlighted result])


(defn highlight-address
  "Puts `address` as highlighted in state.
  If `address` is `nil`, puts `none-highlighted-inst`"
  [state address]
  (state-highlighted state
   (if address
     (make-highlighted address)
     none-highlighted-inst)))

(defn unhighlight-address
  "Puts `none-highlighted-inst` as highlighted in state"
  [state address]
  (state-highlighted state none-highlighted-inst))

(defn clear-result
  "Puts `no-result-inst` as result in state"
  [state]
  (state-result state no-result-inst))

(defn set-result
  "Puts the given address as result in state"
  [state address]
  (state-result state (make-result address)))



;; Callback-message Type
;; A callback-message is one of the following:
;; * result-failure-message, if there was no matching address in request `id`
;; * result-success-message, containing matching addresses for request `id`

(qt/def-type callback-message-t
  [(result-failure-message [id])
   (result-success-message [id addresses])])


;; Interaction-message Type
;; Signaling user interactions.
;; A interaction-message-t is one of the following:
;; * set-result-message, used to set `address` as a result in state
;; * highlight-address-message, used to set `address` as highlighted in state
;; * unhighlight-address-message, used to unhighlight iff `address` is currently
;;                                highlighted in state
;; * clear-result-message, used to set no-result in state
;; * typed-message, used to set user input as content in state

(qt/def-type interaction-message-t
  [(set-result-message [address])
   (highlight-address-message [address])
   (unhighlight-address-message [address])
   (clear-result-message [])
   (typed-message [content])])


;; Message Type
;; A message-t is one of the following:
;; interaction-message-t
;; callback-message-t

(qt/def-type message-t
  [interaction-message-t
   callback-message-t])

;; Action Type
;; A action is one of the following:
;; search-action, used to trigger a new request for addresses (names) & result-callback
(qt/def-type action-t
  [(search-action [addresses callback])])


(def initial-public-state (make-state none-highlighted-inst no-result-inst))
(def initial-state (make-local-state nil (make-idle)))

