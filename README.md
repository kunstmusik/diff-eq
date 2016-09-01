# diff-eq

A Clojure library for writing difference equation functions. Users use the dfn (difference function) macro to write functions that use previously given or generated values.  The macro will handle storage and retrieval of state. Historical values are employed in code using a 2-vector with the first argument as the argument name and the second value as the offset in time. For example, given x, to use the previously given value of x, use [x -1]. (Equivalent to x(n -1).) 

This is not a solver, as the generated functions are meant to be used in real-time processing of values one sample at a time.  

## Usage

```clojure

(require '[diff-eq.core :refer [dfn])

;; y(n) = a * x(n) - b * y(n - 1)

(def one-pole-filter
  (dfn y [x a0 b0]
    (- (* a0 x) (* b0 [y -1]))))

(one-pole-filter 1.0 0.5 0.5)

;; make coefficients constant and make one-pole-filter return filter function

(defn one-pole-filter [a0 b0]
  (dfn y [x]
    (- (* a0 x) (* b0 [y -1]))))

(def filter0 
  (one-pole-filter 0.5 0.5))

(filter0 1.0)

```

## License

Copyright © 2016 Steven Yi 

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
