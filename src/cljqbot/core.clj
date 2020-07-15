(ns cljqbot.core
  (:require [clojure.string :as string]
            [org.httpkit.client :as http]
            [clojure.data.json :as json])
  (:import (java.util Date))
  (:gen-class))


(def base-url (let [token (slurp "telegram-api-token")]
                (str "https://api.telegram.org/bot" token "/")))
(def poll-seconds 10)
(defonce offset (atom -1)) ; -1 will only retrieve the latest update
(defonce running (atom true))

(defonce served (atom 0)) ; how many quotes were delivered


(defn log [& args]
  (apply println (str (Date.) "> ") args)
  (println))


(defn convertJson [content]
  (try
    (json/read-str content :key-fn keyword)
    (catch Exception e
      (log :ERROR
           "Could not convert JSON to edn.\n"
           (.getMessage e)
           "\n Json was:" content
           "\n -> StackTrace:" (.getStackTrace e)))))


(defn async-post [path params]
  (http/post (str base-url path) {:form-params params}))

(defn post [path params]
  (let [resp @(async-post path params)
        resp (update resp :body #(and % (convertJson %)))
        {:keys [status error body]} resp]
    #_(with-open [writer (FileWriter. "responses.txt" true)]
      (.write writer (str "POST:   " path "\n"
                          "params: " params "\n"
                          "status: " status "\n"
                          "error:  " error "\n"
                          "body:\n"))
      (clojure.pprint/pprint body writer)
      (.write writer "\n\n"))
    (when (or error (not= status 200))
      (log :WARN "Failed. Status:" status "Error:" error "- Body: " body))
    (or body {:ok false})))
  


(defn get-updates
  "Retrieves all updates based on the current offset.
   Will use long-polling to get the updates"
  []
  (let [params {:timeout poll-seconds :offset @offset}
        {:keys [ok result]} (post "getUpdates" params)]
    (if (or (not ok) (empty? result))
      []
      (do (reset! offset
                  (inc (apply max (map :update_id result))))
          result))))


(defn text
  "Retrieves the text from an update map"
  [upd]
  (get-in upd [:message :text]))


(defn command?
  "Checks if the given update matches the desired command"
  [upd cmd]
  (let [msg (text upd)
        cmd-full (str "/" cmd)
        cmd-at (str cmd-full "@")
        cmd-start (str cmd-full " ")]
    (and msg (or (= msg cmd-full)
                 (string/starts-with? msg cmd-start)
                 (string/starts-with? msg cmd-at)))))

(defn quote-command? [upd]
  (command? upd "quote"))

(defn help-command? [upd]
  (or (command? upd "help")
      (command? upd "start")))

(defn status-command? [upd]
  (command? upd "status"))


(declare random-formatted-quote)

(defn send-html-message [upd message]
  (let [chat-id (get-in upd [:message :chat :id])]
    (async-post "sendMessage"
                {:chat_id chat-id
                 :text message
                 :parse_mode "HTML"
                 :disable_web_page_preview true
                 :disable_notification true})))

(defn post-help [upd]
  (send-html-message upd
                     (str "/help - displays this message\n"
                          "/quote - displays a random quote"))
  (log :INFO "Posted help for: " upd))

(defn post-quote
  "Posts a random clojure quote to the chat that caused the
   given update."
  [upd]
  (let [clj-quote (random-formatted-quote)]
    (send-html-message upd clj-quote)
    (swap! served inc)
    (log :INFO "Posted quote for:" upd)))

(defn post-status
  "Posts the bot and JVM status to the chat"
  [upd]
  (send-html-message upd (str "<i>Delivered Quotes:</i> <b>" @served "</b>"))
  (log :INFO "Posted status for:" upd))


(defn process
  "Processes a single update"
  [upd]
  (cond
    (quote-command? upd) (post-quote upd)
    (help-command? upd) (post-help upd)
    (status-command? upd) (post-status upd)))


(defn execute!
  "fetches updates and processes them"
  []
  (doseq [upd (get-updates)]
    (process upd)))


(defn start-bot! []
  (log "Called start-bot")
  (reset! running true)
  (future (while @running (execute!))))

(defn stop-bot! []
  (log "Called stop-bot")
  (reset! running false))


(defn -main [& args]
  (log "Starting Clojure Quotes Bot")
  (try
    (while true (execute!))
    (catch Exception e
      (log :ERROR "Bot crashed. ->" (.getMessage e)
           " -> StackTrace:" (.getStackTrace e)))))


;; quote stuff
(defonce clj-quotes
  (-> (slurp "https://github.com/Azel4231/clojure-quotes/raw/master/quotes.edn")
      (string/replace "#:clojure-quotes.core" "")
      read-string))

(defn random-quote []
  (clj-quotes (rand-int (count clj-quotes))))

(defn format-time [qt]
  (let [time (:time (:reference qt))]
    (if time (str ", Time: " time) "")))

(defn format-source [qt]
  (let [url (:url (:reference qt))]
    (if url
      (str "\n("
           "<a href=\"" url "\">"
           "Source"
           "</a>"
           (format-time qt)
           ")")
      "")))

(defn format-quote [qt]
  (str "<i>\"" (:text qt) "\"</i>\n"
       "<b>" (:quotee qt)  "</b>"
       (format-source qt)))

(defn random-formatted-quote
  "Returns a random clojure quote, markdown formatted,
   ready to be sent to Telegram"
  []
  (format-quote (random-quote)))
