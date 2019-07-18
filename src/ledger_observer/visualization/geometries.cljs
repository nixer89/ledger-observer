(ns ledger-observer.visualization.geometries
  (:require [ledger-observer.visualization.materials :as materials]
            three))

(defn vec-to-vector3 [[x y z]]
  (three/Vector3. x y z))



(defn line-geometry-js [from to]
  (let [geometry (three/Geometry.)]
    (.push (.-vertices geometry) from)
    (.push (.-vertices geometry) to)
    (three/Line. geometry materials/base-line-material)))


(defn line-geometry [[from to]]
  (line-geometry-js
   (vec-to-vector3 from)
   (vec-to-vector3 to)))

(def point-geometry (three/BufferGeometry.))
