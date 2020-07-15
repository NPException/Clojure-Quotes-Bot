(ns cljqbot.telegram
  (:require [cljqbot.quotes :as quotes]
            [clojure.string :as string]
            [clojure.tools.logging :as log]
            [org.httpkit.client :as http]
            [clojure.data.json :as json]))

;; formatting functions

(defn ^:private format-source
  [source]
  (let [url (:url source)
        time (:time source)]
    (when url
      (str "\n("
           "<a href=\"" url "\">"
           "Source"
           "</a>"
           (when time (str ", Time: " time))
           ")"))))

(defn ^:private random-formatted-quote
  "Returns a random clojure quote, markdown formatted,
   ready to be sent to Telegram"
  []
  (let [qt (quotes/random-quote)]
    (str "<i>\"" (:text qt) "\"</i>\n"
         "<b>" (:quotee qt)  "</b>"
         (format-source (:reference qt)))))


;; telegram interaction

(def ^:private base-url (delay (str "https://api.telegram.org/bot" (slurp "telegram-api-token") "/")))
(def ^:private poll-seconds 10)
(defonce ^:private offset (atom -1)) ; -1 will only retrieve the latest update
(defonce ^:private running (atom true))


(defn ^:private convertJson
  [content]
  (try
    (json/read-str content :key-fn keyword)
    (catch Exception e
      (log/error e (str "Could not convert JSON to edn: " content)))))


(defn ^:private async-post
  [path params]
  (http/post (str @base-url path) {:form-params params}))

(defn ^:private post
  [path params]
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
      (log/warn (str "Failed. Status: " status " Error: " error " - Body: " body)))
    (or body {:ok false})))



(defn ^:private get-updates
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


(defn ^:private text
  "Retrieves the text from an update map"
  [upd]
  (get-in upd [:message :text]))


(defn ^:private command?
  "Checks if the given update matches the desired command"
  [upd cmd]
  (let [msg (text upd)
        cmd-full (str "/" cmd)
        cmd-at (str cmd-full "@")
        cmd-start (str cmd-full " ")]
    (and msg (or (= msg cmd-full)
                 (string/starts-with? msg cmd-start)
                 (string/starts-with? msg cmd-at)))))

(defn ^:private quote-command?
  [upd]
  (command? upd "quote"))

(defn ^:private help-command?
  [upd]
  (or (command? upd "help")
      (command? upd "start")))

(defn ^:private status-command?
  [upd]
  (command? upd "status"))


(defn ^:private send-html-message
  [upd message]
  (let [chat-id (get-in upd [:message :chat :id])]
    (async-post "sendMessage"
                {:chat_id chat-id
                 :text message
                 :parse_mode "HTML"
                 :disable_web_page_preview true
                 :disable_notification true})))

(defn ^:private post-help
  [upd]
  (send-html-message upd
                     (str "/help - displays this message\n"
                          "/quote - displays a random quote"))
  (log/debug (str "Posted help for: " upd)))

(defn ^:private post-quote
  "Posts a random clojure quote to the chat that caused the
   given update."
  [upd]
  (let [clj-quote (random-formatted-quote)]
    (send-html-message upd clj-quote)
    (swap! quotes/served inc)
    (log/debug (str "Posted quote for: " upd))))

(defn ^:private post-status
  "Posts the bot and JVM status to the chat"
  [upd]
  (send-html-message upd (str "<i>Delivered Quotes:</i> <b>" @quotes/served "</b>"))
  (log/debug (str "Posted status for: " upd)))


(defn ^:private process
  "Processes a single update"
  [upd]
  (cond
    (quote-command? upd) (post-quote upd)
    (help-command? upd) (post-help upd)
    (status-command? upd) (post-status upd)))


(defn ^:private execute!
  "fetches updates and processes them"
  []
  (doseq [upd (get-updates)]
    (process upd)))


(defn start-bot! []
  (log/info (str "Called " *ns* "/start-bot!"))
  (reset! running true)
  (future
    (try
      (while @running (execute!))
      (catch Exception e
        (log/error e "Telegram bot crashed")))))

(defn stop-bot! []
  (log/info (str "Called " *ns* "/stop-bot!"))
  (log/info "Called stop-bot")
  (reset! running false))