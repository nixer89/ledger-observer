(defproject ledger-observer "0.1.0-SNAPSHOT"
  :description "A visualization of the XRP-Ledger in real-time"
  :url "http://ledger.observer"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :dependencies [[org.clojure/clojure "1.9.0" :scope "provided"]
                 [org.clojure/core.async "0.4.474"]

                 [reacl "2.1.1"]
                 [de.active-group/active-viz "0.2.2"]

                 [cljsjs/jquery "3.2.1-0"]
                 [cljsjs/three "0.0.91-1"]
                 [cljs-ajax "0.8.0"]

                 [active-clojure "0.27.0" :exclusions [org.clojure/clojure]]
                 [com.cognitect/transit-cljs "0.8.256"]]


  #_:plugins #_[[lein-cljsbuild "1.1.7"]]
  :output-to "target/public/cljs-out/main.js"

  :profiles
  {:dev
   {:dependencies [[org.clojure/clojurescript "1.10.339"]
                   [com.bhauman/figwheel-main "0.1.9"]
                   ;; optional but recommended
                   [com.bhauman/rebel-readline-cljs "0.1.4"]]
    :resource-paths ["target"]
    :clean-targets ^{:protect false} ["target"]}}


  :aliases {"fig" ["trampoline" "run" "-m" "figwheel.main"]
            "build-prod" ["trampoline" "run" "-m" "figwheel.main" "-O" "advanced" "-bo" "prod"]
            "build-dev" ["trampoline" "run" "-m" "figwheel.main" "-b" "dev" "-r"]}
  )
