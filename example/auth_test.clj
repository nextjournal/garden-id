(ns auth-test
  (:require [org.httpkit.server :as httpkit]
            [nrepl.server]
            [ring.middleware.session :as session]
            [ring.middleware.session.cookie :refer [cookie-store]]
            [nextjournal.garden-id :as garden-id]))

(defn consuming-app [req]
  (case (:uri req)
    "/"
    (let [session (update (:session req) :visits (fnil inc 0))]
      {:status 200 :headers {"content-type" "text/html"}
       :body (format "hi %s - you have visited %s times - %s"
                     (or (:user session) "stranger")
                     (or (:visits session) "unknown")
                     (if (:user session)
                       "<a href=/logout>logout from app</a>"
                       "<a href=/start>login</a>"))
       :session session})

    "/logout"
    {:status 302 :headers {"location" "/"} :body "logged out"
     :session nil}

    "/start"
    {:status 200 :headers {"content-type" "text/html"}
     :body (str "<a href=/login>login</a> <a href=\"https://login.auth.clerk.garden/logout\">logout</a>")}
      
    ;else
    {:status 400 :body "not found"}))

(defn start [_]
  (nrepl.server/start-server :bind "0.0.0.0" :port 6666)
  (httpkit/run-server (-> consuming-app
                          (garden-id/wrap-auth)
                          (session/wrap-session {:store (cookie-store)}))
                      {:legacy-return-value? false :port 7777})
  (println "started."))

(when (= *file* (System/getProperty "babashka.file"))
  @(promise))
