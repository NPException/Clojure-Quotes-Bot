(defproject cljqbot "0.1.0-SNAPSHOT"
  :description "Bot that posts various Clojure quotes taken from https://github.com/Azel4231/clojure-quotes to Telegram"
  :url "https://github.com/NPException/Clojure-Quotes-Bot"
  :license {:name "MIT License"
            :url "https://opensource.org/licenses/MIT"}
  :pedantic? :abort
  :dependencies [[org.clojure/clojure "1.10.3"]
                 [org.clojure/tools.logging "1.1.0"]
                 [ch.qos.logback/logback-classic "1.2.3"]
                 [org.suskalo/discljord "1.1.1"]
                 [tick "0.5.0-RC4"]
                 [http-kit "2.6.0-alpha1"]
                 [metosin/reitit "0.5.11"]
                 [org.clojure/data.json "2.4.0"]]
  :main ^:skip-aot cljqbot.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})

