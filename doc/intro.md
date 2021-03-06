# diff-eq

## About

1. Support 1:1 writing of difference equations for signal processing
2. Generates function optimized for processing of difference equation
3. Support :where clauses for sub-equations 
4. Support initial conditions for primary equation and setting of initial state for history (Not yet implemented)


## Compilation Process

### Preparation
1. gather generated signal values (i.e., out arguments for each equation)
2. gather all signal values (i.e., out + input arguments)

### Analysis
For each equation, analyze:

1. signal history access (i.e., what signals used)
2. signal history degree (i.e., number of steps used)

After each equation is analyzed, determine

1. Total signal history for each signal
2. History method to use for each signal (none, single-sample, multi-sample)
 
### Transform
1. Each signal equation should be transformed to use history retrieval methods
2. Dependencies for each transformed equation are used to sort the order in which they will be executed.  
3. A final function will be generated with the following processing shape:
  1. Create a let-binding to initialize state history
  2. If initial conditions were found, generate values according those until conditions are met
  3. Primary processing will occur in a let-binding, capturing newly generated values for each difference equation
  4. Update history values and indices (for signals which use ring-buffers)
  5. return new value


## History Methods

History for each input- and generated-signal is handled as follows:

1. If no history is required and only given or generated values are used, no history code should be generated for that signal
2. If only a single-history value is required, an array of length 1 will be used and no index will be generated, updated, or used.
3. If a signal is found to require multiple values in history, a ring-buffer will be used.  The ring-buffer will be backed by an array and an index will be used to mark time 0, and ring-reads offset from the current index. 


## TODO

1. Implement support for initial state values (generated signal history)
2. Implement support for initial conditions (pre-history of the primary equation). For example, a Fibonacci sequence could be defined as "y[n] = y[n - 1] + y[n - 2] where y[0] = 0, y[1] = 1".  
3. Implement support for variable length delay times. In difference equation this may be seen in something like "y[n] = x[n] + x[n - m]" where m is a variable.  Since delay times may possible modulate, a clear notation would be required for marking maximum delay times.
4. Depending upon 3, may wish to support fractional delay times and different delay interpolation schemes.  A clear notation is again required.  


```clojure
;; proposal for fibonacci
(def fibonacci
  (dfn []
    y (+ [y -1] [y -2])
    :where
    [y 0] 0
    [y 1] 1)

;; example for variable length delay
;; analysis would be added to check second argument of history notation
(def vdelay
  (dfn [x del-time max-del-time]
    y [x m]
    :where
    m (* del-time sample-rate) :max-delay (* max-del-time sample-rate)))





