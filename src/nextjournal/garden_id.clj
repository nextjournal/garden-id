(ns nextjournal.garden-id
  (:require [cheshire.core :as json]
            [babashka.http-client :as http]
            [ring.util.codec :as codec]
            [ring.middleware.token :as token])
  (:import (com.auth0.jwt.exceptions JWTVerificationException)))

(defonce issuers
  {"https://auth.clerk.garden"
   {:alg :RS256
    :jwk-endpoint "https://auth.clerk.garden/.well-known/jwks.json"}})

(def client-id (System/getenv "OAUTH2_CLIENT_ID"))
(def client-secret (System/getenv "OAUTH2_CLIENT_SECRET"))

;; randomly generated constant
(def uuid-namespace "61c7cd53-0d72-4ad5-8ae6-1a79849d21b7")

(defn username->uuid [username]
  (java.util.UUID/nameUUIDFromBytes (.getBytes (str uuid-namespace username))))

(defn- wrap-auth-fake [app]
  (fn [req]
    (case (:uri req)
      "/login"
      (case (:request-method req)
        :get
        {:status 200
         :headers {"content-type" "text/html"}
         :body
"<h2>No OIDC configured, impersonate user:</h2>
<form method=post>
Username: <input type=text name=username><br>
Display Name: <input type=text name=name><br>
Email: <input type=text name=email><br>
<input type=submit value='Impersonate!'>
</form>"
         :session {}}

        :post
        (let [params (-> req :body slurp codec/form-decode)
              _ (prn params)
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
         :headers {"location" "/login"}})

      "/callback"
      {:status 500}

      ;; else
      (app req))))

(defn- wrap-auth-oidc [app]
  (fn [req]
    (case (:uri req)
      "/login"
      (let [session (-> req
                        :session
                        (assoc :login-state (str (java.util.UUID/randomUUID))))]
        {:status 302
         :headers {"content-type" "text/html"
                   "location" (str "https://auth.clerk.garden/oauth2/auth?response_type=code&scope=openid%20profile&client_id=" client-id "&state=" (:login-state session))}
         :body ""
         :session session})
      
      "/callback"
      (let [query-strings (codec/form-decode (str (:query-string req)))
            session (:session req)
            login-state (:login-state session)]
        (if-let [code (get query-strings "code")]
          (if (= (get query-strings "state") login-state)
            (let [resp (-> (http/post "https://auth.clerk.garden/oauth2/token"
                                      {:basic-auth [client-id client-secret]
                                       :headers {:content-type "application/x-www-form-urlencoded"}
                                       :form-params {"code" code
                                                     "grant_type" "authorization_code"
                                                     "client_id" client-id}})
                           :body
                           (json/parse-string true))]
              (println :resp resp)

              (try
                (let [token (:id_token resp)
                      alg-opts (get issuers (token/decode-issuer token))
                      claims (token/decode token alg-opts)
                      _ (when-not (some #{client-id} (:aud claims))
                          (throw (JWTVerificationException. "The Claim 'aud' value doesn't contain the required audience")))
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

(defn wrap-auth [app]
  (if (and client-id client-secret)
    (wrap-auth-oidc app)
    (wrap-auth-fake app)))

(defn get-user [req]
  (get-in req [:session :user]))

(defn logged-in? [req]
  (some? (get-user req)))
