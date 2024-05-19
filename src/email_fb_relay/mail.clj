(ns email-fb-relay.mail
  (:require [clojure-mail.core :refer :all]
            [clojure-mail.events :as events]
            [clojure-mail.message :refer (read-message)]))

(defn make-session [{:keys [email password server]}]
  (let [s (get-session "imaps")
        gstore (store "imaps" s server email password)]
    [s gstore]))

(defn start-manager [conf f]
  (let [[s gstore] (make-session conf)
        folder (open-folder gstore "inbox" :readonly)
        im (events/new-idle-manager s)
        callback (fn [e] (future (doseq [m (:messages e)]
                                  (f (read-message m))))
                   (.watch im folder))]
    (events/add-message-count-listener callback #(.watch im folder) folder im)
    #(events/stop im)))

(defn grab-some-mail [conf n]
  (let [[_ store] (make-session conf)
        inbox-messages (inbox store)]
    (mapv read-message (take n inbox-messages))))
