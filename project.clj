(defproject slack-nag "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [
                 [hiccup "1.0.5"]
                 [clj-http "1.0.1"]
                 [org.clojure/data.json "0.2.5"]
                 [org.clojure/tools.logging "0.3.1"]
                 [org.clojure/clojure "1.6.0"]]
  :main ^:skip-aot slack-nag.core
  :target-path "target/%s"
  :min-lein-version "2.3.0"
  :profiles {:uberjar {:aot :all}})
