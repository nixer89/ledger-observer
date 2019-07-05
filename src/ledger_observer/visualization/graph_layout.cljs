(ns ledger-observer.visualization.graph-layout
  (:require [active.clojure.record :as rec :include-macros true]
            [ledger-observer.visualization.data :as data]
            [ngraph-layout :as Layout]
            [ngraph-graph :as Graph]))


(enable-console-print!)

(rec/define-record-type SearchAddressMessage
  (make-search-address-message address callback) search-address-message?
  [address search-address-message-address
   callback search-address-message-callback])


(defn node-exists? [^js/Object graph id] (if (.hasNode graph id) true false))
(defn link-exists? [^js/Object graph from to] (if (.hasLink graph from to) true false))

(defn add-data-to-link [^js/Object graph from to kw value]
  (let [link (.getLink graph from to)]
    (aset link (str kw) value)))

(defn update-link-data [^js/Object graph from to kw f default]
  (let [link (.getLink graph from to)
        kw-str (str kw)
        v (aget link kw-str)
        new-v (if v (f v) default)]
    (aset link kw-str new-v)))

(defn js-map
  "Makes a javascript map from a clojure one"
  [cljmap]
  (let [out (js-obj)]
    (doall (map #(aset out (name (first %)) (second %)) cljmap))
    out))

(defn layout []
  (let [graph (Graph [])
        layout (Layout graph
                       (js-map
                        {:dragCoeff 0.24
                         :springLength 1.2
                         :springCoeff 0.0008
                         :gravity -0.005
                         :theta 0.2
                         :timeStep 3
                         }))]
    [graph layout]))

(defn java-coordinate-to-vector [coord]
  [(.-x coord) (.-y coord) (.-z coord)])

(defn node-positions [^js/Object layout ^js/Object graph]
  (let [arr (array)]
    (.forEachNode graph
                  (fn [node]
                    (let [layout-node (.getNodePosition layout (.-id node))]
                      (.push arr (java-coordinate-to-vector layout-node))
                      nil)))
    arr))

(defn link-positions [layout ^js/Object graph]
  (let [arr (array)]
    (.forEachLink graph
                  (fn [link]
                    (let [layout-link (.getLinkPosition layout (.-id link))
                          from (java-coordinate-to-vector (.-from layout-link))
                          to   (java-coordinate-to-vector (.-to layout-link))]
                      (.push arr [from to])
                      nil)))
    arr))





(defn filter-nodes [^js/Object graph filter-exp]
  (let [results (atom (list))]
    (.forEachNode
      graph
      (fn [node]
        (when (filter-exp node)
          (swap! results conj node))))
    (reverse @results)))


(defn get-node-id-by-num [^js/Object graph n]
  (when-let [f (first (filter-nodes graph (fn [node] (= (:num (.-data node)) n))))]
    (.-id f)))

(defn get-node-num-by-id [^js/Object graph id]
  (:num (.-data (first (filter-nodes graph (fn [node] (= (.-id node) id)))))))


(defn search [render-state ?search-message]
  (cond
    (search-address-message? ?search-message)
    (let [graph (data/render-state-graph render-state)
          addresses (search-address-message-address ?search-message)
          res (filter #(node-exists? graph %) addresses)
          callback! (search-address-message-callback ?search-message)]
      (callback! res)
      render-state)

    :default
    render-state))

(defn get-neighbours [^ĵs/Object graph id]
  (let [tx-id (get-node-id-by-num graph id)
        results (atom (list))]
    (.forEachLink
      graph
      (fn [l]
        (when (= tx-id (.-fromId l))
          (swap! results conj (get-node-num-by-id graph (.-toId l))))
        (when (= tx-id (.-toId l))
          (swap! results conj (get-node-num-by-id graph (.-fromId l))))))
    @results))

(defn get-links [^js/Object graph id]
  (let [tx-id   (get-node-id-by-num graph id)
        results (atom (list))]
    (.forEachLink
      graph
      (fn [l]
        (when (or (= tx-id (.-fromId l)) (= tx-id (.-toId l)))
          (swap! results conj (.-id l)))))
    @results))

(defn get-neighbours-num [^js/Object graph id]
  (map #(:num (.-data %)) (get-neighbours graph id)))

(defn step-layout! [layout] (.step layout))

(defn pin-node! [^js/Object layout ^js/Object id]
  (.pinNode layout id true))

(defn unpin-node! [^js/Object layout ^js/Object id]
  (.pinNode layout id false))

#_(defn layout [nodes]
  (Graph.)
  (println Layout)
  #_(Layout (Graph. ))
   #_(layout/forceSimulation nodes))
