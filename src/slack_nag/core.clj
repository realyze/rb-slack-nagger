(ns slack-nag.core
  (:require [clj-http.client :as client]
            [hiccup.util]
            [clojure.data.json :as json]
            [clojure.tools.logging :as log])
  (:gen-class))

;; RB env vars
(def ^:private rb-url (delay (System/getenv "RB_URL")))
(def ^:private rb-user (delay (System/getenv "RB_USER")))
(def ^:private rb-password (delay (System/getenv "RB_PASSWORD")))

;; Slack env vars
(def ^:private slack-token (delay (System/getenv "SLACK_TOKEN")))

(def ^:const slack-api-base-url "https://slack.com/api")

;; RB session id needs to be updated every now and then (it expires).
(def rb-session-id (atom nil))

(defn get-slack-users
  "Returns JSON list of slack users."
  []
  (hiccup.util/with-base-url slack-api-base-url
    (let [url-with-qs (hiccup.util/url "/users.list" {:token @slack-token})]
      (client/get (hiccup.util/to-str url-with-qs)
                  {:content-type :json
                   :throw-exceptions false
                   :as :json}))))


(defn update-rb-session-id
  []
  (hiccup.util/with-base-url @rb-url
    (log/info "Updating RB session...")
    (let [url (hiccup.util/url "/api/review-requests/")
          cs (clj-http.cookies/cookie-store)]
        (client/get (hiccup.util/to-str url)
                    {:content-type :json
                     :cookie-store cs
                     :basic-auth [@rb-user @rb-password]
                     :as :json})
        (let [sid (:value (get (clj-http.cookies/get-cookies cs) "rbsessionid"))]
          (reset! rb-session-id sid)))))


(defn rb-get
  ([endpoint session-id] (rb-get endpoint session-id {}))
  ([endpoint session-id opts]
   (let [url (hiccup.util/url @rb-url endpoint)
         default-opts {:max-results 200 :start 0}
         opts (into default-opts opts)]
     (client/get (hiccup.util/to-str url)
                 {:cookies {:rbsessionid {:value session-id}}
                  :query-params opts
                  :as :json
                  :throw-exceptions false}))))

(defn get-review-requests
  [opts]
  (loop [all-reqs []
         start-at 0]
    (log/info (str "requesting review requests from RB, starting at: " start-at))
    (let [res (rb-get "/api/review-requests/" @rb-session-id {:start start-at})]
      (when (= (get-in res [:body :stat]) "ok")
        (let [reqs (get-in res [:body :review_requests])
              total-count (get-in res [:body :total_results])
              current-count (+ start-at (count reqs))
              updated-reqs (into all-reqs reqs)]
          (if (< current-count total-count)
            (recur updated-reqs current-count)
            updated-reqs))
      ))))
  
(defn -main
  [& args]
  (do
    (update-rb-session-id)
    (let [reqs (get-review-requests {:ship-it 0, :max-results 200})]
      (println (count reqs)))))
