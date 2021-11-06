(ns cljqbot.discord
  (:require [cljqbot.quotes :as quotes]
            [clojure.tools.logging :as log]
            [clojure.core.async :as a]
            [discljord.connections :as c]
            [discljord.messaging :as m]
            [discljord.events :as e]
            [cljqbot.format :as format]
            [clojure.stacktrace :as st]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
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



(def ^:private token (delay (try (string/trim (slurp "discord-api-token"))
                                 (catch Exception _))))

(def ^:private help-text (slurp (io/resource "cljqbot/discord-help.md")))

;; state will look like this
;  {:connection connection-ch
;   :event      event-ch
;   :messaging  messaging-ch
;   :id "733710171599143002"
;   :mentions #{"<@!733710171599143002>"
;               "<@733710171599143002>"}}

(defonce ^:private state (atom nil))


(defn ^:private me?
  "Returns whether the given user/id is this bot."
  [user-or-id]
  (let [bot-id (:id @state)]
    (or (= user-or-id bot-id)
        (= (:id user-or-id) bot-id))))

(defn ^:private mentioned?
  "Returns whether this bot has been mentioned in the content."
  [event_data]
  (let [me (:id @state)]
    (some->> (:mentions event_data)
             (some #(= me (:id %))))))


(defn ^:private on-ready
  [_event-type {:keys [user] :as _event-data}]
  (when-not (:id @state)
    (swap! state assoc
           :id (:id user)
           :mentions #{(str "<@!" (:id user) ">")
                       (str "<@" (:id user) ">")})))


(defn ^:private send-message
  [channel-id message]
  (m/create-message! (:messaging @state) channel-id :content message))


(defmulti ^:private run-command
  "Implement for supported commands"
  (fn [_channel-id & [fn-sym]]
    fn-sym))

;; sends a message about available commands
(defmethod run-command 'help
  [channel-id _]
  (send-message channel-id help-text))

;; sends a response for unknown commands
(defmethod run-command :default
  [channel-id cmd-sym & _args]
  (send-message channel-id (str "I don't know the command `" cmd-sym "`.")))


(defn ^:private try-command
  [channel-id code-string]
  (let [code (try (edn/read-string code-string)
                  (catch Exception _))]
    (if-not code
      (send-message channel-id (str "Error. Message must contain valid EDN, but doesn't:\n```\n" code-string "\n```"))
      (try
        (apply run-command channel-id code)
        (catch Exception e
          (st/print-cause-trace e)
          (send-message channel-id (str "Something went wrong...")))))))


(defn ^:private send-quote
  [channel-id]
  (send-message channel-id (format/discord-markdown (quotes/random-quote))))


(defn ^:private on-message
  [_event-type {:keys [author channel-id content] :as event-data}]
  (when (and (not (me? author))
             (mentioned? event-data))
    (if-let [code-index (string/index-of content "!(")]
      (try-command channel-id (subs content (inc code-index)))
      (send-quote channel-id))))


(def ^:private handlers
  {:message-create [#'on-message]
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
  (when @token
    (log/info (str "Called cljqbot.discord/start-bot!"))
    (future
      (when (nil? @state)
        (let [event-ch (a/chan 100)
              connection-ch (c/connect-bot! @token event-ch)
              messaging-ch (m/start-connection! @token)
              init-state {:connection connection-ch
                          :event event-ch
                          :messaging messaging-ch}]
          (reset! state init-state)
          (try
            (e/message-pump! event-ch (partial e/dispatch-handlers #'handlers))
            (finally
              (stop-bot!))))))))
