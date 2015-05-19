(ns lens.core
  (:require [clojure.tools.cli :as cli]
            [org.httpkit.server :refer [run-server]]
            [lens.app :refer [app]]
            [lens.util :refer [parse-int]]))

(defn cli [args]
  (cli/cli args
    ["-p" "--port" "Listen on this port" :default 8080 :parse-fn parse-int]
    ["-i" "--ip" "The IP to bind" :default "0.0.0.0"]
    ["-t" "--thread" "Number of worker threads" :default 4 :parse-fn parse-int]
    ["-d" "--database-uri" "The Datomic database URI to use"]))

(defn -main [& args]
  (let [[options args banner] (cli args)
        version (System/getProperty "lens.version")]
    (if (:database-uri options)
      (do
        (run-server (app (:database-uri options) version)
                    (merge {:worker-name-prefix "http-kit-worker-"} options))
        (println "Datomic:" (:database-uri options))
        (println "Server started")
        (println "Listen at" (str (:ip options) ":" (:port options)))
        (println "Using" (:thread options) "worker threads"))
      (println banner))))
