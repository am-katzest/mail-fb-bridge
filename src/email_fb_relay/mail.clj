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
                   (log/infof "received mail! (%d)" (count (:messages e)))
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

(defn- try-starting-manager-until-it-works [conf f]
  (log/info "trying to start a new im")
  (loop [time 100]
    (or (try (let [im (start-manager conf f)]
               (log/info "started new im successfully")
               im)
             (catch Throwable t
               (log/error t "failed to start new im")))
        (do
          (Thread/sleep time)
          (recur (min 600000 (* time 2)))))))

(defn start-persistant-manager [conf f]
  (loop []
    (let [im (try-starting-manager-until-it-works conf f)]
      (wait-until-im-stops-running im)
      (recur))))

(defn grab-some-mail [conf n]
  (let [[_ store] (make-session conf)
        inbox-messages (inbox store)]
    (mapv read-message (take n inbox-messages))))
