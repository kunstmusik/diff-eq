# diff-eq

A Clojure library for writing difference equations. Users use the dfn macro (short for difference function) to write functions that use previously given or generated values.  The macro will handle storage and retrieval of state. Historical values are given using a 2-vector with the first argument as the argument name and the second value as the offset in time. For example, given x, to use the previously given value of x, use [x -1]. (Equivalent to x(n -1).) 

## Usage

```clojure

(require '[diff-eq.core :refer [dfn])

;; y(0) = a * x(0) - b * y(-1)

(def one-pole-filter
  (dfn y [x a0 b0]
    (- (* a0 x) (* b0 [y -1]))))

(one-pole-filter 1.0 0.5 0.5)

```

## License

Copyright © 2016 Steven Yi 

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
