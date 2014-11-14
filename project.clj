(defproject girltalk "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [enlive "1.1.5"]
                 [clj-time "0.8.0"]
                 [cheshire "5.3.1"]
                 [org.clojure/clojurescript "0.0-2261"]
                 [reagent "0.4.2"]
                 [cljs-http "0.1.17"]
                 [instaparse "1.3.4"]
]
  :plugins [[lein-cljsbuild "1.0.3"]]

  :cljsbuild {
              :builds [{
                        ;; The path to the top-level ClojureScript source directory:
                        :source-paths ["src-cljs"]
                        ;; The standard ClojureScript compiler options:
                        ;; (See the ClojureScript compiler documentation for details.)
                        :compiler {:optimizations :whitespace
                                   :pretty-print true

}}]}
)
