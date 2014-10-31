(ns slack-nag.reviewboard
  (:require [clj-http.client :as client]
            [hiccup.util]
            [clojure.data.json :as json]
            [clojure.tools.logging :as log])
  (:gen-class))


;; RB env vars
(def ^:private rb-url (delay (System/getenv "RB_URL")))
(def ^:private rb-user (delay (System/getenv "RB_USER")))
(def ^:private rb-password (delay (System/getenv "RB_PASSWORD")))


;; RB session id needs to be updated every now and then (it expires).
(def rb-session-id (atom nil))


(defn- rb-get
  ([endpoint session-id] (rb-get endpoint session-id {}))
  ([endpoint session-id opts]
   (log/info (str "rb-get: " endpoint ", " opts))
   (let [url (hiccup.util/url @rb-url endpoint)
         default-opts {:start 0}
         opts (into default-opts opts)]
     (client/get (hiccup.util/to-str url)
                 {:cookies {:rbsessionid {:value session-id}}
                  :query-params opts
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


(defn get-last-update-info
  [rid]
  (let [url (str "/api/review-requests/" rid "/last-update/")
        res (rb-get url @rb-session-id)]
    (get-in res [:body :last_update])))

(defn get-rb-user
  [username]
  (let [url (str "/api/users/" username "/")
        res (rb-get url@rb-session-id)]
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
  

