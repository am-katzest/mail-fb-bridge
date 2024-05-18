(ns email-fb-relay.core
  (:require [email-fb-relay.fb :as fb]
            [email-fb-relay.mail :as mail]
            [aero.core :as aero]))

(defn PA? [msg]
  (-> msg :from first :name (= "511PA")))
(defn initial? [msg]
  (->> msg :subject (re-matches #".*: INITIAL$")))

(def config (aero/read-config "config.edn"))
