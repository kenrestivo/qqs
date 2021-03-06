(ns qqs.core
  (:require [cemerick.friend :as friend]
            [cemerick.friend.workflows :as workflows]
            [clojure.string :as string]
            ring.util.response)
  (:use [cemerick.friend.util :only (gets)])
  (:import [com.google.step2
            AuthRequestHelper ConsumerHelper Step2
            AuthResponseHelper$ResultType Step2$AxSchema]
           [com.google.step2.discovery
            IdpIdentifier SecureUrlIdentifier
            Discovery2 LegacyXrdsResolver UrlHostMetaFetcher
            DefaultHostMetaFetcher]
           org.openid4java.discovery.UrlIdentifier
           org.openid4java.discovery.yadis.YadisResolver
           org.openid4java.discovery.html.HtmlResolver
           org.openid4java.util.HttpFetcherFactory
           org.openid4java.discovery.xri.LocalXriResolver
           [org.openid4java.message
            AuthRequest ParameterList]
           org.openid4java.consumer.ConsumerManager
           com.google.step2.http.DefaultHttpFetcher
           [com.google.step2.xmlsimplesign
            Verifier CachedCertPathValidator
            DefaultTrustRootsProvider DefaultCertValidator]))




(def ^{:private true} return-key :auth_return)

(defn-  goog-partial-url [host]
  (str "https://www.google.com/accounts/o8/.well-known/host-meta?hd=" host))


;;;  ConsumerManager must be persistent and stateful throughout life of servlet?
(def ^ConsumerHelper con-helper
  (ConsumerHelper.
   (ConsumerManager.)
   (Discovery2.
    (proxy [com.google.step2.discovery.UrlHostMetaFetcher]
        [(DefaultHttpFetcher.)]
      (getHostMetaUriForHost [host]
        (java.net.URI.
         (goog-partial-url host))))
    (LegacyXrdsResolver.
     (DefaultHttpFetcher.)
     (Verifier.
      (CachedCertPathValidator. (DefaultTrustRootsProvider.))
      (DefaultHttpFetcher.))
     (DefaultCertValidator. ))
    (HtmlResolver. (HttpFetcherFactory. ))
    (YadisResolver. (HttpFetcherFactory.))
    (LocalXriResolver.))))




(defn gen-auth-url [domain return-url]
  (let [^AuthRequestHelper arh
        (doto (.getAuthRequestHelper
               con-helper
               (IdpIdentifier. domain)
               return-url)
          (.requestUxIcon true)
          (.requestAxAttribute
           "email" (.getUri Step2$AxSchema/EMAIL) true 20)
          (.requestAxAttribute
           "firstName" (.getUri Step2$AxSchema/FIRST_NAME) true)
          (.requestAxAttribute
           "lastName"  (.getUri Step2$AxSchema/LAST_NAME) true))
        ^AuthRequest ar (doto (.generateRequest arh)
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

(defn build-credentials [^AuthRequestHelper arh]
  (let [result (.toString (.getAuthResultType  arh))]
    ;; TODO: throw an exception if result is not success
    (when (= result "AUTH_SUCCESS")
      (hash-map
       :identity (-> arh .getClaimedId .getIdentifier)
       :emails (seq (.getAxFetchAttributeValues  arh "email"))
       :first-name (str (.getAxFetchAttributeValue  arh "firstName"))
       :last-name (str (.getAxFetchAttributeValue  arh "lastName"))))))


;; this feels like a hack. exception handling needs more thought.
(defmacro wrap-failure
  [request step2-config & body]
  `(try
     ~@body
     (catch Exception e#
       (.printStackTrace e#)
       ((gets :login-failure-handler ~step2-config
              (::friend/auth-config ~request))
        ~request))))


(defn handle-return [{:keys [params session] :as request} step2-config]
  (let [di (::step2-disc session)
        return-url (#'friend/original-url request)
        ^ParameterList plist (ParameterList.
                              (clojure.walk/stringify-keys params))
        arh (.verify con-helper return-url plist di)
        credentials (build-credentials arh)]
    ((gets :credential-fn step2-config (::friend/auth-config request))
     credentials)))


(defn- munge-domain
  "Hack to allow regular OpenID auth for non-GoogleApps domains
   if you give it google.com or similar instead of a domain."
  [domain]
  (let [domain (string/replace domain #"(.*[/@])?(.+)" "$2")]
    (case domain
      "google.com" "https://www.google.com/accounts/o8/id"
      ;; could put yahoo or others in here
      domain)))


(defn- handle-init
  [domain step2-config {:keys [session] :as request}]
  (wrap-failure
   request step2-config
   (let [domain (munge-domain domain)
         return-url (#'friend/original-url
                     (assoc request :query-string
                            (str (name return-key) "=1")))
         auth-req (gen-auth-url domain return-url)]
     (assoc (ring.util.response/redirect (:url auth-req))
       :session (assoc session ::step2-disc (:di auth-req))))))





(defn workflow
  [& {:keys [credential-fn  login-failure-handler  domain-param step2-uri]
      :or {step2-uri "/step2"
           domain-param :domain}
      :as step2-config}]
  (fn [{:keys [uri request-method params session] :as request}]
    (when (= uri step2-uri)
      (cond
       (contains? params domain-param)
       (handle-init (domain-param params) step2-config request)
       (contains? params return-key)
       (wrap-failure
        request step2-config
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
           (update-in request [::friend/auth-config] merge step2-config))))
       ;; TODO correct response code?
       :else (login-failure-handler request)))))
