(ns cljqbot.core
  (:require [cljqbot.telegram :as telegram]
            [cljqbot.discord :as discord])
  (:gen-class))

(defn -main [& args]
  (doseq [task [(telegram/start-bot!)
                (discord/start-bot!)]]
    ;; only exit program if both bots have stopped
    @task))
