(ns ledger-observer.visualization.raytrace
  (:require
   [active.clojure.cljs.record :as rec :include-macros true]))


(rec/define-record-type Ray
  (make-ray ox oy oz dx dy dz) ray?
  [ox ray-ox
   oy ray-oy
   oz ray-oz
   dx ray-dx
   dy ray-dy
   dz ray-dz])


(defn- vec-distance [[x y z] [a b c]]
  (let [dist-x (- a x)
        dist-y (- b y)
        dist-z (- c z)]
    (Math/sqrt
     (+ (* dist-x dist-x)
        (* dist-y dist-y)
        (* dist-z dist-z)))))

(defn- distance-to [[x y z] ^js/Object ray]
  (let [ox (ray-ox ray)
        oy (ray-oy ray)
        oz (ray-oz ray)

        dx (ray-dx ray)
        dy (ray-dy ray)
        dz (ray-dz ray)

        direction-distance (+ (* (- x ox) dx)
                              (* (- y oy) dy)
                              (* (- z oz) dz))]

    (if (< direction-distance 0)
      ;; point behind ray
      [direction-distance (vec-distance [x y z] [ox oy oz])]
      ;; else
      (let [vx (+ (* dx direction-distance) ox)
            vy (+ (* dy direction-distance) oy)
            vz (+ (* dz direction-distance) oz)]
        [direction-distance (vec-distance [x y z] [vx vy vz])]))))

(defn- nearest-to [[x y z] ^js/Object ray direction-distance]
  (let [ox (ray-ox ray)
        oy (ray-oy ray)
        oz (ray-oz ray)

        dx (ray-dx ray)
        dy (ray-dy ray)
        dz (ray-dz ray)]

    (if (< direction-distance 0)
      ;; point behind ray
      [ox oy oz]
      [(+ ox (* dx direction-distance))
       (+ oy (* dy direction-distance))
       (+ oz (* dz direction-distance))])))

(defn- apply-matrix [[x y z] e]
  [(+ (* (aget e 0) x) (* (aget e 4) y) (* (aget e 8) z) (aget e 12))
   (+ (* (aget e 1) x) (* (aget e 5) y) (* (aget e 9) z) (aget e 13))
   (+ (* (aget e 2) x) (* (aget e 6) y) (* (aget e 10) z) (aget e 14))])

(defn- ray-distance [v ^js/Object ray]
  (vec-distance v [(ray-ox ray) (ray-oy ray) (ray-oz ray)]))

(defn- test-point [v ^js/Object ray index old-distance old-value]
  (let [[direction-distance distance] (distance-to v ray)]
    (if (< distance 0.5)
      (let [nearest (nearest-to v ray direction-distance)
            ;;nearest-in-world (apply-matrix nearest world-matrix)
            distance (ray-distance nearest ray)]
        (if (< distance old-distance)
          [index distance]
          old-value))
      old-value)))



(defn get-z [^js/Object o]
  (.. o -z))

(defn get-y [^js/Object o]
  (.. o -y))

(defn get-x [^js/Object o]
  (.. o -x))

(defn raycast [^js/Object positions ^js/Object ray max]
  ;; positions is an array where 3 consecutive values define
  ;; the coordinates of one point
  (let [point-count (js->clj (/ (.-length positions) 3))

        ox (get-x (.. ray -origin))
        oy (get-y (.. ray -origin))
        oz (get-z (.. ray -origin))

        dx (get-x (.. ray -direction))
        dy (get-y (.. ray -direction))
        dz (get-z (.. ray -direction))

        ray-rec (make-ray ox oy oz dx dy dz)]

    (first
     (reduce (fn [old-mins index]
               (test-point
                [(aget positions (* 3 index))
                 (aget positions (inc (* 3 index)))
                 (aget positions (inc (inc (* 3 index))))]
                ray-rec
                index
                (second old-mins)
                old-mins))
             [-1 999999999]
             (range 0 max)))))
