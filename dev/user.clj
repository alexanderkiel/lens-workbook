(ns user
  (:use plumbing.core)
  (:use criterium.core)
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.set :as set]
            [clojure.pprint :refer [pprint pp]]
            [clojure.repl :refer :all]
            [clojure.test :as test]
            [clojure.tools.namespace.repl :refer [refresh]]
            [clojure.core.reducers :as r]
            [clojure.core.async :as async :refer [chan go go-loop <! <!! >! alts! close!]]
            [lens.schema :as schema]
            [lens.routes :as routes]
            [lens.api :as api]
            [datomic.api :as d]
            [system]
            [lens.util :as util]))

(def system nil)

(defn init []
  (alter-var-root #'system (constantly (system/system (system/env)))))

(defn start []
  (alter-var-root #'system system/start))

(defn stop []
  (alter-var-root #'system system/stop))

(defn startup []
  (init)
  (start)
  (println "Server running at port" (:port system)))

(defn reset []
  (stop)
  (refresh :after 'user/startup))

(comment
  (startup)
  (reset))

(defn delete-database []
  (d/delete-database (:db-uri system)))

(defn create-database []
  (d/create-database (:db-uri system)))

(defn connect []
  (d/connect (:db-uri system)))

(defn load-schema []
  (schema/load-schema (connect)))

(comment
  (time (count-datoms db)))

(comment
  (load-schema)
  (def conn (connect))
  (api/add-standard-workbook conn)

  (let [db (d/db conn)]
    (->> (d/q '[:find [?w ...] :where [?w :workbook/id]] db)
         (map #(d/entity db %))
         (map :workbook/id)))

  (api/workbook (d/db conn) "")
  )
