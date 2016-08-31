(ns diff-eq.core-test
  (:require [clojure.test :refer :all]
            [diff-eq.core :refer :all]))

(deftest one-pole-test
  (testing "one-pole filter"
    (let [one-pole
          (dfn y [x a0 b0] 
               (- (* a0 x) (* b0 [y -1])))]
       (is (= 0.5 (one-pole 1 0.5 0.5))) 
       (is (= 0.25 (one-pole 1 0.5 0.5))) 
       (is (= 0.375 (one-pole 1 0.5 0.5))) 
       (is (= -0.1875 (one-pole 0 0.5 0.5))) 
      )
    ))


