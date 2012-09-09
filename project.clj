(defproject qqs "0.1.3"
  :description "A friend workflow for Google Apps login using Step2 library."
  :url "http://github.com/kenrestivo/qqs"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.3.0"]
                 [com.cemerick/friend "0.0.8"]
                 [com.google.step2/step2-consumer "1-20120608.183645-1"]
                 ;; to force around a bug in httpclient 4.1.x [0]
                 [org.apache.httpcomponents/httpclient "[4.2-beta1]"]
                 ;; needed in some cases to force over clj-http if included
                 [org.apache.httpcomponents/httpcore "[4.2-beta1]"]]
  ;; TODO: put step2 on clojars or somewhere more permanent
  :repositories {"kens" "http://www.restivo.org/mvn/"})



;;; [0] http://forum.springsource.org/showthread.php?114685-Samples-OpenId-Google-Login-Hostname-in-Certificate-didn-t-match

