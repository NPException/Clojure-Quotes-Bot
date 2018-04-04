(ns cljqbot.core
  (:require [clojure.string :as string]
            [org.httpkit.server :as server]
            [org.httpkit.client :as http]
            [clojure.data.json :as json])
  (:import [java.io FileWriter])
  (:gen-class))


(def base-url (let [token (slurp "telegram-api-token")]
                (str "https://api.telegram.org/bot" token "/")))
(def poll-seconds 10)
(defonce offset (atom -1)) ; -1 will only retrieve the latest update
(defonce running (atom true))


(defn async-post [path params]
  (http/post (str base-url path) {:form-params params}))

(defn post [path params]
  (let [resp @(async-post path params)
        resp (update resp :body #(and
                                  %
                                  (json/read-str % :key-fn keyword)))
        {:keys [status error body]} resp]
    #_(with-open [writer (FileWriter. "responses.txt" true)]
      (.write writer (str "POST:   " path "\n"
                          "params: " params "\n"
                          "status: " status "\n"
                          "error:  " error "\n"
                          "body:\n"))
      (clojure.pprint/pprint body writer)
      (.write writer "\n\n"))
    (if (or error (not= status 200))
      (println "Failed. Status:" status "Error:" error))
    (or body
        {:ok false})))
  


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


(defn from-user?
  "Checks if the update came from a normal user (not a bot)"
  [upd]
  (not (get-in upd [:message :is_bot])))


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


(declare random-formatted-quote)

(defn send-html-message [upd message]
  (let [chat-id (get-in upd [:message :chat :id])]
    ;; TODO: return path and parameter-map
    (async-post "sendMessage"
                {:chat_id chat-id
                 :text message
                 :parse_mode "HTML"
                 :disable_web_page_preview true
                 :disable_notification true})))

(defn post-help [upd]
  (send-html-message upd
                     (str "/help - displays this message\n"
                          "/quote - displays a random quote")))

(defn post-quote
  "Posts a random clojure quote to the chat that caused the
   given update."
  [upd]
  (let [from (get-in upd [:message :from :first_name])
        clj-quote (random-formatted-quote)]
    (send-html-message upd clj-quote)))


(defn process
  "Processes a single update"
  [upd]
  (cond
    (quote-command? upd) (post-quote upd)
    (help-command? upd) (post-help upd)))


(defn execute
  "fetches updates and processes them"
  []
  ;; TODO: take returned path-param combos,
  ;;       merge them as needed,
  ;;       post them,
  ;;       wait 1 to 3 seconds (3 if an involved chat was a group)
  (doseq [upd (get-updates)]
    (process upd)))


(defn start-bot []
  (reset! running true)
  (future (while @running (execute))))

(defn stop-bot []
  (reset! running false))


(defn -main [& args]
  (while true (execute)))


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

;; NOTES: - send single reply for all updates from the same chat?
