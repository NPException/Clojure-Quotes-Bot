(ns cljqbot.discord
  (:require [cljqbot.quotes :as quotes]
            [clojure.tools.logging :as log]
            [clojure.core.async :as a]
            [discljord.connections :as c]
            [discljord.messaging :as m]
            [discljord.events :as e]
            [cljqbot.format :as format]
            [clojure.string :as string]))

#_(defn ^:private log-event [event-type event-data]
  (try
    (let [file (clojure.java.io/file "events" (str (System/currentTimeMillis) "-" (name event-type) ".edn"))]
      (-> file .getParentFile .mkdirs)
      (spit
        file
        (with-out-str (clojure.pprint/pprint event-data))))
    (catch Exception e
      (.printStackTrace e))))



(def ^:private token (delay (string/trim (slurp "discord-api-token"))))

;; state will look like this
;  {:connection connection-ch
;   :event      event-ch
;   :messaging  messaging-ch
;   :id "733710171599143002"
;   :mentions #{"<@!733710171599143002>"
;               "<@733710171599143002>"}}

(defonce ^:private state (atom nil))


(defn ^:private me?
  "Returns whether or not the given user/id is this bot."
  [user-or-id]
  (let [bot-id (:id @state)]
    (or (= user-or-id bot-id)
        (= (:id user-or-id) bot-id))))

(defn ^:private mentioned?
  "Returns whether or not this bot has been mentioned in the content."
  [content]
  (some #(string/includes? content %) (:mentions @state)))


(defn ^:private on-ready
  [_event-type {:keys [user] :as _event-data}]
  (when-not (:id @state)
    (swap! state assoc
           :id (:id user)
           :mentions #{(str "<@!" (:id user) ">")
                       (str "<@" (:id user) ">")})))


(defn ^:private send-quote
  [_event-type {:keys [channel-id content author] :as _event-data}]
  (when (and (not (me? author))
             (mentioned? content))
    (m/create-message!
      (:messaging @state) channel-id
      :content (format/discord-markdown (quotes/random-quote)))))


(def ^:private handlers
  {:message-create [#'send-quote]
   :ready          [#'on-ready]})


(defn stop-bot!
  []
  (when-let [current-state @state]
    (reset! state nil)
    (log/info (str "Called cljqbot.discord/stop-bot!"))
    (m/stop-connection! (:messaging current-state))
    (c/disconnect-bot! (:connection current-state))
    (a/close! (:event current-state))))


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
