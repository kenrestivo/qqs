# qqs

A workflow for cemerick's [friend authentication system](http://github.com/cemerick/friend) to handle Google Apps auth using the [Step2 library](http://code.google.com/p/step2/).

## Usage

* Set up friend per its instructions
* Add QQS to your project.clj :dependencies:

```clojure
[qqs "0.1.0-SNAPSHOT"]
```

* Write a credentials function to check the incoming credentials against some database.
  There's an example step2-creds function in test/qqs/core_test.clj.
* Add a workflow

```clojure
(:require [qqs.core :as qqs] )
;;; ...
{:workflows
[(qqs/workflow :credential-fn somewhere/step2-creds)]
```

* Be sure to hit the step2-uri page with the domain, i.e. "/step2?domain=mydomain.com", otherwise it will login-failure on you.


## The name?

It's QQS, quick-quick-slow, the Texas 2-Step.

## Status

It works, I'm going to use it in a production app soon.

See the TODO-qqs.org file for moar.

## License

Copyright © 2012 ken restivo

Distributed under the Eclipse Public License, the same as Clojure.
