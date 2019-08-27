(ns ledger-observer.visualization.render
  (:require cljsjs.three
            [active.clojure.cljs.record :as rec :include-macros true]
            [clojure.core.async :as async]
            [active.clojure.lens :as lens :include-macros true]
            [three-controls :as Controls]
            [reacl2.core :as reacl :include-macros true]
            [ledger-observer.mailbox :as mailbox]
            [ledger-observer.visualization.materials :as materials]
            [ledger-observer.visualization.geometries :as geometries]
            [ledger-observer.visualization.animations :as animations]
            [ledger-observer.visualization.mouse :as mouse]
            #_[ledger-observer.visualization.stats :as stats]
            [ledger-observer.visualization.graph-layout :as graph-layout]
            [ledger-observer.visualization.app-interaction :as ai]
            [ledger-observer.visualization.render-utils :as u]
            [ledger-observer.visualization.render-macros :as mu :include-macros true]
            [ledger-observer.visualization.data :as data]))


(defn tx->num [a]
  (cond
    (= a "OfferCreate") 0
    (= a "OfferCancel") 1
    (= a "Payment") 2
    (clojure.string/starts-with? a "Escrow") 3
    (= a "TrustSet") 4
    :default 5))

(def click-size 20)
(def hover-size 20)
(def normal-size 6)


(defn unpin-node! [render-state index]
  (graph-layout/unpin-node!
    (data/render-state-layout render-state)
    (first (graph-layout/filter-nodes
             (data/render-state-graph render-state)
             (fn [node] (= (:num (.-data node)) index))))))

(defn pin-node! [render-state index]
  (graph-layout/pin-node!
    (data/render-state-layout render-state)
    (first (graph-layout/filter-nodes
             (data/render-state-graph render-state)
             (fn [node] (= (:num (.-data node)) index))))))

(defn clear-scene! [scene]
  (dotimes [n (.-length (.-children scene))]
    (.remove scene (get (.-children scene) 0))))


(defn set-renderer-full-width! [renderer]
  (let [width (.-innerWidth js/window)
        height (.-innerHeight js/window)]
    (.setSize renderer width height)))


(defn screen-ratio []
  (/ (.-innerWidth js/window) (.-innerHeight js/window)))


(defn camera-resize-handler [^js/Object camera]
  (fn []
    (set! (.. camera -aspect) (screen-ratio))
    (.updateProjectionMatrix camera)))

(defn set-camera-on-resize! [camera]
  (.addEventListener js/window "resize" (camera-resize-handler camera)))

(defn init-camera [scene]
  (let [camera (js/THREE.PerspectiveCamera. 45 (screen-ratio) 1 10000)]
    (do (set! (.. camera -position -z) 0)
        (set! (.. camera -position -x) 70)
        (set! (.. camera -position -y) 0)
        (.updateMatrix camera)
        (set-camera-on-resize! camera)
        camera)))


(defn init-points []
  (let [max-points 100000
        points geometries/point-geometry

        positions (js/Float32Array. (* 3 max-points) )
        colors (js/Float32Array. (* 3 max-points))
        sizes (js/Float32Array. (* max-points))
        _ (.fill sizes normal-size)

        _ (.addAttribute points "position" (js/THREE.BufferAttribute. positions 3))
        _ (.addAttribute points "customColor" (js/THREE.BufferAttribute. colors 3))
        _ (.addAttribute points "size" (js/THREE.BufferAttribute. sizes 1))

        material (js/THREE.ShaderMaterial.
                  (clj->js {:uniforms {:color {:value (js/THREE.Color. 0xffffff)}
                                       :texture {:value (.load (js/THREE.TextureLoader.) "images/circle.png")}}
                            :vertexShader   (.-textContent (.getElementById js/document "vertexshader"))
                            :fragmentShader (.-textContent (.getElementById js/document "fragmentshader"))
                            :alphaTest 0.5}))

        points-cloud (js/THREE.Points. points material)]

    (data/make-points-data points positions points-cloud colors sizes)))


(defn add-to-html [^js/THREE.WebGLRenderer renderer]
  (.appendChild
   (.getElementById js/document "renderer-container")
   (.-domElement renderer)))

(defn init-controls [camera]
  (let [ret (Controls. camera (.getElementById js/document "renderer-container"))]
    (set! (.-autoRotate ret) true)
    (set! (.-autoRotateSpeed ret) 1.5)
    (set! (.-screenSpacePanning ret) true)
    (set! (.-minDistance ret) 20)
    ret
    ))

