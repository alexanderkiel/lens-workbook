(ns lens.api
  (:require [clojure.core.reducers :as r]
            [datomic.api :as d]
            [lens.util :refer [uuid? entity?]]
            [lens.util :as util]))

;; ---- Single Accessors ------------------------------------------------------

(defn branch [db id]
  (d/entity db [:branch/id id]))

(defn workbook [db id]
  (d/entity db [:workbook/id id]))

(defn query [db id]
  (d/entity db [:query/id id]))

;; ---- Lists -----------------------------------------------------------------

(defn all-branches
  "Returns a reducible coll of all branches."
  [db]
  (->> (d/datoms db :avet :branch/id)
       (r/map #(d/entity db (:e %)))))

;; ---- Traversal -------------------------------------------------------------

(defn queries
  "Returns a lazy seq of all queries of a workbook or nil."
  [workbook]
  (some-> workbook :workbook/queries util/to-seq))

(defn query-cols
  "Returns a seq of all query columns of a query sorted by rank."
  [query]
  (->> (:query-col/_query query)
       (sort-by :query-col/rank)))

(defn query-cells
  "Returns a seq of all query cells of a query column sorted by rank."
  [query]
  (->> (:query-cell/_col query)
       (sort-by :query-cell/rank)))

;; ---- Creations -------------------------------------------------------------

(defn- create [conn fn]
  (let [tid (d/tempid :db.part/user)
        tx-result @(d/transact conn [(fn tid)])
        db (:db-after tx-result)]
    (d/entity db (d/resolve-tempid db (:tempids tx-result) tid))))

(defn create-standard-workbook
  "Creates a workbook entity with one query and a default of three empty query
  cols."
  [conn]
  {:post [(:workbook/id %)]}
  (let [tid (d/tempid :db.part/user)
        tx-result @(d/transact conn [[:workbook.fn/create-standard tid]])
        db (:db-after tx-result)]
    (d/entity db (d/resolve-tempid db (:tempids tx-result) tid))))

(defn create-branch
  "Creates a new branch based on the given workbook."
  [conn workbook]
  {:pre [(:workbook/id workbook)]
   :post [(:branch/id %)]}
  (create conn (fn [tid] [:workbook.fn/create-branch tid (:db/id workbook)])))

(defn add-query
  "Creates a new workbook which shares all queries with the old one and adds one
  new query to it. Returns the new workbook."
  [conn workbook]
  {:pre [(:workbook/id workbook)]
   :post [(:workbook/id %)]}
  (let [tid (d/tempid :db.part/user)
        tx-result @(d/transact conn [[:workbook.fn/add-query tid
                                      (:db/id workbook)]])
        db (:db-after tx-result)]
    (d/entity db (d/resolve-tempid db (:tempids tx-result) tid))))

(defn update-branch!
  "Updates the branch to point to the given workbook.

  Returns the branch based on the new database."
  [conn branch workbook]
  (let [r @(d/transact conn [[:db/add (:db/id branch) :branch/workbook (:db/id workbook)]])]
    (d/entity (:db-after r) (:db/id branch))))
