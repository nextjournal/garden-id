(ns nextjournal.garden-id
  (:require [cheshire.core :as json]
            [clojure.java.io :as io]
            [babashka.http-client :as http]
            [huff.core :as h]
            [ring.util.codec :as codec]
            [ring.middleware.token :as token])
  (:import (com.auth0.jwt.exceptions JWTVerificationException)))

(defonce issuers
  {"https://auth.clerk.garden"
   {:alg :RS256
    :jwk-endpoint "https://auth.clerk.garden/.well-known/jwks.json"}
   "https://auth.application.garden"
   {:alg :RS256
    :jwk-endpoint "https://auth.application.garden/.well-known/jwks.json"}})

(def client-id (System/getenv "OAUTH2_CLIENT_ID"))
(def client-secret (System/getenv "OAUTH2_CLIENT_SECRET"))

(def github-api-token (System/getenv "GITHUB_API_TOKEN"))

;; randomly generated constant
(def uuid-namespace "61c7cd53-0d72-4ad5-8ae6-1a79849d21b7")

(defn username->uuid [username]
  (java.util.UUID/nameUUIDFromBytes (.getBytes (str uuid-namespace username))))

(defn render-link-button [{:as attributes :keys [label]}]
  [:a.bg-green-300.font-sans.rounded-md.text-center.block.w-full.px-4.py-2.text-sm.font-medium.hover:bg-green-200.transition-all
   (dissoc attributes :label)
   label])

(defn render-button [{:as attributes :keys [label]}]
  [:button.bg-green-300.font-sans.rounded-md.text-center.block.w-full.px-4.py-2.text-sm.font-medium.hover:bg-green-200.transition-all
   (dissoc attributes :label)
   label])

(defn render-text-input [{:as attributes :keys [label name]}]
  (let [dom-id (str "input-" name)]
    [:div
     (when label
       [:label.block.text-sm.font-medium.leading-6.text-white.font-sans.mb-1 {:for dom-id} label])
     [:input
      (-> attributes
          (assoc :class "block w-full rounded-md border-0 bg-white/5 py-1.5 text-white shadow-sm ring-1 ring-inset ring-white/10 focus:ring-2 focus:ring-inset focus:ring-indigo-500 sm:text-sm sm:leading-6 px-2 font-sans"
                 :id dom-id)
          (dissoc :label))]]))

(def tw-config
  "tailwind.config = { theme: {fontFamily: { sans: [\"Fira Sans\", \"-apple-system\", \"BlinkMacSystemFont\", \"sans-serif\"], serif: [\"PT Serif\", \"serif\"], mono: [\"Fira Mono\", \"monospace\"] } } }")

