# qqs

A workflow for cemerick's [friend authentication system](http://github.com/cemerick/friend) to handle Google Apps auth using the [Step2 library](http://code.google.com/p/step2/).

## Usage

* Set up friend per its instructions
* Add QQS to your project.clj :dependencies:

```clojure
[qqs "0.1.3"]
```

* Write a credentials function to check the incoming credentials against some database.
  There's an example step2-creds function in test/qqs/core_test.clj.
* Add a workflow to your friend/authenticate function:

```clojure
(:require [qqs.core :as qqs] )
;;; ...
(friend/authenticate 
  {:workflows
    [(qqs/workflow :credential-fn somewhere/step2-creds)]
  ;; ...
  }
  :login-failure-handler you-must-have-something-here-or-you-will-NPE
  ;; ...
 })
```

* Be sure to hit the step2-uri page with the domain, i.e. "/step2?domain=mydomain.com".
* If you use the domain "google.com", or anything ending in "google.com", it'll do a regular OpenID lookup, using Google's login screen. This way, sites that want to allow multiple Google Apps domains can have one login screen.


## Status

There's an demo using this workflow, up on [http://young-cloud-3820.herokuapp.com/](http://young-cloud-3820.herokuapp.com/). You can try it if you have an account on a Google Apps domain.

It is in use, in production at the moment.

See the TODO-qqs.org file for moar.

## The name?

It's QQS, quick-quick-slow, the Texas 2-Step.

## License

Copyright © 2012 ken restivo

Distributed under the Eclipse Public License, the same as Clojure.
