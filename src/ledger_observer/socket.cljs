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
    (some #{(data/transaction-event-from message)} addresses)
    (some (fn [addr] (some #{addr} addresses)) (data/transaction-event-targets message))))


(defn update-ledger-number [json]
  (let [ln (get json "ledger_index")]
    (when (> ln @ledger-number)
      (reset! ledger-number ln)
      ln)))


(defn parse-issuers [sender receiver parsed-changes]
  (reduce-kv
    (fn [acc k _]
      (if (not (or (= sender k) (= k receiver)))
        (cons k acc)
        acc))
    []
    parsed-changes))

(defn parse-payment [tx-json meta success?]
  (let [sender         (get tx-json "Account")
        receiver    (get tx-json "Destination")
        hash     (get tx-json "hash")
        parsed-changes (js->clj (js/txparser.parseBalanceChanges (clj->js meta)))
        issuers (parse-issuers sender receiver parsed-changes)
        signers (parse-accounts-helper (get tx-json "Signers"))]
    (data/make-payment-transaction-event hash sender receiver issuers signers nil success?)))


(defn parse-generic [json success?]
  (let [source     (get-in json ["transaction" "Account"])
        tx-type    (get-in json ["transaction" "TransactionType"])
        taker-gets (get-in json ["transaction" "TakerPays"])
        taker-pays (get-in json ["transaction" "TakerGets"])
        hash       (get-in json ["transaction" "hash"])
        targets    (if (= tx-type "Payment")
                     [(get-in json ["transaction" "Destination"])]
                     (vec (remove #{source} (set (parse-accounts-helper json)))))]
    (when source
      (data/make-transaction-event hash source targets tx-type ledger-number success?))))


(defn parse-transaction [json]

  ;; (js/console.log (js/txparser.parseBalanceChanges (clj->js (get (read-json json) "meta"))))
  ;; (js/console.log (count (js->clj (js/txparser.parseBalanceChanges (clj->js (get (read-json json) "meta"))))))
  (let [tx (get json "transaction")
        tx-type (get tx "TransactionType")
        meta (get json "meta")
        success? (= "tesSUCCESS" (get meta "tesSUCCESS"))]

    (cond
      (= tx-type "Payment")
      (parse-payment tx meta success?)

      :default
      (parse-generic json success?))))

#_(defn create-ripple-socket [] (ws/connect "ws://s1.ripple.com"))


(defn create-ripple-socket! [callback-open callback-close]
  (let [socket (js/WebSocket. "wss://s1.ripple.com")]
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
    (let [json                   (read-json (.-data tx))
          ?message               (parse-transaction json)
          ?new-ledger-number     (update-ledger-number json)
          ?filter-address-events (mailbox/receive-all socket-mailbox)]

      (run! process-filter-address-event! ?filter-address-events)

      (when ?new-ledger-number
        (mailbox/send! ledger-number-mailbox (data/make-update-ledger-number-event ?new-ledger-number)))

      (when ?message ; process new transaction
        (.setTimeout js/window
          #(do (when (and (not-empty @filter-addresses) (contains-address? ?message @filter-addresses))
                 (mailbox/send! app-mailbox [@filter-addresses ?message]))
               (mailbox/send! render-tx-mailbox ?message))
          (rand-int 500))))))


(defn setup-ripple-socket! [ripple-socket render-tx-mailbox socket-mailbox app-mailbox ledger-number-app-mailbox]
  (.send ripple-socket (.stringify js/JSON (clj->js subscribe)))
  (set! (.-onmessage ripple-socket)
    (make-handle-tx app-mailbox socket-mailbox render-tx-mailbox ledger-number-app-mailbox)))

#_(defn setup-ripple-socket! [ripple-socket render-tx-mailbox socket-mailbox app-mailbox ledger-number-app-mailbox]
  (js/window.setInterval
    #(mailbox/send! render-tx-mailbox (data/make-new-transaction-event 123 (str (rand-int 10)) [(str (rand-int 10))] "Payment" 3 true))
    3000)
  )

