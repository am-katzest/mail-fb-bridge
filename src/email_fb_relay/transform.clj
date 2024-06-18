(ns email-fb-relay.transform
  (:require
   [clojure.tools.logging :as log]
   [net.cgrand.enlive-html :as e]
   [clojure.string :as str]))

(defn parse [email]
  (with-open [s (-> (java.io.StringReader. (-> email :body :body))
                    clojure.lang.LineNumberingPushbackReader.)]
    (e/xml-parser s)))


(defn PA? [msg]
  (-> msg :from first :name (= "511PA")))

(defn relevant? [msg]
  (->> msg :subject (re-matches #"^Events on.*")))

(defn transform-inner [inner]
  (let [link-cell (filter #(= '("View on map") (:content %)) (e/select inner [:tbody :tr :td [:a (e/attr? :href )]]))
        url (-> link-cell first :attrs :href)
        message (-> (e/select inner [:tbody [:tr (e/nth-child 2)] :td :b]) first :content)
        [img actual-subject] (-> (e/select inner [:tbody [:tr (e/nth-child 1)] :th]) first :content)
        [before after] (map str/trim (str/split actual-subject #"-"))]
    (assert (and before after))
    (assert (#{0 1} (count link-cell)))
    (assert (= 1 (count message)))
    (assert (some? message) "can't split into body & url")
    (assert (= :img (:tag img)))
    (if (= after "New")
      (if url
        (do
          (assert (re-matches #"https.*" url))
          (log/debugf "%s looks normal" actual-subject)
          {:message (str/trim (first message))
           :link url
           :caption "more details at source"})
        (do
          (log/debugf "couldn't extract url from %s" actual-subject)
          {:message (str/trim (first message))}))
      (log/debugf "ignoring %s" actual-subject))))

(defn extract-inners [msg]
      (let [html (parse msg)]
        (e/select html [[:table (e/has-class "contents")] :tbody :tr :td :div :p [:table (e/has-class "fontStyle")] :tbody])))

(defn process-msg [msg]
  (if (and (some? msg) (PA? msg) (relevant? msg))
    (let [out (keep transform-inner (extract-inners msg))]
      (log/infof "accepted %s (%s)" out (:subject msg))
      out)
    (log/debugf "ignoring %s" (:subject msg))))

(defn handle-msg [msg]
  (try (process-msg msg)
       (catch Throwable e
         (log/errorf e "invalid msg: %s" (str msg)))))
