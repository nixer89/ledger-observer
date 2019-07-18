(ns ledger-observer.visualization.animations
  (:require [active.clojure.cljs.record :as rec :include-macros true]
            [active.clojure.sum-type :as st]
            [ledger-observer.visualization.data :as data]
            [ledger-observer.visualization.data.animations :as an]
            [ledger-observer.visualization.materials :as materials]
            [ledger-observer.visualization.geometries :as geometries]
            [ledger-observer.visualization.render-macros :as mu :include-macros true]
            [quick-type.core :as qt :include-macros true]))



(qt/def-record no-animation [])
(def no-animation-inst (make-no-animation))


(qt/def-record linear-animation [state pace repeating?])
(def linear make-linear-animation)


(defn step-linear-animation [animation]
  (let [next (+ (linear-animation-state animation) (linear-animation-pace animation))
        repeating? (linear-animation-repeating? animation)]
    (linear-animation-state animation
      (cond
        (and (> next 100) repeating?) 0
        (> next 100) 100
        :default next))))


(defn step [animation]
  (cond
    (linear-animation? animation)
    (step-linear-animation animation)

    (no-animation? animation)
    animation
    ))


(defn get-value [animation]
  (cond
    (linear-animation? animation)
    (linear-animation-state animation)

    (no-animation? animation)
    0
    ))



(defn normalize [t]
  (/ (- 100 t) 100))


(let [animation-line-length 0.6
      temp-reduction 1.5 ; influences animation speed
      ]
 (defn animate-payment [scene animation]
   (let [temp          (an/payment-animation-temperature animation)
         initialized?  (an/payment-animation-initialized? animation)
         animation-ref (an/payment-animation-animation-ref animation)

         graph-ref (an/payment-animation-link-ref animation)
         link-ref  (.-mesh graph-ref)
         vertices  (.-vertices (.-geometry link-ref))
         from      (aget vertices 0)
         to        (aget vertices 1)
         c         (mu/js-kw-get graph-ref :count)]
     (cond
       (neg? temp)
       (do (.remove scene animation-ref)
           nil)

       initialized?
       (let [vertices (.-vertices (.-geometry animation-ref))
             old-from (aget vertices 0)
             old-to   (aget vertices 1)

             temp-rel      (normalize temp)
             temp-rel-plus (min 1.0 (+ animation-line-length temp-rel))

             dist     (.clone to)
             new-from (.clone from)
             new-to   (.clone from)]

         ;; fast but imperative
         (.sub dist from)
         (.addScaledVector new-from dist temp-rel)
         (.addScaledVector new-to dist temp-rel-plus)
         (.copy old-from new-from)
         (.copy old-to new-to)

         (mu/needs-update! animation-ref)
         (an/payment-animation-temperature animation (- temp temp-reduction)))

       :default
       (let [dist     (.clone to)
             _        (.sub dist from)
             new-from (.clone from)
             new-to   (.clone from)
             _        (.addScaledVector new-to dist animation-line-length)
             new-line (geometries/line-geometry-js new-from new-to)]

         (mu/push-color! new-line  0xd4145a)
         (mu/push-color! new-line  0xffffff)
         (.add scene new-line)
         (mu/set-material! new-line materials/payment-transaction-material)
         (mu/needs-update! new-line)

         (-> animation
           (an/payment-animation-animation-ref new-line)
           (an/payment-animation-initialized? true)))))))


(defn animate-1 [scene animation]
  (st/match an/link-animation-t animation
            an/payment-animation? (animate-payment scene animation)))

(defn animate [animation-state ^js/Object scene]
  (let [link-animations (an/animation-state-link-animations animation-state)
        node-animations (an/animation-state-node-animations animation-state)]
    (an/put-link-animations
     animation-state
     (remove nil? (mapv (partial animate-1 scene) link-animations)))))


(defn add-payment-link-animation [animation-state graph-ref direction]
  (an/add-link-animation
   animation-state
   (an/make-payment-animation false 100 nil graph-ref direction)))