(defn ->html [contents]
  (h/html
   {:allow-raw true}
   [:html
    [:head
     [:meta {:charset "UTF-8"}]
     [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
     [:link {:rel "preconnect" :href "https://fonts.bunny.net"}]
     [:link {:rel "stylesheet" :href "https://fonts.bunny.net/css?family=fira-mono:400,700%7Cfira-sans:400,400i,500,500i,700,700i%7Cfira-sans-condensed:700,700i%7Cpt-serif:400,400i,700,700i"}]
     [:script {:type "text/javascript" :src "https://cdn.tailwindcss.com?plugins=typography"}]
     [:script [:hiccup/raw-html tw-config]]
     [:script {:type "text/javascript"}
      [:hiccup/raw-html (slurp (io/resource "nextjournal/garden-id/js/login.js"))]]]
    [:body.bg-slate-950.flex.w-screen.h-screen.justify-center.items-center
     [:div.sm:mx-auto.sm:w-full.sm:max-w-sm
      [:div.max-w-lg.flex.justify-center.items-center.w-full.mb-6
       [:img {:src "https://cdn.nextjournal.com/data/QmTWkWW9XkFVWjnNLLyXbU3TvZXx9DuS4nTVpETQGCwRTV?filename=The-Garden.png&content-type=image/png"
              :width 100
              :height 100}]]
      contents]]]))

(defn- wrap-auth-fake [app _opts]
  (fn [req]
    (case (:uri req)
      "/.application.garden/garden-id/login"
      (case (:request-method req)
        :get
        {:status 200
         :headers {"content-type" "text/html"}
         :body
         (->html [:div
                  [:div.text-center.mb-6
                   [:div.uppercase.text-white.tracking-wide.text-xs.mb-1.font-sans
                    "No OIDC configured"]
                   [:h2.text-white.font-bold.text-xl "Impersonate User"]]
                  [:form.flex.flex-col.gap-3.mb-6 {:method "post"}
                   (render-text-input {:name "username" :label "User name"})
                   (render-text-input {:name "name" :label "Display name"})
                   (render-text-input {:name "email" :label "Email"})
                   (render-button {:type "submit" :label "Impersonate" :id "submit-persona"})]
                  [:div#personas.flex.flex-col.gap-3]])
         :session {}}

        :post
        (let [params (-> req :body slurp codec/form-decode)
              username (get params "username" "anonymous")
              session (-> {}
                          (assoc-in [:user :uuid] (str (username->uuid username)))
                          (assoc-in [:user :email] (get params "email" "default@example.org"))
                          (assoc-in [:user :name] (get params "name" "A. Nonymous"))
                          (assoc-in [:user :username] username))]
          {:status 302
           :headers {"location" "/"}
           :body ""
           :session session})

        ;; else
        {:status 302
         :headers {"location" "/.application.garden/garden-id/login"}})

      "/.application.garden/garden-id/logout"
      {:status 302 :headers {"location" "/"} :body "logged out"
       :session nil}

      "/.application.garden/garden-id/callback"
      {:status 500}

      ;; else
      (app req))))

(defn- validate-github-member? [user-id org team]
  ;; first map user-id to login name, then look up team membership
  (let [username (-> (http/get (format "https://api.github.com/user/%d" user-id)
                               {:headers {"Authorization"
                                          (str "token " github-api-token)}
                                :throw false})
                     :body
                     (json/parse-string true)
                     :login)
        url (if team
              (format "https://api.github.com/orgs/%s/teams/%s/memberships/%s"
                      org team username)
              (format "https://api.github.com/orgs/%s/memberships/%s"
                      org username))
        resp (http/get url {:headers {"Authorization"
                                      (str "token " github-api-token)}
                            :throw false})]
    (and (= 200 (:status resp))
         (= "active" (:state (json/parse-string (:body resp) true))))))

(defn- validate-extra-claims? [claims opts]
  (cond
    (empty? opts)
    true

    (:github opts)
    (and (= (:issuer claims) "https://github.com/login/oauth/access_token")
         (or (empty? opts)
             (some (fn [org-and-team]
                     (validate-github-member? (:github_id claims)
                                              (first org-and-team)
                                              (second org-and-team)))
                   (:github opts))))

    (:apple opts)
    (= (:issuer claims) "https://appleid.apple.com")

    :else
    false))

(def login-uri "/.application.garden/garden-id/login")
(def logout-uri "/.application.garden/garden-id/logout")

(defn- wrap-auth-oidc [app opts]
  (fn [req]
    (case (:uri req)
      "/.application.garden/garden-id/login"
      (let [session (-> req
                        :session
                        (assoc :login-state (str (java.util.UUID/randomUUID))))]
        {:status 302
         :headers {"content-type" "text/html"
                   "location" (str "https://auth.application.garden/oauth2/auth?response_type=code&scope=openid%20profile&client_id=" client-id "&state=" (:login-state session))}
         :body ""
         :session session})

      "/.application.garden/garden-id/logout"
      {:status 302 :headers {"location" "/"} :body "logged out"
       :session nil}

      "/.application.garden/garden-id/callback"
      (let [query-strings (codec/form-decode (str (:query-string req)))
            session (:session req)
            login-state (:login-state session)]
        (if-let [code (get query-strings "code")]
          (if (= (get query-strings "state") login-state)
            (let [resp (-> (http/post "https://auth.application.garden/oauth2/token"
                                      {:basic-auth [client-id client-secret]
                                       :headers {:content-type "application/x-www-form-urlencoded"}
                                       :form-params {"code" code
                                                     "grant_type" "authorization_code"
                                                     "client_id" client-id}})
                           :body
                           (json/parse-string true))]
              (prn resp)
              (try
                (let [token (:id_token resp)
                      alg-opts (get issuers (token/decode-issuer token))
                      claims (token/decode token alg-opts)
                      _ (when-not (some #{client-id} (:aud claims))
                          (throw (JWTVerificationException. "The Claim 'aud' value doesn't contain the required audience")))
                      _ (when-not (validate-extra-claims? claims opts)
                          (throw (JWTVerificationException. "Additional claims could not be verified")))
                      session (-> session
                                  (assoc-in [:user :uuid] (:sub claims))
                                  (assoc-in [:user :email] (:email claims))
                                  (assoc-in [:user :username] (:username claims))
                                  (assoc-in [:user :name] (:name claims)))]
                  {:status 302 :headers {"content-type" "text/html"
                                         "location" "/"}
                   :body "Succesfully logged in!"
                   :session session})
                (catch JWTVerificationException e
                  {:status 403 :headers {"content-type" "text/plain"} :body (format "failed to validate jwt - %s" (ex-message e))})))
            {:status 403 :headers {"content-type" "text/html"} :body "denied"})
          {:status 403 :headers {"content-type" "text/html"} :body "denied - wrong state"}))

      ;; else
      (app req))))

(defn wrap-auth
  ([app]
   (wrap-auth app {}))
  ([app opts]
   (if (and client-id client-secret)
     (wrap-auth-oidc app opts)
     (wrap-auth-fake app opts))))

(defn get-user [req]
  (get-in req [:session :user]))

(defn logged-in? [req]
  (some? (get-user req)))
