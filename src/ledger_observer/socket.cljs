(ns ledger-observer.socket
  (:require
   [active.clojure.cljs.record :as rec :include-macros true]
   [ledger-observer.mailbox :as mailbox]
   [ledger-observer.visualization.render :as renderer]
   [ledger-observer.visualization.data :as data]
   [cognitect.transit :as t]))

(def ledger-number (atom 0))

(rec/define-record-type SetFilterAddress
  (make-set-filter-address address) set-filter-address?
  [address set-filter-address-address])

(rec/define-record-type UnsetFilterAddress (make-unset-filter-address address) unset-filter-address?
  [address unset-filter-address-address])

(def subscribe {:id      "test"
                :command "subscribe"
                :streams ["transactions"]})

(def reader (t/reader :json))
(defn read-json [s] (t/read reader s))

(defn wallet? [key]
  (some #{key} (list "Account" "owner" "issuer")))

(def address-pattern (re-pattern #"r[0-9a-zA-Z]{24,34}"))
(defn address? [value] (re-find address-pattern value))

(defn parse-accounts-helper [elem]
  (cond
    (vector? elem)
    (mapcat parse-accounts-helper elem)

    (map? elem)
    (mapcat
      (fn [[_ value]] (parse-accounts-helper value))
      elem)

    (string? elem)
    (if (address? elem) [elem] [])

    :default
    []))



(defn contains-address? [message addresses]
  (or
    (some #{(data/new-transaction-event-from message)} addresses)
    (some (fn [addr] (some #{addr} addresses)) (data/new-transaction-event-targets message))))

(defn parse-accounts [json]
  (let [msg      (read-json json)
        source   (get-in msg ["transaction" "Account"])
        ln       (get msg "ledger_index")
        success? (= "tesSUCCESS" (get-in msg ["meta" "TransactionResult"]))
        tx-type  (get-in msg ["transaction" "TransactionType"])
        hash     (get-in msg ["transaction" "hash"])
        targets  (vec (remove #{source} (set (parse-accounts-helper msg))))]
    [(if (> ln @ledger-number)
       (do
         (reset! ledger-number ln)
         ln)
       nil)
     (data/make-new-transaction-event hash source targets tx-type ledger-number success?)]))

#_(defn create-ripple-socket [] (ws/connect "ws://xrplcluster.com"))


(defn create-ripple-socket! [callback-open callback-close]
  (let [socket (js/WebSocket. "wss://xrplcluster.com")]
    (set! (.-onopen socket) (fn [_] (.setTimeout js/window #(callback-open socket)  1000)))
    (set! (.-onclose socket) #(callback-close %))))

(def filter-addresses (atom []))

(defn remove* [coll fn]
  (remove fn coll))

(defn process-filter-address-event! [?event]
  (cond
    (set-filter-address? ?event)   (swap! filter-addresses conj (set-filter-address-address ?event))
    (unset-filter-address? ?event) (swap! filter-addresses remove* #{(unset-filter-address-address ?event)})))


(defn make-handle-tx [app-mailbox socket-mailbox render-tx-mailbox ledger-number-mailbox]
  (fn [tx]
    (let [[?new-ledger-number message] (parse-accounts (.-data tx))
          ?filter-address-events       (mailbox/receive-all socket-mailbox)]

      (run! process-filter-address-event! ?filter-address-events)

      (when ?new-ledger-number
        (mailbox/send! ledger-number-mailbox (data/make-update-ledger-number-event ?new-ledger-number)))

      (when (data/new-transaction-event-from message) ; process new transaction
        (.setTimeout js/window
          #(do (when (and (not-empty @filter-addresses) (contains-address? message @filter-addresses))
                 (mailbox/send! app-mailbox [@filter-addresses message]))
               (mailbox/send! render-tx-mailbox message))
          (rand-int 500))))))


(defn setup-ripple-socket! [ripple-socket render-tx-mailbox socket-mailbox app-mailbox ledger-number-app-mailbox]
  (.send ripple-socket (.stringify js/JSON (clj->js subscribe)))
  (set! (.-onmessage ripple-socket)
    (make-handle-tx app-mailbox socket-mailbox render-tx-mailbox ledger-number-app-mailbox)))


