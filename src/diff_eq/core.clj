(ns diff-eq.core
  (:require [clojure.walk :as w]))

(defn ring-read
  "Read from array as ring buffer, given start index and offset"
  [darr indx offset]
  (let [off (+ indx offset)
        adjoff 
        (if (neg? off)
          (+ off (alength darr))
          off )]
    (aget darr adjoff)))

(defn- map-filter
  "Utility function to filter a map and reassemble into a map."
  [ffn m]
  (into {} (filter ffn m)))

(defn- analyze-dfn
  "Analyzes body according to given out arg and input arguments. Rewrites 
  2-vectors in body that represent memory into history reads.  Returns filtered
  history map with argument symbols as keys and num of memory locations as 
  values, map of arg symbols to generated history symbols, map of arg symbols
  to generated index symbols, and the updated body."
  [y args body]
  (let [syms (into #{y} args)
        sym-hist (reduce #(assoc % %2 (gensym (str %2 "-hist"))) {} syms)
        sym-indx (reduce #(assoc % %2 (gensym (str %2 "-indx"))) {} syms)
        hist-map (atom (reduce #(assoc % %2 0) {} syms))
        update-hist!
        (fn [x]
          (if (and (vector? x)
                     (= 2 (count x)))
            (if-let [sym (syms (first x))] 
              (let [n (second x)]
                ;(println sym n)
                (when (< n (@hist-map sym))
                  (swap! hist-map 
                         #(assoc % sym n))
                  (let [s (sym-hist sym) 
                        indx (sym-indx sym)]
                    `(diff-eq.core/ring-read ~s (aget ~indx 0) ~n)
                  )))
              x) 
            x))
        updated-body (w/postwalk update-hist! body)
        filt-hist-map 
        (->> 
          @hist-map
          (filter #(< (second %) 0)) 
          (map #(assoc % 1 (* -1 (second %))))
          (into {}))
        hist-keys (into #{} (keys filt-hist-map))] 
   [filt-hist-map 
    (map-filter #(hist-keys (first %)) sym-hist) 
    (map-filter #(hist-keys (first %)) sym-indx) 
    updated-body]))


(defn- gen-hist-state
  "Generates bindings to create state space for history."
  [hist-map sym-hist]
  (reduce-kv (fn [m k v] 
               (assoc m (sym-hist k) 
                      `(double-array ~v))) 
          {} hist-map))

(defn- gen-indx-state
  "Generates bindings to create state space for ring buffer indices."
  [hist-map sym-indx]  
  (reduce-kv (fn [m k v] 
               (assoc m (sym-indx k) 
                      `(long-array 1))) 
          {} hist-map))


(defn- gen-hist-updates
  "Generates history storage statements."
  [y res-sym hist-map sym-hist sym-indx]
  (reduce-kv 
    (fn [m k v] 
      (conj m 
            (if (= k y)
            `(aset-double ~(sym-hist k) (aget ~v 0) ~res-sym)
            `(aset-double ~(sym-hist k) (aget ~v 0) ~k)))) 
    [] sym-indx))


(defn- gen-indx-updates
  "Generates index update and storage statements."
  [hist-map sym-indx]
  (reduce-kv 
    (fn [m k v] 
      (conj m 
            `(aset-long ~v 0 (mod (inc (aget ~v 0)) ~(hist-map k))))) 
    [] sym-indx))

(defmacro dfn
  "Generates a difference equation function. Previous values of inputs or 
  ouputs can be expressed using a 2-vector with the argument symbol used
  as the first value and the n-offset as the second value. dfn will generate
  code to read and write history for the inputs and outputs that require it. 
  dfn assumes inputs and outputs are all of type double.

  Internally, arrays and indexes are used to create ring buffers for keeping
  track of history."
  [y args body]
  {:pre [(symbol? y) (vector? args)]} 
  (let [[hist-map sym-hist sym-indx new-body] 
        (analyze-dfn y args body)
        res-sym (gensym "result")
        hist-state (gen-hist-state hist-map sym-hist)
        indx-state (gen-indx-state hist-map sym-indx)
        state-bindings (reduce #(into % %2) [] 
                               (concat indx-state hist-state))
        hist-updates (gen-hist-updates y res-sym hist-map sym-hist sym-indx)
        indx-updates (gen-indx-updates hist-map sym-indx)
        ]
    (if (pos? (count hist-state))
      `(let ~state-bindings
         (fn ~args
           (let [~res-sym ~new-body]
             ~@hist-updates
             ~@indx-updates 
             ~res-sym 
             ))) 

      `(fn ~args
         ~new-body) 
      )))

