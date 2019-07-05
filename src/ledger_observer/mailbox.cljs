(ns ledger-observer.mailbox
  (:require [active.clojure.record :as rec :include-macros true]))


(rec/define-record-type Mailbox
  (make-mailbox* queue) mailbox?
  [queue mailbox-queue])


(defn make-mailbox []
  (make-mailbox* (atom [])))


(defn send! [mailbox msg]
  (swap! (mailbox-queue mailbox) conj msg))


(defn receive-n
  "Receives n elements from mailbox, returns less if only less are available
  Note that this must not be transactional, since we dont have threads anyways. "
  [mailbox n]
  (let [queue (mailbox-queue mailbox)
        n (min n (count @queue))
        ret   (take n @queue)]
    (swap! queue subvec n)
    ret))


(defn receive-all [mailbox]
  (let [queue (mailbox-queue mailbox)
        ret @queue]
    (reset! queue [])
    ret))


(defn receive-next [mailbox]
  (first (receive-n mailbox 1)))


