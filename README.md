# diff-eq

A Clojure library for generating signal processing functions given difference equations. Users use the dfn (difference function) macro to process a primary difference equation and optional dependent equations.  The macro will handle processing of history storage and retrieval of state. Historical values are notated in code using a 2-vector with the first argument as the argument name and the second value as the offset in time. For example, given x, to use the previously given value of x, use [x -1]. (Equivalent to x(n -1).) 

This is not a solver, as the generated functions are meant to be used in real-time processing of values one sample at a time.  

## Usage

```clojure

(require '[diff-eq.core :refer [dfn])

;; y(n) = b0 * x(n) - a1 * y(n - 1)

(def one-pole-filter
  (dfn [x b0 a1]
    y (- (* b0 x) (* a1 [y -1]))))

(one-pole-filter 1.0 0.5 0.5)

;; y(n) = b0 * x(n) + b1 * x(n - 1)

(def one-zero-filter
  (dfn [x b0 b1]
    y (+ (* b0 x) (* b1 [x -1]))))

(one-zero-filter 1.0 0.5 0.5)

;; make coefficients constant and make one-pole-filter return filter function

(defn one-pole-filter [b0 a1]
  (dfn [x]
    y (- (* b0 x) (* a1 [y -1]))))

(def filter0 
  (one-pole-filter 0.5 0.5))

(filter0 1.0)

;; Use :where clause to provide dependent difference equations
;;
;; Biquad (Transposed Direct-Form II)
;; https://en.wikipedia.org/wiki/Digital_biquad_filter
;;
;; y(n) = b0 * x + s1(n -1)
;; where
;; s1(n) = s2(n - 1) + b1 * x(n) - a1 * y(n)
;; s2(n) = b2 * x(n) - a2 * y(n)

(def biquad-tdfII
  (dfn [x b0 b1 b2 a1 a2]
        y (+ (* b0 x) [s1 -1])
        :where
        s1 (+ [s2 -1] (- (* b1 x) (* a1 y)))
        s2 (- (* b2 x) (* a2 y))))

```
## Documentation

Further information about the macro implementation is available in the [documentation](docs/intro.md).

## License

Copyright © 2016 Steven Yi 

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
