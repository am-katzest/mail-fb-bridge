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
                             (f (read-message m)))))]
    (events/add-message-count-listener callback (constantly nil) folder im)
    im))

(defn- kill-im-if-it-fails [im kill-request]
  (future
    (loop []
      (Thread/sleep 10000)
      (when (not (realized? kill-request))
        (when-not (.isRunning im)
          (log/error "idle manager is not running, requesting restart")
          (deliver kill-request true))
        (recur)))))

(defn- kill-client-before-server-decides-its-dead [im kill-request]
  (future (Thread/sleep (* 60 20 1000))
          (log/info "requesting im restart because 20 minutes passed")
          (deliver kill-request true)))

(defn- try-starting-manager-until-it-works [conf f]
  (log/info "trying to start a new im")
  (loop [time 100]
    (or (try (start-manager conf f)
             (catch Throwable t
               (log/error t "failed to start new im")))
        (do
          (Thread/sleep time)
          (recur (min 600000 (* time 2)))))))

(defn start-persistant-manager [conf f]
  (future
    (loop []
      (let [kill-request (promise)
            f' (fn [m] (f m) (deliver kill-request true))
            im (try-starting-manager-until-it-works conf f')]
        (log/info "started new im successfully")
        (kill-client-before-server-decides-its-dead im kill-request)
        (kill-im-if-it-fails im kill-request)
        (deref kill-request)
        (events/stop im)
        (log/info "killed im")
        (recur)))))

(defn grab-some-mail [conf n]
  (let [[_ store] (make-session conf)
        inbox-messages (inbox store)]
    (mapv read-message (take n inbox-messages))))
