(ns matrix-terminal-bot.core
  (:require
    ["child_process" :refer [spawn]]
    ["matrix-bot-sdk" :as bot]
    [cljs.core.async :refer [go go-loop chan <! >! put! timeout]]
    [cljs.core.async.interop :refer-macros [<p!]]
    [clojure.string :as str]))


(defn init-matrix-client
  [token url]
  (let [storage (new bot/SimpleFsStorageProvider "data.json")]
    (new bot/MatrixClient url token storage)))


(defn send-message
  [client room message]
  (go-loop [time 100]
    (let [result (try
                   (<p! (.sendHtmlText client room message))
                   :success
                   (catch js/Error e
                     e))]
      (when-not (= result :success)
        (<! (timeout time))
        (recur (* time 2))))))


(defn handle-message
  [client bot-room process room event]
  (go
    (when-not (= (.-sender event) (<p! (.getUserId client)))
      (when (and (= room bot-room) (= (.. event -content -msgtype) "m.text"))
        (.write (.-stdin @process) (str (.. event -content -body) "\n"))))))


(declare inform-about-exit receive-output)


(defn start-process
  [client bot-room process output-queue sending max-message-length program]
  (reset! process (spawn program))
  (.on @process "close" #(inform-about-exit client bot-room process output-queue sending max-message-length program %1 %2))
  (.on (.-stdout @process) "data" #(receive-output client bot-room output-queue sending max-message-length %))
  (.on (.-stderr @process) "data" #(receive-output client bot-room output-queue sending max-message-length %)))


(defn inform-about-exit
  [client bot-room process output-queue sending max-message-length program code signal]
  (go
    (let [message
          (cond (and code signal) (str "The program was terminated by signal " signal " and exited with code " code ". Restarting...")
                signal (str "The program was terminated by signal " signal ". Restarting...")
                code (str "The program exited with code " code ". Restarting..."))]
      (<! (receive-output client bot-room output-queue sending max-message-length message))
      (start-process client bot-room process output-queue sending max-message-length program))))


(defn send-output
  [client bot-room sending max-message-length output]
  (go
    (when @sending
      (let [channel (chan)]
        (add-watch sending :send-output
                   (fn [key reference old-value new-value]
                     (if (= new-value false) (put! channel :ready))))
        (<! channel)
        (remove-watch sending :send-output)))
    (reset! sending true)
    (loop [length 0 message "" output output]
      (cond (empty? output) (do
                              (<! (send-message client bot-room (str "<pre>" (subs message 1) "</pre>")))
                              (reset! sending false))
            (> (count (first output)) max-message-length) (do
                                                            (<! (send-message client bot-room "<pre>Error: line too long.</pre>"))
                                                            (recur length message (rest output)))
            (> (+ length 1 (count (first output))) max-message-length) (do
                                                                         (<! (send-message client bot-room (str "<pre>" (subs message 1) "</pre>")))
                                                                         (recur 0 "" output))
            :else
            (recur (+ length 1 (count (first output))) (str message "\n" (first output)) (rest output))))))


(defn receive-output
  [client bot-room output-queue sending max-message-length output]
  (go
    (let [output-string (.toString output)]
      (if @output-queue
        (swap! output-queue concat (str/split-lines output-string))
        (do (reset! output-queue (str/split-lines output-string))
            (<! (timeout 500))
            (send-output client bot-room sending max-message-length @output-queue)
            (reset! output-queue nil))))))


(defn main
  [& args]
  (go
    (let [matrix-token (.. js/process -env -MATRIX_TOKEN)
          matrix-server-url (.. js/process -env -MATRIX_SERVER_URL)
          matrix-room-id (.. js/process -env -MATRIX_ROOM_ID)
          program (.. js/process -env -PROGRAM)
          max-message-length (.. js/process -env -MAX_MESSAGE_LENGTH)]
      (when-not matrix-token (throw "MATRIX_TOKEN environment variable not set"))
      (when-not matrix-server-url (throw "MATRIX_SERVER_URL environment variable not set"))
      (when-not matrix-room-id (throw "MATRIX_ROOM_ID environment variable not set"))
      (when-not program (throw "PROGRAM environment variable not set"))
      (let [client (init-matrix-client matrix-token matrix-server-url)
            process (atom nil)
            output-queue (atom nil)
            sending (atom false)
            max-message-length (if max-message-length max-message-length 64000)]
        (<p! (. client start))
        (start-process client matrix-room-id process output-queue sending max-message-length program)
        (. client on "room.message" #(handle-message client matrix-room-id process %1 %2))
        (println "The bot is running.")))))
