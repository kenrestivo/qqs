(ns qqs.core
  (:require [cemerick.friend :as friend]
            [cemerick.friend.workflows :as workflows]
            [clojure.string :as string]
            ring.util.response)
  (:use [cemerick.friend.util :only (gets)])
  (:import [com.google.step2
            AuthRequestHelper ConsumerHelper Step2]
           [com.google.step2.discovery
            IdpIdentifier Discovery2 LegacyXrdsResolver UrlHostMetaFetcher]
           [org.openid4java.message
            AuthRequest ParameterList]
           org.openid4java.consumer.ConsumerManager
           com.google.step2.http.DefaultHttpFetcher
           [com.google.step2.xmlsimplesign
            Verifier CachedCertPathValidator
            DefaultTrustRootsProvider DefaultCertValidator]))
  



(def ^{:private true} return-key :auth_return)

(def ^{:private true}  goog-partial-url
  "https://www.google.com/accounts/o8/.well-known/host-meta?hd=")




;;;  ConsumerManager must be persistent and stateful throughout life of servlet?
(def con-helper
  (ConsumerHelper.
   (ConsumerManager.)  
   (Discovery2.
    (proxy [com.google.step2.discovery.UrlHostMetaFetcher]
        [(DefaultHttpFetcher.)]
      (getHostMetaUriForHost [host]
        (java.net.URI.
         (str goog-partial-url host))))    
    (LegacyXrdsResolver.
     (DefaultHttpFetcher.)
     (Verifier.
      (CachedCertPathValidator. (DefaultTrustRootsProvider.))
      (DefaultHttpFetcher.))
     (DefaultCertValidator. ))
    nil nil nil)))




(defn gen-auth-url [domain return-url]
  (let [arh (doto (.getAuthRequestHelper
                   con-helper
                   (IdpIdentifier. domain)
                   return-url)
              (.requestUxIcon true)
              (.requestAxAttribute
               "email" "http://axschema.org/contact/email" true 20)
              (.requestAxAttribute
               "firstName" "http://axschema.org/namePerson/first" true)
              (.requestAxAttribute
               "lastName" "http://axschema.org/namePerson/last" true))
        ar (doto (.generateRequest arh) 
             ;; google requires realm to = return url
             (.setRealm  return-url))]
    ;; and here is the url at last!!
    (hash-map
     :url (.getDestinationUrl ar true)
     ;; will need this in session for the return trip
     :di (.getDiscoveryInformation arh))))



;; i could go all
;;(into {} (map #(vector (keyword %) (.getAxFetchAttributeValue  arh %)) attrs))
;; but for only two attributes, the repetition is clearer

(defn build-credentials [arh]
  (let [result (.toString (.getAuthResultType  arh))]
    ;; TODO: throw an exception if result is not success
    (when (= result "AUTH_SUCCESS")
      (hash-map
       :identity (-> arh .getClaimedId .getIdentifier)
       :emails (seq (.getAxFetchAttributeValues  arh "email"))
       :first-name (str (.getAxFetchAttributeValue  arh "firstName"))
       :last-name (str (.getAxFetchAttributeValue  arh "lastName"))))))
       


(defn handle-return [{:keys [params session] :as req} step2-config]
  (let [di (::step2-disc session)
        return-url (#'friend/original-url req)
        plist (ParameterList.
               (clojure.walk/stringify-keys params))
        arh (.verify con-helper return-url plist di)
        credentials (build-credentials arh)]
    (or ((gets :credential-fn step2-config (::friend/auth-config req))
         credentials)
        ((gets :login-failure-handler step2-config (::friend/auth-config req))
         req))))


(defn- handle-init
  [domain {:keys [session] :as request}]
  (let [domain (string/replace domain #"(.*[/@])?(.+)" "$2")
        return-url (#'friend/original-url
                    (assoc request :query-string
                           (str (name return-key) "=1")))
        auth-req (gen-auth-url domain return-url)]
    (assoc (ring.util.response/redirect (:url auth-req))
      :session (assoc session ::step2-disc (:di auth-req)))))




(defn workflow
  [& {:keys [credential-fn  login-failure-handler  domain-param step2-uri]
      :or {step2-uri "/step2"
           domain-param :domain}
      :as step2-config}]
  (fn [{:keys [uri request-method params session] :as request}]
    (when (= uri step2-uri)
      (cond
       (contains? params domain-param)
       (try
         (handle-init (domain-param params) request)
         (catch Exception e
           (.printStackTrace e)
           (ring.util.response/redirect
            (-> request ::friend/auth-config :unauthorized-uri))))
       (contains? params return-key)
       (if-let [auth-map
                (handle-return (assoc request :params params) step2-config)]
         (vary-meta auth-map merge {::friend/workflow ::step2
                                    ;; for proposed friend cleanup:
                                    ;; https://github.com/cemerick/friend/pull/11
                                    :session (dissoc session ::step2-disc)
                                    :type ::friend/auth})
         ((or (gets :login-failure-handler step2-config
                    (::friend/auth-config request))
              #'workflows/interactive-login-redirect)
          (update-in request [::friend/auth-config] merge step2-config)))
       (contains? params domain-param) (handle-init (domain-param params) request)
       ;; TODO correct response code?
       :else (login-failure-handler request)))))
       





  
