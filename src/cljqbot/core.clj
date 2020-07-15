(ns cljqbot.core
  (:require [cljqbot.telegram :as telegram])
  (:gen-class))

(defn -main [& args]
  @(telegram/start-bot!))
