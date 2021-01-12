(ns cljqbot.core
  (:require [cljqbot.telegram :as telegram]
            [cljqbot.server :as server]
            [cljqbot.discord :as discord])
  (:gen-class))

(defn -main [& args]
  (doseq [task [(telegram/start-bot!)
                (server/start-bot!)
                (discord/start-bot!)]]
    ;; only exit program if all bots have stopped
    @task))
