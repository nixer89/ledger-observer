(ns ledger-observer.visualization.geometries
  (:require [ledger-observer.visualization.materials :as materials]))

(defn vec-to-vector3 [[x y z]]
  (js/THREE.Vector3. x y z))



(defn line-geometry-js [from to]
  (let [geometry (js/THREE.Geometry.)]
    (.push (.-vertices geometry) from)
    (.push (.-vertices geometry) to)
    (js/THREE.Line. geometry materials/base-line-material)))


(defn line-geometry [[from to]]
  (line-geometry-js
   (vec-to-vector3 from)
   (vec-to-vector3 to)))

(defn point-geometry [] (js/THREE.BufferGeometry.))
