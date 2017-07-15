(ns diff-eq.core
  (:require [clojure.walk :as w]
            [alandipert.kahn :refer [kahn-sort]]
            ))

(defn ring-read
  "Read from array as ring buffer, given start index and offset"
  ^double [^doubles darr ^long indx ^long offset]
  (let [off (+ indx offset)
        adjoff 
        (if (neg? off)
          (+ off (alength darr))
          off )]
    (aget darr adjoff)))

(defn- gen-hist-state
  "Generates bindings to create state space for history."
  [max-hist sym-hist]
  (reduce-kv (fn [m k v] 
               (into m [(sym-hist k) `(double-array ~v)])) 
             [] max-hist))

(defn- gen-indx-state
  "Generates bindings to create state space for ring buffer indices."
  [max-hist sym-indx]  
  (reduce-kv (fn [m k ^long v]
               (if (> v 1)
                 (into m [(sym-indx k)
                          `(long-array 1)])
                 m))
             [] max-hist ))



(defn- gen-hist-updates
  "Generates history storage statements."
  [hist-keys multi-hist-keys sym-hist sym-indx]
  (let [multi-set (into #{} multi-hist-keys)] 
    (reduce 
      (fn [m k] 
        (conj m 
              (if (multi-set k) 
                `(aset-double ~(sym-hist k) (aget ~(sym-indx k) 0) ~k)
                `(aset-double ~(sym-hist k) 0 ~k)))) 
      [] hist-keys)))

(defn- gen-indx-updates
  "Generates index update and storage statements."
  [max-hist multi-hist-keys sym-indx]
  (reduce 
    (fn [m k] 
      (conj m 
            (let [v (sym-indx k)]
              `(aset-long ~v 0 (mod (inc (aget ~v 0)) 
                                    ~(max-hist k)))))) 
    [] multi-hist-keys))

(defn- process-hist-map
  "Takes found history map and converts to positive values to use as array
  lengths"
  [hist-map]
  (->> 
    hist-map
    (filter #(< (long (second %)) 0)) 
    (map #(assoc % 1 (* -1 (long (second %)))))
    (into {})))

(defn- analyze-dfn 
  "sigs - set of all signals (given and generated)
  eq - equation to analyze"
  [sigs eq] 
  (let [hist-map (atom (zipmap sigs (repeat 0)))
        update-hist!
        (fn [x]
          (when (and (vector? x)
                     (= 2 (count x))
                     (sigs (first x)))
            (let [[sym ^long n] x]
              (when (< n (long (@hist-map sym)))
                (swap! hist-map assoc sym n))))
          x)] 
    (w/postwalk update-hist! eq)
    (process-hist-map @hist-map)
    ))

(defn- transform-dfn
  "Transforms difference equation function, replacing history notation with
  history handling code (array or ring-buffer get)."
  [sym-hist sym-indx eq]
  (letfn [(hist-transform [x]
            (if (and (vector? x)
                     (= 2 (count x))
                     (contains? sym-hist (first x)))
              (let [[sym n] x
                    s (sym-hist sym)
                    indx (sym-indx sym)]
                (if indx
                  `(diff-eq.core/ring-read ~s (aget ~indx 0) ~n) 
                  `(aget ~s 0)))
              x))]
    (w/postwalk hist-transform eq)
    ))

(defn- get-deps
  "Find dependencies for a difference equation function, given the possible
  generated signals it could depend upon. Should be called after initial
  equations are transformed for history notation so that the sorting is done
  for generated values in the current n time."
  [dfn gen-sigs]
  (let [deps (atom #{})
        analyzer 
        (fn [a]
          (when (and (symbol? a) (gen-sigs a))
            (swap! deps conj a))
          a)]
    (w/postwalk analyzer dfn) 
    @deps))

(defn- reorder-dfn
  "Reorders difference equation functions based on dependencies upon
  outputs of other difference equations.  Uses Alan Dipert's implementation
  of Kahn's algorithm for topological sort."
  [dfns gen-sigs]
  (let [deps-map 
        (zipmap 
          (map first dfns)
          (map #(get-deps (second %) gen-sigs) dfns))
        dfn-map (zipmap (map first dfns) (map second dfns))
        sorted-deps (kahn-sort deps-map)]
    (when-not sorted-deps
      (throw (Exception. "Cyclic dependency found")))
    (mapcat #(vector % (dfn-map %)) (reverse sorted-deps))))

(defn- analyze-history
  "Given the found history for each difference equation, merge to give the
  largest history found per signal."
  [found-hist]
  (reduce 
    (fn [a b]
      (reduce-kv
        (fn [m k ^long v]
          (if (contains? m k)
            (if (> v (long (m k)))
              (assoc m k v) 
              m) 
            (assoc m k v))) 
        a b))
    {} found-hist))

(defn- type-hint-args
  [args]
  (let [res (mapv #(with-meta % {:tag "double"}) args)]
    (with-meta res {:tag "double"})))

(defmacro dfn
  "Generates a signal processing function from given difference equations. Previous values of inputs or 
  ouputs are expressed within difference equations using a 2-vector with the argument symbol used
  as the first value and the n-offset as the second value. dfn will generate
  code to read and write history for the inputs and outputs that require it. 
  dfn currently assumes inputs and outputs are all of type double.

  Difference equations may be ordered as the user wishes; equations will be analyzed for dependencies and
  topologically sorted.  If a circular dependency is found, dfn will throw an exception. 

  Internally, arrays and indexes are used to create ring buffers for keeping
  track of history."
  [args & body]
  {:pre [(vector? args)
         (or (= 2 (count body))
             (and (>= (count body) 5) 
                  (= :where (nth body 2))
                  (odd? (count body))))]} 
  (let [ ;; analysis
        main-eq (take 2 body)
        rest-eq (partition 2 (drop 3 body)) 
        result-sym (first main-eq)
        all-eq (map (partial into []) (cons main-eq rest-eq))
        gen-sigs (set (cons result-sym (map first rest-eq)))
        all-sigs (set (into args gen-sigs))
        found-hist (map #(analyze-dfn all-sigs (second %)) all-eq)
        max-hist (analyze-history found-hist)
        hist-keys (keys max-hist)
        multi-hist-keys 
        (reduce-kv (fn [m k ^long v] (if (> v 1)
                                 (conj m k)
                                 m))
                   [] max-hist)
        analyses (map #(conj % %2) all-eq found-hist)

        ;; create symbols for history and indices
        sym-hist (zipmap hist-keys 
                         (map #(gensym (str % "-hist")) 
                              hist-keys))              
        sym-indx (zipmap multi-hist-keys 
                         (map #(gensym (str % "-indx")) 
                              multi-hist-keys))              
        ;; transformation 
        trans-eq 
        (map #(update % 1 (partial transform-dfn sym-hist sym-indx)) 
             analyses)
        main-body (reorder-dfn trans-eq gen-sigs)

        ;; state
        hist-state (gen-hist-state max-hist sym-hist)
        indx-state (gen-indx-state max-hist sym-hist)
        hist-updates
        (gen-hist-updates 
          hist-keys multi-hist-keys
          sym-hist sym-indx)
        indx-updates
        (gen-indx-updates 
          max-hist multi-hist-keys sym-indx) 

        ;; function body
        func-body-start `(let [~@main-body])
        func-body (concat func-body-start
                          hist-updates
                          indx-updates
                          [result-sym])]
    ;(println main-eq)
    ;(println rest-eq)
    ;(println gen-sigs)
    ;(println all-sigs)
    ;(println found-hist)
    ;(println max-hist)
    ;(println analyses)
    ;(println sym-hist)
    ;(println sym-indx)
    ;(println trans-eq)
    ;(println hist-state)
    ;(println indx-state)
    ;(println main-body)

    `(let ~(into hist-state indx-state)
       (fn ~(type-hint-args args)
         ~func-body
         ))
    )
  )

(comment

  (def biquad-tdfII
    (dfn [x b0 b1 b2 a1 a2]
         y (+ (* b0 x) [s1 -1])
         :where
         s1 (+ [s2 -1] (- (* b1 x) (* a1 y)))
         s2 (- (* b2 x) (* a2 y))))

  ;; one-pole, zero-delay feedback filter

  (def sr 44100)

  ;(defn calc-G [cut]
  ;  (let [wd  (* cut (* 2.0 Math/PI))
  ;        T (/ 1.0 sr)
  ;        wa  (* (/ 2.0 T) 
  ;               (Math/tan (* wd (/ T 2.0))))
  ;        g (* wa (/ T 2))]
  ;    (/ g (+ 1.0 g))))

  ;; Zero-delay feedback, one-pole low-pass filter
  (def zdf-lpf1
    (dfn [x cut]
         y (+ v [s -1])

         :where
         v (* G (- x [s -1])) 
         s (+ v y)

         ;; G calculation
         wd  (* cut (* 2.0 Math/PI))
         T (/ 1.0 sr)
         wa  (* (/ 2.0 T) 
                (Math/tan (* wd (/ T 2.0))))
         g (* wa (/ T 2))
         G (/ g (+ 1.0 g))
         )))
