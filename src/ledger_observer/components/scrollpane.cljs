(ns ledger-observer.components.scrollpane
  (:require [active.clojure.record :as rec :include-macros true]
            [reacl2.core :as reacl :include-macros true]
            [ledger-observer.mouse :as mouse]
            [active-viz.scale :as scale]
            [reacl2.dom :as dom :include-macros true]))


(defn scroll-bottom! [id]
  (let [^js/Object el (.getElementById js/document id)
        scroll-height (.-scrollHeight el)
        scroll-top    (.-scrollTop el)]
    (set! (.-scrollTop el) scroll-height)))


(defn at-bottom? [id]
  (let [^js/Object el (.getElementById js/document id)
        scroll-height (.-scrollHeight el)
        scroll-top    (.-scrollTop el)
        outer-height  (.-offsetHeight el)]
    (>= outer-height (- scroll-height scroll-top))))

(def scrollbar-height 60)

(rec/define-record-type ScrollPaneState
  (make-scroll-pane-state outer-id inner-id padding-right height
    scrollbar-pos scale clicked-pos bottom? scrollable? args)
  scroll-pane-state?
  [outer-id scroll-pane-state-outer-id
   inner-id scroll-pane-state-inner-id
   padding-right scroll-pane-state-padding-right
   height scroll-pane-state-height
   scrollbar-pos scroll-pane-state-scrollbar-pos
   scale scroll-pane-state-scale
   clicked-pos scroll-pane-state-clicked-pos
   bottom? scroll-pane-state-bottom?
   scrollable? scroll-pane-state-scrollable?
   args scroll-pane-state-args
   ])


(rec/define-record-type Scrolled (make-scrolled) scrolled? [])


(rec/define-record-type Clicked
  (make-clicked x y) clicked?
  [x clicked-x
   y clicked-y])

(rec/define-record-type Moved
  (make-moved x y) moved?
  [x moved-x
   y moved-y])

(rec/define-record-type Released (make-released) released? [])

(defn el-by-id [id]
  (.getElementById js/document id))

(defn scale-by-ref [ref outer]
  (let [inner        (el-by-id ref)
        inner-height (.-scrollHeight inner)
        outer-height (.-offsetHeight (el-by-id outer))]
    (scale/make-linear-scale
      [0 (- inner-height outer-height)]
      [0 (- outer-height scrollbar-height)])))


(defn scrollable? [ref outer]
  (let [inner        (el-by-id ref)
        inner-height (.-scrollHeight inner)
        outer-height (.-offsetHeight (el-by-id outer))]
    (> inner-height outer-height)))


(defn relative-to [y ref]
  (- y (.-top (.offset (js/jQuery (str "#" ref))))))



(reacl/defclass pane this [fix-bottom? style clazz & args]


  local-state [local-state (make-scroll-pane-state (str (gensym)) (str (gensym))
                             nil nil 0 nil nil true false nil)]

  component-did-mount
  (fn [_]
    (let [inner (el-by-id (scroll-pane-state-inner-id local-state))
          padding (- (.-offsetWidth inner) (.-clientWidth inner))
          height (.-offsetHeight inner)
          pos (if fix-bottom? (- height scrollbar-height) 0)]
      (reacl/return :local-state
        (-> local-state
          (scroll-pane-state-padding-right padding)
          (scroll-pane-state-height height)
          (scroll-pane-state-scrollbar-pos pos)
          (scroll-pane-state-scale (scale-by-ref (scroll-pane-state-inner-id local-state)
                                     (scroll-pane-state-outer-id local-state)))))))


  component-did-update
  (fn [_ _]
    (let [pane-id (scroll-pane-state-inner-id local-state)
          outer (scroll-pane-state-outer-id local-state)]

      (when-not (= (scroll-pane-state-args local-state) args)
        (do
          (when (and fix-bottom? (scroll-pane-state-bottom? local-state))
            (scroll-bottom! pane-id))
          (reacl/return :local-state
            (-> local-state
              (scroll-pane-state-args args)
              (scroll-pane-state-scrollable? (scrollable? pane-id outer))
              (scroll-pane-state-scale  (scale-by-ref pane-id outer))))))))

  render
  (let [pane-id (scroll-pane-state-inner-id local-state)
        outer (scroll-pane-state-outer-id local-state)]
    (dom/div {:style    style
              :onscroll #(reacl/send-message! this (make-scrolled))
              :id       outer
              :class    "inspect-box"}
     (when (scroll-pane-state-scrollable? local-state)
       (dom/div {:class       "scroll-bar-handle"
                 :id          (str "scroll-bar-handle" pane-id)
                 :onmousedown (fn [ev]
                                (reacl/send-message! this
                                  (make-clicked (.-clientX ev)
                                    (relative-to (.-clientY ev) (str "scroll-bar-handle" pane-id)))))
                 :style       {:top    (scroll-pane-state-scrollbar-pos local-state)
                               :height scrollbar-height}}))

     (dom/div {:class "inspect-box-inner"
               :id    pane-id
               :style {:width         "100%"
                       :box-sizing    "content-box"
                       :padding-right (or (scroll-pane-state-padding-right local-state) 0)}}
       (apply clazz args))))


  handle-message
  (fn [msg]
    (cond
      (scrolled? msg)
      (let [el (el-by-id (scroll-pane-state-inner-id local-state))
            scroll-top  (.-scrollTop el)]
        (reacl/return :local-state
          (-> local-state
            (scroll-pane-state-bottom? (at-bottom? (scroll-pane-state-inner-id local-state)))
            (scroll-pane-state-scrollbar-pos
              (scale/scale (scroll-pane-state-scale local-state) scroll-top)))))

      (clicked? msg)
      (let [y (clicked-y msg)]
        (reacl/return
          :local-state (scroll-pane-state-clicked-pos local-state y)
          :action (mouse/make-register-mouse-handlers-action
                    "abs"
                    (fn [x y] (reacl/send-message! this (make-moved x y)))
                    nil
                    (fn [_ _] (reacl/send-message! this (make-released))))))

      (moved? msg)
      (let [pane-id (scroll-pane-state-inner-id local-state)
            new-y (min (- (.-offsetHeight (el-by-id pane-id)) scrollbar-height)
                    (max 0
                      (- (relative-to (moved-y msg) pane-id)
                        (scroll-pane-state-clicked-pos local-state))))]
        (set! (.-scrollTop (el-by-id pane-id))
          (scale/scale-inverted (scroll-pane-state-scale local-state) new-y))
        (reacl/return
          :local-state (scroll-pane-state-scrollbar-pos local-state new-y)))

      (released? msg)
      (let [new-local-state (if (at-bottom? (scroll-pane-state-inner-id local-state))
                              (scroll-pane-state-bottom? local-state true)
                              local-state)]
       (reacl/return
         :local-state new-local-state
         :action (mouse/make-deregister-mouse-handlers-action "abs")))


      )))
