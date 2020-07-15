(defproject cljqbot "0.1.0-SNAPSHOT"
  :description "Bot that posts various Clojure quotes taken from https://github.com/Azel4231/clojure-quotes to Telegram"
  :url "https://github.com/NPException/Clojure-Quotes-Bot"
  :license {:name "MIT License"
            :url "https://opensource.org/licenses/MIT"}
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [http-kit "2.3.0"]
                 [org.clojure/data.json "0.2.7"]]
  :jvm-opts ["--add-modules" "java.xml.bind"]
  :main ^:skip-aot cljqbot.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})

