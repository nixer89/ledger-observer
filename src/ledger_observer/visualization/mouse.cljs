(ns ledger-observer.visualization.mouse
  (:require three

            [active.clojure.lens :as lens :include-macros true]
            [active.clojure.cljs.record :as rec :include-macros true]

            [ledger-observer.visualization.graph-layout :as graph-layout]
            [ledger-observer.visualization.animations :as animations]
            [ledger-observer.visualization.render-utils :as u]
            [ledger-observer.visualization.data :as data]
            [ledger-observer.visualization.app-interaction :as ai]
            [ledger-observer.visualization.raytrace :as raytrace]))


(rec/define-record-type UnclickMessage (make-unclick-message) unclick-message? [])
(rec/define-record-type ClickedMessage (make-clicked-message) clicked-message? [])

(defn mouse-message? [msg]
  (or
    (unclick-message? msg)
    (clicked-message? msg)))


(def mouse-position (atom nil))

(def raycaster (let [^js/Object raycaster (three/Raycaster.)]
                 (.log js/console raycaster)
                 (set! (.. raycaster -params -Points -threshold) 3)
                 raycaster))



(defn get-draw-range-2 [^js/Object a] (.. a -count))
(defn get-draw-range-1 [^js/Object a]
  (get-draw-range-2 (.. a -drawRange)))
(defn get-draw-range [^js/Object a]
  (get-draw-range-1 (.. a -geometry)))

(defn raycast* [render-state point ^js/Object points ^js/Object raycaster]
  (let [camera (data/render-state-camera render-state)
        scene  (data/render-state-scene render-state)
        points (data/render-state-points-cloud render-state)
        _      (.setFromCamera raycaster point camera)
        ray    (.-ray raycaster)]
    ()
    (raytrace/raycast
      (data/render-state-points-positions render-state)
      ray (get-draw-range points))))

(defn raycast [render-state point]
  (let [points (data/render-state-points-cloud render-state)]
    (raycast* render-state point points raycaster)))



(defn set-needs-update [render-state]
  (set! (.. (lens/yank render-state data/points-cloud-lens)
            -geometry -attributes -size -needsUpdate) true))



(defn unset-mouse-hover [render-state]
  (let [current (u/render-state-node-marked-lens render-state)
        previous (u/render-state-previous-node-marked-lens render-state)]
   (cond
     (ai/node-marked-by-mouse-hover? current)
     (lens/overhaul render-state
       data/render-state-interaction-state
       #(ai/mark % ai/no-node-marked))

     (and
       (ai/no-node-marked? current)
       (ai/node-marked-by-mouse-hover? previous))
     (lens/overhaul render-state
       data/render-state-interaction-state
       #(ai/mark % ai/no-node-marked))

     :default
     render-state)))

(defn set-mouse-hover [render-state index]
  (let [m (u/render-state-previous-node-marked-lens render-state)
        ^js/Object graph (data/render-state-graph render-state)
        neighbours (graph-layout/get-neighbours graph index)
        links (graph-layout/get-links graph index)]
    (if (= index (ai/marked-node-index m))
      render-state
      (lens/overhaul
        render-state
        data/render-state-interaction-state
        #(ai/mark % (ai/make-node-marked-by-mouse-hover index neighbours
                      links (animations/linear 0 2 true)))))))


(defn unset-mouse-click [render-state]
  (let [selected (u/render-state-node-selected-lens render-state)]
    (if (not (ai/no-node-selected? selected))
      (lens/overhaul render-state
        data/render-state-interaction-state
        #(ai/select % ai/no-node-selected-inst))
      render-state)))


(defn set-mouse-click [render-state index]
  (lens/overhaul
    render-state
    data/render-state-interaction-state
    #(ai/select % (ai/make-node-selected-by-mouse-click
                    index (animations/linear 0 1 true)))))


(defn mouse-hover [render-state point]
  (let [index (raycast render-state point)]
    (if (> index -1)
      (set-mouse-hover render-state index)
      (unset-mouse-hover render-state))))


(defn maybe-mouse-hover [render-state point]
  (if point (mouse-hover render-state point) render-state))

(defn mouse-click [render-state point]
  (let [index (raycast render-state point)]
    (if (> index -1)
      (set-mouse-click render-state index)
      render-state)))

(defn cljs-mouse-event [^js/MouseEvent ev]
  (let [x (.-clientX ev)
        y (.-clientY ev)]
   (clj->js {:x (- (* 2 (/ x (.-innerWidth js/window))) 1)
             :y (+ 1 (* -1 (* 2 (/ y (.-innerHeight js/window)))))})))

(defn update-render-mouse-position! [^js/MouseEvent ev]
  (reset! mouse-position (cljs-mouse-event ev)))


(defn raycast-mouse-hover [render-state]
  (let [next-render-state (lens/overhaul render-state data/mouse-mod-counter-lens inc)
        counter (data/mouse-mod-counter-lens next-render-state)]
    (if (= 0 (mod counter 3))
      (maybe-mouse-hover next-render-state @mouse-position)
      next-render-state)))


(defn mouse-message-handler [render-state ?msg]

  ;; This event handler relies on @mouse-position.
  ;; We don't want to spam MouseMove events due to performance reasons

  (cond

    (unclick-message? ?msg)
    (unset-mouse-click render-state)

    (clicked-message? ?msg)
    (mouse-click render-state @mouse-position)))
