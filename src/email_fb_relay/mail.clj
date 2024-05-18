(ns email-fb-relay.mail
  (:require [clojure-mail.core :refer :all]
            [clojure-mail.events :as events]
            [clojure-mail.message :refer (read-message)]))

(defn start-manager [{:keys [email password server]} f]
  (let [s (get-session "imaps")
        gstore (store "imaps" s server email password)
        folder (open-folder gstore "inbox" :readonly)
        im (events/new-idle-manager s)]
    (events/add-message-count-listener (fn [e]
                                         (future (doseq [m (:messages e)]
                                                   (f (read-message m)))))
                                       (constantly nil)
                                       folder
                                       im)
    #(events/stop im)))
