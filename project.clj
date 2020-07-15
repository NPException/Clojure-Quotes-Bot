(defproject cljqbot "0.1.0-SNAPSHOT"
  :description "Bot that posts various Clojure quotes taken from https://github.com/Azel4231/clojure-quotes to Telegram"
  :url "https://github.com/NPException/Clojure-Quotes-Bot"
  :license {:name "MIT License"
            :url "https://opensource.org/licenses/MIT"}
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [org.clojure/tools.logging "1.1.0"]
                 [clj-time "0.15.2"]
                 [http-kit "2.4.0-alpha5"]
                 [org.clojure/data.json "0.2.7"]]
  :main ^:skip-aot cljqbot.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})

