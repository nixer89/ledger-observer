(ns ledger-observer.visualization.geometries
  (:require [ledger-observer.visualization.materials :as materials]
            three))

(defn add-point-to-geometry [geo x y z]
  (.push (.-vertices geo) (three/Vector3. x y z)))

(defn line-geometry [[from to]]
  (let [geometry (three/Geometry.)]
    (do
      (apply add-point-to-geometry geometry from)
      (apply add-point-to-geometry geometry to))
    (three/Line. geometry materials/base-line-material)))


(def point-geometry (three/BufferGeometry.))
