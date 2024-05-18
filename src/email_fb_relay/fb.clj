(ns email-fb-relay.fb
  (:require [clj-facebook-graph.client :as client]
            [clj-facebook-graph.auth :as auth]))


(defn post! [{:keys [token page-id]} msg]
  (auth/with-facebook-auth {:access-token token}
    (client/post [page-id :feed]
                 {:form-params msg})))
