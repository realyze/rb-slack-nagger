(ns slack-nag.reviewboard
  (:require [clj-http.client :as client]
            [clj-http.cookies :as cookies]
            [hiccup.util]
            [clojure.data.json :as json]
            [environ.core :refer [env]]
            [clojure.tools.logging :as log])
  (:gen-class))


;; RB env vars
(def ^:private rb-url (env :rb-url))
(def ^:private rb-user (env :rb-user))
(def ^:private rb-password (env :rb-password))


;; RB session id needs to be updated every now and then (it expires).
(def rb-session-id (atom nil))


(defn- rb-get
  ([endpoint session-id] (rb-get endpoint session-id {}))
  ([endpoint session-id opts]
   (log/debug "rb-get: " endpoint ", " opts)
   (let [url (hiccup.util/url rb-url endpoint)
         default-opts {:start 0}
         opts (into default-opts opts)]
     (client/get (hiccup.util/to-str url)
                 {:cookies {:rbsessionid {:value session-id}}
                  :query-params opts
                  :as :json}))))


(defn update-rb-session-id
  []
  (hiccup.util/with-base-url rb-url
    (log/info "Updating RB session...")
    (let [url (hiccup.util/url "/api/review-requests/")
          cs (cookies/cookie-store)
          res (client/get (hiccup.util/to-str url)
                          {:content-type :json
                           :basic-auth [rb-user rb-password]
                           :as :json})
          rbsessionid (get-in res [:cookies "rbsessionid" :value])]
      (reset! rb-session-id rbsessionid))))


(defn get-last-update-info
  [rid]
  (let [url (str "/api/review-requests/" rid "/last-update/")
        res (rb-get url @rb-session-id)]
    (get-in res [:body :last_update])))


(defn get-rb-user
  [username]
  (let [url (str "/api/users/" username "/")
        res (rb-get url @rb-session-id)]
    (get-in res [:body :user])))


(defn get-review-requests
  [opts]
  ;; Get the first chunk and read how many total items there are. Then GET the rest
  ;; in parallel.
  (let [get-rrs (partial rb-get "/api/review-requests/" @rb-session-id)
        opts (into {:max-results 100} opts)
        chunk-size (:max-results opts)
        first-res (get-rrs opts)
        first-chunk-count (count (get-in first-res [:body :review_requests]))
        total-count (get-in first-res [:body :total_results])
        steps (range first-chunk-count total-count chunk-size)
        responses (pmap #(get-rrs (into opts {:start %})) steps)
        all-responses (into [first-res] responses)]
    (->> all-responses
         (map #(get-in % [:body :review_requests]))
         flatten
         vec)))
  

