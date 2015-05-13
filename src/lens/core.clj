(ns lens.core
  (:require [clojure.string :as str]
            [clojure.tools.cli :as cli]
            [org.httpkit.server :refer [run-server]]
            [lens.app :refer [app]]
            [lens.util :refer [parse-int]]))

(def cli-options
  [["-p" "--port PORT" "Listen on this port" :default 8080 :parse-fn parse-int
    :validate [#(< 0 % 0x10000) "Must be a number between 0 and 65536"]]
   ["-i" "--ip IP" "The IP to bind" :default "0.0.0.0"]
   ["-t" "--thread NUM" "Number of worker threads" :default 4 :parse-fn parse-int
    :validate [#(< 0 % 64) "Must be a number between 0 and 64"]]
   ["-d" "--database-uri URI" "The Datomic database URI to use"
    :validate [#(.startsWith % "datomic")
               "Database URI has to start with datomic."]]
   [nil "--token-introspection-uri URI" "The OAuth2 token inspection URI to use"]
   ["-c" "--context-path PATH" (str "An optional context path under which the "
                                    "workbook service runs. Has to start and end "
                                    "with a slash.") :default "/"]
   ["-h" "--help" "Show this help"]])

(defn usage [options-summary]
  (->> ["Usage: lens-workbook [options]"
        ""
        "Options:"
        options-summary
        ""]
       (str/join "\n")))

(defn error-msg [errors]
  (str/join "\n" errors))

(defn exit [status msg]
  (println msg)
  (System/exit status))

(defn -main [& args]
  (let [{:keys [options errors summary]} (cli/parse-opts args cli-options)]
    (cond
      (:help options)
      (exit 0 (usage summary))

      errors
      (exit 1 (error-msg errors))

      (nil? (:database-uri options))
      (exit 1 "Missing database URI.")

      (nil? (:token-introspection-uri options))
      (exit 1 "Missing OAuth2 token inspection URI."))

    (let [{:keys [database-uri token-introspection-uri context-path]} options]
      (run-server (app database-uri token-introspection-uri context-path)
                  (merge {:worker-name-prefix "http-kit-worker-"} options))
      (println "Datomic:" database-uri)
      (println "OAuth2:" token-introspection-uri)
      (println "Context Path:" context-path)
      (println "Server started")
      (println "Listen at" (str (:ip options) ":" (:port options)))
      (println "Using" (:thread options) "worker threads"))))
