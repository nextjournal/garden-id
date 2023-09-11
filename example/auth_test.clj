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
       :body (garden-id/->html [:div.text-center
                                [:h2.font-bold.text-white.text-xl.mb-2.text-center
                                 (format "Hi %s 👋" (or (garden-id/get-user req) "Stranger"))]
                                [:p.text-white.mb-6.text-center
                                 (format "You have visited %s times" (or (:visits session) "unknown"))]
                                (if (garden-id/logged-in? req)
                                  (garden-id/render-link-button {:href "/logout" :label "Logout from app"})
                                  (garden-id/render-link-button {:href "/start" :label "Login"}))])
       :session session})

    "/logout"
    {:status 302 :headers {"location" "/"} :body "logged out"
     :session nil}

    "/start"
    {:status 200 :headers {"content-type" "text/html"}
     :body (garden-id/->html [:div
                              [:div
                               (garden-id/render-link-button {:href "/login" :label "Login"})
                               (garden-id/render-link-button {:href "/https://login.auth.clerk.garden/logout" :label "Logout"})]])}

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
