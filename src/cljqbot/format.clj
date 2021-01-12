(ns cljqbot.format)

;; TELEGRAM

(defn ^:private source->telegram-html
  [source]
  (let [url (:url source)
        time (:time source)]
    (when url
      (str "\n("
           "<a href=\"" url "\">"
           "Source"
           "</a>"
           (when time (str ", Timestamp: " time))
           ")"))))

(defn telegram-html
  "Formats a quote with Telegram compatible HTML"
  [qt]
  (str "<i>\"" (:text qt) "\"</i>\n"
       "<b>" (:quotee qt) "</b>"
       (source->telegram-html (:reference qt))))



;; DISCORD

(defn discord-mention
  "Creates a formatted mention for the given user (id string or map)."
  [user]
  (let [id (if (string? user) user (:id user))]
    (str "<@!" id ">")))


(defn ^:private source->discord-markdown
  [source]
  (let [url (:url source)
        time (:time source)]
    (when url
      (str "\n(Source: <" url ">"
           (when time (str ",\n Timestamp: " time))
           ")"))))

(defn discord-markdown
  "Formats a quote with Discord compatible Markdown"
  [qt]
  (str "> " (:text qt) "\n"
       "_**~ " (:quotee qt) "**_"
       (source->discord-markdown (:reference qt))))


;; PLAIN TEXT

(defn ^:private source->plain-text
  [source]
  (let [url (:url source)
        time (:time source)]
    (when url
      (str "\n\nSource:"
           "\n  " url
           (when time (str "\n  Timestamp: " time))))))

(defn plain-text
  "Formats a quote in plain text"
  [qt]
  (str (:text qt) \newline
       "~ " (:quotee qt)
       (source->plain-text (:reference qt))))