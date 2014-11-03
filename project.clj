(defproject slack-nag "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :user  {:plugins  [[lein-midje  "3.1.3"]]}
  :dependencies [
                 [hiccup "1.0.5"]
                 [midje  "1.6.3"]
                 [lein-midje "3.1.3"]
                 [clj-http "1.0.1"]
                 [clj-time "0.8.0"]
                 [clojurewerkz/urly  "1.0.0"]
                 [org.clojure/data.json "0.2.5"]
                 [org.clojure/tools.logging "0.3.1"]
                 [clj-http-fake "0.7.8"]
                 [org.clojure/clojure "1.6.0"]]
  :main ^:skip-aot slack-nag.core
  :target-path "target/%s"
  :min-lein-version "2.3.0"
  :profiles {:uberjar {:aot :all}})
