(ns slack-nag.reviewboard-test
  (:require [midje.sweet :refer :all]
            [clojure.java.io :refer [as-file]]
            [clj-http.fake :refer [with-fake-routes-in-isolation]]
            [slack-nag.reviewboard :refer [rb-session-id
                                           get-review-requests
                                           get-rb-user
                                           update-rb-session-id]]))


(def ^:const fixture-ok-path "test/fixtures/reviewboard/ok.json")

(defn load-response-from-file
  "Return the contents of the file as an HTTP response. If a page is
  specified, it is added to the file path using `format` (for mocking
  APIs that support paging)."
  [filename & [page]]
  (let [filepath (if page (format filename page) filename)
        body (if (.exists (as-file filepath)) (slurp filepath) "")]
    {:status 200 :headers {"content-type" "text/json"} :body body}))


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
              :query-params {:start 0 :max-results 100}}
             (fn [request] res)}
            (fact "returns all the review requests"
                  (count (get-review-requests {})) => 25)
            (fact "all review requests records have `:id` field"
                  (some #(nil? {:id %}) (get-review-requests {})) => falsey))))

  (fact "get-rb-user"
        (let [url "https://review.salsitasoft.com/api/users/test-user/"
              headers {"content-type" "text/json"}]
          (with-fake-routes-in-isolation
            {{:address url :query-params {:start 0}}
             (fn [request] {:status 200 :body "{\"user\": {\"id\": 42}}"})}
            (let [user (get-rb-user "test-user")]
              (fact "returns a user map"
                    user => truthy)
              (fact "returned user has the right id"
                    user => (contains {:id 42}))))))