(defn init-scene [points-data]
  (let [scene (js/THREE.Scene.)]
    (.add scene (data/points-data-points-cloud points-data))
    scene))

(defn set-renderer-onresize! [renderer]
  (.resize (js/jQuery js/window)
    #(set-renderer-full-width! renderer)))

(defn init-renderer []
  (let [renderer (js/THREE.WebGLRenderer. (clj->js {:antialias true :alpha true}))]
    (set-renderer-full-width! renderer)
    (set-renderer-onresize! renderer)
    (add-to-html renderer)
    renderer))

(defn init-visualization-state []
  "Swap into the app state the renderer, and a function to stop the current animation loop."
  []
  (let [points-data (init-points)
        ;;globe-data (globe/init-globe)
        scene (init-scene points-data)
        camera (init-camera scene)]
    (data/make-render-state
     (data/make-webgl-state scene camera (init-renderer) (init-controls camera))
     points-data
     (apply data/make-layout-data (graph-layout/layout))
     (mailbox/make-mailbox)
     ai/initial-state
     (data/make-mouse-state 0)
     0)))

(defn step-layout! [render-state]
  (graph-layout/step-layout!
   (-> render-state
       (data/render-state-layout-data)
       (data/layout-layout-data)))
   render-state)

(defn app-marked-node-ids [render-state]
  (if-let [marked (u/render-state-marked-tx-state-lens render-state)]
    (cond
      (ai/tx-marked? marked)
      (cons (ai/tx-marked-from marked)
        (ai/tx-marked-targets marked))

      (ai/no-tx-marked? marked)
      [])))


(defn set-needs-update! [render-state]
  (set! (.. (lens/yank render-state data/points-cloud-lens)
          -geometry -attributes -size -needsUpdate)
    true))

(defn update-sizes [render-state]
  (let [handled-interaction 

        (if (u/render-state-interaction-needs-update-lens render-state)
          (do
            (let [is          (data/render-state-interaction-state render-state)
                  marked      (u/render-state-node-marked-lens render-state)
                  prev-marked (u/render-state-previous-node-marked-lens render-state)

                  sizes (data/point-sizes-lens render-state)

                  marked-idx      (ai/marked-index is)
                  prev-marked-idx (ai/previously-marked-index is)

                  selected-idx      (ai/selected-index is)
                  prev-selected-idx (ai/previously-selected-index is)]

              (if (ai/node-marked-by-mouse-hover? marked)
                (set! (.. js/document -body -style -cursor) "pointer")
                (when (ai/node-marked-by-mouse-hover? prev-marked)
                  (set! (.. js/document -body -style -cursor) "default")))

              (when prev-marked-idx
                (unpin-node! render-state prev-marked-idx)
                (aset sizes prev-marked-idx normal-size))

              (when prev-selected-idx
                (unpin-node! render-state prev-selected-idx)
                (aset sizes prev-selected-idx normal-size))

              (when marked-idx
                (pin-node! render-state marked-idx)
                (aset sizes marked-idx hover-size))

              (when selected-idx
                (pin-node! render-state selected-idx)
                (aset sizes selected-idx click-size)))

            render-state)
          render-state)]

    ;; animate node-sizes
    (let [is    (data/render-state-interaction-state handled-interaction)
          sizes (data/point-sizes-lens handled-interaction)

          [?marked-idx marked-animation next-is]
          (ai/get-marked-animation-value&step is)

          [?selected-idx selected-animation next-is]
          (ai/get-selected-animation-value&step next-is)]

      (when ?marked-idx
        (aset sizes ?marked-idx (* hover-size (+ 0.7 (/ (inc marked-animation) 300)))))

      (when ?selected-idx
        (aset sizes ?selected-idx (* hover-size (+ 0.7 (/ (inc selected-animation) 300)))))

      (set-needs-update! render-state)
      (data/render-state-interaction-state handled-interaction next-is))))

(defn maybe-neighbours [render-state]
  (let [marked   (u/render-state-node-marked-lens render-state)]
    (if (ai/node-marked-by-mouse-hover? marked)
      (ai/node-marked-by-mouse-hover-neighbours marked)
      nil)))



(defn update-node-positions [render-state]
  (let [^js/Object graph (data/render-state-graph render-state)
        ^js/Object points-cloud (data/render-state-points-cloud render-state)
        positions    (data/render-state-points-positions render-state)
        colors       (data/render-state-points-colors render-state)
        layout       (data/render-state-layout render-state) clicked      (ai/selected-index
                       (data/render-state-interaction-state render-state))
        hovered      (ai/marked-index
                       (data/render-state-interaction-state render-state))
        ?neighbours  (maybe-neighbours render-state)
        marked-txs   (app-marked-node-ids render-state)]

    (.forEachNode
      graph
      (fn [node]
        (let [id          (.-id node)
              num         (:num (.-data node))
              x-num       (* 3 num)
              y-num       (inc (* 3 num))
              z-num       (inc (inc (* 3 num)))
              layout-node (.getNodePosition layout id)]

          (aset positions x-num (.-x layout-node))
          (aset positions y-num (.-y layout-node))
          (aset positions z-num (.-z layout-node))

          (cond
            (= clicked num)
            (do (aset colors x-num 0.94)
                (aset colors y-num 0.75)
                (aset colors z-num 0.01))

            (= hovered num)
            (do (aset colors x-num 0.94)
                (aset colors y-num 0.75)
                (aset colors z-num 0.02))

            (and ?neighbours (some #{num} ?neighbours))
            (do (aset colors x-num 0.94)
                (aset colors y-num 0.75)
                (aset colors z-num 0.02))

            :default
            (do (aset colors x-num 1)
                (aset colors y-num 1)
                (aset colors z-num 1)))

          ;; override with marked number if needed
          (when (and (not-empty marked-txs) (some #{id} marked-txs))
            (do (aset colors x-num 0.94)
                (aset colors y-num 0.75)
                (aset colors z-num 0.02))))
        nil))
    (set! (.-needsUpdate (.-position (.-attributes (.-geometry points-cloud)))) true)
    (set! (.-needsUpdate (.-customColor (.-attributes (.-geometry points-cloud)))) true)
    (.setDrawRange (.-geometry points-cloud) 0 (.getNodesCount graph))
    render-state))


(defn must-be-marked?
  "Checks if a specific link must be marked by comparing it
  using the from and targets of a transaction."
  [from targets [link-from link-to]]
  (and
    (or (= link-from from) (= link-to from))
    (or (some #{link-from} targets) (some #{link-to} targets))))


(defn update-link-positions!
  [render-state]
  (let [^js/Object graph  (data/render-state-graph render-state)
        ^js/Object layout (data/render-state-layout render-state)
        ^js/Object scene  (data/render-state-scene render-state)

        ?marked-state (u/render-state-marked-tx-state-lens render-state)
        marked-state? (ai/tx-marked? ?marked-state)

        [?from ?targets] (if marked-state?
                           (let [from    (ai/tx-marked-from ?marked-state)
                                 targets (ai/tx-marked-targets ?marked-state)]
                             [from targets])
                           [nil nil])


        ?hovered-state (u/render-state-node-marked-lens render-state)
        hovered-state? (ai/node-marked-by-mouse-hover? ?hovered-state)
        ?hovered-txs (when hovered-state?
                       (ai/node-marked-by-mouse-hover-neighbour-links ?hovered-state))
        dec-tx-count? (= (mod (data/render-state-frame-counter render-state) 30) 0)]
    (.forEachLink
      graph
      (fn [^js/Object link]
        (let [id          (.-id link)
              layout-link (.getLinkPosition layout id)
              from-x      (.-x (.-from layout-link))
              from-y      (.-y (.-from layout-link))
              from-z      (.-z (.-from layout-link))
              to-x        (.-x (.-to layout-link))
              to-y        (.-y (.-to layout-link))
              to-z        (.-z (.-to layout-link))

              alpha       0.8
              temperature (mu/js-kw-get link :temperature)
              c           (mu/js-kw-get link :count)]

          (if-let [line (.-mesh link)]
            (do
              (let [vertices (.-vertices (.-geometry line))
                   old-from (aget vertices 0)
                   old-to   (aget vertices 1)]
               (mu/set-coords! old-from from-x from-y from-z)
               (mu/set-coords! old-to to-x to-y to-z)
               (set! (.-verticesNeedUpdate
                       (.-geometry line)) true)


               (cond
                 (< 0 temperature)
                 (do (mu/js-kw-set! link :temperature (- temperature 0.7))
                     (set! (.-material line)
                       (if (mu/js-kw-get link :success)
                        (case (mu/js-kw-get link :type)
                          0 (materials/active-link-material-red c temperature)
                          1 (materials/active-link-material-dark-red c temperature)
                          2 (materials/active-link-material-blue c temperature)
                          3 (materials/active-link-material-yellow c temperature)
                          (materials/active-link-material-dark-blue c temperature))
                        (materials/active-link-material-error-red c temperature)
                        )))
                 :default
                 (do (mu/js-kw-set! link :temperature -1)
                     (when dec-tx-count? (mu/js-kw-set! link :count (max 0 (dec c))))
                     (set! (.-material line) (materials/base-line-material c)))))

              ;; this overrides with marked color
              (when marked-state?
                (let [from-id (.-fromId link)
                      to-id   (.-toId link)]
                  (when (must-be-marked? ?from ?targets [from-id to-id])
                    (set! (.-material line) materials/marked-line-material))))

              (when (and hovered-state? (some #{(.-id link)} ?hovered-txs))
                (set! (.-material line) materials/marked-line-material)))

            ;; else
            (let [new-line (geometries/line-geometry
                             [[from-x from-y from-z] [to-x to-y to-z]])]
              (set! (.-mesh link) new-line)
              (.add scene new-line)
              (set! (.-material new-line) (materials/base-line-material c))))
          nil)))))


(defn update-link-positions [render-state]
  (update-link-positions! render-state)
  render-state)


(defn update-controls! [render-state]
  (.update (data/render-state-controls render-state)))

(defn update-controls [render-state]
  (update-controls! render-state)
  render-state)


(defn render-scene! [render-state]
  (let [renderer (data/render-state-renderer render-state)
        scene (data/render-state-scene render-state)
        camera (data/render-state-camera render-state)]
    (.render renderer scene camera)
    render-state))

(defn render-scene [render-state]
  (render-scene! render-state)
  (lens/overhaul
    render-state
    data/render-state-frame-counter
    u/increase-frame-counter))


(declare visualization-loop)

(defn request-loop-call [render-state interaction-callbacks]
  (js/requestAnimationFrame #(visualization-loop render-state interaction-callbacks))
  render-state)

(defn update-stats! [render-state]
  (.update (u/render-state-tx-stats-state-lens render-state))
  render-state)

(defn step [render-state]
  (-> render-state
      (step-layout!)
      (update-node-positions)
      #_(globe/update-globe!)
      (update-link-positions)
      (update-sizes)
      ))


(defn animate-link! [^js/Object graph from to type success?]
  (graph-layout/update-link-data graph from to :count u/inc-count 0)
  (graph-layout/add-data-to-link graph from to :temperature 100)
  (graph-layout/add-data-to-link graph from to :success success?)
  (graph-layout/add-data-to-link graph from to :type (tx->num type)))


(defn add-link-or-animate! [^js/Object graph from to type success?]
  (when-not (graph-layout/link-exists? graph from to)
    (.addLink graph from to))
  (animate-link! graph from to type success?))

(defn add-node-if-not-exists! [^js/Object graph id]
  (let [count (.getNodesCount graph)]
   (when (not (graph-layout/node-exists? graph id))
     (.addNode graph id {:num count}))))

(defn add-tx-to-graph! [render-state event]
  (let [from (data/new-transaction-event-from event)
        to (data/new-transaction-event-targets event)
        type (data/new-transaction-event-type event)
        success? (data/new-transaction-event-success? event)

        graph (data/render-state-graph render-state)]
    (do
      (add-node-if-not-exists! graph from)
      (run! (fn [id]
              (add-node-if-not-exists! graph id)
              (add-link-or-animate! graph from id type success?))
            to))))


(defn tx-event-handler [render-state tx-event]
  (cond

    (data/new-transaction-event? tx-event)
    (let [num-tx (inc (lens/yank render-state u/tx-counter-lens))]
      (add-tx-to-graph! render-state tx-event)
      (-> render-state
       (u/tx-counter-lens num-tx)
       (u/tx-stats-updated-lens (.getTime (js/Date.)))))

    :default
    render-state))


(defn maybe-inform-app [render-state gui-callback]
  (let [interaction-state (data/render-state-interaction-state render-state)
        needs-update? (u/render-state-interaction-needs-update-lens render-state)
        marked (ai/interaction-state-node-marked interaction-state)
        prev-marked (ai/interaction-state-previous-node-marked interaction-state)
        selected (ai/interaction-state-node-selected interaction-state)
        graph (data/render-state-graph render-state)]

    (if needs-update?
      (do
        (when (ai/node-marked-by-mouse-hover? marked)
          ((data/gui-component-callbacks-hover-address gui-callback)
           (graph-layout/get-node-id-by-num graph (ai/node-marked-by-mouse-hover-index marked))))

        (when (and (ai/no-node-marked? marked) (not (ai/node-marked-by-app? prev-marked)))
          ((data/gui-component-callbacks-hover-address gui-callback) nil))

        (when (ai/node-selected-by-mouse-click? selected)
          ((data/gui-component-callbacks-set-address gui-callback)
           (graph-layout/get-node-id-by-num graph (ai/selected-node-index selected))))

        (u/render-state-interaction-needs-update-lens render-state false))
      render-state)))


(defn maybe-inform-counter [render-state gui-callback]
  (let [last-updated (u/tx-stats-updated-lens render-state)
        now (.getTime (js/Date.))
        counter-call (data/gui-component-callbacks-tx-counter gui-callback)]
    (if (< 100 (- now last-updated))
      (do (counter-call (lens/yank render-state u/tx-counter-lens))
          (u/tx-stats-updated-lens render-state now))
      render-state)))


(defn interact [render-state old-render-state gui-callback]
  (-> render-state
      (maybe-inform-counter gui-callback)
      (maybe-inform-app gui-callback)))


(defn handle-app-message [render-state msg]

  (cond
    (ai/nop? msg)
    render-state

    (mouse/mouse-message? msg)
    (mouse/mouse-message-handler render-state msg)

    (graph-layout/search-address-message? msg)
    (graph-layout/search render-state msg)

    (ai/click-address-message? msg)
    (let [idx (graph-layout/get-node-num-by-id (data/render-state-graph render-state)
                (ai/click-address-message-address msg))]
      (lens/overhaul render-state data/render-state-interaction-state
        #(ai/select % (ai/make-node-selected-by-app idx (animations/linear 0 1 true)))))

    (ai/unclick-address-message? msg)
    (lens/overhaul render-state data/render-state-interaction-state
      #(ai/select % ai/no-node-selected-inst))

    (ai/hovered-address-message? msg)
    (let [idx (graph-layout/get-node-num-by-id (data/render-state-graph render-state)
                (ai/hovered-address-message-address msg))]
      (lens/overhaul render-state data/render-state-interaction-state
        #(ai/mark-node-by-app % idx)))

    (ai/unhovered-address-message? msg)
    (lens/overhaul render-state data/render-state-interaction-state ai/unmark-node)

    (ai/app-mark-transaction-message? msg)
    (let [from    (ai/app-mark-transaction-message-from msg)
          targets (ai/app-mark-transaction-message-targets msg)]
      (u/render-state-marked-tx-state-lens render-state
        (ai/make-tx-marked from targets)))

    (ai/app-unmark-transaction-message? msg)
    (u/render-state-marked-tx-state-lens render-state ai/no-tx-marked)

    (ai/auto-rotate-message-t? msg)
    (ai/handle-auto-rotate-message render-state msg)

    :default
    render-state))


(defn visualization-loop [render-state interaction-callbacks]
  (async/go
    (let [tx-mailbox (data/render-state-new-tx-mailbox render-state)

          ;; read next tx from socket. We have 60 frames per second at best
          ;; and max 10tx/s nowadays on XRPL. Maybe we need to increase that
          ;; reading (e.g. read 5 a time) later on.
          ?tx-event (or (mailbox/receive-next tx-mailbox) ai/nop)

          ;; read next message from reacl app
          from-app-mailbox (u/render-state-from-app-mailbox-lens render-state)
          ?app-message (or (mailbox/receive-next from-app-mailbox) ai/nop)]

      (-> render-state
        (tx-event-handler ?tx-event)
        (handle-app-message ?app-message)
        (mouse/raycast-mouse-hover)
        (step)
        (update-controls)
        (render-scene)
        (interact render-state interaction-callbacks)
        (request-loop-call interaction-callbacks)))))


(defn kickoff-visualization! [render-state interaction-callbacks]
  (visualization-loop render-state interaction-callbacks))
