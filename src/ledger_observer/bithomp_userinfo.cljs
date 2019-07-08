(ns ledger-observer.bithomp-userinfo
  (:require [active.clojure.cljs.record :as rec :include-macros true]
            [ajax.core :refer [GET]]))

(def bithomp-user-info (atom nil))


(rec/define-record-type UserInfo
  (make-user-info name domain accounts) user-info?
  [name user-info-name
   domain user-info-domain
   accounts user-info-accounts])

(rec/define-record-type FetchUsersInfoAction
  (make-fetch-users-info-action ref) fetch-users-info-action?
  [ref fetch-users-info-action-ref])

(def route "https://bithomp.com/api/v1/userinfo")

(defn parse-user-info [user-info-json]
  (make-user-info
    (get user-info-json "name")
    (get user-info-json "domain")
    (get user-info-json "accounts")))

(defn parse-users-info [response]
  (reduce
    (fn [acc el]
      (assoc acc (get el "address") (parse-user-info el)))
    {}
    (get (js->clj response) "usersinfo")))

(defn get-users-info []
  (GET route
    {:handler #(reset! bithomp-user-info (parse-users-info %))}))


(defn addresses-by-name [name]
  (if @bithomp-user-info
   (let [name (clojure.string/lower-case name)]
     (map first (filter (fn [[_ info]]
                          (clojure.string/starts-with?
                            (clojure.string/lower-case (user-info-name info))
                            name)) @bithomp-user-info)))
   []))

(defn get-name
  "Returns address if users-info is nil or address not present in users-info, else name"
  [address]
  (if @bithomp-user-info
    (if-let [user-info (get @bithomp-user-info address)]
      (user-info-name user-info)
      address)
    address))
