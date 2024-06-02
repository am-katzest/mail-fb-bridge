(ns email-fb-relay.core
  (:gen-class)
  (:require [email-fb-relay.fb :as fb]
            [email-fb-relay.mail :as mail]
            [clojure.string :as str]
            [clojure.core.async :as a]
            [clojure.tools.logging :as log]
            [aero.core :as aero]))

(defn PA? [msg]
  (-> msg :from first :name (= "511PA")))
(defn initial? [msg]
  (->> msg :subject (re-matches #".*: INITIAL$")))

(defn remove-unsubscribe-lines [lines]
  (let [[f e d c b a & rest] (reverse lines)]
    (assert (= "" a) a)
    (assert (= "To unsubscribe, click the following link:" b) b)
    (assert (re-matches #"https.*"  c) c)
    (assert (= "" d) d)
    (assert (= "To pause all notifications, click the following link:" e) e)
    (assert (re-matches #"https.*"  f) f)
    (reverse rest)))

(defn transformPA [msg]
  (let [content (->> msg :body :body
                     str/split-lines
                     remove-unsubscribe-lines
                     (remove #{""})
                     (str/join  " ")
                     str/trim)]
    (assert (= 1 (count (re-seq #"https" content))))
    (let [[[_ message url]] (re-seq #"^(.*) +Visit +(https://511PA\.com.*)$" content)]
      (assert (and (some? message) (some? url)) "can't split into body & url")
      {:message (str/trim message)
       :link url
       :caption "more details at source"})))

(defn handle-msg [conf msg]
  (if (and (some? msg) (PA? msg) (initial? msg))
    (let [out (transformPA msg)]
      (log/infof "posting %s (%s)" out (:subject msg))
      (fb/post! (:facebook conf) out))
    (log/debugf "ignoring %s" (:subject msg))))

(defn -main [& args]
  (log/info "starting...")
  (let [conf (aero/read-config (or (first args) "config.edn"))
        chan (a/chan (a/sliding-buffer 10))]
    (log/info "read config...")
    (mail/start-persistant-manager (:email conf) #(a/>!! chan %))
    (log/info "started listening...")
    (loop []
      (let [msg (a/<!! chan)]
        (log/debugf "processing msg: %s" (:subject msg))
        (try (handle-msg conf msg)
             (catch Throwable e
               (log/errorf e "invalid msg: %s" (str msg)))))
      (recur))
    (log/error "main loop stopped, this shouldn't happen")))
