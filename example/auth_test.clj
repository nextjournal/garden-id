(ns auth-test
  (:require [org.httpkit.server :as httpkit]
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
                                 (format "Hi %s 👋" (or (:name (garden-id/get-user req)) "Stranger"))]
                                [:p.text-white.mb-6.text-center
                                 (format "You have visited %s times" (or (:visits session) "unknown"))]
                                (if (garden-id/logged-in? req)
                                  (garden-id/render-link-button {:href garden-id/logout-uri :label "Logout"})
                                  (garden-id/render-link-button {:href garden-id/login-uri :label "Login"}))])
       :session session})

    {:status 404 :body "not found"}))

(defn start [_]
  (httpkit/run-server (-> consuming-app
                          (garden-id/wrap-auth #_{:github [["nextjournal"]]})
                          (session/wrap-session {:store (cookie-store)}))
                      {:legacy-return-value? false :port 7777})
  (println "started."))

(when (= *file* (System/getProperty "babashka.file"))
  @(promise))
