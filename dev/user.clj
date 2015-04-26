(ns user
  (:use plumbing.core)
  (:use criterium.core)
  (:require [clojure.pprint :refer [pprint pp]]
            [clojure.repl :refer :all]
            [clojure.tools.namespace.repl :refer [refresh]]
            [lens.schema :as schema]
            [lens.api :as api]
            [datomic.api :as d]
            [system]))

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
  (api/create-standard-workbook conn)
  (pst)
  (d/touch (first (api/queries (d/entity (d/db conn) 17592186045434))))

  (let [db (d/db conn)]
    (->> (d/q '[:find [?w ...] :where [?w :workbook/id]] db)
         (map #(d/entity db %))
         (map :workbook/id)))

  (api/workbook (d/db conn) "")

  @(d/transact conn [[:workbook.fn/create (d/tempid :db.part/user)]])

  )
