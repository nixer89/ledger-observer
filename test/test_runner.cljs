(ns ledger-observer.test-runner
  (:require [doo.runner :refer-macros [doo-tests]]
            [ledger-observer.socket-test]))

;; This isn't strictly necessary, but is a good idea depending
;; upon your application's ultimate runtime engine.
(enable-console-print!)

(doo-tests 'ledger-observer.socket-test)
