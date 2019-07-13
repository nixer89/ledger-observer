(ns ledger-observer.components.search.data
  (:require [active.clojure.cljs.record :as rec :include-macros true]
            [active.clojure.sum-type :as st :include-macros true]
            [active.clojure.lens :as lens]
            [quick-type.core :as qt :include-macros true]))


;; Represents the status of the search field. A status is
;; a status is one of the following:

;; * waiting: waiting for the results of a search request with id `id`
;; * error: received and error for search request with id `id` with message `msg`
;; * success: a request with `id` found `address` 
;; * idle: nothings on.

(qt/def-type status-t
  [(waiting [id])
   (error [msg])
   (success [address])
   (idle [])])

;; constant for idle
(def idle-inst (make-idle))


;; Represents the state of a search field. Consists of content,
;; which is typed content and a status of type status-t
(qt/def-record local-state [content status])


(qt/def-type result-t
  [(no-result [])
   (result [result])])

(def no-result-inst (make-no-result))

(qt/def-type highlighted-t
  [(none-highlighted [])
   (highlighted [address])])


(def none-highlighted-inst (make-none-highlighted))

(qt/def-record state [highlighted result])


(defn highlight-address [public-state address]
  (state-highlighted public-state (make-highlighted address)))

(defn unhighlight-address [public-state address]
  (state-highlighted public-state none-highlighted-inst))

(defn clear-result [public-state]
  (state-result public-state no-result-inst))

(defn set-result [public-state address]
  (state-result public-state (make-result address)))




(qt/def-type callback-message
  [(result-failure-message [id])
   (result-success-message [id address])])

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


(def initial-public-state (make-state none-highlighted-inst no-result-inst))
(def initial-state (make-local-state nil (make-idle)))

(defn has-idle-status? [local-state]
  (idle? (local-state-status local-state)))


(defn waiting-with-id? [local-state id]
  (let [?waiting (local-state-status local-state)]
    (and (waiting? ?waiting) (= (waiting-id ?waiting) id))))


(def address-not-found-error
  (make-error "Address or name not found!"))

(def min-character-error
  (make-error "Please enter at least two characters"))
