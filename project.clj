(defproject kunstmusik/diff-eq "0.1.2"
  :description "Library for generating signal processing functions from difference equations."
  :url "http://github.com/kunstmusik/diff-eq"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]]
  
  :profiles {
            :dev {
                  :global-vars {*warn-on-reflection* true
                                *unchecked-math* :warn-on-boxed }}}
  )
