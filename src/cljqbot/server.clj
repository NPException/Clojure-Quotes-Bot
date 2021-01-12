(ns cljqbot.server
  (:require [cljqbot.quotes :as quotes]
            [cljqbot.format :as format]
            [clojure.tools.logging :as log]
            [org.httpkit.server :refer [run-server]]
            [reitit.ring :as ring]))

(defn handler [_]
  {:status 200
   :headers {"Content-Type" "text/plain"}
   :body (format/plain-text (quotes/random-quote))})


(def ^:private app
  (ring/ring-handler
    (ring/router
      [["/quote" {:get handler}]])))


(defonce ^:private stop-atom (atom nil))

(defn start-bot!
  []
  (log/info (str "Called cljqbot.server/start-bot!"))
  (reset! stop-atom (run-server app {:port 8090})))

(defn stop-bot!
  []
  (log/info (str "Called cljqbot.server/stop-bot!"))
  (when-let [stop-fn @stop-atom]
    (stop-fn))
  (reset! stop-atom nil))