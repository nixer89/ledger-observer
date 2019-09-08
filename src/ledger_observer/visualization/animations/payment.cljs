(ns ledger-observer.visualization.animations.payment
  (:require
   [active.clojure.cljs.record :as rec :include-macros true]
   [quick-type.core :as qt :include-macros true]
   [active.clojure.sum-type :as st]
   [quick-type.core :as qt :include-macros true]
   [ledger-observer.visualization.geometries :as geometries]
   [ledger-observer.visualization.materials :as materials]
   [ledger-observer.visualization.render-macros :as mu :include-macros true]
   [active.clojure.lens :as lens :include-macros true]))


(qt/def-type payment-t
  [(payment [temperature animated-link-ref animated-point-ref animated-point-pos original-link direction])
   (uninitialized-payment [original-link direction])
   (done-payment [animated-link-ref animated-point-ref])])

(defn uninitialized-payment->payment [unitialized-payment animated-link-ref animated-point-ref animated-point-pos]
  (make-payment 100 animated-link-ref animated-point-ref animated-point-pos
    (uninitialized-payment-original-link unitialized-payment)
    (uninitialized-payment-direction unitialized-payment)))

(defn payment->done-payment [payment]
  (make-done-payment
    (payment-animated-link-ref payment)
    (payment-animated-point-ref payment)))

(def init-payment make-uninitialized-payment)

(defn step-payment [payment step]
  (let [temp     (payment-temperature payment)
        new-temp (- temp step)]
    (if (neg? new-temp)
      (payment->done-payment payment)
      (payment-temperature payment new-temp))))

(defn points-cloud [num]
  (let [points (js/THREE.BufferGeometry.)

        positions (js/Float32Array. (* 3 num) )
        colors    (js/Float32Array. (* 3 num))
        sizes     (js/Float32Array. (* num))
        _         (.fill sizes 10)
        _         (.fill colors 1)

        _ (.addAttribute points "position" (js/THREE.BufferAttribute. positions 3))
        _ (.addAttribute points "customColor" (js/THREE.BufferAttribute. colors 3))
        _ (.addAttribute points "size" (js/THREE.BufferAttribute. sizes 1))

        material (materials/point)
        points-cloud (js/THREE.Points. points material)]
    (.setDrawRange points 0 num)
    [positions points-cloud]))


(let [temp-reduction 1.5]
 (defn animate-payment [scene animation]

   (st/match payment-t animation

     (make-uninitialized-payment original-link _)
     (let [original-mesh      (.-mesh original-link)
           vertices           (.-vertices (.-geometry original-mesh))
           from               (aget vertices 0)
           to                 (aget vertices 1)
           c                  (mu/js-kw-get original-link :count)
           [positions points] (points-cloud 1)
           new-line           (geometries/line-geometry-js to from)]

       (.add scene new-line)
       (aset positions 0 (.-x from))
       (aset positions 1 (.-y from))
       (aset positions 2 (.-z from))
       (.add scene points)

       (set! (.-needsUpdate (.-position (.-attributes (.-geometry points)))) true)
       (set! (.-needsUpdate (.-customColor (.-attributes (.-geometry points)))) true)
       (mu/set-material! new-line (materials/active-link-material-blue c 100))
       (mu/needs-update! new-line)

       (uninitialized-payment->payment animation new-line points positions))


     (make-payment temp animated-link-ref animated-points-ref animated-point-pos original-link direction)
     (let [temp-rel (/ temp 100)
           original-mesh (.-mesh original-link)
           vertices      (.-vertices (.-geometry original-mesh))
           from          (aget vertices 0)
           to            (aget vertices 1)
           c             (mu/js-kw-get original-link :count)

           dist-x (- (.-x to) (.-x from))
           dist-y (- (.-y to) (.-y from))
           dist-z (- (.-z to) (.-z from))

           scaled-x (* temp-rel dist-x)
           scaled-y (* temp-rel dist-y)
           scaled-z (* temp-rel dist-z)

           new-x (+ scaled-x (.-x from))
           new-y (+ scaled-y (.-y from))
           new-z (+ scaled-z (.-z from))]

       ;; fast but imperative
       (aset animated-point-pos 0 new-x)
       (aset animated-point-pos 1 new-y)
       (aset animated-point-pos 2 new-z)

       (set! (.-needsUpdate (.-position (.-attributes (.-geometry animated-points-ref)))) true)
       (set! (.-needsUpdate (.-customColor (.-attributes (.-geometry animated-points-ref)))) true)
       (.setDrawRange (.-geometry animated-points-ref) 0 3)

       (mu/set-material! animated-link-ref (materials/active-link-material-blue c temp))
       (mu/needs-update! animated-link-ref)
       (step-payment animation 1.5))


     (make-done-payment animated-link-ref animated-point-ref)
     (do
       (.remove scene animated-link-ref)
       (.remove scene animated-point-ref)
       nil)
     )))
