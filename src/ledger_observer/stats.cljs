(ns ledger-observer.visualization.stats
  (:require [stats-js :as Stats]))

(defn set-stats-html [stats]
  (.appendChild (.-body js/document) (.-domElement stats)))

(defn init []
  (let [stats (Stats.)]
    (set! (.. stats -domElement -style -position) "absolute")
    (set! (.. stats -domElement -style -right) "10px")
    (set! (.. stats -domElement -style -bottom) "10px")
    (set-stats-html stats)
    stats))

