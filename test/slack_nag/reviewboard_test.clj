(ns slack-nag.reviewboard-test
  (:require [midje.sweet :refer :all]
            [clojure.java.io :refer [as-file]]
            [clj-http.fake :refer [with-fake-routes-in-isolation]]
            [slack-nag.reviewboard :refer [rb-session-id
                                           get-review-requests
                                           get-rb-user
                                           update-rb-session-id]]))


(def ^:const fixture-ok-path "test/fixtures/reviewboard/ok.json")

(defmacro with-rb-redefs
  "Set RB-specific env vars for tests."
  [& body]
  `(with-redefs [slack-nag.reviewboard/rb-url (delay "https://review.salsitasoft.com")
                 slack-nag.reviewboard/rb-user (delay "test-user")
                 slack-nag.reviewboard/rb-password (delay "test-password")] ~@body))


(defn load-response-from-file
  "Return the contents of the file as an HTTP response. If a page is
  specified, it is added to the file path using `format` (for mocking
  APIs that support paging)."
  [filename & [page]]
  (let [filepath (if page (format filename page) filename)
        body (if (.exists (as-file filepath)) (slurp filepath) "")]
    {:status 200 :headers {"content-type" "text/json"} :body body}))


(with-rb-redefs
  (fact "update-rb-session-id"
        (with-fake-routes-in-isolation
          {"https://review.salsitasoft.com/api/review-requests/"
           (fn [request] {:status 200
                          :headers {"content-type" "text/json"
                                    "set-cookie" "rbsessionid=testsessionid;Path=/"}
                          :body "{\"review_requests\": []}"})}
          (fact "it updates the `rb-session-id` atom"
                (reset! rb-session-id nil)
                (update-rb-session-id)
                @rb-session-id => "testsessionid")))

  (fact "get-review-requests"
        (let [url #"https://review.salsitasoft.com/api/review-requests/"
              headers {"content-type" "text/json"}
              res (load-response-from-file fixture-ok-path)]
          (with-fake-routes-in-isolation
            {{:address url
              :query-params {:start 0
                             :max-results 100}}
             (fn [request] res)}
            (fact "returns all the review requests"
                  (let [rrs (get-review-requests {})]
                    (count rrs) => 25))
            (fact "all review requests records have `:id` field"
                  (let [rrs (get-review-requests {})]
                    (some #(nil? {:id %}) rrs) => falsey)))))

  (fact "get-rb-user"
        (let [url "https://review.salsitasoft.com/api/users/test-user/"
              headers {"content-type" "text/json"}]
          (with-fake-routes-in-isolation
            {{:address url
              :query-params {:start 0}}
             (fn [request] {:status 200 :body "{\"user\": {\"id\": 42}}"})}
            (fact "returns a user map"
                  (let [user (get-rb-user "test-user")]
                    user => truthy))))))
