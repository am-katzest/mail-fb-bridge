(ns email-fb-relay.core
  (:gen-class)
  (:require [email-fb-relay.fb :as fb]
            [email-fb-relay.mail :as mail]
            [email-fb-relay.transform :as transform]
            [clojure.core.async :as a]
            [clojure.tools.logging :as log]
            [aero.core :as aero]))


(defn -main [& args]
  (log/debug "debug enabled")
  (log/info "starting...")
  (let [conf (aero/read-config (or (first args) "config.edn"))
        chan (a/chan (a/sliding-buffer 10) (keep transform/handle-msg))]
    (log/info "read config...")
    (mail/start-persistant-manager (:email conf) #(a/>!! chan %))
    (log/info "started listening...")
    (loop []
      (let [msg (a/<!! chan)]
        (log/debugf "posting: %s" msg)
        (fb/post! (:facebook conf) msg))
      (recur))
    (log/error "main loop stopped, this shouldn't happen")))
