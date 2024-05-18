(ns email-fb-relay.core
  (:require [email-fb-relay.fb :as fb]
            [email-fb-relay.mail :as mail]
            [clojure.string :as str]
            [aero.core :as aero]))

(defn PA? [msg]
  (-> msg :from first :name (= "511PA")))
(defn initial? [msg]
  (->> msg :subject (re-matches #".*: INITIAL$")))

(def config (aero/read-config "config.edn"))

(defn transformPA [msg]
  (let [body (-> msg :body :body)
        content (str/trim (first (str/split-lines body)))]
    (assert (= 7 (count (str/split-lines body))) "wrong line count")
    (assert (= 1 (count (re-seq #"https" content))))
    (let [[[_ message url]] (re-seq #"^(.*) +Visit +(https://511PA\.com.*)$" content)]
      (assert (and (some? message) (some? url)) "can't split into body & url")
      {:message (str/trim message)
       :link url
       :caption "more details at source"})))
