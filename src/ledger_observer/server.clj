(ns ledger-observer.server
  (:require 
   [ring.util.response :refer [resource-response content-type not-found]]))

;; the routes that we want to be resolved to index.html
(def route-set #{"/run"})

(defn handler [req]
  (if (route-set (:uri req))
    (some-> (resource-response "visualization.html" {:root "public"})
      (content-type "text/html; charset=utf-8"))
    (some-> (resource-response "index.html" {:root "public"})
      (content-type "text/html; charset=utf-8"))))
