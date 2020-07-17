(ns cljqbot.discord
  (:require [cljqbot.quotes :as quotes]
            [clojure.tools.logging :as log]
            [clojure.core.async :as a]
            [discljord.connections :as c]
            [discljord.messaging :as m]
            [discljord.events :as e]
            [cljqbot.format :as format]
            [clojure.string :as string]))

(def ^:private token (delay (slurp "discord-api-token")))
(def ^:private bot-id "733710171599143002")
(def ^:private bot-mentions #{(str "<@!" bot-id ">")
                              (str "<@" bot-id ">")})

(defn ^:private me?
  [user-or-id]
  (or (= user-or-id bot-id)
      (= (:id user-or-id) bot-id)))


(defonce ^:private state (atom nil))


#_(defn ^:private log-event [event-type event-data]
  (try
    (let [file (io/file "events" (str (System/currentTimeMillis) "-" (name event-type) ".edn"))]
      (-> file .getParentFile .mkdirs)
      (spit
        file
        (with-out-str (clojure.pprint/pprint event-data))))
    (catch Exception e
      (.printStackTrace e))))


(defn ^:private send-quote
  [_event-type {:keys [channel-id content author] :as _event-data}]
  (when (and (not (me? author))
             (some #(string/includes? content %) bot-mentions))
    (m/create-message!
      (:messaging @state) channel-id
      :content (format/discord-markdown (quotes/random-quote)))))


(def ^:private handlers
  {:message-create [#'send-quote]})


(defn stop-bot!
  []
  (when-let [state @state]
    (reset! state nil)
    (log/info (str "Called cljqbot.discord/stop-bot!"))
    (m/stop-connection! (:messaging state))
    (c/disconnect-bot! (:connection state))
    (a/close! (:event state))))


(defn start-bot!
  []
  (log/info (str "Called cljqbot.discord/start-bot!"))
  (future
    (when (nil? @state)
      (let [event-ch (a/chan 100)
            connection-ch (c/connect-bot! @token event-ch)
            messaging-ch (m/start-connection! @token)
            init-state {:connection connection-ch
                        :event      event-ch
                        :messaging  messaging-ch}]
        (reset! state init-state)
        (try
          (e/message-pump! event-ch (partial e/dispatch-handlers #'handlers))
          (finally
            (stop-bot!)))))))
