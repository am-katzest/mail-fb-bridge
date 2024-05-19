(ns email-fb-relay.mail
  (:require [clojure-mail.core :refer :all]
            [clojure-mail.events :as events]
            [clojure.tools.logging :as log]
            [clojure-mail.message :refer (read-message)]))

(defn make-session [{:keys [email password server]}]
  (let [s (get-session "imaps")
        gstore (store "imaps" s server email password)]
    [s gstore]))

(defn start-manager [conf f]
  (let [[s gstore] (make-session conf)
        folder (open-folder gstore "inbox" :readonly)
        im (events/new-idle-manager s)
        callback (fn [e]
                   (log/info "received mail!")
                   (future (doseq [m (:messages e)]
                             (f (read-message m))))
                   (.watch im folder))]
    (events/add-message-count-listener callback #(.watch im folder) folder im)
    im))

(defn- wait-until-im-stops-running [im]
  (loop []
        (Thread/sleep 10000)
        (if (.isRunning im) (recur)
            (log/error "idle manager is not running"))))

(defn start-persistant-manager [conf f]
  (loop []
    (log/info "starting new idle manager")
    (let [im (start-manager conf f)]
      (wait-until-im-stops-running im)
      (recur))))

(defn grab-some-mail [conf n]
  (let [[_ store] (make-session conf)
        inbox-messages (inbox store)]
    (mapv read-message (take n inbox-messages))))
