(ns email-fb-relay.transform
  (:require
   [clojure.tools.logging :as log]
   [clojure.string :as str]))



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

(defn process-msg [msg]
  (if (and (some? msg) (PA? msg) (initial? msg))
         (let [out (transformPA msg)]
           (log/infof "accepted %s (%s)" out (:subject msg))
           out)
         (log/debugf "ignoring %s" (:subject msg))))

(defn handle-msg [msg]
  (try (process-msg msg)
      (catch Throwable e
          (log/errorf e "invalid msg: %s" (str msg)))))
