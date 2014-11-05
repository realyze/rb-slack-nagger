(defproject slack-nag "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :plugins  [
             [lein-midje  "3.1.3"]
             [lein-environ "1.0.0"]]
  :dependencies [
                 [hiccup "1.0.5"]
                 [midje  "1.6.3"]
                 [clj-http "1.0.1"]
                 [clj-time "0.8.0"]
                 [clojurewerkz/urly  "1.0.0"]
                 [org.clojure/data.json "0.2.5"]
                 [org.clojure/tools.logging "0.3.1"]
                 [org.slf4j/slf4j-log4j12 "1.7.7"]
                 [log4j/log4j "1.2.17" :exclusions [javax.mail/mail
                                                    javax.jms/jms
                                                    com.sun.jmdk/jmxtools
                                                    com.sun.jmx/jmxri]]
                 [org.clojure/core.memoize "0.5.6"]
                 [clj-http-fake "0.7.8"]
                 [environ "1.0.0"]
                 [org.clojure/tools.nrepl "0.2.5"]
                 [clojurewerkz/quartzite "1.3.0"]
                 [compojure "1.1.8"]
                 [ring/ring-jetty-adapter "1.2.2"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                 [trammel "0.8.0"]
                 [org.clojure/clojure "1.6.0"]]
  :main ^:skip-aot slack-nag.core
  :target-path "target/%s"
  :min-lein-version "2.4.0"
  :profiles {:uberjar {:aot :all}
             :shared {:env {:rb-url "https://review.salsitasoft.com"
                            :tz "Europe/Prague"
                            :port 3000
                            :cron-expr "0 30 8 ? * MON-FRI"}}
             :default [:shared {:env {:environment "development"}}]
             :production [:shared {:env {:environment "production"}}]
             :test [:shared {:env {:rb-user "test-user"
                                   :rb-password "test-password"
                                   :environment "test"}}]})
