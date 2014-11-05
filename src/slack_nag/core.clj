(ns slack-nag.core
  (:require [slack-nag.reviewboard :as rb]
            [clj-http.client :as client]
            [hiccup.util]
            [clojure.data.json :as json]
            [clj-time.core :as t]
            [clj-time.format :as tf]
            [clj-time.local :as tl]
            [clj-time.predicates :as tpr]
            [clojure.core.memoize :as memo]
            [environ.core :refer [env]]
            [ring.adapter.jetty :as jetty]
            [clojure.string :refer [split]]

            [clojurewerkz.quartzite.scheduler :as qs]
            [clojurewerkz.quartzite.triggers :as tr]
            [clojurewerkz.quartzite.jobs :as j]
            [clojurewerkz.quartzite.jobs :refer [defjob]]
            [clojurewerkz.quartzite.schedule.daily-interval :refer
             [schedule
              monday-through-friday
              starting-daily-at
              time-of-day
              ending-daily-at
              with-interval-in-seconds]]

            [clojure.tools.logging :as log])
  (:gen-class))


(def job-start-time (or (env :job-start-time) ""))


;; Slack
(def ^:private slack-token (env :slack-token))
(def ^:const slack-api-base-url "https://slack.com/api")

(defn -get-slack-users
  "returns json list of slack users."
  [slack-token]
  (hiccup.util/with-base-url slack-api-base-url
    (let [url-with-qs (hiccup.util/url "/users.list" {:token slack-token})
          res (client/get (hiccup.util/to-str url-with-qs)
                          {:content-type :json
                           :as :json})]
      (log/info "get all slack users")
      (get-in res [:body :members]))))


(def get-slack-users (memo/ttl -get-slack-users :ttl/threshold (* 1000 60 10)))


(defn weekdays
  "How many weekdays there are from 'since' to 'to' dates (inclusive)"
  [since to]
  (let [interval (t/interval since to)
        get-day #(tpr/weekday? (t/plus (t/start %1) (t/days %2)))
        days-in-interval (range 1 (t/in-days interval))]
    (count (filter (partial get-day interval) days-in-interval))))

(defn- old-enough?
  "Is this RR old enough for us to care about it?"
  [req]
  (let [time-added (tf/parse (:time_added req))
        age-in-working-days (weekdays time-added (t/now))]
    (>= age-in-working-days 2)))


(defn- naggable?
  "Should someone be nagged because of this RR?"
  [req]
  (let [last-updated (:last_updated req)
        idle (t/interval (tf/parse last-updated) (t/now))]
    (> (t/in-hours idle) 20)))

(defn- naggable-serious?
  "Should someone be *seriously* nagged because of this RR (i.e., it's
  getting old)?"
  [req]
  (let [last-updated (:last_updated req)
        idle (t/interval (tf/parse last-updated) (t/now))]
    (> (t/in-days idle) 2)))


(defn- get-rb-target-person
  "Helper function that retrieves RB user from the href provided
  by RB API (target person in a rr)."
  [person-json]
  (let [url (:href person-json)
        trimmed-url (clojure.string/replace url #"/$" "")
        username (last (clojure.string/split trimmed-url #"/"))
        res (rb/get-rb-user username)]
    (:email res)))


(defn find-naggees
  "Returns emails of people who should be nagged for this review request."
  [req]
  (let [rid (:id req)
        last-update (rb/get-last-update-info rid)
        last-update-type (:type last-update)
        submitter (get-in req [:links :submitter :href])
        last-updater (get-in last-update [:user :links :self :href])]
    ;; This is like "if <foo> in <vector>".
    (if (some #{last-update-type} ["diff", "review_request"])
      ;; Last update was a new diff or review request update =>
      ;; we should notify the reviewers.
      (map #(get-rb-target-person %) (:target_people req))
      (if (= submitter last-updater)
        ;; Last review message was from the submitter => notify the reviewers.
        (map #(get-rb-target-person %) (:target_people req))
        ;; Last update was from a reviewer => nag the sumbitter.
        [(get-rb-target-person (get-in req [:links :submitter]))]))))


(defn get-message
  "Returns the nagging message to be sent to Slack."
  [review-request slack-user serious?]
  (let [real-name (get-in slack-user [:profile :real_name])
        repo (get-in review-request [:links :repository :title])
        rid (:id review-request)
        msg (format "%s, you have a lonely review request (repo %s) waiting on your action at: https://review.salsitasoft.com/r/%s ." real-name repo rid)]
    (if serious?
      (let [last-updated (:last_updated review-request)
            idle (t/interval (tf/parse last-updated) (t/now))]
        (str msg " This is getting *serious*! It's been lying there for " (t/in-days idle) " days!"))
      msg)))

(defn slack-post-message
  "Posts a message to Slack channel."
  [token channel msg]
  (if (not (= (env :environment) "production"))
    (log/debug "Posting SLACK for: " channel)
    (client/post "https://slack.com/api/chat.postMessage"
                 {:form-params {:token token
                                :channel channel
                                :text msg}})))

(defn slack-notify
  "Sends a Slack message to the users identified by email."
  [email review-request serious?]
  (let [slack-users (get-slack-users slack-token)
        email-matches? (fn [obj] (= (get-in obj [:profile :email]) email))
        target-user (first (filter email-matches? slack-users))]
    (when target-user
      (log/info "slack-notify for: " email "; serious?" serious? "; rid" (:id review-request))
      (let [target-user-id (:id target-user)
            msg (get-message review-request target-user serious?)]
        (slack-post-message slack-token target-user-id msg)))))


(defjob Nag
  [ctx]
  (let [reqs (rb/get-review-requests {:ship-it 0, :max-results 100})
        old-enough-reqs (filter old-enough? reqs)
        naggable-reqs (filter naggable? old-enough-reqs)
        naggees-emails (pmap find-naggees naggable-reqs)]
    ;; Nag away!
    (doseq [[req, emails] (map list naggable-reqs, (remove nil? naggees-emails))]
      (doall (map #(slack-notify % req (naggable-serious? req)) emails)))))


(defn forever []
  "Start a periodic job using `quartzite`."
  (qs/initialize)
  (qs/start)
  (log/info "Starting background thread (scheduler)...")
  (let [job (j/build
              (j/of-type Nag)
              (j/with-identity (j/key "jobs.nag.1")))
        job-time (map #(Integer/parseInt %) (split job-start-time #":"))
        trigger (tr/build
                  (tr/with-identity (tr/key "triggers.1"))
                  (tr/start-now)
                  (tr/with-schedule (schedule
                                      (monday-through-friday)
                                      (starting-daily-at (apply time-of-day job-time)))))]
    (qs/schedule job trigger)))


(defn server
  "Start a web server (we're only doing that because Heroku needs to bind to PORT)."
  []
  (jetty/run-jetty
    (fn [req] {:status 200 :body "Who is John Galt?"})
    {:port (env :port)}))

(defn -main
  [& args]
  (do
    ;; Update the RB session token (used to auhtenticate).
    (rb/update-rb-session-id)
    (.start (Thread. forever))
    (server)))
