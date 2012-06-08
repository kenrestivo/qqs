(ns qqs.core-test
  (:use clojure.test
        qqs.core))


;; not really a test, more like a usage example
;; I needed to authenticate people based on email, where their UID is email
;; and they may have > 1 email address in their Google Apps account

(def step2-users {"foo@bar.com"
               {:username "foo"
                :roles #{::user ::admin}}})


(defn step2-creds
  [{:keys [emails identity] :as auth-map}]
  ;; auth map is usually:
  ;; {:emails ("foo@bar.com"),
  ;;  :first-name "Foo"
  ;;  :last-name "Bar"
  ;;  :identity "http://bar.com/openid?id=108942743407080579212"}
  (if-let [email-found (first (filter #(contains? step2-users %) emails))]
    (-> auth-map
        (dissoc :emails)
        (merge (get step2-users email-found))
        (assoc :email email-found))))

