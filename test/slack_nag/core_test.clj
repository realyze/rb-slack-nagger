(ns slack-nag.core-test
  (:require [midje.sweet :refer :all]
            [clj-http.fake :refer [with-fake-routes-in-isolation]]
            [slack-nag.core :refer :all]
            [slack-nag.reviewboard :as rb]))


(facts "find-naggees"
       (let [submitter "http://foo.com/api/users/test-submitter"
             reviewer "http://foo.com/api/users/grumpy/"
             req {:id 42
                  :links {:submitter {:href submitter}}
                  :target_people [{:href reviewer}]}]

         (doseq [update-type ["review_request" "diff"]]
           (fact (str "when last update type is " update-type)
                 (fact "returns reviewers' emails"
                       (find-naggees req) => ["test-1@test.com"]
                       (provided
                         (rb/get-rb-user "grumpy") => {:email "test-1@test.com"}
                         (rb/get-last-update-info 42) =>{:type update-type}))))

         (doseq [update-type ["reply" "review"]]
           (fact (str "when last update type is " update-type)
                 (fact "when last updater is the submitter it returns reviewers' emails"
                       (find-naggees req) => ["test-1@test.com"]
                       (provided
                         (rb/get-rb-user "grumpy") => {:email "test-1@test.com"}
                         (rb/get-last-update-info 42) => {:type update-type
                                                          :user {:links {:self {:href submitter}}}}))

                 (fact "when last updater is a reviewer returns submitter's email as a vector"
                       (find-naggees req) => ["test-submitter@test.com"]
                       (provided
                         (rb/get-rb-user "test-submitter") => {:email "test-submitter@test.com"}
                         (rb/get-last-update-info 42) => {:type update-type
                                                          :user {:links {:self {:href reviewer}}}}))))))
