(ns cljqbot.format)

(defn ^:private source->telegram-html
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

(defn telegram-html
  "Formats a quote with Telegram compatible HTML"
  [qt]
  (str "<i>\"" (:text qt) "\"</i>\n"
       "<b>" (:quotee qt) "</b>"
       (source->telegram-html (:reference qt))))
