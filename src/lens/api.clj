(ns lens.api
  (:require [datomic.api :as d]
            [lens.util :refer [uuid? entity?]]))

;; ---- Single Accessors ------------------------------------------------------

(defn workbook [db id]
  {:pre [(uuid? id)]}
  (d/entity db [:workbook/id id]))

(defn query [db id]
  {:pre [(uuid? id)]}
  (d/entity db [:query/id id]))

;; ---- Traversal -------------------------------------------------------------

(defn queries
  "Returns a seq of all queries of a workbook sorted by rank."
  [workbook]
  (->> (:query/_workbook workbook)
       (sort-by :query/rank)))

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

;; ---- Additions/Retractions -------------------------------------------------

(defn add-standard-workbook
  "Adds a standard workbook with one query and three empty query columns and
  returns it."
  [conn]
  {:post [(entity? %)]}
  (let [tid (d/tempid :db.part/user)
        tx-result @(d/transact conn [[:add-standard-workbook tid]])
        db (:db-after tx-result)]
    (d/entity db (d/resolve-tempid db (:tempids tx-result) tid))))
