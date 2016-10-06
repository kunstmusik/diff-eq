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

;; :where clauses may be ordered as fits the user. dfn will analyze
;; the clauses and main equation and topologically sort them based upon
;; signal dependencies.
;;
;; Zero-delay feedback, one-pole low pass filter
;; Based on code by Will Pirkle, presented in:
;;
;; http://www.willpirkle.com/Downloads/AN-4VirtualAnalogFilters.2.0.pdf
;; 
;; and in his book "Designing software synthesizer plug-ins in C++ : for 
;; RackAFX, VST3, and Audio Units"
;;
;; ZDF using Trapezoidal integrator by Vadim Zavalishin, presented in "The Art 
;; of VA Filter Design" (https://www.native-instruments.com/fileadmin/ni_media/
;; downloads/pdf/VAFilterDesign_1.1.1.pdf)
;; 
;; Note, use of s here differs from Zavalishin's notation in that it is defined 
;; as the value before the unit-delay, which keeps the n-time relationship 
;; consistent

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
         ))

```
## Documentation

Further information about the macro implementation is available in the [documentation](docs/intro.md).

## Acknowledgements

This library uses Alan Dipert's implementation of Kahn's Algorithm for
topological sorting. It is available
[online](https://gist.github.com/alandipert/1263783) and it has also been
included within this project.  


## Change Log

### 0.1.1
* fixed reflection warning


### 0.1.0
* initial release

## License

Copyright © 2016 Steven Yi 

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
