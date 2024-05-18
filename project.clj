(defproject email_fb_relay "0.1.0-SNAPSHOT"
  :description "reads emails, post them on facebook"
  :url "http://example.com/FIXME"
  :license {:name "AGPLv3" }
  :dependencies [[org.clojure/clojure "1.11.1"]
                 [io.forward/clojure-mail "1.0.8"]
                 [clj-facebook-graph "0.4.0"]
                 [aero "1.1.6"]]
  :repl-options {:init-ns email-fb-relay.core})
