# Clojure Quotes Bot

This is a small toy program, that interacts with the
Telegram Bot API to deliver quotes from
[the Clojure Quotes repo](https://github.com/Azel4231/clojure-quotes)
to the Telegram Instant Messaging App.

You can add the bot on Telegram: [Clojure Quotes (@cljqbot)](https://telegram.me/cljqbot)

At the moment I have to manually restart the bot when it's no longer working,
so don't worry if it does not react to your commands immediately. :)

## Build & Usage

Build with Leiningen:

    lein uberjar

To run it, put a file named `telegram-api-token` next to your jar,
which must contain your bot's Telegram API token. You can
then start the bot like this:

    $ java -jar cljqbot-0.1.0-standalone.jar

## License

Copyright © 2018 Dirk Wetzel

Distributed under the MIT License
