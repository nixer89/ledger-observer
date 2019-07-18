(ns ledger-observer.visualization.render-macros)


(defmacro set-coords! [node from-x from-y from-z]
  `(.set ~node ~from-x ~from-y ~from-z))


(defmacro js-kw-set! [obj kw value]
  `(aset ~obj ~(str kw) ~value))

(defmacro js-kw-get [obj kw]
  `(aget ~obj ~(str kw)))

(defmacro needs-update! [obj]
  `(set! (.-verticesNeedUpdate (.-geometry ~obj)) true))


(defmacro push-color! [obj color-hex]
  `(.push (.-colors (.-geometry ~obj)) (js/THREE.Color. ~color-hex)))


(defmacro set-material! [obj material]
  `(set! (.-material ~obj) ~material))
