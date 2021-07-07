(ns ledger-observer.visualization.data
  (:require
   [active.clojure.cljs.record :as rec :include-macros true]
   [quick-type.core :as qt :include-macros true]
   [active.clojure.lens :as lens :include-macros true]))

(rec/define-record-type FilterMailboxes
  (make-filter-mailboxes in-f in-ledger-number out-f) filter-mailboxes?
  [in-f  filter-mailboxes-socket-mailbox
   in-ledger-number filter-mailboxes-app-mailbox-ledger-number
   out-f filter-mailboxes-app-mailbox
   ])

(rec/define-record-type GuiComponentsCallbacks
  (make-gui-component-callbacks tx-counter set-address hover-address) gui-component-callback?
  [tx-counter gui-component-callbacks-tx-counter
   set-address gui-component-callbacks-set-address
   hover-address gui-component-callbacks-hover-address])


(qt/def-type socket-message
  [(transaction-event [tid from targets type ledger-number success?])
   (payment-transaction-event [tid sender receiver issuers signers ledger-number success?])])

(qt/def-record update-ledger-number-event [ledger-number])
(qt/def-record update-ledger-number-message [ledger-number])

(qt/def-record update-ledger-number-event [ledger-number])
(qt/def-record update-ledger-number-message [ledger-number])

(rec/define-record-type PointsData
  (make-points-data points positions points-cloud colors sizes) points-data?
  [points points-data-points
   positions points-data-positions
   points-cloud points-data-points-cloud
   colors points-data-colors
   sizes points-data-sizes])

#_(rec/define-record-type GlobeData
  (make-globe-data points positions points-cloud colors sizes rotators lines
                   lines-positions lines-colors solid-globe) globe-data?
  [points globe-data-points
   positions globe-data-positions
   points-cloud globe-data-points-cloud
   colors globe-data-colors
   sizes globe-data-sizes
   rotators globe-data-rotators
   lines globe-data-lines
   lines-positions globe-data-lines-positions
   lines-colors globe-data-lines-colors
   solid-globe globe-data-solid-globe])

(rec/define-record-type LayoutData
  (make-layout-data graph layout) layout-data?
  [graph graph-layout-data
   layout layout-layout-data])

(rec/define-record-type WebglState
  (make-webgl-state scene camera renderer controls) webgl-state?
  [scene scene-webgl-state
   camera camera-webgl-state
   renderer renderer-webgl-state
   controls controls-webgl-state])


(rec/define-record-type MouseState
  (make-mouse-state mod-counter) mouse-state?
  [mod-counter mouse-state-mod-counter])

(rec/define-record-type RenderState
  (make-render-state webgl-state points-data layout-data animation-state
                     new-tx-mailbox interaction-state mouse-state frame-counter) render-state?
  [webgl-state render-state-webgl-state
   points-data render-state-points-data
   layout-data render-state-layout-data
   animation-state render-state-animation-state
   new-tx-mailbox render-state-new-tx-mailbox
   interaction-state render-state-interaction-state
   mouse-state render-state-mouse-state
   frame-counter render-state-frame-counter])


(defn render-state-layout [render-state]
  (-> render-state render-state-layout-data layout-layout-data))

(defn render-state-graph [render-state]
  (-> render-state  render-state-layout-data graph-layout-data))

(defn render-state-points-positions [render-state]
  (-> render-state render-state-points-data points-data-positions))

(defn render-state-points-colors [render-state]
  (-> render-state render-state-points-data points-data-colors))

(defn render-state-points-cloud [render-state]
  (-> render-state render-state-points-data points-data-points-cloud))

(defn render-state-points [render-state]
  (-> render-state render-state-points-data points-data-points))

(defn render-state-scene [render-state]
  (-> render-state render-state-webgl-state scene-webgl-state))

(defn render-state-camera [render-state]
  (-> render-state render-state-webgl-state camera-webgl-state))

(defn render-state-renderer [render-state]
  (-> render-state render-state-webgl-state renderer-webgl-state))

(defn render-state-controls [render-state]
  (-> render-state render-state-webgl-state controls-webgl-state))

(def point-sizes-lens (lens/>> render-state-points-data points-data-sizes))

(def mouse-mod-counter-lens (lens/>> render-state-mouse-state mouse-state-mod-counter))




(def points-cloud-lens (lens/>> render-state-points-data points-data-points-cloud))

