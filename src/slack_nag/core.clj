(ns slack-nag.core
  (:require [slack-nag.reviewboard :as rb]
            [clj-http.client :as client]
            [hiccup.util]
            [clojure.data.json :as json]
            [clj-time.core :as t]
            [clj-time.format :as tf]
            [clj-time.local :as tl]
            [clj-time.predicates :as tpr]
            [clojurewerkz.urly.core :as urly]
            [clojure.tools.logging :as log])
  (:gen-class))


;; Slack env vars
(def ^:private slack-token (delay (System/getenv "SLACK_TOKEN")))


(def ^:const slack-api-base-url "https://slack.com/api")


(defn get-slack-users
  "Returns JSON list of slack users."
  [slack-token]
  (hiccup.util/with-base-url slack-api-base-url
    (let [url-with-qs (hiccup.util/url "/users.list" {:token slack-token})
          res (client/get (hiccup.util/to-str url-with-qs)
                          {:content-type :json
                           :as :json})]
      (get-in res [:body :members]))))


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
  "Helper function that retrieves RB user's username from the href provided
  by RB API"
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
    ;; This is like "if <foo> in vector".
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
  [token channel msg]
  (client/post "https://slack.com/api/chat.postMessage"
               {:form-params {:token token
                              :channel channel
                              :text msg}}))

(defn slack-notify
  "Sends a Slack message to all users identified by emails."
  [all-slack-users [emails review-request] serious?]
  (loop [email (first emails)
         emails-to-go (rest emails)]
    (let [email-matches? (fn [obj] (= (get-in obj [:profile :email]) email))
          target (first (filter email-matches? all-slack-users))
          target-id (:id target)
          msg (get-message review-request target serious?)]
      (do
        (slack-post-message @slack-token target-id msg)
        (if (> (count emails-to-go) 0)
          (recur (first emails-to-go) (rest emails-to-go)))))))

(defn -main
  [& args]
  (do
    (rb/update-rb-session-id)
    (let [slack-users (get-slack-users @slack-token)
          reqs (rb/get-review-requests {:ship-it 0, :max-results 100})
          old-enough-reqs (filter old-enough? reqs)

          naggable (filter #(and (naggable? %) (not (naggable-serious? %))) old-enough-reqs)
          naggable-serious (filter naggable-serious? old-enough-reqs)

          naggees (pmap find-naggees naggable)
          naggees-serious (pmap find-naggees naggable-serious)

          naggees-zipped (map vector naggees naggable)
          naggees-serious-zipped (map vector naggees-serious naggable-serious)]

      (doall (map #(slack-notify slack-users % false) naggees-zipped))
      (doall (map #(slack-notify slack-users % true) naggees-serious-zipped)))))
